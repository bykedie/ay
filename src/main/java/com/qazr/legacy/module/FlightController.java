package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class FlightController {
    private static int suspendTicks;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private EntityPlayerSP controlledPlayer;
    private boolean originalAllowFlying;
    private boolean originalFlying;
    private float originalFlySpeed;

    public FlightController(ModuleManager modules) {
        this.modules = modules;
    }

    public static void suspend(int ticks) {
        suspendTicks = Math.max(suspendTicks, ticks);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean normalFlight = modules.isEnabled(ModuleId.FLIGHT) && ModConfig.flightElytra && !ModConfig.flightBoat;
        if (!normalFlight || mc.player == null || mc.world == null) restoreCapabilities();
        if (suspendTicks > 0) {
            stabilizeDuringSuspend();
            suspendTicks--;
            return;
        }
        if (!modules.isEnabled(ModuleId.FLIGHT)) return;
        if (mc.player == null || mc.world == null || mc.player.connection == null || mc.currentScreen != null) return;
        if (ModConfig.flightBoat) boatFlight();
        else if (ModConfig.flightElytra) normalFlight();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        restoreCapabilities();
        suspendTicks = 0;
    }

    private void normalFlight() {
        if (mc.player.isRiding()) {
            restoreCapabilities();
            return;
        }
        captureCapabilities();
        mc.player.capabilities.allowFlying = true;
        mc.player.capabilities.isFlying = true;
        mc.player.capabilities.setFlySpeed(flySpeedFor(ModConfig.flightSpeed));
        double[] horizontal = movementFor(mc.player.rotationYaw, mc.player.movementInput.moveForward,
            mc.player.movementInput.moveStrafe, ModConfig.flightSpeed);
        mc.player.motionX = horizontal[0];
        mc.player.motionY = verticalMotion(mc.gameSettings.keyBindJump.isKeyDown(),
            mc.gameSettings.keyBindSneak.isKeyDown(), ModConfig.flightVerticalSpeed);
        mc.player.motionZ = horizontal[1];
        mc.player.fallDistance = 0.0F;
    }

    private void boatFlight() {
        restoreCapabilities();
        Entity riding = mc.player.getRidingEntity();
        if (!(riding instanceof EntityBoat)) return;
        double[] motion = requestedMotion();
        riding.motionX = motion[0];
        riding.motionY = motion[1];
        riding.motionZ = motion[2];
        riding.fallDistance = 0.0F;
        riding.setPosition(riding.posX + motion[0], riding.posY + motion[1], riding.posZ + motion[2]);
        mc.player.connection.sendPacket(new CPacketVehicleMove(riding));
    }

    private void stabilizeDuringSuspend() {
        if (mc.player == null || mc.world == null) return;
        mc.player.fallDistance = 0.0F;
        if (!modules.isEnabled(ModuleId.FLIGHT)) return;
        mc.player.motionX = 0.0;
        mc.player.motionY = 0.0;
        mc.player.motionZ = 0.0;
    }

    private void captureCapabilities() {
        if (controlledPlayer == mc.player) return;
        restoreCapabilities();
        controlledPlayer = mc.player;
        originalAllowFlying = mc.player.capabilities.allowFlying;
        originalFlying = mc.player.capabilities.isFlying;
        originalFlySpeed = mc.player.capabilities.getFlySpeed();
    }

    private void restoreCapabilities() {
        if (controlledPlayer == null) return;
        controlledPlayer.capabilities.allowFlying = originalAllowFlying;
        controlledPlayer.capabilities.isFlying = originalFlying;
        controlledPlayer.capabilities.setFlySpeed(originalFlySpeed);
        controlledPlayer = null;
    }

    private double[] requestedMotion() {
        double[] horizontal = movementFor(mc.player.rotationYaw, mc.player.movementInput.moveForward,
            mc.player.movementInput.moveStrafe, ModConfig.flightSpeed);
        double vertical = verticalMotion(mc.gameSettings.keyBindJump.isKeyDown(),
            mc.gameSettings.keyBindSneak.isKeyDown(), ModConfig.flightVerticalSpeed);
        return new double[] {horizontal[0], vertical, horizontal[1]};
    }

    static float flySpeedFor(double blocksPerTick) {
        return (float) Math.max(0.005, Math.min(1.0, blocksPerTick / 10.0));
    }

    static double verticalMotion(boolean jump, boolean sneak, double speed) {
        if (jump == sneak) return 0.0;
        return jump ? speed : -speed;
    }

    static double[] movementFor(float yaw, double forward, double strafe, double speed) {
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length <= 0.01) return new double[] {0.0, 0.0};
        forward /= length;
        strafe /= length;
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new double[] {forward * -sin * speed + strafe * cos * speed,
            forward * cos * speed + strafe * sin * speed};
    }
}

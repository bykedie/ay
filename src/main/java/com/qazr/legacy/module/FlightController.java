package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class FlightController {
    private static int suspendTicks;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private int elytraPulse;

    public FlightController(ModuleManager modules) {
        this.modules = modules;
    }

    public static void suspend(int ticks) {
        suspendTicks = Math.max(suspendTicks, ticks);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (suspendTicks > 0) {
            stabilizeDuringSuspend();
            suspendTicks--;
            return;
        }
        if (!modules.isEnabled(ModuleId.FLIGHT)) return;
        if (mc.player == null || mc.world == null || mc.player.connection == null || mc.currentScreen != null) return;
        if (ModConfig.flightBoat) {
            boatFlight();
        } else if (ModConfig.flightElytra) {
            elytraFlight();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            elytraPulse = 0;
            suspendTicks = 0;
        }
    }

    private void elytraFlight() {
        if (mc.player.isRiding()) return;
        double[] motion = requestedMotion();
        mc.player.motionX = motion[0];
        mc.player.motionY = motion[1];
        mc.player.motionZ = motion[2];
        mc.player.fallDistance = 0.0F;
        mc.player.onGround = false;
        if (elytraPulse-- <= 0) {
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
            elytraPulse = 8;
        }
        mc.player.connection.sendPacket(new CPacketPlayer.Position(
            mc.player.posX + motion[0], mc.player.posY + motion[1], mc.player.posZ + motion[2], false));
    }

    private void boatFlight() {
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
        elytraPulse = 0;
    }

    private double[] requestedMotion() {
        double forward = mc.player.movementInput.moveForward;
        double strafe = mc.player.movementInput.moveStrafe;
        double vertical = 0.0;
        if (mc.gameSettings.keyBindJump.isKeyDown()) vertical += ModConfig.flightVerticalSpeed;
        if (mc.gameSettings.keyBindSneak.isKeyDown()) vertical -= ModConfig.flightVerticalSpeed;
        double horizontal = Math.sqrt(forward * forward + strafe * strafe);
        if (horizontal > 0.01) {
            forward /= horizontal;
            strafe /= horizontal;
        } else {
            forward = 0.0;
            strafe = 0.0;
        }
        double radians = Math.toRadians(mc.player.rotationYaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = forward * -sin + strafe * cos;
        double z = forward * cos + strafe * sin;
        double speed = ModConfig.flightSpeed;
        return new double[] {MathHelper.clamp(x * speed, -speed, speed), vertical, MathHelper.clamp(z * speed, -speed, speed)};
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

package com.qazr.legacy.module;

import com.qazr.legacy.config.FlightMode;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class FlightController {
    private static final double HYPIXEL_OFFSET = 1.0E-9;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private EntityPlayerSP controlledPlayer;
    private FlightMode controlledMode;
    private boolean originalFlying;
    private float originalFlySpeed;

    public FlightController(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player != mc.player) return;
        boolean ready = modules.isEnabled(ModuleId.FLIGHT) && mc.player != null && mc.world != null
            && mc.player.connection != null && !mc.player.isRiding();
        if (!ready) {
            restorePlayer();
            return;
        }

        capturePlayer();
        switchMode(ModConfig.flightMode);
        switch (ModConfig.flightMode) {
            case VANILLA:
                vanillaFlight();
                break;
            case HYPIXEL:
                hypixelFlight();
                break;
            case STATIC:
            default:
                staticFlight();
                break;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) restorePlayer();
    }

    private void staticFlight() {
        double[] horizontal = movementFor(mc.player.rotationYaw, mc.player.movementInput.moveForward,
            mc.player.movementInput.moveStrafe, ModConfig.flightSpeed);
        mc.player.motionX = horizontal[0];
        mc.player.motionY = verticalMotion(mc.gameSettings.keyBindJump.isKeyDown(),
            mc.gameSettings.keyBindSneak.isKeyDown(), ModConfig.flightSpeed);
        mc.player.motionZ = horizontal[1];
        mc.player.fallDistance = 0.0F;
    }

    private void vanillaFlight() {
        mc.player.capabilities.isFlying = true;
        mc.player.capabilities.setFlySpeed(flySpeedFor(ModConfig.flightSpeed));
        mc.player.fallDistance = 0.0F;
    }

    private void hypixelFlight() {
        mc.player.motionY = 0.0;
        mc.player.onGround = true;
        applyHypixelOffsets(mc.player, mc.player.ticksExisted);
        mc.player.fallDistance = 0.0F;
    }

    private void capturePlayer() {
        if (controlledPlayer == mc.player) return;
        restorePlayer();
        controlledPlayer = mc.player;
        controlledMode = null;
        originalFlying = mc.player.capabilities.isFlying;
        originalFlySpeed = mc.player.capabilities.getFlySpeed();
    }

    private void switchMode(FlightMode nextMode) {
        if (controlledMode == nextMode) return;
        if (controlledMode == FlightMode.VANILLA) {
            mc.player.capabilities.isFlying = originalFlying;
            mc.player.capabilities.setFlySpeed(originalFlySpeed);
        }
        clearMotion(mc.player);
        controlledMode = nextMode;
    }

    private void restorePlayer() {
        if (controlledPlayer == null) return;
        controlledPlayer.capabilities.isFlying = originalFlying;
        controlledPlayer.capabilities.setFlySpeed(originalFlySpeed);
        controlledPlayer.fallDistance = 0.0F;
        clearMotion(controlledPlayer);
        controlledPlayer = null;
        controlledMode = null;
    }

    private static void clearMotion(EntityPlayerSP player) {
        player.motionX = 0.0;
        player.motionY = 0.0;
        player.motionZ = 0.0;
    }

    static float flySpeedFor(double speed) {
        return (float) (speed / 10.0);
    }

    static double verticalMotion(boolean jump, boolean sneak, double speed) {
        if (jump == sneak) return 0.0;
        return jump ? speed : -speed;
    }

    static double[] movementFor(float yaw, double forward, double strafe, double speed) {
        if (forward == 0.0 && strafe == 0.0) return new double[] {0.0, 0.0};
        float movementYaw = yaw;
        if (forward != 0.0) {
            if (strafe > 0.0) movementYaw += forward > 0.0 ? -45.0F : 45.0F;
            else if (strafe < 0.0) movementYaw += forward > 0.0 ? 45.0F : -45.0F;
            strafe = 0.0;
            forward = forward > 0.0 ? 1.0 : -1.0;
        }
        double radians = Math.toRadians(movementYaw + 90.0F);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new double[] {forward * speed * cos + strafe * speed * sin,
            forward * speed * sin - strafe * speed * cos};
    }

    static double hypixelOffsetForTick(int ticksExisted) {
        return ticksExisted % 3 == 0 ? 0.0 : HYPIXEL_OFFSET * 3.0;
    }

    private static void applyHypixelOffsets(EntityPlayerSP player, int ticksExisted) {
        for (int i = 0; i < 3; i++) {
            player.setPosition(player.posX, player.posY + HYPIXEL_OFFSET, player.posZ);
            if (ticksExisted % 3 == 0) {
                player.setPosition(player.posX, player.posY - HYPIXEL_OFFSET, player.posZ);
            }
        }
    }
}

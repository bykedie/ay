package com.qazr.legacy.module;

import com.qazr.legacy.config.FlightMode;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class FlightController {
    private static final double HYPIXEL_OFFSET = 1.0E-9;
    private static final double LANDING_SEARCH_DISTANCE = 4.0;
    private static final double DEFAULT_DESCENT_SPEED = 0.35;
    private static final double MAX_CONTROLLED_DESCENT_SPEED = 1.0;

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
        boolean controlReady = mc.player != null && mc.world != null
            && mc.player.connection != null && !mc.player.isRiding();
        if (!controlReady) {
            restorePlayer();
            return;
        }
        if (!modules.isEnabled(ModuleId.FLIGHT)) {
            continueLandingAfterDisable();
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
        protectLanding(landingRequested(mc.gameSettings.keyBindJump.isKeyDown(),
            mc.gameSettings.keyBindSneak.isKeyDown()));
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
        mc.player.onGround = hypixelPacketOnGround(mc.player.onGround);
        applyHypixelOffsets(mc.player, mc.player.ticksExisted);
        mc.player.fallDistance = 0.0F;
    }

    static boolean hypixelPacketOnGround(boolean physicallyOnGround) {
        return physicallyOnGround;
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
        restoreFlightCapabilities();
        controlledPlayer.fallDistance = 0.0F;
        clearMotion(controlledPlayer);
        controlledPlayer = null;
        controlledMode = null;
    }

    private void restoreFlightCapabilities() {
        if (controlledPlayer == null) return;
        controlledPlayer.capabilities.isFlying = originalFlying;
        controlledPlayer.capabilities.setFlySpeed(originalFlySpeed);
    }

    private void protectLanding(boolean descending) {
        if (!descending) return;
        double distance = groundDistance(mc.player, LANDING_SEARCH_DISTANCE);
        mc.player.motionY = safeLandingMotion(
            mc.player.motionY, distance, ModConfig.flightSpeed);
        mc.player.fallDistance = 0.0F;
    }

    private void continueLandingAfterDisable() {
        if (controlledPlayer != mc.player) {
            restorePlayer();
            return;
        }
        double distance = groundDistance(mc.player, LANDING_SEARCH_DISTANCE);
        boolean fallSafeState = mc.player.isInWater() || mc.player.isInLava()
            || mc.player.isOnLadder() || mc.player.isElytraFlying()
            || mc.player.capabilities.allowFlying;
        if (!shouldContinueLandingAfterDisable(
                true, mc.player.onGround, fallSafeState, distance)) {
            restorePlayer();
            return;
        }
        restoreFlightCapabilities();
        mc.player.motionX = 0.0;
        mc.player.motionZ = 0.0;
        protectLanding(true);
    }

    private double groundDistance(EntityPlayerSP player, double maxDistance) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB search = new AxisAlignedBB(box.minX + 0.001, box.minY - maxDistance,
            box.minZ + 0.001, box.maxX - 0.001, box.minY + 0.001, box.maxZ - 0.001);
        List<AxisAlignedBB> collisions = mc.world.getCollisionBoxes(player, search);
        double nearest = Double.POSITIVE_INFINITY;
        for (AxisAlignedBB collision : collisions) {
            if (collision.maxY > box.minY + 0.001) continue;
            double distance = Math.max(0.0, box.minY - collision.maxY);
            nearest = Math.min(nearest, distance);
        }
        return nearest;
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

    static boolean landingRequested(boolean jump, boolean sneak) {
        return sneak && !jump;
    }

    static double safeLandingMotion(
            double requestedMotion, double groundDistance, double configuredSpeed) {
        if (groundDistance <= 0.001) return 0.0;
        double requestedSpeed = requestedMotion < -0.01
            ? -requestedMotion : Math.max(DEFAULT_DESCENT_SPEED, configuredSpeed);
        double descentSpeed = Math.min(MAX_CONTROLLED_DESCENT_SPEED, requestedSpeed);
        if (Double.isFinite(groundDistance)) descentSpeed = Math.min(descentSpeed, groundDistance);
        return -descentSpeed;
    }

    static boolean shouldContinueLandingAfterDisable(
            boolean controlled, boolean onGround, boolean fallSafeState, double groundDistance) {
        return controlled && !onGround && !fallSafeState
            && (!Double.isFinite(groundDistance) || groundDistance > 0.001);
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

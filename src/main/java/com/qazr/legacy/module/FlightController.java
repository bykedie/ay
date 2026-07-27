package com.qazr.legacy.module;

import com.qazr.legacy.config.FlightMode;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class FlightController {
    private static final double HYPIXEL_OFFSET = 1.0E-9;
    private static final double LANDING_SEARCH_DISTANCE = 4.0;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private EntityPlayerSP controlledPlayer;
    private FlightMode controlledMode;
    private boolean originalFlying;
    private float originalFlySpeed;
    private boolean landingConfirmed;

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
        protectLanding(mc.gameSettings.keyBindSneak.isKeyDown());
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
        landingConfirmed = false;
    }

    private void protectLanding(boolean descending) {
        if (!descending) {
            landingConfirmed = false;
            return;
        }
        double distance = groundDistance(mc.player, LANDING_SEARCH_DISTANCE);
        if (shouldResetLandingConfirmation(landingConfirmed, distance)) landingConfirmed = false;
        mc.player.motionY = safeLandingMotion(mc.player.motionY, distance);
        mc.player.fallDistance = 0.0F;
        if (!landingConfirmed && shouldConfirmLanding(distance, mc.player.motionY)) {
            mc.player.setPosition(mc.player.posX, landingPositionY(mc.player.posY, distance),
                mc.player.posZ);
            mc.player.motionY = 0.0;
            mc.player.onGround = true;
            mc.player.connection.sendPacket(new CPacketPlayer.Position(mc.player.posX,
                mc.player.posY, mc.player.posZ, true));
            landingConfirmed = true;
        }
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

    static double safeLandingMotion(double requestedMotion, double groundDistance) {
        if (!Double.isFinite(groundDistance)) return descendingMotion(requestedMotion);
        if (groundDistance <= 0.001) return 0.0;
        if (groundDistance <= 1.0) return -Math.min(0.08, groundDistance);
        return descendingMotion(requestedMotion);
    }

    private static double descendingMotion(double requestedMotion) {
        return requestedMotion < -0.01 ? Math.max(requestedMotion, -0.35) : -0.35;
    }

    static boolean shouldConfirmLanding(double groundDistance, double motionY) {
        if (!Double.isFinite(groundDistance)) return false;
        return groundDistance <= Math.max(0.08, Math.max(0.0, -motionY) + 0.02);
    }

    static double landingPositionY(double currentY, double groundDistance) {
        if (!Double.isFinite(groundDistance) || groundDistance <= 0.0) return currentY;
        return currentY - groundDistance;
    }

    static boolean shouldResetLandingConfirmation(boolean confirmed, double groundDistance) {
        return confirmed && (!Double.isFinite(groundDistance) || groundDistance > 0.25);
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

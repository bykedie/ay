package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.util.BlinkPath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class BlinkStrike {
    private static final int FLIGHT_SUSPEND_TICKS = 8;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private int delay;

    public BlinkStrike(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.BLINK_STRIKE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        if (delay > 0) {
            delay--;
            return;
        }
        if (mc.player.getCooledAttackStrength(0.0F) < 0.95F) return;

        int targetLimit = ModConfig.blinkMultiTarget ? ModConfig.blinkMaxTargets : 1;
        List<EntityLivingBase> targets = CombatSupport.findTargets(mc, ModuleId.BLINK_STRIKE, targetLimit);
        if (targets.isEmpty()) return;
        if (ModConfig.blinkAutoWeapon) CombatSupport.selectBestWeapon(mc);
        if (ModConfig.blinkRotate) face(targets.get(0));

        BlinkPath.Point origin = new BlinkPath.Point(mc.player.posX, mc.player.posY, mc.player.posZ);
        boolean attacked = false;
        FlightController.suspend(FLIGHT_SUSPEND_TICKS);
        for (EntityLivingBase target : targets) {
            if (strike(target, origin)) attacked = true;
        }
        if (attacked) {
            mc.player.resetCooldown();
            delay = ModConfig.blinkDelayTicks;
        } else {
            delay = Math.max(2, ModConfig.blinkDelayTicks / 2);
        }
    }

    private boolean strike(EntityLivingBase target, BlinkPath.Point origin) {
        BlinkPath.Point destination = findDestination(target, origin);
        if (destination == null) return false;
        boolean originOnGround = originOnGround(origin);
        List<BlinkPath.Point> path = BlinkPath.interpolate(origin, destination, ModConfig.blinkStep);
        int sent = 0;
        try {
            for (BlinkPath.Point point : path) {
                sendPosition(point);
                sent++;
            }
            Vec3d remoteEyes = new Vec3d(destination.x, destination.y + mc.player.getEyeHeight(), destination.z);
            float[] rotations = CombatSupport.rotations(remoteEyes, target, ModConfig.blinkAttackPoint);
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation(rotations[0], rotations[1], false));
            sendRemoteCritical(destination, originOnGround);
            mc.player.connection.sendPacket(new CPacketUseEntity(target));
            mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            List<BlinkPath.Point> returnPath = BlinkPath.returnPath(origin, path, sent);
            for (int i = 0; i < returnPath.size(); i++) {
                sendPosition(returnPath.get(i), i == returnPath.size() - 1 && originOnGround);
            }
            mc.player.fallDistance = 0.0F;
            mc.player.motionY = 0.0;
            mc.player.motionX = 0.0;
            mc.player.motionZ = 0.0;
            mc.player.onGround = originOnGround;
            mc.player.setPosition(origin.x, origin.y, origin.z);
            FlightController.suspend(FLIGHT_SUSPEND_TICKS);
        }
        return true;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) delay = 0;
    }

    private BlinkPath.Point findDestination(EntityLivingBase target, BlinkPath.Point origin) {
        double predictedX = target.posX + target.motionX * ModConfig.blinkPredictTicks;
        double predictedY = target.getEntityBoundingBox().minY + target.motionY * ModConfig.blinkPredictTicks;
        double predictedZ = target.posZ + target.motionZ * ModConfig.blinkPredictTicks;
        AxisAlignedBB predictedBox = target.getEntityBoundingBox().offset(
            predictedX - target.posX, predictedY - target.getEntityBoundingBox().minY, predictedZ - target.posZ);
        if (CombatSupport.distanceSqToHitbox(new Vec3d(origin.x, origin.y + mc.player.getEyeHeight(), origin.z), predictedBox)
                <= ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance) return origin;
        for (BlinkPath.Point candidate : candidatePositions(origin, predictedX, predictedY, predictedZ,
                ModConfig.blinkAttackDistance)) {
            if (origin.distanceTo(candidate) > ModConfig.blinkRange) continue;
            Vec3d eyes = new Vec3d(candidate.x, candidate.y + mc.player.getEyeHeight(), candidate.z);
            if (CombatSupport.distanceSqToHitbox(eyes, predictedBox)
                    > ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance) continue;
            if (isPathClear(origin, candidate)) return candidate;
        }
        return null;
    }

    static List<BlinkPath.Point> candidatePositions(BlinkPath.Point origin, double targetX, double targetY,
            double targetZ, double attackDistance) {
        double dx = origin.x - targetX;
        double dz = origin.z - targetZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        double baseAngle = length > 0.001 ? Math.atan2(dz, dx) : 0.0;
        double radius = Math.max(0.65, attackDistance * 0.72);
        List<BlinkPath.Point> result = new ArrayList<>(16);
        double[] heights = {targetY, origin.y};
        for (double height : heights) {
            for (int i = 0; i < 8; i++) {
                double angle = baseAngle + Math.PI * 2.0 * i / 8.0;
                result.add(new BlinkPath.Point(targetX + Math.cos(angle) * radius, height,
                    targetZ + Math.sin(angle) * radius));
            }
        }
        return result;
    }

    private void face(EntityLivingBase target) {
        float[] rotations = CombatSupport.rotations(mc.player.getPositionEyes(1.0F), target, ModConfig.blinkAttackPoint);
        mc.player.rotationYaw = rotations[0];
        mc.player.rotationPitch = rotations[1];
    }

    private boolean isPathClear(BlinkPath.Point origin, BlinkPath.Point destination) {
        AxisAlignedBB base = mc.player.getEntityBoundingBox();
        for (BlinkPath.Point point : BlinkPath.interpolate(origin, destination, 0.4)) {
            AxisAlignedBB box = base.offset(point.x - origin.x, point.y - origin.y, point.z - origin.z).shrink(0.02);
            if (!mc.world.getWorldBorder().contains(box) || !mc.world.getCollisionBoxes(mc.player, box).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean originOnGround(BlinkPath.Point origin) {
        if (mc.player.onGround) return true;
        AxisAlignedBB box = mc.player.getEntityBoundingBox()
            .offset(origin.x - mc.player.posX, origin.y - mc.player.posY, origin.z - mc.player.posZ)
            .offset(0.0, -0.04, 0.0);
        return !mc.world.getCollisionBoxes(mc.player, box).isEmpty();
    }

    private void sendPosition(BlinkPath.Point point) {
        sendPosition(point, false);
    }

    private void sendPosition(BlinkPath.Point point, boolean onGround) {
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, onGround));
    }

    private void sendRemoteCritical(BlinkPath.Point point, boolean originOnGround) {
        if (!modules.isEnabled(ModuleId.CRITICALS) || !originOnGround) return;
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isRiding()
                || mc.player.isPotionActive(MobEffects.BLINDNESS)
                || mc.world.getBlockState(new BlockPos(point.x, point.y, point.z)).getMaterial().isLiquid()) return;
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y + 0.0625, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y + 0.0125, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, false));
    }
}

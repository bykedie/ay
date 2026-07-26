package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.util.BlinkPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class BlinkStrike {
    private static final int MAX_PLAN_CHECKS_PER_TICK = 12;
    private static final int UNREACHABLE_CACHE_TICKS = 12;
    private static final int RECOVERY_TICKS = 40;
    private static final double RECOVERY_ORIGIN_RADIUS = 3.0;
    private static final double RECOVERY_JUMP_DISTANCE = 2.0;
    private static final double RECOVERY_PATH_RADIUS = 1.5;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Map<Integer, Integer> unreachableUntil = new HashMap<>();
    private final Map<Integer, Integer> reachableUntil = new HashMap<>();
    private final List<List<BlinkPath.Point>> recoveryRoutes = new ArrayList<>();
    private BlinkPath.Point recoveryOrigin;
    private BlinkPath.Point lastRecoveryPosition;
    private boolean recoveryOnGround;
    private boolean recoveryTransportOnGround;
    private boolean recoveryPlayerOnGround;
    private double recoveryMotionX;
    private double recoveryMotionY;
    private double recoveryMotionZ;
    private int recoveryUntilTick;
    private int delay;

    public BlinkStrike(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        recoverUnexpectedTeleport();
        if (!modules.isEnabled(ModuleId.BLINK_STRIKE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        if (delay > 0) {
            delay--;
            return;
        }
        if (mc.player.getCooledAttackStrength(0.0F) < 0.95F) return;

        int targetLimit = ModConfig.blinkMultiTarget ? ModConfig.blinkMaxTargets : 1;
        BlinkPath.Point origin = new BlinkPath.Point(mc.player.posX, mc.player.posY, mc.player.posZ);
        boolean originOnGround = originOnGround(origin);
        if (!originOnGround && !safeAirborneOrigin()) return;
        List<EntityLivingBase> targets = CombatSupport.findTargets(mc, ModuleId.BLINK_STRIKE,
            candidateScanLimit(targetLimit));
        List<PlannedStrike> strikes = planStrikes(targets, origin, targetLimit, planningBudget(targetLimit));
        if (strikes.isEmpty()) {
            delay = 2;
            return;
        }
        if (ModConfig.blinkAutoWeapon) CombatSupport.selectBestWeapon(mc);
        if (ModConfig.blinkRotate) face(strikes.get(0).target);

        boolean attacked = false;
        beginRecovery(origin, originOnGround);
        for (PlannedStrike strike : strikes) {
            StrikePlan currentPlan = refreshStrikePlan(strike.target, origin, strike.plan);
            if (currentPlan == null) continue;
            if (!currentPlan.path.isEmpty()) recoveryRoutes.add(new ArrayList<>(currentPlan.path));
            if (strike(strike.target, origin, currentPlan, originOnGround)) attacked = true;
        }
        if (attacked) {
            mc.player.resetCooldown();
            delay = ModConfig.blinkDelayTicks;
        } else {
            delay = Math.max(2, ModConfig.blinkDelayTicks / 2);
        }
    }

    private boolean strike(EntityLivingBase target, BlinkPath.Point origin, StrikePlan plan,
            boolean originOnGround) {
        boolean transportOnGround = transportOnGround(originOnGround,
            modules.isEnabled(ModuleId.FLIGHT));
        List<BlinkPath.Point> path = plan.path;
        double originalMotionX = mc.player.motionX;
        double originalMotionY = mc.player.motionY;
        double originalMotionZ = mc.player.motionZ;
        boolean originalOnGround = mc.player.onGround;
        int sent = 0;
        try {
            for (BlinkPath.Point point : path) {
                sendPosition(point, transportOnGround);
                sent++;
            }
            Vec3d remoteEyes = new Vec3d(plan.destination.x,
                plan.destination.y + mc.player.getEyeHeight(), plan.destination.z);
            float[] rotations = CombatSupport.rotations(remoteEyes, target, ModConfig.blinkAttackPoint);
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation(rotations[0], rotations[1], transportOnGround));
            sendRemoteCritical(plan.destination, originOnGround);
            mc.player.connection.sendPacket(new CPacketUseEntity(target));
            mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            List<BlinkPath.Point> returnPath = completeReturnPath(origin, path, sent);
            for (int i = 0; i < returnPath.size(); i++) {
                sendPosition(returnPath.get(i), i + 1 == returnPath.size()
                    ? originOnGround : transportOnGround);
            }
            mc.player.fallDistance = 0.0F;
            mc.player.motionX = originalMotionX;
            mc.player.motionY = originalMotionY;
            mc.player.motionZ = originalMotionZ;
            mc.player.onGround = originalOnGround;
            mc.player.setPosition(origin.x, origin.y, origin.z);
        }
        return true;
    }

    static List<BlinkPath.Point> completeReturnPath(BlinkPath.Point origin,
            List<BlinkPath.Point> outward, int sentPoints) {
        List<BlinkPath.Point> result = BlinkPath.returnPath(origin, outward, sentPoints);
        return result.isEmpty() ? java.util.Collections.singletonList(origin) : result;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            delay = 0;
            unreachableUntil.clear();
            reachableUntil.clear();
            clearRecovery();
        }
    }

    private void beginRecovery(BlinkPath.Point origin, boolean onGround) {
        recoveryOrigin = origin;
        lastRecoveryPosition = origin;
        recoveryOnGround = onGround;
        recoveryTransportOnGround = transportOnGround(onGround, modules.isEnabled(ModuleId.FLIGHT));
        recoveryPlayerOnGround = mc.player.onGround;
        recoveryMotionX = mc.player.motionX;
        recoveryMotionY = mc.player.motionY;
        recoveryMotionZ = mc.player.motionZ;
        recoveryRoutes.clear();
        recoveryUntilTick = mc.player.ticksExisted + RECOVERY_TICKS;
    }

    private void recoverUnexpectedTeleport() {
        if (mc.player == null || mc.world == null || mc.player.connection == null || recoveryOrigin == null) return;
        if (mc.player.ticksExisted > recoveryUntilTick) {
            clearRecovery();
            return;
        }
        BlinkPath.Point current = new BlinkPath.Point(mc.player.posX, mc.player.posY, mc.player.posZ);
        List<BlinkPath.Point> returnPath = recoveryReturnPath(recoveryOrigin, lastRecoveryPosition, current,
            recoveryRoutes, ModConfig.blinkStep);
        if (returnPath.isEmpty()) {
            lastRecoveryPosition = current;
            recoveryPlayerOnGround = mc.player.onGround;
            recoveryMotionX = mc.player.motionX;
            recoveryMotionY = mc.player.motionY;
            recoveryMotionZ = mc.player.motionZ;
            return;
        }
        for (int i = 0; i < returnPath.size(); i++) {
            sendPosition(returnPath.get(i), i + 1 == returnPath.size()
                ? recoveryOnGround : recoveryTransportOnGround);
        }
        mc.player.setPosition(recoveryOrigin.x, recoveryOrigin.y, recoveryOrigin.z);
        mc.player.fallDistance = 0.0F;
        mc.player.motionX = recoveryMotionX;
        mc.player.motionY = recoveryMotionY;
        mc.player.motionZ = recoveryMotionZ;
        mc.player.onGround = recoveryPlayerOnGround;
        lastRecoveryPosition = recoveryOrigin;
    }

    private void clearRecovery() {
        recoveryOrigin = null;
        lastRecoveryPosition = null;
        recoveryOnGround = false;
        recoveryTransportOnGround = false;
        recoveryPlayerOnGround = false;
        recoveryMotionX = 0.0;
        recoveryMotionY = 0.0;
        recoveryMotionZ = 0.0;
        recoveryRoutes.clear();
        recoveryUntilTick = 0;
    }

    static List<BlinkPath.Point> recoveryReturnPath(BlinkPath.Point origin, BlinkPath.Point previous,
            BlinkPath.Point current, List<List<BlinkPath.Point>> routes, double maxStep) {
        if (origin.distanceTo(current) <= RECOVERY_ORIGIN_RADIUS
                || previous == null || previous.distanceTo(current) <= RECOVERY_JUMP_DISTANCE) {
            return java.util.Collections.emptyList();
        }
        List<BlinkPath.Point> nearestRoute = null;
        int nearestIndex = -1;
        double nearestDistance = RECOVERY_PATH_RADIUS;
        for (List<BlinkPath.Point> route : routes) {
            for (int i = 0; i < route.size(); i++) {
                double distance = route.get(i).distanceTo(current);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearestRoute = route;
                    nearestIndex = i;
                }
            }
        }
        if (nearestRoute == null) return java.util.Collections.emptyList();
        List<BlinkPath.Point> result = new ArrayList<>();
        result.addAll(BlinkPath.interpolate(current, nearestRoute.get(nearestIndex), maxStep));
        result.addAll(BlinkPath.returnPath(origin, nearestRoute, nearestIndex + 1));
        return result;
    }

    private List<PlannedStrike> planStrikes(List<EntityLivingBase> targets, BlinkPath.Point origin,
            int targetLimit, int planningBudget) {
        List<PlannedStrike> result = new ArrayList<>();
        int tick = mc.player.ticksExisted;
        pruneUnreachableCache(tick);
        int checked = 0;
        for (EntityLivingBase target : targets) {
            if (result.size() >= targetLimit || checked >= planningBudget) break;
            Integer blockedUntil = unreachableUntil.get(target.getEntityId());
            if (blockedUntil != null && blockedUntil > tick) continue;
            checked++;
            StrikePlan plan = findStrikePlan(target, origin);
            if (plan == null) {
                unreachableUntil.put(target.getEntityId(), tick + UNREACHABLE_CACHE_TICKS);
                reachableUntil.remove(target.getEntityId());
                continue;
            }
            unreachableUntil.remove(target.getEntityId());
            reachableUntil.put(target.getEntityId(), tick + Math.max(UNREACHABLE_CACHE_TICKS,
                ModConfig.blinkDelayTicks + 2));
            result.add(new PlannedStrike(target, plan));
        }
        return result;
    }

    private void pruneUnreachableCache(int tick) {
        unreachableUntil.entrySet().removeIf(entry -> entry.getValue() <= tick);
        reachableUntil.entrySet().removeIf(entry -> entry.getValue() <= tick);
    }

    boolean isReachableForRender(EntityLivingBase target) {
        if (mc.player == null || target == null) return false;
        int tick = mc.player.ticksExisted;
        Integer blocked = unreachableUntil.get(target.getEntityId());
        if (blocked != null && blocked > tick) return false;
        Integer reachable = reachableUntil.get(target.getEntityId());
        return reachable != null && reachable > tick;
    }

    static int planningBudget(int requestedTargets) {
        int requested = Math.max(1, Math.min(50, requestedTargets));
        if (requested <= 6) return Math.min(MAX_PLAN_CHECKS_PER_TICK, Math.max(4, requested * 2));
        return Math.min(50, requested * 2);
    }

    static int candidateScanLimit(int requestedTargets) {
        return 50;
    }

    private StrikePlan findStrikePlan(EntityLivingBase target, BlinkPath.Point origin) {
        double predictedX = target.posX + target.motionX * ModConfig.blinkPredictTicks;
        double predictedY = target.getEntityBoundingBox().minY + target.motionY * ModConfig.blinkPredictTicks;
        double predictedZ = target.posZ + target.motionZ * ModConfig.blinkPredictTicks;
        AxisAlignedBB predictedBox = target.getEntityBoundingBox().offset(
            predictedX - target.posX, predictedY - target.getEntityBoundingBox().minY, predictedZ - target.posZ);
        Vec3d targetPoint = ModConfig.blinkAttackPoint.point(target);
        Vec3d attackPoint = new Vec3d(targetPoint.x + predictedX - target.posX,
            targetPoint.y + predictedY - target.getEntityBoundingBox().minY,
            targetPoint.z + predictedZ - target.posZ);
        if (CombatSupport.distanceSqToHitbox(new Vec3d(origin.x, origin.y + mc.player.getEyeHeight(), origin.z), predictedBox)
                <= ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance) {
            Vec3d originEyes = new Vec3d(origin.x, origin.y + mc.player.getEyeHeight(), origin.z);
            if (hasAttackLine(originEyes, attackPoint)) {
                return new StrikePlan(origin, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
            }
        }
        for (BlinkPath.Point candidate : candidatePositions(origin, predictedX, predictedY, predictedZ,
                ModConfig.blinkAttackDistance)) {
            if (origin.distanceTo(candidate) > ModConfig.blinkRange) continue;
            Vec3d eyes = new Vec3d(candidate.x, candidate.y + mc.player.getEyeHeight(), candidate.z);
            if (CombatSupport.distanceSqToHitbox(eyes, predictedBox)
                    > ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance) continue;
            if (!hasAttackLine(eyes, attackPoint)) continue;
            for (List<BlinkPath.Point> waypoints : routeWaypoints(origin, candidate)) {
                if (!isRouteClear(origin, waypoints)) continue;
                return new StrikePlan(candidate, buildPath(origin, waypoints, ModConfig.blinkStep),
                    new ArrayList<>(waypoints));
            }
        }
        return null;
    }

    private StrikePlan refreshStrikePlan(EntityLivingBase target, BlinkPath.Point origin, StrikePlan planned) {
        if (target == null || target.isDead || target.getHealth() <= 0.0F) return null;
        if (planStillValid(target, planned)) return planned;
        return findStrikePlan(target, origin);
    }

    private boolean planStillValid(EntityLivingBase target, StrikePlan plan) {
        if (plan == null || plan.destination == null) return false;
        double predictedX = target.posX + target.motionX * ModConfig.blinkPredictTicks;
        double predictedY = target.getEntityBoundingBox().minY + target.motionY * ModConfig.blinkPredictTicks;
        double predictedZ = target.posZ + target.motionZ * ModConfig.blinkPredictTicks;
        AxisAlignedBB predictedBox = target.getEntityBoundingBox().offset(
            predictedX - target.posX, predictedY - target.getEntityBoundingBox().minY, predictedZ - target.posZ);
        Vec3d eyes = new Vec3d(plan.destination.x, plan.destination.y + mc.player.getEyeHeight(), plan.destination.z);
        Vec3d targetPoint = ModConfig.blinkAttackPoint.point(target);
        Vec3d attackPoint = new Vec3d(targetPoint.x + predictedX - target.posX,
            targetPoint.y + predictedY - target.getEntityBoundingBox().minY,
            targetPoint.z + predictedZ - target.posZ);
        boolean lineAndRangeValid = CombatSupport.distanceSqToHitbox(eyes, predictedBox)
                <= ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance
            && hasAttackLine(eyes, attackPoint);
        return strikePlanStillUsable(true, lineAndRangeValid,
            plan.waypoints.isEmpty() || isRouteClear(new BlinkPath.Point(mc.player.posX, mc.player.posY, mc.player.posZ),
                plan.waypoints));
    }

    static boolean strikePlanStillUsable(boolean targetAlive, boolean lineAndRangeValid,
            boolean routeClear) {
        return targetAlive && lineAndRangeValid && routeClear;
    }

    private boolean hasAttackLine(Vec3d eyes, Vec3d attackPoint) {
        return mc.world.rayTraceBlocks(eyes, attackPoint, false, true, false) == null;
    }

    static List<List<BlinkPath.Point>> routeWaypoints(BlinkPath.Point origin, BlinkPath.Point destination) {
        List<List<BlinkPath.Point>> routes = new ArrayList<>(4);
        routes.add(java.util.Collections.singletonList(destination));
        addRoute(routes, new BlinkPath.Point(destination.x, origin.y, destination.z), destination);
        addRoute(routes, new BlinkPath.Point(origin.x, destination.y, origin.z), destination);
        double raisedY = Math.max(origin.y, destination.y) + 1.0;
        List<BlinkPath.Point> raised = new ArrayList<>(3);
        raised.add(new BlinkPath.Point(origin.x, raisedY, origin.z));
        raised.add(new BlinkPath.Point(destination.x, raisedY, destination.z));
        raised.add(destination);
        routes.add(raised);
        return routes;
    }

    private static void addRoute(List<List<BlinkPath.Point>> routes, BlinkPath.Point waypoint,
            BlinkPath.Point destination) {
        List<BlinkPath.Point> route = new ArrayList<>(2);
        route.add(waypoint);
        if (waypoint.distanceTo(destination) > 0.0001) route.add(destination);
        routes.add(route);
    }

    static List<BlinkPath.Point> buildPath(BlinkPath.Point origin, List<BlinkPath.Point> waypoints,
            double maxStep) {
        List<BlinkPath.Point> path = new ArrayList<>();
        BlinkPath.Point previous = origin;
        for (BlinkPath.Point waypoint : waypoints) {
            path.addAll(BlinkPath.interpolate(previous, waypoint, maxStep));
            previous = waypoint;
        }
        return path;
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

    private boolean isRouteClear(BlinkPath.Point origin, List<BlinkPath.Point> waypoints) {
        BlinkPath.Point previous = origin;
        for (BlinkPath.Point waypoint : waypoints) {
            if (!isSegmentClear(origin, previous, waypoint)) return false;
            previous = waypoint;
        }
        return true;
    }

    private boolean isSegmentClear(BlinkPath.Point origin, BlinkPath.Point from, BlinkPath.Point destination) {
        AxisAlignedBB base = mc.player.getEntityBoundingBox();
        for (BlinkPath.Point point : BlinkPath.interpolate(from, destination, 0.4)) {
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

    private boolean safeAirborneOrigin() {
        return safeAirborneOrigin(mc.player.isInWater(), mc.player.isInLava(), mc.player.isOnLadder(),
            mc.player.capabilities.isFlying);
    }

    static boolean safeAirborneOrigin(boolean inWater, boolean inLava, boolean onLadder, boolean flying) {
        return inWater || inLava || onLadder || flying;
    }

    static boolean transportOnGround(boolean originOnGround, boolean flightEnabled) {
        return originOnGround || flightEnabled;
    }

    private void sendPosition(BlinkPath.Point point, boolean onGround) {
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, onGround));
    }

    private void sendRemoteCritical(BlinkPath.Point point, boolean originOnGround) {
        if (!modules.isEnabled(ModuleId.CRITICALS) || !originOnGround) return;
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isRiding()
                || mc.player.isPotionActive(MobEffects.BLINDNESS) || isLiquidAt(point)) return;
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y + 0.0625, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y + 0.0125, point.z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, false));
    }

    private boolean isLiquidAt(BlinkPath.Point point) {
        AxisAlignedBB remoteBox = mc.player.getEntityBoundingBox().offset(
            point.x - mc.player.posX, point.y - mc.player.posY, point.z - mc.player.posZ);
        return mc.world.containsAnyLiquid(remoteBox);
    }

    private static final class StrikePlan {
        private final BlinkPath.Point destination;
        private final List<BlinkPath.Point> path;
        private final List<BlinkPath.Point> waypoints;

        private StrikePlan(BlinkPath.Point destination, List<BlinkPath.Point> path,
                List<BlinkPath.Point> waypoints) {
            this.destination = destination;
            this.path = path;
            this.waypoints = waypoints;
        }
    }

    private static final class PlannedStrike {
        private final EntityLivingBase target;
        private final StrikePlan plan;

        private PlannedStrike(EntityLivingBase target, StrikePlan plan) {
            this.target = target;
            this.plan = plan;
        }
    }
}

package com.qazr.legacy.module;

import com.qazr.legacy.config.AttackPoint;
import com.qazr.legacy.config.FlightMode;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class BlinkStrike {
    private static final int MAX_PLAN_CHECKS_PER_TICK = 12;
    private static final int MAX_CANDIDATE_CHECKS_PER_TICK = 16;
    private static final int MAX_COLLISION_CHECKS_PER_TICK = 96;
    private static final int UNREACHABLE_CACHE_TICKS = 12;
    private static final int RESERVED_MOVEMENT_PACKETS = 1;
    private static final double SURVIVAL_DIRECT_REACH = 3.0;
    private static final double CREATIVE_DIRECT_REACH = 6.0;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Map<Integer, Integer> unreachableUntil = new HashMap<>();
    private final Map<Integer, Integer> reachableUntil = new HashMap<>();
    private final Map<Integer, PlanSearch> pendingPlans = new HashMap<>();
    private BlockPos reachabilityFeet;
    private boolean reachabilityFlight;
    private int delay;

    public BlinkStrike(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!modules.isEnabled(ModuleId.BLINK_STRIKE)) {
            pendingPlans.clear();
            return;
        }
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        refreshReachabilityContext(new BlockPos(mc.player.posX,
            mc.player.getEntityBoundingBox().minY, mc.player.posZ),
            modules.isEnabled(ModuleId.FLIGHT));
        if (delay > 0) {
            delay--;
            return;
        }
        if (mc.player.getCooledAttackStrength(0.0F) < 0.95F) return;

        int targetLimit = ModConfig.blinkMultiTarget ? ModConfig.blinkMaxTargets : 1;
        BlinkPath.Point origin = new BlinkPath.Point(mc.player.posX, mc.player.posY, mc.player.posZ);
        BlinkPath.Point movementOrigin = new BlinkPath.Point(mc.player.lastTickPosX,
            mc.player.lastTickPosY, mc.player.lastTickPosZ);
        boolean originOnGround = originOnGround(origin);
        if (!originOnGround && !safeAirborneOrigin()) return;
        List<EntityLivingBase> targets = CombatSupport.findTargets(mc, ModuleId.BLINK_STRIKE,
            candidateScanLimit(targetLimit));
        PlanBatch batch = planStrikes(targets, origin, targetLimit, planningBudget(targetLimit));
        List<PlannedStrike> strikes = batch.strikes;
        if (strikes.isEmpty()) {
            if (!batch.incomplete) delay = 2;
            return;
        }
        if (ModConfig.blinkAutoWeapon) CombatSupport.selectBestWeapon(mc);
        if (ModConfig.blinkRotate) face(strikes.get(0).target);

        boolean attacked = false;
        boolean remoteAttacked = false;
        for (PlannedStrike strike : strikes) {
            StrikePlan currentPlan = refreshStrikePlan(strike.target, origin, strike.plan);
            if (currentPlan == null) continue;
            if (currentPlan.path.isEmpty()) {
                if (ModConfig.blinkRotate) face(strike.target);
                mc.playerController.attackEntity(mc.player, strike.target);
                mc.player.swingArm(EnumHand.MAIN_HAND);
                attacked = true;
                continue;
            }
            if (remoteAttacked) continue;
            if (strike(strike.target, movementOrigin, origin, currentPlan, originOnGround)) {
                attacked = true;
                remoteAttacked = true;
            }
        }
        if (attacked) {
            mc.player.resetCooldown();
            delay = ModConfig.blinkDelayTicks;
        } else {
            delay = Math.max(2, ModConfig.blinkDelayTicks / 2);
        }
    }

    private boolean strike(EntityLivingBase target, BlinkPath.Point movementOrigin,
            BlinkPath.Point origin, StrikePlan plan, boolean originOnGround) {
        boolean transportOnGround = transportOnGround(originOnGround,
            modules.isEnabled(ModuleId.FLIGHT) && ModConfig.flightMode == FlightMode.VANILLA);
        boolean releaseAirborne = releaseAirborneTransport(originOnGround, transportOnGround);
        List<BlinkPath.Point> path = plan.path;
        boolean critical = remoteCriticalEligible(plan.destination, originOnGround)
            && remoteMovementPlanAccepted(movementOrigin, origin, path, plan.directReturn,
                true, releaseAirborne, RESERVED_MOVEMENT_PACKETS);
        if (!remoteMovementPlanAccepted(movementOrigin, origin, path, plan.directReturn,
                critical, releaseAirborne, RESERVED_MOVEMENT_PACKETS)) return false;
        double originalMotionX = mc.player.motionX;
        double originalMotionY = mc.player.motionY;
        double originalMotionZ = mc.player.motionZ;
        boolean originalOnGround = mc.player.onGround;
        int sent = 0;
        Vec3d remoteEyes = new Vec3d(plan.destination.x,
            plan.destination.y + mc.player.getEyeHeight(), plan.destination.z);
        float[] rotations = CombatSupport.rotations(remoteEyes, target, ModConfig.blinkAttackPoint);
        try {
            for (int i = 0; i < path.size(); i++) {
                BlinkPath.Point point = path.get(i);
                if (i + 1 == path.size()) {
                    sendPositionRotation(point, rotations[0], rotations[1], transportOnGround);
                } else {
                    sendPosition(point, transportOnGround);
                }
                sent++;
            }
            if (critical) sendRemoteCritical(plan.destination);
            mc.player.connection.sendPacket(new CPacketUseEntity(target));
            mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            List<BlinkPath.Point> returnPath = plannedReturnPath(
                origin, path, sent, plan.directReturn);
            for (BlinkPath.Point point : returnPath) sendPosition(point, transportOnGround);
            if (releaseAirborne) mc.player.connection.sendPacket(new CPacketPlayer(false));
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
            pendingPlans.clear();
            reachabilityFeet = null;
            reachabilityFlight = false;
        }
    }

    private void refreshReachabilityContext(BlockPos feet, boolean flightEnabled) {
        if (sameReachabilityContext(reachabilityFeet, reachabilityFlight, feet, flightEnabled)) return;
        unreachableUntil.clear();
        reachableUntil.clear();
        if (pendingPlanContextChanged(reachabilityFeet, reachabilityFlight, flightEnabled)) {
            pendingPlans.clear();
        }
        reachabilityFeet = feet == null ? null : feet.toImmutable();
        reachabilityFlight = flightEnabled;
    }

    static boolean sameReachabilityContext(BlockPos cachedFeet, boolean cachedFlight,
            BlockPos currentFeet, boolean currentFlight) {
        return cachedFeet != null && cachedFeet.equals(currentFeet)
            && cachedFlight == currentFlight;
    }

    static boolean pendingPlanContextChanged(BlockPos cachedFeet, boolean cachedFlight,
            boolean currentFlight) {
        return cachedFeet != null && cachedFlight != currentFlight;
    }

    private PlanBatch planStrikes(List<EntityLivingBase> targets, BlinkPath.Point origin,
            int targetLimit, int planningBudget) {
        List<PlannedStrike> result = new ArrayList<>();
        int tick = mc.player.ticksExisted;
        pruneUnreachableCache(tick);
        pendingPlans.entrySet().removeIf(entry -> entry.getValue().lastUsedTick < tick - UNREACHABLE_CACHE_TICKS);
        PlanningWorkBudget workBudget = new PlanningWorkBudget(
            candidatePlanningBudget(), routeCollisionBudget());
        int checked = 0;
        boolean incomplete = false;
        for (EntityLivingBase target : targets) {
            if (result.size() >= targetLimit || checked >= planningBudget) break;
            Integer blockedUntil = unreachableUntil.get(target.getEntityId());
            if (blockedUntil != null && blockedUntil > tick) continue;
            checked++;
            PlanProgress progress = advanceStrikePlan(target, origin, workBudget, tick);
            if (!progress.complete) {
                incomplete = true;
                break;
            }
            StrikePlan plan = progress.plan;
            if (cacheUnreachablePlan(progress.complete, plan != null)) {
                unreachableUntil.put(target.getEntityId(), tick + UNREACHABLE_CACHE_TICKS);
                reachableUntil.remove(target.getEntityId());
                continue;
            }
            unreachableUntil.remove(target.getEntityId());
            reachableUntil.put(target.getEntityId(), tick + Math.max(UNREACHABLE_CACHE_TICKS,
                ModConfig.blinkDelayTicks + 2));
            result.add(new PlannedStrike(target, plan));
        }
        return new PlanBatch(result, incomplete);
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
        return Math.min(MAX_PLAN_CHECKS_PER_TICK, Math.max(4, requested * 2));
    }

    static int candidatePlanningBudget() {
        return MAX_CANDIDATE_CHECKS_PER_TICK;
    }

    static int routeCollisionBudget() {
        return MAX_COLLISION_CHECKS_PER_TICK;
    }

    static int routeCollisionChecks(int sampleCursor, int sampleCount, int budget) {
        int start = Math.max(0, Math.min(sampleCursor, Math.max(0, sampleCount)));
        return Math.min(Math.max(0, budget), Math.max(0, sampleCount - start));
    }

    static boolean routeSamplingRequired(int sampleCursor, int sampleCount) {
        return Math.max(0, sampleCursor) < Math.max(0, sampleCount);
    }

    static boolean cacheUnreachablePlan(boolean complete, boolean hasPlan) {
        return complete && !hasPlan;
    }

    static int candidateScanLimit(int requestedTargets) {
        return 50;
    }

    static double directAttackReach(boolean creativeExtendedReach) {
        return creativeExtendedReach ? CREATIVE_DIRECT_REACH : SURVIVAL_DIRECT_REACH;
    }

    private PlanProgress advanceStrikePlan(EntityLivingBase target, BlinkPath.Point origin,
            PlanningWorkBudget budget, int tick) {
        int targetId = target.getEntityId();
        if (target.isDead || target.getHealth() <= 0.0F) {
            pendingPlans.remove(targetId);
            return PlanProgress.complete(null);
        }
        PlanSearch search = pendingPlans.get(targetId);
        if (search == null || !search.matches(origin)) {
            search = new PlanSearch(target, origin);
            pendingPlans.put(targetId, search);
        }
        search.lastUsedTick = tick;
        if (!search.directChecked) {
            search.directChecked = true;
            StrikePlan direct = directStrikePlan(target, origin);
            if (direct != null) {
                pendingPlans.remove(targetId);
                return PlanProgress.complete(direct);
            }
        }

        while (true) {
            if (search.routeSamples != null) {
                if (routeSamplingRequired(
                        search.routeSampleCursor, search.routeSamples.size())) {
                    int checks = routeCollisionChecks(search.routeSampleCursor,
                        search.routeSamples.size(), budget.remainingCollisions);
                    if (checks <= 0) return PlanProgress.pending();
                    boolean blocked = false;
                    AxisAlignedBB base = mc.player.getEntityBoundingBox().offset(
                        search.origin.x - origin.x, search.origin.y - origin.y,
                        search.origin.z - origin.z);
                    int end = search.routeSampleCursor + checks;
                    while (search.routeSampleCursor < end) {
                        BlinkPath.Point point = search.routeSamples.get(search.routeSampleCursor++);
                        budget.remainingCollisions--;
                        AxisAlignedBB box = base.offset(point.x - search.origin.x,
                            point.y - search.origin.y, point.z - search.origin.z).shrink(0.02);
                        if (!mc.world.getWorldBorder().contains(box)
                                || !mc.world.getCollisionBoxes(mc.player, box).isEmpty()) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        search.clearRoute();
                        continue;
                    }
                    if (routeSamplingRequired(
                            search.routeSampleCursor, search.routeSamples.size())) {
                        return PlanProgress.pending();
                    }
                }
                if (!planDestinationStillValid(target, origin, search.candidate)) {
                    pendingPlans.remove(targetId);
                    return PlanProgress.pending();
                }
                boolean originChanged = search.origin.distanceTo(origin) > 0.0001;
                List<BlinkPath.Point> packetPath = search.packetPath;
                if (originChanged) {
                    packetPath = rebasePath(origin, search.currentWaypoints, search.step);
                    boolean directReturn = search.currentWaypoints.size() == 1;
                    if (!remoteMovementPlanAccepted(origin, origin, packetPath, directReturn,
                            false, RESERVED_MOVEMENT_PACKETS)) {
                        search.clearRoute();
                        continue;
                    }
                    List<BlinkPath.Point> currentSamples = rebasePath(
                        origin, search.currentWaypoints, 0.4);
                    if (currentSamples.size() > budget.remainingCollisions) {
                        return PlanProgress.pending();
                    }
                    AxisAlignedBB currentBase = mc.player.getEntityBoundingBox();
                    boolean currentRouteBlocked = false;
                    for (BlinkPath.Point point : currentSamples) {
                        budget.remainingCollisions--;
                        AxisAlignedBB box = currentBase.offset(point.x - origin.x,
                            point.y - origin.y, point.z - origin.z).shrink(0.02);
                        if (!mc.world.getWorldBorder().contains(box)
                                || !mc.world.getCollisionBoxes(mc.player, box).isEmpty()) {
                            currentRouteBlocked = true;
                            break;
                        }
                    }
                    if (currentRouteBlocked) {
                        search.clearRoute();
                        continue;
                    }
                }
                StrikePlan plan = new StrikePlan(search.candidate, packetPath,
                    new ArrayList<>(search.currentWaypoints), search.currentWaypoints.size() == 1);
                pendingPlans.remove(targetId);
                return PlanProgress.complete(plan);
            }

            if (search.routes != null && search.routeIndex < search.routes.size()) {
                search.currentWaypoints = search.routes.get(search.routeIndex++);
                search.packetPath = buildPath(search.origin, search.currentWaypoints, search.step);
                if (!remoteMovementPlanAccepted(search.origin, search.origin, search.packetPath,
                        search.currentWaypoints.size() == 1, false, RESERVED_MOVEMENT_PACKETS)) {
                    search.currentWaypoints = null;
                    search.packetPath = null;
                    continue;
                }
                search.routeSamples = buildPath(search.origin, search.currentWaypoints, 0.4);
                search.routeSampleCursor = 0;
                continue;
            }

            if (search.routes != null) search.clearCandidate();
            if (search.candidateIndex >= search.candidates.size()) {
                pendingPlans.remove(targetId);
                return PlanProgress.complete(null);
            }
            if (budget.remainingCandidates <= 0) return PlanProgress.pending();
            budget.remainingCandidates--;
            BlinkPath.Point candidate = search.candidates.get(search.candidateIndex++);
            if (!candidateEligible(search, target, candidate)) continue;
            search.candidate = candidate;
            search.routes = routeWaypoints(search.origin, candidate);
            search.routeIndex = 0;
        }
    }

    private StrikePlan directStrikePlan(EntityLivingBase target, BlinkPath.Point origin) {
        Vec3d originEyes = new Vec3d(origin.x, origin.y + mc.player.getEyeHeight(), origin.z);
        Vec3d currentAttackPoint = ModConfig.blinkAttackPoint.point(target);
        double directReach = directAttackReach(mc.playerController.extendedReach());
        if (CombatSupport.distanceSqToHitbox(originEyes, target.getEntityBoundingBox())
                <= directReach * directReach && hasAttackLine(originEyes, currentAttackPoint)
                && serverAttackCandidateAllows(origin, target)) {
            return new StrikePlan(origin, java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), false);
        }
        return null;
    }

    private boolean candidateEligible(PlanSearch search, EntityLivingBase target,
            BlinkPath.Point candidate) {
        if (!destinationWithinRange(search.origin, candidate, search.range)) return false;
        Vec3d eyes = new Vec3d(candidate.x, candidate.y + mc.player.getEyeHeight(), candidate.z);
        if (CombatSupport.distanceSqToHitbox(eyes, search.predictedBox)
                > search.attackDistance * search.attackDistance) return false;
        return hasAttackLine(eyes, search.attackPoint)
            && serverAttackCandidateAllows(candidate, target);
    }

    private boolean planDestinationStillValid(EntityLivingBase target, BlinkPath.Point origin,
            BlinkPath.Point destination) {
        if (target == null || target.isDead || target.getHealth() <= 0.0F) return false;
        if (!destinationWithinRange(origin, destination, ModConfig.blinkRange)) return false;
        BlinkPath.Point predicted = predictedTargetPosition(target.posX,
            target.getEntityBoundingBox().minY, target.posZ, target.motionX, target.motionZ,
            ModConfig.blinkPredictTicks);
        AxisAlignedBB predictedBox = target.getEntityBoundingBox().offset(
            predicted.x - target.posX, predicted.y - target.getEntityBoundingBox().minY,
            predicted.z - target.posZ);
        Vec3d targetPoint = ModConfig.blinkAttackPoint.point(target);
        Vec3d attackPoint = new Vec3d(targetPoint.x + predicted.x - target.posX,
            targetPoint.y + predicted.y - target.getEntityBoundingBox().minY,
            targetPoint.z + predicted.z - target.posZ);
        Vec3d eyes = new Vec3d(destination.x,
            destination.y + mc.player.getEyeHeight(), destination.z);
        return CombatSupport.distanceSqToHitbox(eyes, predictedBox)
                <= ModConfig.blinkAttackDistance * ModConfig.blinkAttackDistance
            && hasAttackLine(eyes, attackPoint)
            && serverAttackCandidateAllows(destination, target);
    }

    private StrikePlan refreshStrikePlan(EntityLivingBase target, BlinkPath.Point origin, StrikePlan planned) {
        return target == null || target.isDead || target.getHealth() <= 0.0F ? null : planned;
    }

    static boolean strikePlanStillUsable(boolean targetAlive, boolean lineAndRangeValid,
            boolean routeClear) {
        return targetAlive && lineAndRangeValid && routeClear;
    }

    static BlinkPath.Point predictedTargetPosition(double x, double minY, double z,
            double motionX, double motionZ, int predictTicks) {
        int ticks = Math.max(0, predictTicks);
        return new BlinkPath.Point(x + motionX * ticks, minY, z + motionZ * ticks);
    }

    private boolean hasAttackLine(Vec3d eyes, Vec3d attackPoint) {
        return mc.world.rayTraceBlocks(eyes, attackPoint, false, true, false) == null;
    }

    private boolean serverAttackCandidateAllows(BlinkPath.Point candidate,
            EntityLivingBase target) {
        Vec3d eyes = new Vec3d(candidate.x, candidate.y + mc.player.getEyeHeight(), candidate.z);
        Vec3d targetEyes = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        boolean entityEyeVisible = mc.world.rayTraceBlocks(
            eyes, targetEyes, false, true, false) == null;
        return serverAttackCandidateAllows(candidate, target.posX, target.posY, target.posZ,
            entityEyeVisible);
    }

    static boolean serverAttackCandidateAllows(BlinkPath.Point candidate, double targetX,
            double targetY, double targetZ, boolean entityEyeVisible) {
        double dx = candidate.x - targetX;
        double dy = candidate.y - targetY;
        double dz = candidate.z - targetZ;
        return serverAttackEnvelopeAllows(dx * dx + dy * dy + dz * dz, entityEyeVisible);
    }

    static boolean serverAttackEnvelopeAllows(double entityPositionDistanceSq,
            boolean entityEyeVisible) {
        return entityPositionDistanceSq < (entityEyeVisible ? 36.0D : 9.0D);
    }

    static boolean remoteMovementPlanAccepted(BlinkPath.Point origin,
            List<BlinkPath.Point> outward, boolean critical, int priorMovementPackets) {
        return remoteMovementPlanAccepted(origin, origin, outward, false, critical,
            false, priorMovementPackets);
    }

    static boolean remoteMovementPlanAccepted(BlinkPath.Point movementOrigin,
            BlinkPath.Point returnOrigin, List<BlinkPath.Point> outward, boolean directReturn,
            boolean critical, int priorMovementPackets) {
        return remoteMovementPlanAccepted(movementOrigin, returnOrigin, outward, directReturn,
            critical, false, priorMovementPackets);
    }

    static boolean remoteMovementPlanAccepted(BlinkPath.Point movementOrigin,
            BlinkPath.Point returnOrigin, List<BlinkPath.Point> outward, boolean directReturn,
            boolean critical, boolean releaseAirborne, int priorMovementPackets) {
        if (movementOrigin == null || returnOrigin == null
                || outward == null || outward.isEmpty()) return false;
        int ordinal = Math.max(0, priorMovementPackets);
        for (BlinkPath.Point point : outward) {
            if (!vanillaSameTickMoveAccepted(
                    ++ordinal, displacementSq(movementOrigin, point))) return false;
        }
        BlinkPath.Point destination = outward.get(outward.size() - 1);
        if (critical) {
            if (!vanillaSameTickMoveAccepted(++ordinal,
                    displacementSq(movementOrigin, destination.x,
                        destination.y + 0.0625D, destination.z))) {
                return false;
            }
            if (!vanillaSameTickMoveAccepted(
                    ++ordinal, displacementSq(movementOrigin, destination))) return false;
            if (!vanillaSameTickMoveAccepted(++ordinal,
                    displacementSq(movementOrigin, destination.x,
                        destination.y + 0.0125D, destination.z))) {
                return false;
            }
            if (!vanillaSameTickMoveAccepted(
                    ++ordinal, displacementSq(movementOrigin, destination))) return false;
        }
        for (BlinkPath.Point point : plannedReturnPath(
                returnOrigin, outward, outward.size(), directReturn)) {
            if (!vanillaSameTickMoveAccepted(
                    ++ordinal, displacementSq(movementOrigin, point))) return false;
        }
        if (releaseAirborne && !vanillaSameTickMoveAccepted(
                ++ordinal, displacementSq(movementOrigin, returnOrigin))) return false;
        return true;
    }

    static List<BlinkPath.Point> plannedReturnPath(BlinkPath.Point returnOrigin,
            List<BlinkPath.Point> outward, int sentPoints, boolean directReturn) {
        if (directReturn && sentPoints > 0) {
            return java.util.Collections.singletonList(returnOrigin);
        }
        return completeReturnPath(returnOrigin, outward, sentPoints);
    }

    static boolean vanillaSameTickMoveAccepted(int packetOrdinal, double firstGoodDisplacementSq) {
        int multiplier = packetOrdinal > 5 ? 1 : Math.max(1, packetOrdinal);
        return firstGoodDisplacementSq <= 100.0D * multiplier;
    }

    private static double displacementSq(BlinkPath.Point origin, BlinkPath.Point point) {
        return displacementSq(origin, point.x, point.y, point.z);
    }

    private static double displacementSq(BlinkPath.Point origin, double x, double y, double z) {
        double dx = x - origin.x;
        double dy = y - origin.y;
        double dz = z - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }

    static List<List<BlinkPath.Point>> routeWaypoints(BlinkPath.Point origin, BlinkPath.Point destination) {
        List<List<BlinkPath.Point>> routes = new ArrayList<>(4);
        routes.add(java.util.Collections.singletonList(destination));
        addRoute(routes, origin, new BlinkPath.Point(destination.x, origin.y, destination.z), destination);
        addRoute(routes, origin, new BlinkPath.Point(origin.x, destination.y, origin.z), destination);
        double raisedY = Math.max(origin.y, destination.y) + 1.0;
        List<BlinkPath.Point> raised = new ArrayList<>(3);
        raised.add(new BlinkPath.Point(origin.x, raisedY, origin.z));
        raised.add(new BlinkPath.Point(destination.x, raisedY, destination.z));
        raised.add(destination);
        routes.add(raised);
        return routes;
    }

    private static void addRoute(List<List<BlinkPath.Point>> routes, BlinkPath.Point origin,
            BlinkPath.Point waypoint, BlinkPath.Point destination) {
        if (waypoint.distanceTo(origin) <= 0.0001
                || waypoint.distanceTo(destination) <= 0.0001) return;
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

    static List<BlinkPath.Point> rebasePath(BlinkPath.Point currentOrigin,
            List<BlinkPath.Point> waypoints, double maxStep) {
        return buildPath(currentOrigin, waypoints, maxStep);
    }

    static List<BlinkPath.Point> candidatePositions(BlinkPath.Point origin, double targetX, double targetY,
            double targetZ, double attackDistance) {
        double dx = origin.x - targetX;
        double dz = origin.z - targetZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        double baseAngle = length > 0.001 ? Math.atan2(dz, dx) : 0.0;
        double radius = Math.max(0.65, attackDistance * 0.72);
        List<BlinkPath.Point> result = new ArrayList<>(16);
        double[] heights = candidateHeights(origin.y, targetY);
        for (double height : heights) {
            for (int i = 0; i < 8; i++) {
                double angle = baseAngle + Math.PI * 2.0 * i / 8.0;
                result.add(new BlinkPath.Point(targetX + Math.cos(angle) * radius, height,
                    targetZ + Math.sin(angle) * radius));
            }
        }
        return result;
    }

    static double[] candidateHeights(double originY, double targetY) {
        return Math.abs(originY - targetY) > 2.0
            ? new double[] {originY, targetY}
            : new double[] {targetY, originY};
    }

    private void face(EntityLivingBase target) {
        float[] rotations = CombatSupport.rotations(mc.player.getPositionEyes(1.0F), target, ModConfig.blinkAttackPoint);
        mc.player.rotationYaw = rotations[0];
        mc.player.rotationPitch = rotations[1];
    }

    private boolean originOnGround(BlinkPath.Point origin) {
        AxisAlignedBB box = mc.player.getEntityBoundingBox()
            .offset(origin.x - mc.player.posX, origin.y - mc.player.posY, origin.z - mc.player.posZ)
            .offset(0.0, -0.04, 0.0);
        boolean collisionBelow = !mc.world.getCollisionBoxes(mc.player, box).isEmpty();
        return actualGrounded(mc.player.onGround, modules.isEnabled(ModuleId.FLIGHT), collisionBelow);
    }

    static boolean actualGrounded(boolean reportedOnGround, boolean controlledFlight,
            boolean collisionBelow) {
        return collisionBelow || (reportedOnGround && !controlledFlight);
    }

    private boolean safeAirborneOrigin() {
        return safeAirborneOrigin(mc.player.isInWater(), mc.player.isInLava(), mc.player.isOnLadder(),
            mc.player.capabilities.isFlying, modules.isEnabled(ModuleId.FLIGHT));
    }

    static boolean safeAirborneOrigin(boolean inWater, boolean inLava, boolean onLadder, boolean flying,
            boolean controlledFlight) {
        return inWater || inLava || onLadder || flying || controlledFlight;
    }

    static boolean transportOnGround(boolean originOnGround) {
        return originOnGround;
    }

    static boolean transportOnGround(boolean originOnGround, boolean vanillaFlight) {
        return originOnGround || vanillaFlight;
    }

    static boolean releaseAirborneTransport(boolean originOnGround, boolean transportOnGround) {
        return !originOnGround && transportOnGround;
    }

    private void sendPosition(BlinkPath.Point point, boolean onGround) {
        mc.player.connection.sendPacket(new CPacketPlayer.Position(point.x, point.y, point.z, onGround));
    }

    private void sendPositionRotation(BlinkPath.Point point, float yaw, float pitch,
            boolean onGround) {
        mc.player.connection.sendPacket(new CPacketPlayer.PositionRotation(
            point.x, point.y, point.z, yaw, pitch, onGround));
    }

    private boolean remoteCriticalEligible(BlinkPath.Point point, boolean originOnGround) {
        if (!modules.isEnabled(ModuleId.CRITICALS) || !originOnGround) return false;
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isRiding()
                || mc.player.isPotionActive(MobEffects.BLINDNESS) || isLiquidAt(point)) return false;
        return true;
    }

    private void sendRemoteCritical(BlinkPath.Point point) {
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

    private final class PlanSearch {
        private final BlinkPath.Point origin;
        private final AxisAlignedBB predictedBox;
        private final Vec3d attackPoint;
        private final List<BlinkPath.Point> candidates;
        private final double range;
        private final double attackDistance;
        private final double step;
        private final int predictTicks;
        private final AttackPoint configuredAttackPoint;
        private boolean directChecked;
        private int candidateIndex;
        private BlinkPath.Point candidate;
        private List<List<BlinkPath.Point>> routes;
        private int routeIndex;
        private List<BlinkPath.Point> currentWaypoints;
        private List<BlinkPath.Point> packetPath;
        private List<BlinkPath.Point> routeSamples;
        private int routeSampleCursor;
        private int lastUsedTick;

        private PlanSearch(EntityLivingBase target, BlinkPath.Point origin) {
            this.origin = origin;
            this.range = ModConfig.blinkRange;
            this.attackDistance = ModConfig.blinkAttackDistance;
            this.step = ModConfig.blinkStep;
            this.predictTicks = ModConfig.blinkPredictTicks;
            this.configuredAttackPoint = ModConfig.blinkAttackPoint;
            BlinkPath.Point predicted = predictedTargetPosition(target.posX,
                target.getEntityBoundingBox().minY, target.posZ, target.motionX, target.motionZ,
                predictTicks);
            this.predictedBox = target.getEntityBoundingBox().offset(
                predicted.x - target.posX, predicted.y - target.getEntityBoundingBox().minY,
                predicted.z - target.posZ);
            Vec3d targetPoint = configuredAttackPoint.point(target);
            this.attackPoint = new Vec3d(targetPoint.x + predicted.x - target.posX,
                targetPoint.y + predicted.y - target.getEntityBoundingBox().minY,
                targetPoint.z + predicted.z - target.posZ);
            this.candidates = candidatePositions(origin, predicted.x, predicted.y, predicted.z,
                attackDistance);
        }

        private boolean matches(BlinkPath.Point currentOrigin) {
            return planningOriginMatches(origin, currentOrigin, step)
                && Double.compare(range, ModConfig.blinkRange) == 0
                && Double.compare(attackDistance, ModConfig.blinkAttackDistance) == 0
                && Double.compare(step, ModConfig.blinkStep) == 0
                && predictTicks == ModConfig.blinkPredictTicks
                && configuredAttackPoint == ModConfig.blinkAttackPoint;
        }

        private void clearRoute() {
            currentWaypoints = null;
            packetPath = null;
            routeSamples = null;
            routeSampleCursor = 0;
        }

        private void clearCandidate() {
            clearRoute();
            candidate = null;
            routes = null;
            routeIndex = 0;
        }
    }

    static boolean planningOriginMatches(BlinkPath.Point plannedOrigin,
            BlinkPath.Point currentOrigin, double maxDrift) {
        return plannedOrigin != null && currentOrigin != null && maxDrift >= 0.0
            && plannedOrigin.distanceTo(currentOrigin) <= maxDrift;
    }

    static boolean destinationWithinRange(BlinkPath.Point origin,
            BlinkPath.Point destination, double range) {
        return origin != null && destination != null && range >= 0.0
            && origin.distanceTo(destination) <= range;
    }

    private static final class PlanningWorkBudget {
        private int remainingCandidates;
        private int remainingCollisions;

        private PlanningWorkBudget(int candidates, int collisions) {
            this.remainingCandidates = Math.max(0, candidates);
            this.remainingCollisions = Math.max(0, collisions);
        }
    }

    private static final class PlanProgress {
        private final boolean complete;
        private final StrikePlan plan;

        private PlanProgress(boolean complete, StrikePlan plan) {
            this.complete = complete;
            this.plan = plan;
        }

        private static PlanProgress pending() {
            return new PlanProgress(false, null);
        }

        private static PlanProgress complete(StrikePlan plan) {
            return new PlanProgress(true, plan);
        }
    }

    private static final class PlanBatch {
        private final List<PlannedStrike> strikes;
        private final boolean incomplete;

        private PlanBatch(List<PlannedStrike> strikes, boolean incomplete) {
            this.strikes = strikes;
            this.incomplete = incomplete;
        }
    }

    private static final class StrikePlan {
        private final BlinkPath.Point destination;
        private final List<BlinkPath.Point> path;
        private final List<BlinkPath.Point> waypoints;
        private final boolean directReturn;

        private StrikePlan(BlinkPath.Point destination, List<BlinkPath.Point> path,
                List<BlinkPath.Point> waypoints, boolean directReturn) {
            this.destination = destination;
            this.path = path;
            this.waypoints = waypoints;
            this.directReturn = directReturn;
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

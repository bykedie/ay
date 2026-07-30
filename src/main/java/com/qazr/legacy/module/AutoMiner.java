package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockMagma;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class AutoMiner {
    private static final int MAX_PATH_NODES = 1600;
    private static final int PATH_NODES_PER_TICK = 128;
    private static final int PATH_STATE_CHECKS_PER_TICK = 64;
    private static final int MAX_PATH_STATES_TO_VALIDATE = 128;
    private static final int MAX_PATH_SEARCH_RESTARTS = 1;
    private static final int REQUIRED_STABLE_PATH_VALIDATION_PASSES = 2;
    private static final int PATH_CANDIDATE_BATCH_SIZE = 4;
    private static final int MAX_ROUTE_COMPARISON_TICKS = 4;
    private static final int MAX_FAILED_CANDIDATES_PER_SNAPSHOT = 8;
    private static final int MAX_CACHED_TARGETS = 96;
    private static final int PATH_RETRY_TICKS = 20;
    private static final int FAILED_ROUTE_RETRY_TICKS = 100;
    private static final double ROUTE_SPEED = 0.18;
    private static final int MAX_VISIBLE_TARGETS = 16;
    private static final int FIXED_LABELED_VISIBILITY_INSPECTIONS = 8;
    private static final int BLOCK_VISIBILITY_SAMPLE_COUNT = 7;
    private static final int MAX_STALLED_ROUTE_TICKS = 30;
    private static final int MAX_STALLED_ROUTE_REPLANS = 1;
    private static final double ROUTE_PROGRESS_EPSILON = 0.0025;
    private static final double ROUTE_NODE_REACH_DISTANCE_SQ = 0.04;
    private static final double PRECISE_ROUTE_NODE_REACH_DISTANCE_SQ = 0.01;
    private static final int ROUTE_RENDER_LIMIT = 220;
    private static final int SCAFFOLD_TIMEOUT_TICKS = 40;
    private static final int SCAFFOLD_RETRY_TICKS = 20;
    private static final int SCAFFOLD_ASCENT_RETRY_TICKS = 4;
    private static final int MAX_SCAFFOLD_ATTEMPTS = 5;
    private static final int MAX_ROUTE_VERTICAL_STEP = 1;
    private static final int MIN_DESTRUCTION_ATTEMPTS = 12;
    private static final int MAX_DESTRUCTION_ATTEMPTS = 240;
    private static final int DESTRUCTION_ATTEMPT_GRACE = 8;
    private static final int DESTRUCTION_RETRY_TICKS = 200;
    private static final int COMPLETION_CONFIRM_TICKS = 40;
    private static final int MAX_PENDING_COMPLETIONS = 16;
    private static final int REQUIRED_MISSING_CONFIRM_TICKS = 3;
    private static final int[] PATH_VERTICAL_OFFSETS = {0, 1, -1};

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final OreVisualizer oreVisualizer;
    private final EnumMap<OreType, Integer> minedCounts = new EnumMap<>(OreType.class);
    private final List<AxisAlignedBB> supportCollisionBoxes = new ArrayList<>(4);
    private final Map<BlockPos, Integer> targetLabels = new HashMap<>();
    private final Map<BlockPos, Integer> blockedTargetsUntil = new HashMap<>();
    private final Map<BlockPos, Integer> rejectedTargetsUntil = new HashMap<>();
    private final Map<BlockPos, Integer> rejectedObstaclesUntil = new HashMap<>();
    private final Map<BlockPos, Integer> rejectedScaffoldsUntil = new HashMap<>();
    private final Map<BlockPos, RejectedMiningStands> rejectedMiningStandsUntil =
        new HashMap<>();
    private final List<OreVisualizer.CachedOre> labeledVisibilityCandidates =
        new ArrayList<>(MAX_CACHED_TARGETS);
    private List<OreVisualizer.CachedOre> labeledVisibilityCandidateSource;
    private List<BlockPos> path = java.util.Collections.emptyList();
    private BlockPos currentOre;
    private OreType currentOreType;
    private BlockPos miningPos;
    private OreType miningType;
    private boolean miningRouteBlocker;
    private int miningAttempts;
    private int miningAttemptBudget;
    private int miningDeadlineTick;
    private BlockPos clearingPos;
    private int clearingAttempts;
    private int clearingAttemptBudget;
    private int clearingDeadlineTick;
    private int clearingMissingTicks;
    private final Deque<PendingCompletion> pendingCompletions = new ArrayDeque<>();
    private BlockPos lastMinedOre;
    private OreType lastMinedType;
    private int pathIndex;
    private int delay;
    private int manualPause;
    private int pathRetryDelay;
    private long pathRetryMarkerRevision = Long.MIN_VALUE;
    private long pathRetrySelectionRevision = Long.MIN_VALUE;
    private int pathCandidateOffset;
    private List<OreVisualizer.CachedOre> pathCandidateBatch = java.util.Collections.emptyList();
    private BlockPos pathCandidateFeet;
    private PathTarget pendingPathTarget;
    private final Set<BlockPos> pendingFailedPathTargets = new HashSet<>();
    private int pendingPathTargetScore = Integer.MAX_VALUE;
    private boolean pendingPathTargetSameVein;
    private int pendingPathTargetLabel = Integer.MAX_VALUE;
    private int pendingPathComparisonTicks;
    private PathSearch pendingPathSearch;
    private boolean pathSnapshotRefreshRequested;
    private boolean observedEnabled;
    private long observedSelectionRevision = Long.MIN_VALUE;
    private BlockPos miningPlayerFeet;
    private Vec3d miningEyes;
    private double miningReachDistance;
    private double lastRouteDistanceSq = Double.POSITIVE_INFINITY;
    private int stalledRouteTicks;
    private BlockPos stalledRouteOre;
    private OreType stalledRouteType;
    private BlockPos stalledRouteFeet;
    private int stalledRouteReplans;
    private OreType targetLabelType;
    private int labeledVisibilityCursor;
    private BlockPos scaffoldPos;
    private BlockPos scaffoldOre;
    private int scaffoldStartedTick;
    private int scaffoldNextPlaceTick;
    private int scaffoldAttempts;
    private List<BlockPos> plannedObstacleCache = java.util.Collections.emptyList();
    private List<BlockPos> plannedObstacleCachePath = java.util.Collections.emptyList();
    private int plannedObstacleCacheTick = Integer.MIN_VALUE;
    private int plannedObstacleCacheIndex = -1;
    private List<BlockPos> routeCorridorCache = java.util.Collections.emptyList();
    private List<BlockPos> routeCorridorCachePath = java.util.Collections.emptyList();
    private int routeCorridorCacheIndex = -1;
    private BlockPos routeCorridorCacheStart;
    private List<OreVisualizer.CachedOre> currentCandidateCache = java.util.Collections.emptyList();
    private int currentCandidateTickBucket = Integer.MIN_VALUE;
    private BlockPos currentCandidateFeet;
    private long currentCandidateMarkerRevision = Long.MIN_VALUE;
    private long currentCandidateSelectionRevision = Long.MIN_VALUE;
    private double currentCandidateX = Double.NaN;
    private double currentCandidateY = Double.NaN;
    private double currentCandidateZ = Double.NaN;
    private List<OreVisualizer.CachedOre> extendedTargetLabelCandidates =
        java.util.Collections.emptyList();

    public AutoMiner(ModuleManager modules, OreVisualizer oreVisualizer) {
        this.modules = modules;
        this.oreVisualizer = oreVisualizer;
        reloadTargets();
    }

    public void reloadTargets() {
        observedSelectionRevision = ModConfig.autoMineSelectionRevision();
        delay = 0;
        manualPause = 0;
        minedCounts.clear();
        pathRetryDelay = 0;
        blockedTargetsUntil.clear();
        rejectedTargetsUntil.clear();
        rejectedObstaclesUntil.clear();
        rejectedScaffoldsUntil.clear();
        rejectedMiningStandsUntil.clear();
        extendedTargetLabelCandidates = java.util.Collections.emptyList();
        invalidateCurrentCandidateCache();
        clearPendingCompletion();
        clearTargetLabels();
        clearPath();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player != null && mc.world != null) updateMinedCount();
        if (!modules.isEnabled(ModuleId.AUTO_MINE)) {
            if (observedEnabled) stopAutomatedWork(true);
            observedEnabled = false;
            return;
        }
        if (!observedEnabled) {
            reloadTargets();
            observedEnabled = true;
        }
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        long selectionRevision = ModConfig.autoMineSelectionRevision();
        if (selectionRevisionChanged(observedSelectionRevision, selectionRevision)) {
            observedSelectionRevision = selectionRevision;
            stopAutomatedWork(true);
            delay = 0;
        }
        if (mc.currentScreen != null) {
            stopAutomatedWork(true);
            return;
        }
        if (!ModConfig.hasEnabledMineOre()) return;
        miningPlayerFeet = playerNavigationFeetCell();
        miningEyes = mc.player.getPositionEyes(1.0F);
        miningReachDistance = mc.playerController.getBlockReachDistance();
        pruneBlockedTargets(mc.player.ticksExisted);
        pruneRejectedBlocks(mc.player.ticksExisted);
        pruneTargetLabels();
        if (allFiniteQuotasReached()) {
            modules.setEnabled(ModuleId.AUTO_MINE, false);
            stopAutomatedWork(true);
            return;
        }
        if (manualMovementRequested()) {
            manualPause = Math.max(manualPause, ModConfig.mineManualPauseTicks);
            stopAutomatedWork(false);
        }
        if (manualPause > 0) {
            manualPause--;
            return;
        }
        if (continueClearingConfirmation()) return;
        if (delay-- > 0) return;
        if (continueScaffoldAssist()) return;
        if (currentRouteAwaitingCompletion()) {
            stopRouteMotion();
            return;
        }
        if (continueClearingObstacle()) return;
        if (continueMiningTarget()) return;
        if (tryMineCurrentOreDirectly()) return;
        if (ModConfig.mineScaffoldAssist && beginScaffoldAssist(currentOre, currentOreType)) return;
        if (hasActiveRoute()) {
            followPathToOre();
            return;
        }
        long markerRevision = oreVisualizer.markerRevision();
        boolean markerChangedDuringRetry = pathRetryInterruptedByMarkerChange(
            pathRetryDelay, pathRetryMarkerRevision, markerRevision);
        boolean feetChangedDuringRetry = pathRetryInterruptedByFeetChange(
            pathRetryDelay, currentCandidateFeet, miningPlayerFeet);
        int candidateTickBucket = mc.player.ticksExisted / 4;
        boolean selectionChangedDuringRetry = pathRetryInterruptedBySelectionChange(
            pathRetryDelay, pathRetrySelectionRevision, selectionRevision);
        if (markerChangedDuringRetry && reuseCurrentCandidateCache(currentCandidateTickBucket,
                candidateTickBucket, currentCandidateFeet, miningPlayerFeet,
                currentCandidateSelectionRevision, selectionRevision)) {
            mc.player.motionX = planningMotion(mc.player.motionX);
            mc.player.motionZ = planningMotion(mc.player.motionZ);
            return;
        }
        if (markerChangedDuringRetry || feetChangedDuringRetry || selectionChangedDuringRetry) {
            invalidateCurrentCandidateCache();
        }
        if (!feetChangedDuringRetry && !selectionChangedDuringRetry
                && continuePathRetryDelay(pathRetryDelay, pathRetryMarkerRevision, markerRevision)) {
            pathRetryDelay--;
            mc.player.motionX = planningMotion(mc.player.motionX);
            mc.player.motionZ = planningMotion(mc.player.motionZ);
            return;
        }
        pathRetryDelay = 0;
        if (routeEndedBeforeMining(currentOre, pathIndex, path.size())) {
            prepareAndMineCurrentOre();
            return;
        }
        List<OreVisualizer.CachedOre> currentCandidates = currentMineCandidates();
        extendTargetLabels(currentCandidates);
        if (!targetLabels.isEmpty() && !containsLabeledCandidate(currentCandidates, targetLabels)) {
            clearTargetLabels();
        }
        MineTarget visible = findNearestReachable(currentCandidates);
        if (visible != null) {
            if (!preserveExistingLabelsForVisibleTarget(targetLabels, visible.pos)) {
                ensureTargetLabels(visible.pos, visible.type, currentCandidates);
            }
            mine(visible);
            return;
        }
        if (ModConfig.mineScaffoldAssist && beginScaffoldAssist(currentCandidates)) return;
        List<OreVisualizer.CachedOre> pathCandidates = reusePathCandidateSnapshot(
            pathCandidateBatch, pathCandidateFeet, miningPlayerFeet)
                ? pathCandidateBatch : currentCandidates;
        followPathToOre(pathCandidates);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!modules.isEnabled(ModuleId.AUTO_MINE) || !ModConfig.mineVisualizePath
                || mc.player == null || mc.world == null || currentOre == null) return;
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.0F);
        try {
            AxisAlignedBB box = new AxisAlignedBB(currentOre).offset(-viewerX, -viewerY, -viewerZ).grow(0.03);
            RenderGlobal.drawSelectionBoundingBox(box, 0.20F, 0.85F, 1.0F, 0.95F);
            for (BlockPos obstacle : plannedObstacles()) {
                AxisAlignedBB obstacleBox = new AxisAlignedBB(obstacle)
                    .offset(-viewerX, -viewerY, -viewerZ).grow(0.02);
                RenderGlobal.drawSelectionBoundingBox(obstacleBox, 1.0F, 0.45F, 0.10F, 0.95F);
            }
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            drawRoute(buffer, viewerX, viewerY, viewerZ);
            Tessellator.getInstance().draw();
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            delay = 0;
            manualPause = 0;
            pathRetryDelay = 0;
            blockedTargetsUntil.clear();
            rejectedTargetsUntil.clear();
            rejectedObstaclesUntil.clear();
            rejectedScaffoldsUntil.clear();
            rejectedMiningStandsUntil.clear();
            minedCounts.clear();
            lastMinedOre = null;
            lastMinedType = null;
            clearTargetLabels();
            clearPath();
            miningPlayerFeet = null;
            miningEyes = null;
            miningReachDistance = 0.0;
            extendedTargetLabelCandidates = java.util.Collections.emptyList();
            invalidateCurrentCandidateCache();
            clearScaffoldAssist();
            clearPendingCompletion();
            supportCollisionBoxes.clear();
        }
    }

    private void updateMinedCount() {
        boolean quotaAvailabilityChanged = false;
        Iterator<PendingCompletion> iterator = pendingCompletions.iterator();
        while (iterator.hasNext()) {
            PendingCompletion pending = iterator.next();
            if (pending.world != mc.world) {
                iterator.remove();
                continue;
            }
            if (!mc.world.isBlockLoaded(pending.pos)) {
                pending.missingTicks = 0;
                if (completionConfirmationExpired(mc.player.ticksExisted, pending.untilTick)) {
                    quotaAvailabilityChanged |= pending.reservesQuota;
                    iterator.remove();
                }
                continue;
            }
            OreType remainingType = OreType.fromBlock(
                mc.world.getBlockState(pending.pos).getBlock());
            if (remainingType == pending.type) {
                if (completionRolledBack(pending.absenceObserved)) {
                    quotaAvailabilityChanged |= pending.reservesQuota;
                    iterator.remove();
                    oreVisualizer.restoreMarker(pending.pos, pending.type);
                    coolDownCandidate(rejectedTargetsUntil, pending.pos,
                        mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
                    if (mc.playerController != null) mc.playerController.resetBlockRemoving();
                    boolean ownsCurrentWork = completionOwnsWork(pending.pos, pending.type,
                        currentOre, currentOreType);
                    boolean ownsMiningWork = completionOwnsWork(pending.pos, pending.type,
                        miningPos, miningType);
                    boolean ownsBoundRoute = completionOwnsWork(pending.routeOre, pending.routeType,
                        currentOre, currentOreType);
                    if (completionInvalidatesCurrentRoute(ownsCurrentWork, ownsBoundRoute)) {
                        restartRouteFromCurrentPosition();
                    }
                    else if (ownsMiningWork) clearMiningTarget();
                    continue;
                }
                pending.missingTicks = 0;
                if (completionConfirmationExpired(mc.player.ticksExisted, pending.untilTick)) {
                    quotaAvailabilityChanged |= pending.reservesQuota;
                    iterator.remove();
                }
                continue;
            }
            boolean previouslyReserved = pending.reservesQuota;
            pending.reservesQuota = pendingQuotaReservationAfter(
                pending.reservesQuota, PendingQuotaEvent.BLOCK_MISSING);
            quotaAvailabilityChanged |= previouslyReserved != pending.reservesQuota;
            pending.absenceObserved = true;
            pending.missingTicks++;
            if (!completionAbsenceConfirmed(true, pending.missingTicks)) continue;
            quotaAvailabilityChanged |= pending.reservesQuota;
            iterator.remove();
            minedCounts.put(pending.type, minedCount(pending.type) + 1);
            lastMinedOre = pending.pos;
            lastMinedType = pending.type;
            rejectedMiningStandsUntil.remove(pending.pos);
            oreVisualizer.reconcileMarker(pending.pos, remainingType);
            if (targetLabels.remove(pending.pos) != null) targetLabelsChanged();
            boolean ownsCurrentWork = completionOwnsWork(pending.pos, pending.type,
                currentOre, currentOreType);
            boolean ownsMiningWork = completionOwnsWork(pending.pos, pending.type,
                miningPos, miningType);
            if (ownsCurrentWork) reorderRemainingTargetLabels();
            if (targetLabels.isEmpty()) targetLabelType = null;
            if (ownsCurrentWork) clearPath();
            else if (ownsMiningWork) clearMiningTarget();
        }
        if (quotaAvailabilityChanged) invalidateCurrentCandidateCache();
    }

    private boolean allFiniteQuotasReached() {
        boolean hasLimitedEnabledOre = false;
        for (OreType type : OreType.values()) {
            if (!ModConfig.isMineOreEnabled(type)) continue;
            int target = ModConfig.getMineTargetCount(type);
            if (target <= 0) return false;
            hasLimitedEnabledOre = true;
            if (minedCount(type) < target) return false;
        }
        return hasLimitedEnabledOre;
    }

    private boolean quotaReached(OreType type) {
        int target = ModConfig.getMineTargetCount(type);
        return quotaSatisfied(target, minedCount(type), pendingCompletionCount(type));
    }

    private int pendingCompletionCount(OreType type) {
        int count = 0;
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world && pending.type == type && pending.reservesQuota) count++;
        }
        return count;
    }

    static boolean quotaSatisfied(int target, int confirmed, int pending) {
        return target > 0 && confirmed + pending >= target;
    }

    private int minedCount(OreType type) {
        Integer count = minedCounts.get(type);
        return count == null ? 0 : count;
    }

    private boolean manualMovementRequested() {
        if (ModConfig.mineManualPauseTicks <= 0) return false;
        return Math.abs(mc.player.movementInput.moveForward) > 0.01F
            || Math.abs(mc.player.movementInput.moveStrafe) > 0.01F
            || mc.gameSettings.keyBindJump.isKeyDown()
            || mc.gameSettings.keyBindSneak.isKeyDown();
    }

    private void mine(MineTarget target) {
        mine(target, false);
    }

    private void mine(MineTarget target, boolean routeBlocker) {
        selectBestPickaxe(target.pos);
        boolean sameMiningTarget = target.pos.equals(miningPos) && target.type == miningType;
        boolean targetChanged = miningTargetChanged(
            miningPos, miningType, target.pos, target.type);
        boolean resetController = miningControllerResetRequired(
            miningPos, miningType, target.pos, target.type);
        if (!sameMiningTarget) {
            if (targetChanged) {
                releasePendingQuotaReservation(miningPos, miningType);
            }
            if (resetController && mc.playerController != null) {
                mc.playerController.resetBlockRemoving();
            }
            miningAttempts = 0;
            miningAttemptBudget = destructionAttemptBudget(
                mc.world.getBlockState(target.pos).getPlayerRelativeBlockHardness(
                    mc.player, mc.world, target.pos));
            miningDeadlineTick = destructionDeadlineTick(mc.player.ticksExisted,
                miningAttemptBudget, ModConfig.mineDelayTicks);
        }
        if (destructionWorkExhausted(miningAttempts, miningAttemptBudget,
                mc.player.ticksExisted, miningDeadlineTick)) {
            rejectMiningTarget(target.pos, target.type);
            return;
        }
        face(target.pos);
        mc.playerController.onPlayerDamageBlock(target.pos, target.side);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        miningPos = target.pos;
        miningType = target.type;
        miningRouteBlocker = routeBlockerOwnership(
            sameMiningTarget, miningRouteBlocker, routeBlocker);
        miningAttempts++;
        boolean preserveRouteTarget = preserveQueuedRouteTarget(
            miningRouteBlocker, target.pos, target.type, currentOre, currentOreType);
        if (!hasActiveRoute() && !preserveRouteTarget && !preserveQueuedVeinTarget(
                target.pos, target.type, currentOre, currentOreType, targetLabels)) {
            currentOre = target.pos;
            currentOreType = target.type;
        }
        rememberPendingCompletion(target.pos, target.type);
        delay = ModConfig.mineDelayTicks;
    }

    private boolean continueMiningTarget() {
        if (miningPos == null || miningType == null) return false;
        if (OreType.fromBlock(mc.world.getBlockState(miningPos).getBlock()) != miningType) {
            clearMiningTarget();
            return true;
        }
        MineTarget target = visibleTarget(miningPos);
        if (target == null) {
            clearMiningTarget();
            return false;
        }
        mine(target, miningRouteBlocker);
        return true;
    }

    private void rejectMiningTarget(BlockPos target, OreType type) {
        coolDownCandidate(rejectedTargetsUntil, target,
            mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
        forgetPendingCompletion(target, type);
        mc.playerController.resetBlockRemoving();
        clearPath();
        delay = 2;
    }

    private boolean tryMineCurrentOreDirectly() {
        if (currentOre == null) return false;
        MineTarget target = visibleTarget(currentOre);
        if (target == null) return false;
        stopRouteMotion();
        mine(target);
        return true;
    }

    private void prepareAndMineCurrentOre() {
        if (currentOre == null || targetType(currentOre) == null) {
            restartRouteFromCurrentPosition();
            return;
        }
        stopRouteMotion();
        boolean stablePosition = stableMiningPosition(miningPlayerFeet, currentOre);
        boolean workAreaReady = miningWorkAreaReady(isPassable(miningPlayerFeet),
            isPassable(miningPlayerFeet.up()), hasSolidSupport(miningPlayerFeet));
        if (endpointRequiresAlternateStand(stablePosition, workAreaReady)) {
            rejectMiningStand(currentOre, miningPlayerFeet);
            restartRouteFromCurrentPosition();
            return;
        }
        BlockPos faceNeighbor = miningFaceNeighbor(miningPlayerFeet, currentOre);
        if (faceNeighbor == null) {
            rejectMiningStand(currentOre, miningPlayerFeet);
            restartRouteFromCurrentPosition();
            return;
        }
        if (!isPassable(faceNeighbor)) {
            if (!clearCorridorCell(faceNeighbor, faceNeighbor)) {
                rejectClearingObstacleAndReplan(faceNeighbor);
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        MineTarget routedTarget = visibleTarget(currentOre);
        if (routedTarget != null) {
            mine(routedTarget);
        } else if (clearMiningExposureObstacle(currentOre)) {
            delay = ModConfig.mineDelayTicks;
        } else {
            rejectMiningStand(currentOre, miningPlayerFeet);
            restartRouteFromCurrentPosition();
        }
    }

    private boolean beginScaffoldAssist(List<OreVisualizer.CachedOre> candidates) {
        if (candidates == null) return false;
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if ((!targetLabels.isEmpty() && !targetLabels.containsKey(candidate.pos()))
                    || !candidateTypeAvailable(
                        ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))
                    || targetTemporarilyUnavailable(candidate.pos())) continue;
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (beginScaffoldAssist(candidate.pos(), candidate.type())) {
                ensureTargetLabels(candidate.pos(), candidate.type(), candidates);
                return true;
            }
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return false;
    }

    private boolean beginScaffoldAssist(BlockPos ore, OreType type) {
        if (scaffoldPos != null || ore == null || type == null || targetType(ore) != type
                || scaffoldTemporarilyUnavailable(ore, rejectedScaffoldsUntil,
                    mc.player.ticksExisted)
                || !mc.player.onGround || mc.player.capabilities.isFlying
                || mc.player.isInWater() || mc.player.isInLava()) return false;
        if (!scaffoldCandidate(miningPlayerFeet, ore, true, isPassable(miningPlayerFeet),
                isPassable(miningPlayerFeet.up()), isPassable(miningPlayerFeet.up(2)),
                hasSolidSupport(miningPlayerFeet))
                || !scaffoldRaiseMakesTargetMineable(
                    miningPlayerFeet, miningEyes, ore, miningReachDistance)) return false;
        Vec3d raisedEyes = new Vec3d(miningEyes.x, miningEyes.y + 1.0, miningEyes.z);
        RayTraceResult hit = rayTraceTarget(raisedEyes, ore, type, false);
        if (hit == null
                || scaffoldPlacementFor(miningPlayerFeet) == null
                || findScaffoldBlock(miningPlayerFeet) < 0) return false;
        currentOre = ore.toImmutable();
        currentOreType = type;
        path = java.util.Collections.emptyList();
        pathIndex = 0;
        scaffoldPos = miningPlayerFeet.toImmutable();
        scaffoldOre = currentOre;
        scaffoldStartedTick = mc.player.ticksExisted;
        scaffoldNextPlaceTick = scaffoldStartedTick;
        scaffoldAttempts = 0;
        stopRouteMotion();
        mc.player.jump();
        return true;
    }

    private boolean continueScaffoldAssist() {
        if (scaffoldPos == null || scaffoldOre == null) return false;
        int tick = mc.player.ticksExisted;
        if (!ModConfig.mineScaffoldAssist || targetType(scaffoldOre) == null
                || tick - scaffoldStartedTick > SCAFFOLD_TIMEOUT_TICKS) {
            failScaffoldAssist();
            return false;
        }
        BlockPos actualFeet = playerNavigationFeetCell();
        if (!scaffoldColumnContains(scaffoldPos, actualFeet)) {
            failScaffoldAssist();
            return false;
        }
        stopRouteMotion();
        if (!isPassable(scaffoldPos)) {
            double playerFeetY = mc.player.getEntityBoundingBox().minY;
            if (playerReachedScaffoldLevel(playerFeetY, scaffoldPos.getY())) {
                MineTarget target = visibleTarget(scaffoldOre);
                if (target == null) {
                    failScaffoldAssist();
                    return false;
                }
                clearScaffoldAssist();
                mine(target);
                return true;
            }
            if (shouldRetryScaffoldAscent(tick, scaffoldNextPlaceTick, playerFeetY,
                    scaffoldPos.getY(), mc.player.motionY, mc.player.onGround)) {
                mc.player.motionY = Math.max(mc.player.motionY, 0.42D);
                mc.player.fallDistance = 0.0F;
                scaffoldNextPlaceTick = tick + SCAFFOLD_ASCENT_RETRY_TICKS;
            }
            return true;
        }
        ScaffoldPlacement placement = scaffoldPlacementFor(scaffoldPos);
        int slot = findScaffoldBlock(scaffoldPos);
        if (placement == null || slot < 0) {
            failScaffoldAssist();
            return false;
        }
        if (!readyToPlaceScaffold(mc.player.getEntityBoundingBox().minY, scaffoldPos.getY())) {
            if (mc.player.onGround) mc.player.jump();
            return true;
        }
        if (tick < scaffoldNextPlaceTick) return true;
        if (scaffoldAttemptsExhausted(scaffoldAttempts, isPassable(scaffoldPos))) {
            failScaffoldAssist();
            return false;
        }
        scaffoldAttempts++;
        placeScaffold(scaffoldPos, placement, slot);
        scaffoldNextPlaceTick = tick + AutoBridge.placementRetryDelay(scaffoldAttempts);
        return true;
    }

    static boolean scaffoldAttemptsExhausted(int attempts, boolean placementStillEmpty) {
        return attempts >= MAX_SCAFFOLD_ATTEMPTS && placementStillEmpty;
    }

    static boolean scaffoldColumnContains(BlockPos scaffold, BlockPos actualFeet) {
        return scaffold != null && actualFeet != null
            && scaffold.getX() == actualFeet.getX()
            && scaffold.getZ() == actualFeet.getZ()
            && actualFeet.getY() >= scaffold.getY()
            && actualFeet.getY() <= scaffold.getY() + 1;
    }

    static boolean scaffoldCandidate(BlockPos feet, BlockPos ore, boolean onGround,
            boolean feetClear, boolean headClear, boolean jumpClear, boolean supported) {
        if (feet == null || ore == null || !onGround || !feetClear || !headClear
                || !jumpClear || !supported) return false;
        int dx = Math.abs(feet.getX() - ore.getX());
        int dz = Math.abs(feet.getZ() - ore.getZ());
        return dx + dz <= 1 && ore.getY() > feet.getY() + 1
            && stableMiningPosition(feet.up(), ore);
    }

    static boolean scaffoldRaiseMakesTargetMineable(BlockPos feet, Vec3d eyes, BlockPos ore,
            double reach) {
        return feet != null && eyes != null && ore != null && reach > 0.0
            && !stableMiningPosition(feet, ore)
            && stableMiningPosition(feet.up(), ore)
            && withinMiningReach(new Vec3d(eyes.x, eyes.y + 1.0, eyes.z), ore, reach);
    }

    static boolean readyToPlaceScaffold(double playerFeetY, int scaffoldY) {
        return playerFeetY >= scaffoldY + 1.0;
    }

    static boolean playerReachedScaffoldLevel(double playerFeetY, int scaffoldY) {
        return playerFeetY >= scaffoldY + 0.99;
    }

    static boolean shouldRetryScaffoldAscent(int currentTick, int nextRetryTick,
            double playerFeetY, int scaffoldY, double motionY, boolean onGround) {
        return currentTick >= nextRetryTick && !playerReachedScaffoldLevel(playerFeetY, scaffoldY)
            && (onGround || motionY <= 0.0);
    }

    private void placeScaffold(BlockPos pos, ScaffoldPlacement placement, int sourceSlot) {
        int original = mc.player.inventory.currentItem;
        boolean swapped = sourceSlot >= 9;
        int inventorySlot = sourceSlot;
        if (swapped) {
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot,
                original, ClickType.SWAP, mc.player);
            sourceSlot = original;
        }
        try {
            mc.player.inventory.currentItem = sourceSlot;
            mc.playerController.updateController();
            Vec3d hit = scaffoldHitVec(placement.neighbor, placement.side);
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world,
                placement.neighbor, placement.side, hit, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.SUCCESS) mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            mc.player.inventory.currentItem = original;
            mc.playerController.updateController();
            if (swapped) {
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot,
                    original, ClickType.SWAP, mc.player);
            }
        }
    }

    private ScaffoldPlacement scaffoldPlacementFor(BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(side);
            IBlockState state = mc.world.getBlockState(neighbor);
            if (!state.getMaterial().isReplaceable() && state.getMaterial().blocksMovement()) {
                Vec3d eyes = mc.player.getPositionEyes(1.0F);
                EnumFacing clickedSide = side.getOpposite();
                Vec3d hit = scaffoldHitVec(neighbor, clickedSide);
                double reach = mc.playerController.getBlockReachDistance() + 0.5;
                if (eyes.squareDistanceTo(hit) <= reach * reach) {
                    return new ScaffoldPlacement(neighbor, clickedSide);
                }
            }
        }
        return null;
    }

    static Vec3d scaffoldHitVec(BlockPos neighbor, EnumFacing side) {
        return new Vec3d(neighbor.getX() + 0.5 + side.getXOffset() * 0.5,
            neighbor.getY() + 0.5 + side.getYOffset() * 0.5,
            neighbor.getZ() + 0.5 + side.getZOffset() * 0.5);
    }

    private int findScaffoldBlock(BlockPos pos) {
        for (int slot = 0; slot < 36; slot++) {
            if (canScaffoldWith(mc.player.inventory.getStackInSlot(slot), pos)) return slot;
        }
        return -1;
    }

    private boolean canScaffoldWith(ItemStack stack, BlockPos pos) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null && stableScaffoldBlock(
            block.getDefaultState().getMaterial().isSolid(), block instanceof BlockFalling,
            block.getDefaultState().isFullCube(),
            block instanceof BlockMagma || block instanceof BlockCactus)
            && block.canPlaceBlockAt(mc.world, pos);
    }

    static boolean stableScaffoldBlock(boolean solid, boolean falling, boolean fullCube,
            boolean hazardousSupport) {
        return solid && !falling && fullCube && !hazardousSupport;
    }

    private void clearScaffoldAssist() {
        scaffoldPos = null;
        scaffoldOre = null;
        scaffoldStartedTick = 0;
        scaffoldNextPlaceTick = 0;
        scaffoldAttempts = 0;
    }

    private void failScaffoldAssist() {
        if (scaffoldOre != null) {
            extendTargetCooldown(rejectedScaffoldsUntil, scaffoldOre,
                mc.player.ticksExisted + SCAFFOLD_RETRY_TICKS);
        }
        clearScaffoldAssist();
        clearPath();
    }

    private boolean continueClearingObstacle() {
        if (clearingPos == null) return false;
        if (!mc.world.isBlockLoaded(clearingPos) || isPassable(clearingPos)) return true;
        if (destructionWorkExhausted(clearingAttempts, clearingAttemptBudget,
                mc.player.ticksExisted, clearingDeadlineTick)) {
            rejectClearingObstacleAndReplan(clearingPos);
            return true;
        }
        if (!damageCorridorBlock(clearingPos)) {
            rejectClearingObstacleAndReplan(clearingPos);
            return true;
        }
        delay = ModConfig.mineDelayTicks;
        return true;
    }

    private boolean continueClearingConfirmation() {
        if (clearingPos == null) return false;
        boolean loaded = mc.world.isBlockLoaded(clearingPos);
        boolean passable = loaded && isPassable(clearingPos);
        clearingMissingTicks = nextClearingMissingTicks(
            loaded, passable, clearingMissingTicks);
        if (completionAbsenceConfirmed(loaded, clearingMissingTicks)) {
            clearClearingTarget();
            delay = 0;
            return false;
        }
        if (loaded && !passable) return false;
        stopRouteMotion();
        if (delay > 0) delay--;
        if (completionConfirmationExpired(mc.player.ticksExisted, clearingDeadlineTick)) {
            rejectClearingObstacleAndReplan(clearingPos);
        }
        return true;
    }

    private void rejectClearingObstacleAndReplan(BlockPos obstacle) {
        rejectRouteObstacle(obstacle);
        mc.playerController.resetBlockRemoving();
        stopRouteMotion();
        restartRouteFromCurrentPosition();
        delay = 2;
    }

    private void rejectRouteObstacle(BlockPos obstacle) {
        extendTargetCooldown(rejectedObstaclesUntil, obstacle,
            mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
    }

    static int nextClearingMissingTicks(boolean loaded, boolean passable, int previous) {
        if (!loaded || !passable) return 0;
        return Math.min(REQUIRED_MISSING_CONFIRM_TICKS, Math.max(0, previous) + 1);
    }

    private boolean hasActiveRoute() {
        return routeOwnsTarget(currentOre, pathIndex, path.size());
    }

    static boolean routeOwnsTarget(BlockPos currentOre, int pathIndex, int pathSize) {
        return currentOre != null && pathIndex >= 0 && pathIndex < pathSize;
    }

    static boolean invalidActiveRouteTarget(BlockPos currentOre, OreType currentType) {
        return currentOre != null && currentType == null;
    }

    private void followPathToOre() {
        followPathToOre(null);
    }

    private void followPathToOre(List<OreVisualizer.CachedOre> candidates) {
        OreType activeType = currentOre == null ? null : targetType(currentOre);
        if (invalidActiveRouteTarget(currentOre, activeType)) {
            restartRouteFromCurrentPosition();
            clearTargetLabels();
        }
        if (currentOre == null || activeType == null || pathIndex >= path.size()) {
            List<OreVisualizer.CachedOre> available = candidates == null
                ? prioritizeCurrentVein(oreVisualizer.cachedMineOres(
                    ModConfig.minePathRange, MAX_CACHED_TARGETS, type -> !quotaReached(type),
                    pos -> !targetTemporarilyUnavailable(pos)))
                : candidates;
            mc.player.motionX = planningMotion(mc.player.motionX);
            mc.player.motionZ = planningMotion(mc.player.motionZ);
            PathTarget target = findNearestPathTarget(available);
            if (target == null) {
                pathRetryDelay = pathSnapshotRefreshRequested ? 1
                    : pathSearchRetryDelay(pathCandidateOffset, pendingPathSearch != null);
                pathRetryMarkerRevision = oreVisualizer.markerRevision();
                pathRetrySelectionRevision = ModConfig.autoMineSelectionRevision();
                pathSnapshotRefreshRequested = false;
                return;
            }
            pathRetryDelay = 0;
            activatePathTarget(target);
            if (routeEndedBeforeMining(currentOre, pathIndex, path.size())) {
                prepareAndMineCurrentOre();
                return;
            }
        }
        BlockPos next = path.get(pathIndex);
        double physicalFeetY = mc.player.getEntityBoundingBox().minY;
        boolean navigationSupport = standingSurfaceSupportsNavigation(physicalFeetY);
        double navigationFeetY = navigationFeetY(physicalFeetY, mc.player.onGround, navigationSupport);
        BlockPos actualFeet = playerFeetCell(mc.player.posX, navigationFeetY, mc.player.posZ);
        BlockPos from = pathIndex == 0
            ? standPos(actualFeet)
            : path.get(pathIndex - 1);
        if (!routeTransitionIsControlled(from, next)) {
            stopRouteMotion();
            restartRouteFromCurrentPosition();
            return;
        }
        if (!routeTransitionContains(actualFeet, from, next)) {
            stopRouteMotion();
            restartRouteFromCurrentPosition();
            return;
        }
        if (routeRequiresSupportRemoval(from, next) && !isPassable(from.down())) {
            stopRouteMotion();
            if (!clearCorridorCell(from.down(), from.down())) {
                rejectClearingObstacleAndReplan(from.down());
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        BlockPos jumpStart = actualFeet.equals(next) ? from : actualFeet;
        if (next.getY() > jumpStart.getY() && !isPassable(jumpStart.up(2))) {
            stopRouteMotion();
            if (!clearCorridorCell(jumpStart.up(2), jumpStart.up(2))) {
                rejectClearingObstacleAndReplan(jumpStart.up(2));
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        if (!isStandable(next)) {
            stopRouteMotion();
            if (!clearBlockingObstacle(next)) {
                restartRouteFromCurrentPosition();
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        double dx = next.getX() + 0.5 - mc.player.posX;
        double dz = next.getZ() + 0.5 - mc.player.posZ;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq > 16.0) {
            restartRouteFromCurrentPosition();
            return;
        }
        double verticalDistance = next.getY() - navigationFeetY;
        BlockPos following = pathIndex + 1 < path.size() ? path.get(pathIndex + 1) : null;
        double reachDistanceSq = routeNodeReachDistanceSq(from, next, following);
        boolean descending = next.getY() < from.getY();
        if (reachedPathNode(actualFeet, next, distanceSq, verticalDistance, reachDistanceSq)
                && routeLandingConfirmed(descending, mc.player.onGround, verticalDistance)) {
            pathIndex++;
            resetRouteStallRecovery();
            resetRouteProgress();
            if (pathIndex < path.size()) {
                followPathToOre(candidates);
            } else {
                stopRouteMotion();
                prepareAndMineCurrentOre();
            }
            return;
        }
        double nodeDistanceSq = routeNodeDistanceSq(distanceSq, verticalDistance);
        if (routeProgressed(lastRouteDistanceSq, nodeDistanceSq)) {
            lastRouteDistanceSq = nodeDistanceSq;
            stalledRouteTicks = 0;
        } else if (routeStallLimitReached(++stalledRouteTicks)) {
            if (stalledRouteReplanAvailable(currentOre, currentOreType,
                    miningPlayerFeet, stalledRouteOre, stalledRouteType, stalledRouteFeet,
                    stalledRouteReplans)) {
                replanStalledRoute();
            } else {
                coolDownUnusableRouteTarget();
            }
            return;
        }
        if (waitingForAscendingClearance(from.getY(), next.getY(), physicalFeetY)) {
            mc.player.motionX = routeMotionComponent(dx, distanceSq);
            mc.player.motionZ = routeMotionComponent(dz, distanceSq);
            if (mc.player.onGround) mc.player.jump();
            delay = 0;
            return;
        }
        if (distanceSq > 0.0001) {
            double motionX = routeMotionComponent(dx, distanceSq);
            double motionZ = routeMotionComponent(dz, distanceSq);
            if (!routeStepClear(motionX, motionZ)) {
                stopRouteMotion();
                if (clearRouteStepObstacle(motionX, motionZ)) {
                    resetRouteProgress();
                } else {
                    restartRouteFromCurrentPosition();
                }
                return;
            }
            mc.player.motionX = motionX;
            mc.player.motionZ = motionZ;
        }
        delay = 0;
    }

    static double routeMotionTowardNode(double unitDirection, double remainingDistance) {
        double speed = Math.min(ROUTE_SPEED, Math.max(0.0, remainingDistance));
        return MathHelper.clamp(unitDirection * speed, -ROUTE_SPEED, ROUTE_SPEED);
    }

    static double routeMotionComponent(double delta, double distanceSq) {
        if (distanceSq <= 0.0001) return 0.0;
        double length = Math.sqrt(distanceSq);
        return routeMotionTowardNode(delta / length, length);
    }

    static double planningMotion(double current) {
        double slowed = current * 0.2;
        return Math.abs(slowed) < 0.005 ? 0.0 : slowed;
    }

    static double routeResetMotion(double current, boolean stopMotion) {
        return stopMotion ? 0.0 : current;
    }

    static boolean routeProgressed(double previousDistanceSq, double currentDistanceSq) {
        return currentDistanceSq + ROUTE_PROGRESS_EPSILON < previousDistanceSq;
    }

    static boolean routeStallLimitReached(int stalledTicks) {
        return stalledTicks >= MAX_STALLED_ROUTE_TICKS;
    }

    static boolean stalledRouteReplanAvailable(BlockPos currentOre, OreType currentType,
            BlockPos currentFeet, BlockPos retryOre, OreType retryType, BlockPos retryFeet,
            int retries) {
        return currentOre != null && currentType != null
            && (!completionOwnsWork(retryOre, retryType, currentOre, currentType)
                || !java.util.Objects.equals(retryFeet, currentFeet)
                || retries < MAX_STALLED_ROUTE_REPLANS);
    }

    static int nextStalledRouteReplanCount(BlockPos currentOre, OreType currentType,
            BlockPos currentFeet, BlockPos retryOre, OreType retryType, BlockPos retryFeet,
            int retries) {
        return completionOwnsWork(retryOre, retryType, currentOre, currentType)
                && java.util.Objects.equals(retryFeet, currentFeet)
            ? Math.max(0, retries) + 1 : 1;
    }

    static double routeNodeDistanceSq(double horizontalDistanceSq, double verticalDistance) {
        return Math.max(0.0, horizontalDistanceSq) + verticalDistance * verticalDistance;
    }

    static BlockPos playerFeetCell(double x, double feetY, double z) {
        return new BlockPos(MathHelper.floor(x), MathHelper.floor(feetY + 0.01), MathHelper.floor(z));
    }

    static double navigationFeetY(double feetY, boolean onGround, boolean supportingSurface) {
        return onGround && supportingSurface ? Math.ceil(feetY - 0.01) : feetY;
    }

    static BlockPos navigationFeetCell(double x, double feetY, double z, boolean onGround,
            boolean supportingSurface) {
        return playerFeetCell(x, navigationFeetY(feetY, onGround, supportingSurface), z);
    }

    private BlockPos playerNavigationFeetCell() {
        double feetY = mc.player.getEntityBoundingBox().minY;
        return navigationFeetCell(mc.player.posX, feetY, mc.player.posZ, mc.player.onGround,
            standingSurfaceSupportsNavigation(feetY));
    }

    private boolean standingSurfaceSupportsNavigation(double feetY) {
        BlockPos surface = new BlockPos(mc.player.posX, feetY - 0.01, mc.player.posZ);
        return hasSolidSupport(surface.up());
    }

    static boolean reachedPathNode(BlockPos actualFeet, BlockPos expectedFeet,
            double horizontalDistanceSq, double verticalDistance) {
        return reachedPathNode(actualFeet, expectedFeet, horizontalDistanceSq,
            verticalDistance, ROUTE_NODE_REACH_DISTANCE_SQ);
    }

    static boolean reachedPathNode(BlockPos actualFeet, BlockPos expectedFeet,
            double horizontalDistanceSq, double verticalDistance, double reachDistanceSq) {
        return actualFeet != null && actualFeet.equals(expectedFeet)
            && horizontalDistanceSq < Math.max(0.0, reachDistanceSq)
            && verticalDistance <= 0.05 && verticalDistance > -0.35;
    }

    static boolean routeLandingConfirmed(boolean descending, boolean onGround,
            double verticalDistance) {
        return !descending || onGround || verticalDistance >= -0.05;
    }

    static boolean routeTransitionIsControlled(BlockPos from, BlockPos next) {
        if (from == null || next == null) return false;
        int horizontalStep = Math.abs(next.getX() - from.getX())
            + Math.abs(next.getZ() - from.getZ());
        int verticalStep = Math.abs(next.getY() - from.getY());
        return horizontalStep <= 1 && verticalStep <= MAX_ROUTE_VERTICAL_STEP;
    }

    static boolean routeRequiresSupportRemoval(BlockPos from, BlockPos next) {
        return from != null && next != null && next.getY() < from.getY()
            && next.getX() == from.getX() && next.getZ() == from.getZ();
    }

    static BlockPos routeTransitionClearance(BlockPos from, BlockPos next) {
        if (from == null || next == null) return null;
        if (next.getY() > from.getY()) return from.up(2);
        return routeRequiresSupportRemoval(from, next) ? from.down() : null;
    }

    static double routeNodeReachDistanceSq(BlockPos from, BlockPos node, BlockPos following) {
        if (from == null || node == null || following == null) {
            return PRECISE_ROUTE_NODE_REACH_DISTANCE_SQ;
        }
        int firstX = Integer.signum(node.getX() - from.getX());
        int firstY = Integer.signum(node.getY() - from.getY());
        int firstZ = Integer.signum(node.getZ() - from.getZ());
        int nextX = Integer.signum(following.getX() - node.getX());
        int nextY = Integer.signum(following.getY() - node.getY());
        int nextZ = Integer.signum(following.getZ() - node.getZ());
        boolean vertical = firstY != 0 || nextY != 0;
        boolean changesDirection = firstX != nextX || firstY != nextY || firstZ != nextZ;
        return vertical || changesDirection
            ? PRECISE_ROUTE_NODE_REACH_DISTANCE_SQ : ROUTE_NODE_REACH_DISTANCE_SQ;
    }

    static boolean routeTransitionContains(BlockPos actualFeet, BlockPos from, BlockPos next) {
        if (actualFeet == null || from == null || next == null) return false;
        return actualFeet.getX() >= Math.min(from.getX(), next.getX())
            && actualFeet.getX() <= Math.max(from.getX(), next.getX())
            && actualFeet.getY() >= Math.min(from.getY(), next.getY())
            && actualFeet.getY() <= Math.max(from.getY(), next.getY())
            && actualFeet.getZ() >= Math.min(from.getZ(), next.getZ())
            && actualFeet.getZ() <= Math.max(from.getZ(), next.getZ());
    }

    static boolean waitingForAscendingClearance(int fromY, int nextY, double feetY) {
        return nextY > fromY && feetY + 0.01 < nextY;
    }

    private void stopRouteMotion() {
        resetRouteMotion(true);
    }

    private void resetRouteMotion(boolean stopMotion) {
        mc.player.motionX = routeResetMotion(mc.player.motionX, stopMotion);
        mc.player.motionZ = routeResetMotion(mc.player.motionZ, stopMotion);
    }

    private boolean routeStepClear(double motionX, double motionZ) {
        AxisAlignedBB box = routeStepBounds(mc.player.getEntityBoundingBox(), motionX, motionZ);
        AxisAlignedBB playerSpace = new AxisAlignedBB(box.minX + 0.001, box.minY + 0.01,
            box.minZ + 0.001, box.maxX - 0.001, box.maxY - 0.001, box.maxZ - 0.001);
        return mc.world.getCollisionBoxes(mc.player, playerSpace).isEmpty();
    }

    private boolean clearRouteStepObstacle(double motionX, double motionZ) {
        AxisAlignedBB routeStep = routeStepBounds(mc.player.getEntityBoundingBox(), motionX, motionZ);
        for (BlockPos cell : routeOccupiedCells(routeStep)) {
            if (isPassable(cell)) continue;
            if (clearCorridorCell(cell, cell)) {
                delay = ModConfig.mineDelayTicks;
                return true;
            }
            rejectRouteObstacle(cell);
            return false;
        }
        return false;
    }

    static AxisAlignedBB routeStepBounds(AxisAlignedBB current, double motionX, double motionZ) {
        AxisAlignedBB destination = current.offset(motionX, 0.0, motionZ);
        return new AxisAlignedBB(Math.min(current.minX, destination.minX), current.minY,
            Math.min(current.minZ, destination.minZ), Math.max(current.maxX, destination.maxX),
            current.maxY, Math.max(current.maxZ, destination.maxZ));
    }

    static List<BlockPos> routeOccupiedCells(AxisAlignedBB box) {
        if (box == null) return java.util.Collections.emptyList();
        int minX = MathHelper.floor(box.minX + 0.001);
        int maxX = MathHelper.floor(box.maxX - 0.001);
        int minY = MathHelper.floor(box.minY + 0.01);
        int maxY = MathHelper.floor(box.maxY - 0.001);
        int minZ = MathHelper.floor(box.minZ + 0.001);
        int maxZ = MathHelper.floor(box.maxZ - 0.001);
        List<BlockPos> cells = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) cells.add(new BlockPos(x, y, z));
            }
        }
        return cells;
    }

    private PathTarget findNearestPathTarget(List<OreVisualizer.CachedOre> candidates) {
        if (!pathCandidateBatch.isEmpty() && !reusePathCandidateSnapshot(
                pathCandidateBatch, pathCandidateFeet, miningPlayerFeet)) {
            resetPathCandidateBatch();
        }
        if (pathCandidateBatch.isEmpty()) {
            pathCandidateBatch = snapshotPathCandidates(candidates, MAX_CACHED_TARGETS);
            pathCandidateFeet = miningPlayerFeet == null ? null : miningPlayerFeet.toImmutable();
            pathCandidateOffset = 0;
        }
        if (pendingPathTarget != null) {
            if (routeComparisonExpired(pendingPathComparisonTicks)) {
                return finishPathCandidateBatch();
            }
            pendingPathComparisonTicks++;
        }
        while (pathCandidateOffset < pathCandidateBatch.size()) {
            OreVisualizer.CachedOre candidate = pathCandidateBatch.get(pathCandidateOffset);
            if ((!targetLabels.isEmpty() && !targetLabels.containsKey(candidate.pos()))
                    || !candidateTypeAvailable(
                        ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))
                    || targetTemporarilyUnavailable(candidate.pos())) {
                pathCandidateOffset++;
                continue;
            } else {
                OreType currentType = OreType.fromBlock(
                    mc.world.getBlockState(candidate.pos()).getBlock());
                if (currentType != candidate.type()) {
                    oreVisualizer.reconcileMarker(candidate.pos(), currentType);
                    pathCandidateOffset++;
                    continue;
                } else {
                    PathSearchResult search = incrementalPathToOre(candidate.pos());
                    if (!search.complete) return null;
                    PathRoute route = search.route;
                    pathCandidateOffset++;
                    if (route != null) {
                        boolean sameVein = sameVein(
                            lastMinedOre, candidate.pos(), lastMinedType, candidate.type());
                        int score = pathTargetScore(route.cost, candidate.distanceSq(), sameVein);
                        int label = targetLabels.getOrDefault(candidate.pos(), Integer.MAX_VALUE);
                        if (betterLabeledPathTarget(label, score, sameVein, pendingPathTargetLabel,
                                pendingPathTargetScore, pendingPathTargetSameVein)) {
                            if (pendingPathTarget == null) pendingPathComparisonTicks = 0;
                            pendingPathTargetLabel = label;
                            pendingPathTargetScore = score;
                            pendingPathTargetSameVein = sameVein;
                            pendingPathTarget = new PathTarget(
                                candidate.pos(), candidate.type(), route.points);
                        }
                    } else {
                        pendingFailedPathTargets.add(candidate.pos().toImmutable());
                        if (pathSnapshotRefreshNeeded(pendingFailedPathTargets.size(),
                                pendingPathTarget != null)) {
                            blockTargets(pendingFailedPathTargets,
                                mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
                            if (pathSearchCanReleaseVeinLock(false, false, pathCandidateOffset,
                                    pathCandidateBatch.size())) clearTargetLabels();
                            resetPathCandidateBatch();
                            pathSnapshotRefreshRequested = true;
                            return null;
                        }
                    }
                }
            }
            if (pathCandidateOffset % PATH_CANDIDATE_BATCH_SIZE == 0
                    && pendingPathTarget != null) {
                return finishPathCandidateBatch();
            }
            return null;
        }
        return finishPathCandidateBatch();
    }

    private PathTarget finishPathCandidateBatch() {
        PathTarget best = pendingPathTarget;
        boolean bestAvailable = best != null && pathTargetAvailable(best.type, targetType(best.ore),
            targetTemporarilyUnavailable(best.ore));
        boolean refreshSnapshot = pathTargetRefreshNeeded(best != null, bestAvailable);
        if (!bestAvailable) best = null;
        if (best != null) ensureTargetLabels(best.ore, best.type, pathCandidateBatch);
        Set<BlockPos> failedTargets = failedPathTargetsToBlock(pendingFailedPathTargets);
        if (!failedTargets.isEmpty()) {
            blockTargets(failedTargets, mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
        }
        if (pathSearchCanReleaseVeinLock(best != null, refreshSnapshot, pathCandidateOffset,
                pathCandidateBatch.size())) clearTargetLabels();
        resetPathCandidateBatch();
        if (refreshSnapshot) pathSnapshotRefreshRequested = true;
        return best;
    }

    static boolean routeComparisonExpired(int elapsedTicks) {
        return elapsedTicks >= MAX_ROUTE_COMPARISON_TICKS;
    }

    static boolean pathTargetAvailable(OreType expected, OreType actual,
            boolean temporarilyUnavailable) {
        return expected != null && expected == actual && !temporarilyUnavailable;
    }

    static boolean pathTargetRefreshNeeded(boolean targetFound, boolean targetAvailable) {
        return targetFound && !targetAvailable;
    }

    static List<OreVisualizer.CachedOre> snapshotPathCandidates(
            List<OreVisualizer.CachedOre> candidates, int limit) {
        int size = Math.min(candidates.size(), Math.max(0, limit));
        return new ArrayList<>(candidates.subList(0, size));
    }

    static int pathSearchSliceBudget(int visited) {
        return Math.max(0, Math.min(PATH_NODES_PER_TICK, MAX_PATH_NODES - Math.max(0, visited)));
    }

    static int pathValidationSliceBudget(int requested) {
        return Math.max(0, Math.min(PATH_STATE_CHECKS_PER_TICK, requested));
    }

    static int pathSearchRetryDelay(int nextCandidateOffset, boolean searchPending) {
        if (searchPending) return 0;
        return nextCandidateOffset > 0 ? 1 : PATH_RETRY_TICKS;
    }

    static boolean continuePathRetryDelay(int delay, long scheduledRevision, long currentRevision) {
        return delay > 0 && scheduledRevision == currentRevision;
    }

    static boolean pathRetryInterruptedByMarkerChange(
            int delay, long scheduledRevision, long currentRevision) {
        return delay > 0 && scheduledRevision != currentRevision;
    }

    static boolean pathRetryInterruptedByFeetChange(
            int delay, BlockPos cachedFeet, BlockPos currentFeet) {
        return delay > 0 && !java.util.Objects.equals(cachedFeet, currentFeet);
    }

    static boolean pathRetryInterruptedBySelectionChange(
            int delay, long scheduledRevision, long currentRevision) {
        return delay > 0 && scheduledRevision != currentRevision;
    }

    static boolean selectionRevisionChanged(long observedRevision, long currentRevision) {
        return observedRevision != currentRevision;
    }

    static boolean pathSnapshotRefreshNeeded(int failedCandidates, boolean routeFound) {
        return !routeFound && failedCandidates >= MAX_FAILED_CANDIDATES_PER_SNAPSHOT;
    }

    static boolean pathSearchCanReleaseVeinLock(boolean routeAvailable, boolean refreshSnapshot,
            int nextCandidateOffset, int candidateCount) {
        return !routeAvailable && !refreshSnapshot
            && nextCandidateOffset >= Math.max(0, candidateCount);
    }

    static boolean reusePathCandidateSnapshot(List<OreVisualizer.CachedOre> snapshot) {
        return snapshot != null && !snapshot.isEmpty();
    }

    static boolean reusePathCandidateSnapshot(List<OreVisualizer.CachedOre> snapshot,
            BlockPos snapshotFeet, BlockPos currentFeet) {
        return reusePathCandidateSnapshot(snapshot)
            && java.util.Objects.equals(snapshotFeet, currentFeet);
    }

    static boolean temporarilyBlocked(BlockPos candidate, Map<BlockPos, Integer> blockedUntil,
            int currentTick) {
        Integer until = blockedUntil == null ? null : blockedUntil.get(candidate);
        return until != null && currentTick < until;
    }

    static Set<BlockPos> failedPathTargetsToBlock(Set<BlockPos> failedTargets) {
        if (failedTargets == null || failedTargets.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return new HashSet<>(failedTargets);
    }

    static boolean containsLabeledCandidate(List<OreVisualizer.CachedOre> candidates,
            Map<BlockPos, Integer> labels) {
        if (candidates == null || labels == null || labels.isEmpty()) return false;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (labels.containsKey(candidate.pos())) return true;
        }
        return false;
    }

    static boolean routeEndedBeforeMining(BlockPos currentOre, int pathIndex, int pathSize) {
        return currentOre != null && pathIndex >= pathSize;
    }

    static int pathTargetScore(int routeCost, double distanceSq, boolean sameVein) {
        int blockDistance = (int) Math.ceil(Math.sqrt(Math.max(0.0, distanceSq)));
        return routeCost + blockDistance * 8 - (sameVein ? 2 : 0);
    }

    static boolean betterPathTarget(int score, boolean sameVein, int bestScore, boolean bestSameVein) {
        if (sameVein != bestSameVein) return sameVein;
        return score < bestScore;
    }

    static boolean betterLabeledPathTarget(int label, int score, boolean sameVein,
            int bestLabel, int bestScore, boolean bestSameVein) {
        if (label != bestLabel) return label < bestLabel;
        return betterPathTarget(score, sameVein, bestScore, bestSameVein);
    }

    private PathSearchResult incrementalPathToOre(BlockPos ore) {
        BlockPos start = standPos(playerNavigationFeetCell());
        double maxDistanceSq = ModConfig.minePathRange * ModConfig.minePathRange;
        if (pendingPathSearch == null || !pendingPathSearch.matches(ore, start, maxDistanceSq)) {
            List<BlockPos> goals = standPositionsAround(ore);
            if (goals.isEmpty()) {
                pendingPathSearch = null;
                return PathSearchResult.complete(null);
            }
            pendingPathSearch = new PathSearch(ore, start, goals, maxDistanceSq);
        }
        PathSearchResult result = advancePathSearch(pendingPathSearch,
            pathSearchSliceBudget(pendingPathSearch.visited));
        if (result.restart) {
            PathSearch stale = pendingPathSearch;
            List<BlockPos> goals = standPositionsAround(ore);
            if (stale.restarts < MAX_PATH_SEARCH_RESTARTS && !goals.isEmpty()) {
                pendingPathSearch = new PathSearch(ore, start, goals, maxDistanceSq,
                    stale.restarts + 1);
                return PathSearchResult.pending();
            }
            pendingPathSearch = null;
            return PathSearchResult.complete(null);
        }
        if (result.complete) pendingPathSearch = null;
        return result;
    }

    private PathSearchResult advancePathSearch(PathSearch search, int nodeBudget) {
        if (search.validatingSuccess) {
            return validateCompletedPathSearch(search, PATH_STATE_CHECKS_PER_TICK);
        }
        if (search.validatingFailure) {
            return validateFailedPathSearch(search, PATH_STATE_CHECKS_PER_TICK);
        }
        if (search.goalSet.contains(search.start)) {
            PathRoute route = new PathRoute(java.util.Collections.emptyList(), 0);
            search.beginSuccessValidation(route);
            return PathSearchResult.pending();
        }
        int expanded = 0;
        while (!search.queue.isEmpty() && search.visited < MAX_PATH_NODES
                && expanded < Math.max(0, nodeBudget)) {
            PathNode node = search.queue.remove();
            expanded++;
            BlockPos pos = node.pos;
            Integer knownCost = search.costs.get(pos);
            if (knownCost == null || node.cost != knownCost) continue;
            search.visited++;
            if (search.goalSet.contains(pos)) {
                PathRoute route = new PathRoute(reconstruct(search.previous, pos), node.cost);
                search.beginSuccessValidation(route);
                return PathSearchResult.pending();
            }
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                for (int dy : PATH_VERTICAL_OFFSETS) {
                    BlockPos next = standPos(pos.offset(facing).add(0, dy, 0));
                    addPathNeighbor(search.queue, search.previous, search.costs,
                        search.traversalCosts, search.jumpClearanceCosts, search.start,
                        search.goals, search.maxDistanceSq, pos, node.cost, next, Math.abs(dy) * 2);
                }
            }
            addPathNeighbor(search.queue, search.previous, search.costs, search.traversalCosts,
                search.jumpClearanceCosts, search.start, search.goals, search.maxDistanceSq,
                pos, node.cost, pos.down(), 2);
        }
        if (!search.queue.isEmpty() && search.visited < MAX_PATH_NODES) {
            return PathSearchResult.pending();
        }
        if (search.restarts >= MAX_PATH_SEARCH_RESTARTS) return PathSearchResult.complete(null);
        search.beginFailureValidation(!search.queue.isEmpty());
        return PathSearchResult.pending();
    }

    private PathSearchResult validateCompletedPathSearch(PathSearch search, int budget) {
        if (!search.successGoalsValidated) {
            search.successGoalsValidated = true;
            if (pathGoalsChanged(search.goalSet, standPositionsAround(search.ore))) {
                return PathSearchResult.restart();
            }
        }
        int checked = 0;
        int limit = pathValidationSliceBudget(budget);
        while (checked < limit && search.successValidation.hasNext()) {
            BlockPos pos = search.successValidation.currentPos();
            int currentCost = search.successValidation.currentUsesTraversalCost()
                ? traversalCost(pos) : jumpClearanceCostAt(pos);
            Map<BlockPos, Integer> cache = search.successValidation.currentUsesTraversalCost()
                ? search.traversalCosts : search.jumpClearanceCosts;
            checked++;
            if (cachedPathStateChanged(cache, pos, currentCost)) {
                return PathSearchResult.restart();
            }
            search.successValidation.advance();
        }
        if (search.successValidation.hasNext()) return PathSearchResult.pending();
        if (pathGoalsChanged(search.goalSet, standPositionsAround(search.ore))) {
            return PathSearchResult.restart();
        }
        if (pathValidationRequiresAnotherPass(search.successValidationPass)) {
            search.successValidationPass++;
            search.successGoalsValidated = false;
            search.successValidation.reset();
            return PathSearchResult.pending();
        }
        return PathSearchResult.complete(search.successRoute);
    }

    private PathSearchResult validateFailedPathSearch(PathSearch search, int budget) {
        if (!search.failureGoalsValidated) {
            search.failureGoalsValidated = true;
            if (pathGoalsChanged(search.goalSet, standPositionsAround(search.ore))) {
                return PathSearchResult.restart();
            }
        }
        int checked = 0;
        int limit = pathValidationSliceBudget(budget);
        while (checked < limit && search.failureTraversalValidation.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = search.failureTraversalValidation.next();
            checked++;
            if (entry.getValue() != traversalCost(entry.getKey())) return PathSearchResult.restart();
        }
        while (checked < limit && search.failureJumpValidation.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = search.failureJumpValidation.next();
            checked++;
            if (entry.getValue() != jumpClearanceCostAt(entry.getKey())) {
                return PathSearchResult.restart();
            }
        }
        if (search.failureTraversalValidation.hasNext() || search.failureJumpValidation.hasNext()) {
            return PathSearchResult.pending();
        }
        if (pathValidationRequiresAnotherPass(search.failureValidationPass)) {
            search.beginNextFailureValidationPass();
            return PathSearchResult.pending();
        }
        return search.failureValidationTruncated
            ? PathSearchResult.restart() : PathSearchResult.complete(null);
    }

    static boolean pathGoalsChanged(Set<BlockPos> planned, List<BlockPos> current) {
        return planned == null || current == null || planned.size() != current.size()
            || !planned.containsAll(current);
    }

    static boolean cachedPathStateChanged(Map<BlockPos, Integer> cache, BlockPos pos, int current) {
        Integer cached = cache == null ? null : cache.get(pos);
        return cached != null && cached != current;
    }

    static boolean pathCacheEntryNeedsValidation(int cachedCost, boolean exhaustive) {
        return exhaustive || cachedCost < 0;
    }

    static boolean pathValidationRequiresAnotherPass(int completedPasses) {
        return completedPasses < REQUIRED_STABLE_PATH_VALIDATION_PASSES;
    }

    static <K> List<Map.Entry<K, Integer>> pathStateEntriesForValidation(
            Map<K, Integer> cache, boolean exhaustive, int limit) {
        List<Map.Entry<K, Integer>> result = new ArrayList<>();
        if (cache == null || limit <= 0) return result;
        for (Map.Entry<K, Integer> entry : cache.entrySet()) {
            if (pathCacheEntryNeedsValidation(entry.getValue(), exhaustive)) result.add(entry);
            if (result.size() >= limit) break;
        }
        return result;
    }

    static int pathStateValidationCount(Map<?, Integer> cache, boolean exhaustive, int limit) {
        if (cache == null || cache.isEmpty() || limit <= 0) return 0;
        int count = 0;
        for (Integer cost : cache.values()) {
            if (cost != null && pathCacheEntryNeedsValidation(cost, exhaustive)) count++;
            if (count >= limit) break;
        }
        return count;
    }

    private void addPathNeighbor(PriorityQueue<PathNode> queue, Map<BlockPos, BlockPos> previous,
            Map<BlockPos, Integer> costs, Map<BlockPos, Integer> traversalCosts,
            Map<BlockPos, Integer> jumpClearanceCosts, BlockPos start, List<BlockPos> goals,
            double maxDistanceSq, BlockPos from, int currentCost, BlockPos next, int verticalPenalty) {
        if (!routeTransitionIsControlled(from, next)) return;
        if (start.distanceSq(next) > maxDistanceSq) return;
        Integer known = costs.get(next);
        if (knownPathCostCannotImprove(known, currentCost, verticalPenalty)) return;
        int stepCost = cachedPathCost(traversalCosts, next, this::traversalCost);
        if (stepCost < 0) return;
        BlockPos clearance = routeTransitionClearance(from, next);
        int jumpExcavation = !transitionNeedsSeparateClearance(clearance, next) ? 0
            : cachedPathCost(jumpClearanceCosts, clearance, this::jumpClearanceCostAt);
        if (jumpExcavation < 0) return;
        int nextCost = currentCost + stepCost + jumpExcavation * 5 + verticalPenalty;
        if (known != null && known <= nextCost) return;
        previous.put(next, from);
        costs.put(next, nextCost);
        queue.add(new PathNode(next, nextCost, pathPriority(nextCost, next, goals)));
    }

    static boolean knownPathCostCannotImprove(Integer knownCost, int currentCost, int verticalPenalty) {
        return knownCost != null && knownCost <= currentCost + 1 + verticalPenalty;
    }

    static int cachedPathCost(Map<BlockPos, Integer> cache, BlockPos pos,
            ToIntFunction<BlockPos> resolver) {
        Integer cached = cache.get(pos);
        if (cached != null) return cached;
        int resolved = resolver.applyAsInt(pos);
        cache.put(pos, resolved);
        return resolved;
    }

    static int pathPriority(int cost, BlockPos pos, List<BlockPos> goals) {
        int nearest = Integer.MAX_VALUE;
        for (BlockPos goal : goals) {
            int distance = Math.abs(pos.getX() - goal.getX())
                + Math.abs(pos.getY() - goal.getY())
                + Math.abs(pos.getZ() - goal.getZ());
            nearest = Math.min(nearest, distance);
        }
        return nearest == Integer.MAX_VALUE ? cost : cost + nearest;
    }

    private static int comparePathNodes(PathNode left, PathNode right) {
        return comparePathOrder(left.priority, left.cost, right.priority, right.cost);
    }

    static int comparePathOrder(int leftPriority, int leftCost, int rightPriority, int rightCost) {
        int priority = Integer.compare(leftPriority, rightPriority);
        return priority != 0 ? priority : Integer.compare(rightCost, leftCost);
    }

    private List<BlockPos> standPositionsAround(BlockPos ore) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos candidate : miningStandCandidates(ore)) {
            if (!safeRemoteMiningStand(candidate, ore)) continue;
            BlockPos stand = standPos(candidate);
            if (miningStandTemporarilyUnavailable(ore, stand)) continue;
            BlockPos faceNeighbor = miningFaceNeighbor(stand, ore);
            if (canTraverse(stand) && faceNeighbor != null
                    && (isPassable(faceNeighbor) || canClearForCorridor(faceNeighbor))) {
                result.add(stand);
            }
        }
        result.sort((left, right) -> compareMiningStandPriority(left, right, ore,
            mc.player.getDistanceSqToCenter(left), mc.player.getDistanceSqToCenter(right)));
        return result;
    }

    static boolean safeRemoteMiningStand(BlockPos stand, BlockPos ore) {
        return stand != null && ore != null && !stand.equals(ore.up());
    }

    static List<BlockPos> miningStandCandidates(BlockPos ore) {
        List<BlockPos> result = new ArrayList<>();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos side = ore.offset(facing);
            for (int dy = -2; dy <= 1; dy++) result.add(side.add(0, dy, 0));
        }
        result.add(ore.up());
        result.add(ore.down(2));
        return result;
    }

    static int compareMiningStandPriority(BlockPos left, BlockPos right, BlockPos ore,
            double leftDistanceSq, double rightDistanceSq) {
        boolean leftAbove = left.equals(ore.up());
        boolean rightAbove = right.equals(ore.up());
        if (leftAbove != rightAbove) return leftAbove ? 1 : -1;
        return Double.compare(leftDistanceSq, rightDistanceSq);
    }

    static BlockPos miningFaceNeighbor(BlockPos playerFeet, BlockPos ore) {
        if (!stableMiningPosition(playerFeet, ore)) return null;
        int dy = ore.getY() - playerFeet.getY();
        if (dy < 0) return ore.up();
        if (dy > 1) return ore.down();
        int dx = Integer.signum(playerFeet.getX() - ore.getX());
        int dz = Integer.signum(playerFeet.getZ() - ore.getZ());
        return ore.add(dx, 0, dz);
    }

    static boolean miningWorkAreaReady(boolean feetClear, boolean headClear, boolean supported) {
        return feetClear && headClear && supported;
    }

    static boolean endpointRequiresAlternateStand(boolean stablePosition, boolean workAreaReady) {
        return !stablePosition || !workAreaReady;
    }

    static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> previous, BlockPos goal) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos cursor = goal; cursor != null; cursor = previous.get(cursor)) result.add(cursor);
        java.util.Collections.reverse(result);
        if (!result.isEmpty()) result.remove(0);
        return result;
    }

    private BlockPos standPos(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isStandable(BlockPos feet) {
        if (!routeStandSafe(isHazardousRouteCell(feet), isHazardousRouteCell(feet.up()))
                || !hasSolidSupport(feet)) return false;
        AxisAlignedBB box = new AxisAlignedBB(feet.getX() + 0.1, feet.getY(), feet.getZ() + 0.1,
            feet.getX() + 0.9, feet.getY() + 1.8, feet.getZ() + 0.9);
        return mc.world.getCollisionBoxes(mc.player, box).isEmpty();
    }

    static boolean routeStandSafe(boolean hazardousFeet, boolean hazardousHead) {
        return !hazardousFeet && !hazardousHead;
    }

    private boolean canTraverse(BlockPos feet) {
        return traversalCost(feet) >= 0;
    }

    private int traversalCost(BlockPos feet) {
        if (!hasSolidSupport(feet)) return -1;
        boolean feetClear = isPassable(feet);
        boolean headClear = isPassable(feet.up());
        boolean feetBreakable = !feetClear && canClearForCorridor(feet);
        boolean headBreakable = !headClear && canClearForCorridor(feet.up());
        int excavation = corridorExcavationCost(feetClear, headClear,
            feetBreakable, headBreakable);
        return excavation < 0 ? -1 : 1 + excavation * 5;
    }

    static int corridorExcavationCost(boolean feetClear, boolean headClear,
            boolean feetBreakable, boolean headBreakable) {
        if (!feetClear && !feetBreakable) return -1;
        if (!headClear && !headBreakable) return -1;
        return (feetClear ? 0 : 1) + (headClear ? 0 : 1);
    }

    static int jumpClearanceCost(boolean clear, boolean breakable) {
        if (clear) return 0;
        return breakable ? 1 : -1;
    }

    static boolean transitionNeedsSeparateClearance(BlockPos clearance, BlockPos destination) {
        return clearance != null && !clearance.equals(destination);
    }

    private int jumpClearanceCostAt(BlockPos pos) {
        boolean clear = isPassable(pos);
        return clear ? 0 : jumpClearanceCost(false, canClearForCorridor(pos));
    }

    private boolean hasSolidSupport(BlockPos feet) {
        BlockPos supportPos = feet.down();
        IBlockState support = mc.world.getBlockState(supportPos);
        boolean materialSupport = !support.getMaterial().isReplaceable()
            && support.getMaterial().blocksMovement();
        boolean hazardousSupport = support.getBlock() instanceof BlockMagma
            || support.getBlock() instanceof BlockCactus;
        if (!routeSupportSafe(materialSupport, hazardousSupport)) {
            return false;
        }
        if (support.isFullCube()) return true;
        double feetY = feet.getY();
        AxisAlignedBB column = new AxisAlignedBB(feet.getX() + 0.1, feetY - 0.999,
            feet.getZ() + 0.1, feet.getX() + 0.9, feetY + 1.8, feet.getZ() + 0.9);
        supportCollisionBoxes.clear();
        support.getBlock().addCollisionBoxToList(
            support, mc.world, supportPos, column, supportCollisionBoxes, mc.player, false);
        boolean collisionBelow = false;
        boolean intrudesAbove = false;
        for (AxisAlignedBB box : supportCollisionBoxes) {
            if (box.maxY > feetY - 0.999 && box.minY < feetY - 0.001) collisionBelow = true;
            if (box.maxY > feetY + 0.001) intrudesAbove = true;
        }
        return routeSupportShapeUsable(materialSupport, false, collisionBelow, intrudesAbove);
    }

    static boolean routeSupportSafe(boolean materialSupport, boolean hazardousSupport) {
        return materialSupport && !hazardousSupport;
    }

    static boolean routeSupportShapeUsable(boolean materialSupport, boolean fullCube,
            boolean collisionBelow, boolean intrudesAbove) {
        return materialSupport && (fullCube || collisionBelow && !intrudesAbove);
    }

    private boolean isPassable(BlockPos pos) {
        Material material = mc.world.getBlockState(pos).getMaterial();
        return routeCellPassable(material.isReplaceable(), hazardousRouteMaterial(material));
    }

    private boolean isHazardousRouteCell(BlockPos pos) {
        return hazardousRouteMaterial(mc.world.getBlockState(pos).getMaterial());
    }

    static boolean hazardousRouteMaterial(Material material) {
        return material == Material.LAVA || material == Material.FIRE;
    }

    static boolean routeCellPassable(boolean replaceable, boolean hazardous) {
        return replaceable && !hazardous;
    }

    private boolean canClearForCorridor(BlockPos pos) {
        if (temporarilyBlocked(pos, rejectedObstaclesUntil, mc.player.ticksExisted)) return false;
        OreType ore = targetType(pos);
        return ore != null || isBreakableBlock(pos);
    }

    private boolean isBreakableBlock(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        if (state.getMaterial().isReplaceable()) return false;
        if (OreType.fromBlock(state.getBlock()) != null) return false;
        if (state.getBlock().getBlockHardness(state, mc.world, pos) < 0.0F) return false;
        if (state.getBlock().canHarvestBlock(mc.world, pos, mc.player)) return true;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(slot);
            if (toolHarvests(stack, pos)) return true;
        }
        return false;
    }

    private boolean clearBlockingObstacle(BlockPos next) {
        if (!isPassable(next.up())) {
            if (clearCorridorCell(next.up(), next)) return true;
            rejectRouteObstacle(next.up());
            return false;
        }
        if (!isPassable(next)) {
            if (clearCorridorCell(next, next)) return true;
            rejectRouteObstacle(next);
        }
        return false;
    }

    private boolean clearCorridorCell(BlockPos desired, BlockPos permittedLower) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        RayTraceResult hit = rayTraceCorridorObstacle(
            eyes, desired, permittedLower, currentCorridorCells());
        if (hit == null) return false;
        BlockPos obstacle = hit.getBlockPos();
        if (temporarilyBlocked(obstacle, rejectedObstaclesUntil, mc.player.ticksExisted)) return false;
        OreType ore = targetType(obstacle);
        if (ore != null) {
            if (!stableMiningPosition(miningPlayerFeet, obstacle)
                    || !withinMiningReach(eyes, obstacle, miningReach())) return false;
            mine(new MineTarget(obstacle.toImmutable(), ore, hit.sideHit), true);
            return true;
        }
        return beginClearingObstacle(obstacle, hit);
    }

    private boolean clearMiningExposureObstacle(BlockPos ore) {
        RayTraceResult hit = rayTraceMiningExposureObstacle(miningEyes, miningPlayerFeet, ore);
        if (hit == null) return false;
        BlockPos obstacle = hit.getBlockPos();
        OreType obstacleType = targetType(obstacle);
        boolean blocked = temporarilyBlocked(
            obstacle, rejectedObstaclesUntil, mc.player.ticksExisted);
        boolean reachable = withinMiningReach(miningEyes, obstacle, miningReach());
        if (obstacleType != null) {
            if (!exposureObstacleUsable(blocked, true,
                    stableMiningPosition(miningPlayerFeet, obstacle), reachable, false)) {
                rejectRouteObstacle(obstacle);
                return false;
            }
            if (!targetLabels.containsKey(obstacle) && connectedToLabeledVein(
                    obstacle, obstacleType, targetLabels, targetLabelType)) {
                targetLabels.put(obstacle.toImmutable(), targetLabels.size() + 1);
                reorderRemainingTargetLabels();
            }
            mine(new MineTarget(obstacle.toImmutable(), obstacleType, hit.sideHit), true);
            return true;
        }
        if (!exposureObstacleUsable(
                blocked, false, false, reachable, isBreakableBlock(obstacle))) {
            rejectRouteObstacle(obstacle);
            return false;
        }
        if (beginClearingObstacle(obstacle, hit)) return true;
        rejectRouteObstacle(obstacle);
        return false;
    }

    static boolean exposureObstacleUsable(boolean temporarilyBlocked, boolean ore,
            boolean stableMiningPosition, boolean withinReach, boolean breakable) {
        return !temporarilyBlocked && withinReach
            && (ore ? stableMiningPosition : breakable);
    }

    private boolean beginClearingObstacle(BlockPos obstacle, RayTraceResult hit) {
        if (!obstacle.equals(clearingPos)) {
            if (clearingControllerResetRequired(clearingPos, obstacle)
                    && mc.playerController != null) {
                mc.playerController.resetBlockRemoving();
            }
            selectBestPickaxe(obstacle);
            clearingPos = obstacle.toImmutable();
            clearingAttempts = 0;
            clearingAttemptBudget = destructionAttemptBudget(
                mc.world.getBlockState(obstacle).getPlayerRelativeBlockHardness(
                    mc.player, mc.world, obstacle));
            clearingDeadlineTick = destructionDeadlineTick(mc.player.ticksExisted,
                clearingAttemptBudget, ModConfig.mineDelayTicks);
            clearingMissingTicks = 0;
        }
        if (!damageCorridorBlock(obstacle, hit)) {
            clearClearingTarget();
            return false;
        }
        return true;
    }

    private RayTraceResult rayTraceMiningExposureObstacle(Vec3d eyes, BlockPos playerFeet,
            BlockPos ore) {
        if (eyes == null || playerFeet == null || ore == null) return null;
        for (int sampleIndex = 0; sampleIndex < BLOCK_VISIBILITY_SAMPLE_COUNT; sampleIndex++) {
            RayTraceResult hit = mc.world.rayTraceBlocks(
                eyes, blockVisibilitySample(ore, sampleIndex), false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && miningExposureObstacleAllowed(
                        hit.getBlockPos(), playerFeet, ore)) return hit;
        }
        return null;
    }

    static boolean miningExposureObstacleAllowed(BlockPos obstacle, BlockPos playerFeet,
            BlockPos ore) {
        if (obstacle == null || playerFeet == null || ore == null || obstacle.equals(ore)
                || obstacle.equals(playerFeet) || obstacle.equals(playerFeet.up())
                || obstacle.equals(playerFeet.down())) return false;
        return Math.abs(obstacle.getX() - ore.getX()) <= 1
            && Math.abs(obstacle.getY() - ore.getY()) <= 1
            && Math.abs(obstacle.getZ() - ore.getZ()) <= 1;
    }

    private List<BlockPos> currentCorridorCells() {
        if (path.isEmpty()) return java.util.Collections.emptyList();
        int start = Math.max(0, pathIndex - 1);
        BlockPos routeStart = start > 0 ? path.get(start - 1) : miningPlayerFeet;
        if (reuseRouteCorridorCache(routeCorridorCachePath == path, routeCorridorCacheIndex,
                pathIndex, routeCorridorCacheStart, routeStart)) return routeCorridorCache;
        routeCorridorCache = corridorCells(path, start, ROUTE_RENDER_LIMIT, routeStart);
        routeCorridorCachePath = path;
        routeCorridorCacheIndex = pathIndex;
        routeCorridorCacheStart = routeStart == null ? null : routeStart.toImmutable();
        return routeCorridorCache;
    }

    static boolean reuseRouteCorridorCache(boolean samePath, int cachedIndex, int currentIndex,
            BlockPos cachedStart, BlockPos currentStart) {
        return samePath && cachedIndex == currentIndex
            && java.util.Objects.equals(cachedStart, currentStart);
    }

    static boolean corridorObstacleAllowed(BlockPos obstacle, BlockPos desired,
            BlockPos permittedLower, List<BlockPos> corridor) {
        return obstacle != null && (obstacle.equals(desired) || obstacle.equals(permittedLower)
            || corridor != null && corridor.contains(obstacle));
    }

    private RayTraceResult rayTraceCorridorObstacle(Vec3d eyes, BlockPos desired,
            BlockPos permittedLower, List<BlockPos> corridor) {
        for (int sampleIndex = 0; sampleIndex < BLOCK_VISIBILITY_SAMPLE_COUNT; sampleIndex++) {
            RayTraceResult hit = mc.world.rayTraceBlocks(
                eyes, blockVisibilitySample(desired, sampleIndex), false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && corridorObstacleAllowed(
                        hit.getBlockPos(), desired, permittedLower, corridor)) return hit;
        }
        return null;
    }

    private RayTraceResult rayTraceExactBlock(Vec3d eyes, BlockPos target) {
        for (int sampleIndex = 0; sampleIndex < BLOCK_VISIBILITY_SAMPLE_COUNT; sampleIndex++) {
            RayTraceResult hit = mc.world.rayTraceBlocks(
                eyes, blockVisibilitySample(target, sampleIndex), false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && target.equals(hit.getBlockPos())) return hit;
        }
        return null;
    }

    private boolean damageCorridorBlock(BlockPos obstacle) {
        return damageCorridorBlock(obstacle, rayTraceExactBlock(
            mc.player.getPositionEyes(1.0F), obstacle));
    }

    private boolean damageCorridorBlock(BlockPos obstacle, RayTraceResult hit) {
        if (!isBreakableBlock(obstacle)) return false;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        if (!withinMiningReach(eyes, obstacle, miningReach())) return false;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK
                || !obstacle.equals(hit.getBlockPos())) return false;
        selectBestPickaxe(obstacle);
        face(obstacle);
        mc.playerController.onPlayerDamageBlock(obstacle, hit.sideHit);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        clearingAttempts++;
        return true;
    }

    private List<BlockPos> plannedObstacles() {
        int currentTick = mc.player.ticksExisted;
        if (reusePlannedObstacleCache(plannedObstacleCacheTick, currentTick,
                plannedObstacleCacheIndex, pathIndex, plannedObstacleCachePath == path)) {
            return plannedObstacleCache;
        }
        List<BlockPos> result = new ArrayList<>();
        BlockPos routeStart = pathIndex == 0
            ? standPos(playerNavigationFeetCell())
            : path.get(pathIndex - 1);
        for (BlockPos cell : corridorCells(path, pathIndex, ROUTE_RENDER_LIMIT, routeStart)) {
            if (!isPassable(cell)) result.add(cell);
        }
        plannedObstacleCache = result;
        plannedObstacleCachePath = path;
        plannedObstacleCacheTick = currentTick;
        plannedObstacleCacheIndex = pathIndex;
        return plannedObstacleCache;
    }

    static boolean reusePlannedObstacleCache(int cachedTick, int currentTick, int cachedIndex,
            int currentIndex, boolean samePath) {
        return samePath && cachedTick == currentTick && cachedIndex == currentIndex;
    }

    static List<BlockPos> corridorCells(List<BlockPos> path, int start, int limit) {
        BlockPos routeStart = start > 0 && start <= path.size() ? path.get(start - 1) : null;
        return corridorCells(path, start, limit, routeStart);
    }

    static List<BlockPos> corridorCells(List<BlockPos> path, int start, int limit, BlockPos routeStart) {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        int from = Math.max(0, start);
        int end = Math.min(path.size(), from + Math.max(0, limit));
        BlockPos previous = routeStart;
        for (int i = from; i < end; i++) {
            BlockPos feet = path.get(i);
            if (previous != null && feet.getY() > previous.getY()) cells.add(previous.up(2));
            cells.add(feet.up());
            cells.add(feet);
            previous = feet;
        }
        return new ArrayList<>(cells);
    }

    static boolean sameVein(BlockPos first, BlockPos second, OreType firstType, OreType secondType) {
        if (first == null || second == null || firstType == null || firstType != secondType) return false;
        return Math.abs(first.getX() - second.getX()) <= 1
            && Math.abs(first.getY() - second.getY()) <= 1
            && Math.abs(first.getZ() - second.getZ()) <= 1;
    }

    private OreType targetType(BlockPos pos) {
        if (temporarilyBlocked(pos, rejectedTargetsUntil,
                mc.player == null ? 0 : mc.player.ticksExisted)) return null;
        IBlockState state = mc.world.getBlockState(pos);
        OreType type = OreType.fromBlock(state.getBlock());
        if (type == null || !ModConfig.isMineOreEnabled(type)
                || quotaBlocksTarget(quotaReached(type), pos.equals(miningPos))) return null;
        return type;
    }

    static boolean quotaBlocksTarget(boolean quotaReached, boolean activeMiningTarget) {
        return quotaReached && !activeMiningTarget;
    }

    private void coolDownUnusableRouteTarget() {
        if (mc.player != null) stopRouteMotion();
        if (currentOre != null) {
            coolDownCandidate(blockedTargetsUntil, currentOre,
                mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
        }
        clearPath();
        pathRetryDelay = 0;
        delay = 2;
    }

    private void restartRouteFromCurrentPosition() {
        stopRouteMotion();
        clearPath();
        pathRetryDelay = 0;
        delay = 0;
    }

    private void replanStalledRoute() {
        BlockPos retryOre = currentOre == null ? null : currentOre.toImmutable();
        OreType retryType = currentOreType;
        BlockPos retryFeet = miningPlayerFeet == null ? null : miningPlayerFeet.toImmutable();
        int retries = nextStalledRouteReplanCount(
            currentOre, currentOreType, miningPlayerFeet, stalledRouteOre, stalledRouteType,
            stalledRouteFeet, stalledRouteReplans);
        stopRouteMotion();
        clearPath();
        stalledRouteOre = retryOre;
        stalledRouteType = retryType;
        stalledRouteFeet = retryFeet;
        stalledRouteReplans = retries;
        pathRetryDelay = 0;
        delay = 0;
    }

    private void activatePathTarget(PathTarget target) {
        resetPathCandidateBatch();
        if (!completionOwnsWork(
                stalledRouteOre, stalledRouteType, target.ore, target.type)) {
            resetRouteStallRecovery();
        }
        currentOre = target.ore;
        currentOreType = target.type;
        path = target.path;
        pathIndex = 0;
        resetRouteProgress();
    }

    private void resetRouteProgress() {
        lastRouteDistanceSq = Double.POSITIVE_INFINITY;
        stalledRouteTicks = 0;
    }

    private void resetRouteStallRecovery() {
        stalledRouteOre = null;
        stalledRouteType = null;
        stalledRouteFeet = null;
        stalledRouteReplans = 0;
    }

    private void pruneBlockedTargets(int currentTick) {
        refreshAfterCooldownExpiry(pruneExpiredTargets(blockedTargetsUntil, currentTick));
    }

    private void pruneRejectedBlocks(int currentTick) {
        boolean targetsChanged = pruneExpiredTargets(rejectedTargetsUntil, currentTick);
        boolean obstaclesChanged = pruneExpiredTargets(rejectedObstaclesUntil, currentTick);
        boolean scaffoldsChanged = pruneExpiredTargets(rejectedScaffoldsUntil, currentTick);
        boolean standsChanged = pruneExpiredMiningStands(
            rejectedMiningStandsUntil, currentTick);
        refreshAfterCooldownExpiry(
            targetsChanged || obstaclesChanged || scaffoldsChanged || standsChanged);
    }

    private void refreshAfterCooldownExpiry(boolean cooldownExpired) {
        pathRetryDelay = retryDelayAfterCooldownExpiry(pathRetryDelay, cooldownExpired);
        if (!cooldownExpired) return;
        resetPathCandidateBatch();
        invalidateCurrentCandidateCache();
    }

    static int retryDelayAfterCooldownExpiry(int delay, boolean cooldownExpired) {
        return cooldownExpired ? 0 : delay;
    }

    static boolean pruneExpiredTargets(Map<BlockPos, Integer> targets, int currentTick) {
        return targets != null
            && targets.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    static boolean pruneExpiredMiningStands(
            Map<BlockPos, RejectedMiningStands> rejectedStands, int currentTick) {
        if (rejectedStands == null || rejectedStands.isEmpty()) return false;
        boolean changed = false;
        Iterator<Map.Entry<BlockPos, RejectedMiningStands>> iterator =
            rejectedStands.entrySet().iterator();
        while (iterator.hasNext()) {
            RejectedMiningStands rejected = iterator.next().getValue();
            changed |= rejected != null && pruneExpiredTargets(rejected.stands, currentTick);
            if (rejected == null || rejected.stands.isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private void rejectMiningStand(BlockPos ore, BlockPos stand) {
        if (ore == null || stand == null) return;
        OreType type = targetType(ore);
        if (type == null) return;
        RejectedMiningStands rejected = rejectedMiningStandsUntil.get(ore);
        if (rejected == null || rejected.type != type) {
            rejected = new RejectedMiningStands(type);
            rejectedMiningStandsUntil.put(ore.toImmutable(), rejected);
        }
        extendTargetCooldown(
            rejected.stands, stand, mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
    }

    private boolean miningStandTemporarilyUnavailable(BlockPos ore, BlockPos stand) {
        return miningStandTemporarilyUnavailable(
            rejectedMiningStandsUntil, ore, stand, targetType(ore), mc.player.ticksExisted);
    }

    static boolean miningStandTemporarilyUnavailable(
            Map<BlockPos, RejectedMiningStands> rejectedStands, BlockPos ore, BlockPos stand,
            OreType currentType, int currentTick) {
        if (rejectedStands == null || ore == null || stand == null) return false;
        RejectedMiningStands rejected = rejectedStands.get(ore);
        if (rejected == null) return false;
        if (rejected.type != currentType) {
            rejectedStands.remove(ore);
            return false;
        }
        return temporarilyBlocked(stand, rejected.stands, currentTick);
    }

    private boolean targetTemporarilyUnavailable(BlockPos target) {
        int currentTick = mc.player.ticksExisted;
        return targetTemporarilyUnavailable(target, blockedTargetsUntil,
            rejectedTargetsUntil, currentTick);
    }

    static boolean targetTemporarilyUnavailable(BlockPos target,
            Map<BlockPos, Integer> blockedTargets, Map<BlockPos, Integer> rejectedTargets,
            int currentTick) {
        return temporarilyBlocked(target, blockedTargets, currentTick)
            || temporarilyBlocked(target, rejectedTargets, currentTick);
    }

    static boolean scaffoldTemporarilyUnavailable(BlockPos target,
            Map<BlockPos, Integer> rejectedScaffolds, int currentTick) {
        return temporarilyBlocked(target, rejectedScaffolds, currentTick);
    }

    private void blockTargets(Iterable<BlockPos> targets, int untilTick) {
        boolean changed = false;
        for (BlockPos target : targets) {
            changed |= extendTargetCooldown(blockedTargetsUntil, target, untilTick);
        }
        if (changed) invalidateCurrentCandidateCache();
    }

    private void coolDownCandidate(Map<BlockPos, Integer> targets, BlockPos target, int untilTick) {
        if (extendTargetCooldown(targets, target, untilTick)) invalidateCurrentCandidateCache();
    }

    static boolean extendTargetCooldown(Map<BlockPos, Integer> targets,
            BlockPos target, int untilTick) {
        if (targets == null || target == null) return false;
        Integer previous = targets.get(target);
        if (previous != null && previous >= untilTick) return false;
        targets.put(target.toImmutable(), untilTick);
        return true;
    }

    private void clearPath() {
        resetPathCandidateBatch();
        path = java.util.Collections.emptyList();
        currentOre = null;
        currentOreType = null;
        clearMiningTarget();
        clearClearingTarget();
        pathIndex = 0;
        clearScaffoldAssist();
        resetRouteProgress();
        resetRouteStallRecovery();
        invalidatePlannedObstacleCache();
    }

    private void invalidatePlannedObstacleCache() {
        plannedObstacleCache = java.util.Collections.emptyList();
        plannedObstacleCachePath = java.util.Collections.emptyList();
        plannedObstacleCacheTick = Integer.MIN_VALUE;
        plannedObstacleCacheIndex = -1;
        routeCorridorCache = java.util.Collections.emptyList();
        routeCorridorCachePath = java.util.Collections.emptyList();
        routeCorridorCacheIndex = -1;
        routeCorridorCacheStart = null;
    }

    private void stopAutomatedWork(boolean stopMotion) {
        if (mc.player != null) resetRouteMotion(stopMotion);
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        pathRetryDelay = 0;
        invalidateCurrentCandidateCache();
        clearPath();
        clearTargetLabels();
    }

    private void clearClearingTarget() {
        if (clearingControllerResetRequired(clearingPos, null)
                && mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }
        clearingPos = null;
        clearingAttempts = 0;
        clearingAttemptBudget = 0;
        clearingDeadlineTick = 0;
        clearingMissingTicks = 0;
    }

    static boolean clearingControllerResetRequired(BlockPos current, BlockPos requested) {
        return current != null && !current.equals(requested);
    }

    private void clearMiningTarget() {
        if (miningControllerResetRequired(miningPos, miningType, null, null)
                && mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }
        releasePendingQuotaReservation(miningPos, miningType);
        miningPos = null;
        miningType = null;
        miningRouteBlocker = false;
        miningAttempts = 0;
        miningAttemptBudget = 0;
        miningDeadlineTick = 0;
    }

    static boolean miningControllerResetRequired(BlockPos currentPos, OreType currentType,
            BlockPos requestedPos, OreType requestedType) {
        return (currentPos != null || currentType != null)
            && !completionOwnsWork(currentPos, currentType, requestedPos, requestedType);
    }

    private void clearPendingCompletion() {
        boolean quotaAvailabilityChanged = false;
        for (PendingCompletion pending : pendingCompletions) {
            quotaAvailabilityChanged |= pending.world == mc.world && pending.reservesQuota;
        }
        pendingCompletions.clear();
        if (quotaAvailabilityChanged) invalidateCurrentCandidateCache();
    }

    private boolean currentRouteAwaitingCompletion() {
        if (currentOre == null) return false;
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world
                    && completionAwaitsRoute(pending.routeOre, pending.routeType, currentOre,
                        currentOreType, pending.absenceObserved)) return true;
        }
        return false;
    }

    private void rememberPendingCompletion(BlockPos pos, OreType type) {
        int untilTick = mc.player.ticksExisted + COMPLETION_CONFIRM_TICKS;
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world && pending.pos.equals(pos) && pending.type == type) {
                pending.untilTick = untilTick;
                boolean previouslyReserved = pending.reservesQuota;
                pending.reservesQuota = pendingQuotaReservationAfter(
                    pending.reservesQuota, PendingQuotaEvent.RETRY);
                if (previouslyReserved != pending.reservesQuota) {
                    invalidateCurrentCandidateCache();
                }
                pending.routeOre = currentOre == null ? null : currentOre.toImmutable();
                pending.routeType = currentOreType;
                return;
            }
        }
        while (pendingCompletions.size() >= MAX_PENDING_COMPLETIONS) evictPendingCompletion();
        pendingCompletions.addLast(new PendingCompletion(mc.world, pos.toImmutable(), type,
            untilTick, currentOre == null ? null : currentOre.toImmutable(), currentOreType));
        invalidateCurrentCandidateCache();
    }

    private void evictPendingCompletion() {
        PendingCompletion selected = null;
        int selectedPriority = Integer.MAX_VALUE;
        int currentTick = mc.player == null ? 0 : mc.player.ticksExisted;
        for (PendingCompletion pending : pendingCompletions) {
            boolean ownsWork = completionOwnsWork(pending.pos, pending.type,
                    currentOre, currentOreType)
                || completionOwnsWork(pending.pos, pending.type, miningPos, miningType)
                || completionOwnsWork(pending.routeOre, pending.routeType,
                    currentOre, currentOreType);
            int priority = pendingCompletionEvictionPriority(pending.world == mc.world,
                completionConfirmationExpired(currentTick, pending.untilTick),
                pending.reservesQuota, ownsWork);
            if (priority < selectedPriority) {
                selected = pending;
                selectedPriority = priority;
            }
        }
        if (selected != null) {
            pendingCompletions.remove(selected);
            if (selected.world == mc.world && selected.reservesQuota) {
                invalidateCurrentCandidateCache();
            }
        }
    }

    private void forgetPendingCompletion(BlockPos pos, OreType type) {
        boolean quotaAvailabilityChanged = false;
        Iterator<PendingCompletion> iterator = pendingCompletions.iterator();
        while (iterator.hasNext()) {
            PendingCompletion pending = iterator.next();
            if (pending.world != mc.world || !pending.pos.equals(pos) || pending.type != type) {
                continue;
            }
            quotaAvailabilityChanged |= pending.reservesQuota;
            iterator.remove();
        }
        if (quotaAvailabilityChanged) invalidateCurrentCandidateCache();
    }

    private void releasePendingQuotaReservation(BlockPos pos, OreType type) {
        if (pos == null || type == null) return;
        boolean quotaAvailabilityChanged = false;
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world != mc.world || !pending.pos.equals(pos) || pending.type != type
                    || !pendingReservationMayRelease(pending.absenceObserved)) continue;
            boolean previouslyReserved = pending.reservesQuota;
            pending.reservesQuota = pendingQuotaReservationAfter(
                pending.reservesQuota, PendingQuotaEvent.VISIBILITY_LOST);
            quotaAvailabilityChanged |= previouslyReserved != pending.reservesQuota;
        }
        if (quotaAvailabilityChanged) invalidateCurrentCandidateCache();
    }

    static int destructionAttemptBudget(float relativeHardness) {
        if (!(relativeHardness > 0.0F)) return MAX_DESTRUCTION_ATTEMPTS;
        int expected = (int) Math.ceil(1.0D / relativeHardness);
        return MathHelper.clamp(expected + DESTRUCTION_ATTEMPT_GRACE,
            MIN_DESTRUCTION_ATTEMPTS, MAX_DESTRUCTION_ATTEMPTS);
    }

    static boolean destructionAttemptsExhausted(int attempts, int budget) {
        return budget > 0 && attempts >= budget;
    }

    static int destructionDeadlineTick(int startTick, int attemptBudget, int actionDelayTicks) {
        long ticks = (long) Math.max(1, attemptBudget)
            * (Math.max(0, actionDelayTicks) + 1L) + COMPLETION_CONFIRM_TICKS;
        return (int) Math.min(Integer.MAX_VALUE, startTick + ticks);
    }

    static boolean destructionWorkExhausted(int attempts, int budget,
            int currentTick, int deadlineTick) {
        return destructionAttemptsExhausted(attempts, budget)
            || deadlineTick > 0 && currentTick > deadlineTick;
    }

    static boolean completionConfirmationExpired(int currentTick, int confirmationUntilTick) {
        return currentTick > confirmationUntilTick;
    }

    static boolean completionAbsenceConfirmed(boolean chunkLoaded, int consecutiveMissingTicks) {
        return chunkLoaded && consecutiveMissingTicks >= REQUIRED_MISSING_CONFIRM_TICKS;
    }

    static boolean completionRolledBack(boolean absenceObserved) {
        return absenceObserved;
    }

    static boolean pendingQuotaReservationAfter(boolean currentlyReserved, PendingQuotaEvent event) {
        if (event == PendingQuotaEvent.VISIBILITY_LOST) return false;
        return event == PendingQuotaEvent.RETRY
            || event == PendingQuotaEvent.BLOCK_MISSING
            || currentlyReserved;
    }

    static boolean pendingReservationMayRelease(boolean absenceObserved) {
        return !absenceObserved;
    }

    enum PendingQuotaEvent {
        RETRY,
        VISIBILITY_LOST,
        BLOCK_MISSING
    }

    static boolean completionOwnsWork(BlockPos completed, OreType completedType,
            BlockPos current, OreType currentType) {
        return completed != null && completed.equals(current)
            && completedType != null && completedType == currentType;
    }

    static boolean preserveQueuedVeinTarget(BlockPos mined, OreType minedType,
            BlockPos queued, OreType queuedType, Map<BlockPos, Integer> labels) {
        return mined != null && queued != null && !mined.equals(queued)
            && minedType != null && minedType == queuedType && labels != null
            && labels.containsKey(mined) && labels.containsKey(queued);
    }

    static boolean preserveQueuedRouteTarget(boolean routeBlocker, BlockPos mined,
            OreType minedType, BlockPos queued, OreType queuedType) {
        return routeBlocker && queued != null && queuedType != null
            && !completionOwnsWork(mined, minedType, queued, queuedType)
            && mined != null && minedType != null;
    }

    static boolean routeBlockerOwnership(boolean sameTarget, boolean previousOwnership,
            boolean requestedOwnership) {
        return requestedOwnership || sameTarget && previousOwnership;
    }

    static boolean miningTargetChanged(BlockPos current, OreType currentType,
            BlockPos requested, OreType requestedType) {
        return current != null && currentType != null
            && !completionOwnsWork(current, currentType, requested, requestedType);
    }

    static boolean completionAwaitsRoute(BlockPos routeOre, OreType routeType,
            BlockPos currentOre, OreType currentType, boolean absenceObserved) {
        return completionOwnsWork(routeOre, routeType, currentOre, currentType)
            && absenceObserved;
    }

    static boolean completionInvalidatesCurrentRoute(boolean ownsCurrentWork,
            boolean ownsBoundRoute) {
        return ownsCurrentWork || ownsBoundRoute;
    }

    static int pendingCompletionEvictionPriority(boolean sameWorld, boolean expired,
            boolean reservesQuota, boolean ownsWork) {
        if (!sameWorld || expired) return 0;
        if (!ownsWork && !reservesQuota) return 1;
        if (!ownsWork) return 2;
        return reservesQuota ? 4 : 3;
    }

    private void resetPathCandidateBatch() {
        pathCandidateOffset = 0;
        pathCandidateBatch = java.util.Collections.emptyList();
        pathCandidateFeet = null;
        pendingPathTarget = null;
        pendingPathTargetScore = Integer.MAX_VALUE;
        pendingPathTargetSameVein = false;
        pendingPathTargetLabel = Integer.MAX_VALUE;
        pendingPathComparisonTicks = 0;
        pendingPathSearch = null;
        pendingFailedPathTargets.clear();
        pathSnapshotRefreshRequested = false;
    }

    private void selectBestPickaxe(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        int bestSlot = mc.player.inventory.currentItem;
        boolean bestHarvests = toolHarvests(mc.player.inventory.getStackInSlot(bestSlot), pos);
        float bestSpeed = toolSpeed(mc.player.inventory.getStackInSlot(bestSlot), state);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(slot);
            boolean harvests = toolHarvests(stack, pos);
            float speed = toolSpeed(stack, state);
            if (betterMiningTool(harvests, speed, bestHarvests, bestSpeed)) {
                bestHarvests = harvests;
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        if (bestSlot != mc.player.inventory.currentItem) {
            mc.player.inventory.currentItem = bestSlot;
            mc.playerController.updateController();
        }
    }

    private List<OreVisualizer.CachedOre> cachedMineCandidates() {
        return prioritizeCurrentVein(oreVisualizer.cachedMineOres(
            ModConfig.minePathRange, MAX_CACHED_TARGETS,
            type -> !quotaReached(type),
            pos -> !targetTemporarilyUnavailable(pos)));
    }

    private List<OreVisualizer.CachedOre> currentMineCandidates() {
        int tickBucket = mc.player.ticksExisted / 4;
        BlockPos feet = miningPlayerFeet;
        long markerRevision = oreVisualizer.markerRevision();
        long selectionRevision = ModConfig.autoMineSelectionRevision();
        boolean sameOrigin = sameCandidateOrigin(currentCandidateX, currentCandidateY,
            currentCandidateZ, mc.player.posX, mc.player.posY, mc.player.posZ);
        if (!reuseCurrentCandidateCache(
                currentCandidateTickBucket, tickBucket, currentCandidateFeet, feet,
                currentCandidateMarkerRevision, markerRevision, sameOrigin,
                currentCandidateSelectionRevision, selectionRevision)) {
            currentCandidateCache = cachedMineCandidates();
            currentCandidateTickBucket = tickBucket;
            currentCandidateFeet = feet == null ? null : feet.toImmutable();
            currentCandidateMarkerRevision = markerRevision;
            currentCandidateSelectionRevision = selectionRevision;
            currentCandidateX = mc.player.posX;
            currentCandidateY = mc.player.posY;
            currentCandidateZ = mc.player.posZ;
        }
        return currentCandidateCache;
    }

    static boolean reuseCurrentCandidateCache(int cachedTickBucket, int currentTickBucket,
            BlockPos cachedFeet, BlockPos currentFeet, long cachedSelectionRevision,
            long currentSelectionRevision) {
        return cachedSelectionRevision == currentSelectionRevision
            && cachedTickBucket == currentTickBucket
            && java.util.Objects.equals(cachedFeet, currentFeet);
    }

    static boolean reuseCurrentCandidateCache(int cachedTickBucket, int currentTickBucket,
            BlockPos cachedFeet, BlockPos currentFeet, long cachedMarkerRevision,
            long currentMarkerRevision, boolean sameOrigin, long cachedSelectionRevision,
            long currentSelectionRevision) {
        if (cachedSelectionRevision != currentSelectionRevision) return false;
        if (!java.util.Objects.equals(cachedFeet, currentFeet)) return false;
        return cachedTickBucket == currentTickBucket
            || cachedMarkerRevision == currentMarkerRevision && sameOrigin;
    }

    static boolean sameCandidateOrigin(double cachedX, double cachedY, double cachedZ,
            double currentX, double currentY, double currentZ) {
        return Double.compare(cachedX, currentX) == 0
            && Double.compare(cachedY, currentY) == 0
            && Double.compare(cachedZ, currentZ) == 0;
    }

    private void invalidateCurrentCandidateCache() {
        currentCandidateCache = java.util.Collections.emptyList();
        currentCandidateTickBucket = Integer.MIN_VALUE;
        currentCandidateFeet = null;
        currentCandidateMarkerRevision = Long.MIN_VALUE;
        currentCandidateSelectionRevision = Long.MIN_VALUE;
        currentCandidateX = Double.NaN;
        currentCandidateY = Double.NaN;
        currentCandidateZ = Double.NaN;
    }

    private float toolSpeed(ItemStack stack, IBlockState state) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemPickaxe)) return -1.0F;
        return stack.getDestroySpeed(state);
    }

    private boolean toolHarvests(ItemStack stack, BlockPos pos) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemPickaxe
            && ForgeHooks.canToolHarvestBlock(mc.world, pos, stack);
    }

    static boolean betterMiningTool(boolean harvests, float speed, boolean bestHarvests, float bestSpeed) {
        if (harvests != bestHarvests) return harvests;
        return speed > bestSpeed;
    }

    private MineTarget findNearestReachable(List<OreVisualizer.CachedOre> candidates) {
        MineTarget labeledTarget = findVisibleLabeledTarget(candidates);
        if (labeledTarget != null) return labeledTarget;
        if (!targetLabels.isEmpty()) return findVisibleUnlabeledTarget(candidates);
        MineTarget veinTarget = findVisibleVeinTarget(candidates);
        if (veinTarget != null) return veinTarget;
        int inspected = 0;
        int previouslyInspectedVein = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (targetTemporarilyUnavailable(candidate.pos())) continue;
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (!candidateTypeAvailable(
                    ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))) continue;
            boolean sameVein = sameVein(lastMinedOre, candidate.pos(),
                lastMinedType, candidate.type());
            if (skipPreviouslyInspectedVein(sameVein, previouslyInspectedVein,
                    MAX_VISIBLE_TARGETS)) {
                previouslyInspectedVein++;
                continue;
            }
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return null;
    }

    private MineTarget findVisibleUnlabeledTarget(List<OreVisualizer.CachedOre> candidates) {
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (targetLabels.containsKey(candidate.pos())
                    || targetTemporarilyUnavailable(candidate.pos())
                    || !connectedToLabeledVein(candidate.pos(), candidate.type(),
                        targetLabels, targetLabelType)) continue;
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (!candidateTypeAvailable(
                    ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) {
                if (!targetLabels.containsKey(visible.pos)
                        && connectedToLabeledVein(visible.pos, visible.type,
                            targetLabels, targetLabelType)) {
                    targetLabels.put(visible.pos, targetLabels.size() + 1);
                    reorderRemainingTargetLabels();
                }
                return visible;
            }
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return null;
    }

    static boolean connectedToLabeledVein(BlockPos candidate, OreType candidateType,
            Map<BlockPos, Integer> labels, OreType labelType) {
        if (candidate == null || candidateType == null || candidateType != labelType
                || labels == null || labels.isEmpty() || labels.containsKey(candidate)) return false;
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbor.setPos(candidate.getX() + dx, candidate.getY() + dy,
                        candidate.getZ() + dz);
                    if (labels.containsKey(neighbor)) return true;
                }
            }
        }
        return false;
    }

    static boolean preserveExistingLabelsForVisibleTarget(
            Map<BlockPos, Integer> labels, BlockPos target) {
        return labels != null && !labels.isEmpty() && target != null && !labels.containsKey(target);
    }

    static boolean skipPreviouslyInspectedVein(boolean sameVein, int previouslyInspected,
            int inspectionLimit) {
        return sameVein && previouslyInspected < Math.max(0, inspectionLimit);
    }

    static boolean candidateTypeAvailable(boolean configured, boolean quotaReached) {
        return configured && !quotaReached;
    }

    private List<OreVisualizer.CachedOre> prioritizeCurrentVein(
            List<OreVisualizer.CachedOre> candidates) {
        if (candidates.size() < 2 || (targetLabels.isEmpty()
                && (lastMinedOre == null || lastMinedType == null))) return candidates;
        List<OreVisualizer.CachedOre> prioritized = new ArrayList<>(candidates);
        prioritized.sort((left, right) -> compareTargetPriority(left, right, targetLabels,
            lastMinedOre, lastMinedType));
        return prioritized;
    }

    static int compareTargetPriority(OreVisualizer.CachedOre left, OreVisualizer.CachedOre right,
            Map<BlockPos, Integer> labels, BlockPos lastMined, OreType lastType) {
        int leftLabel = labels.getOrDefault(left.pos(), Integer.MAX_VALUE);
        int rightLabel = labels.getOrDefault(right.pos(), Integer.MAX_VALUE);
        if (leftLabel != rightLabel) return Integer.compare(leftLabel, rightLabel);
        return compareVeinPriority(left, right, lastMined, lastType);
    }

    static int compareVeinPriority(OreVisualizer.CachedOre left, OreVisualizer.CachedOre right,
            BlockPos lastMined, OreType lastType) {
        boolean leftVein = sameVein(lastMined, left.pos(), lastType, left.type());
        boolean rightVein = sameVein(lastMined, right.pos(), lastType, right.type());
        if (leftVein != rightVein) return leftVein ? -1 : 1;
        return OreVisualizer.compareCachedOres(left, right);
    }

    private MineTarget findVisibleVeinTarget(List<OreVisualizer.CachedOre> candidates) {
        if (lastMinedOre == null || lastMinedType == null) return null;
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (!sameVein(lastMinedOre, candidate.pos(), lastMinedType, candidate.type())) continue;
            if (targetTemporarilyUnavailable(candidate.pos())) continue;
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (!candidateTypeAvailable(
                    ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return null;
    }

    private MineTarget findVisibleLabeledTarget(List<OreVisualizer.CachedOre> candidates) {
        if (targetLabels.isEmpty()) return null;
        if (!reuseLabeledVisibilityCandidates(
                labeledVisibilityCandidateSource, candidates, true)) {
            labeledVisibilityCandidates.clear();
            for (OreVisualizer.CachedOre candidate : candidates) {
                int label = targetLabels.getOrDefault(candidate.pos(), Integer.MAX_VALUE);
                if (label != Integer.MAX_VALUE && !targetTemporarilyUnavailable(candidate.pos())) {
                    labeledVisibilityCandidates.add(candidate);
                }
            }
            labeledVisibilityCandidates.sort((left, right) -> {
                int label = Integer.compare(targetLabels.getOrDefault(left.pos(), Integer.MAX_VALUE),
                    targetLabels.getOrDefault(right.pos(), Integer.MAX_VALUE));
                return label != 0 ? label : OreVisualizer.compareCachedOres(left, right);
            });
            labeledVisibilityCandidateSource = candidates;
        }
        int candidateCount = labeledVisibilityCandidates.size();
        int inspections = labeledVisibilityInspectionCount(candidateCount, MAX_VISIBLE_TARGETS);
        int fixed = fixedLabeledVisibilityInspections(candidateCount, MAX_VISIBLE_TARGETS,
            FIXED_LABELED_VISIBILITY_INSPECTIONS);
        int rotatingInspections = Math.max(0, inspections - fixed);
        for (int inspection = 0; inspection < inspections; inspection++) {
            int index = labeledVisibilityIndex(inspection, candidateCount, MAX_VISIBLE_TARGETS,
                FIXED_LABELED_VISIBILITY_INSPECTIONS, labeledVisibilityCursor);
            OreVisualizer.CachedOre candidate = labeledVisibilityCandidates.get(index);
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (!candidateTypeAvailable(
                    ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
        }
        labeledVisibilityCursor = advanceLabeledVisibilityCursor(labeledVisibilityCursor,
            Math.max(0, candidateCount - fixed), rotatingInspections);
        return null;
    }

    static int labeledVisibilityInspectionCount(int candidateCount, int inspectionLimit) {
        return Math.min(Math.max(0, candidateCount), Math.max(0, inspectionLimit));
    }

    static int fixedLabeledVisibilityInspections(int candidateCount, int inspectionLimit,
            int fixedCount) {
        return Math.min(labeledVisibilityInspectionCount(candidateCount, inspectionLimit),
            Math.max(0, fixedCount));
    }

    static int labeledVisibilityIndex(int inspection, int candidateCount, int inspectionLimit,
            int fixedCount, int cursor) {
        int inspections = labeledVisibilityInspectionCount(candidateCount, inspectionLimit);
        if (inspection < 0 || inspection >= inspections) return -1;
        int fixed = fixedLabeledVisibilityInspections(candidateCount, inspectionLimit, fixedCount);
        if (inspection < fixed) return inspection;
        int rotatingCount = candidateCount - fixed;
        return fixed + Math.floorMod(cursor + inspection - fixed, rotatingCount);
    }

    static int advanceLabeledVisibilityCursor(int cursor, int rotatingCount, int inspected) {
        if (rotatingCount <= 0 || inspected <= 0) return 0;
        return Math.floorMod(cursor + inspected, rotatingCount);
    }

    static boolean reuseLabeledVisibilityCandidates(List<OreVisualizer.CachedOre> previous,
            List<OreVisualizer.CachedOre> current, boolean labelsPresent) {
        return labelsPresent && previous != null && previous == current;
    }

    static boolean cachedOreStillPresent(OreType cached, OreType actual) {
        return cached != null && cached == actual;
    }

    private void ensureTargetLabels(BlockPos seed, OreType type,
            List<OreVisualizer.CachedOre> candidates) {
        if (seed == null || type == null) return;
        if (targetLabelType == type && targetLabels.containsKey(seed)) return;
        targetLabels.clear();
        targetLabels.putAll(labelConnectedVein(seed, type, candidates));
        targetLabelType = targetLabels.isEmpty() ? null : type;
        targetLabelsChanged();
    }

    private void extendTargetLabels(List<OreVisualizer.CachedOre> candidates) {
        boolean inspect = veinExtensionSnapshotChanged(
            extendedTargetLabelCandidates, candidates, !targetLabels.isEmpty());
        extendedTargetLabelCandidates = candidates;
        if (!inspect) return;
        Set<BlockPos> extensions = connectedVeinExtensions(
            targetLabels, targetLabelType, candidates);
        if (extensions.isEmpty()) return;
        for (BlockPos extension : extensions) {
            targetLabels.put(extension, targetLabels.size() + 1);
        }
        reorderRemainingTargetLabels();
    }

    static boolean veinExtensionSnapshotChanged(List<OreVisualizer.CachedOre> previous,
            List<OreVisualizer.CachedOre> current, boolean labelsPresent) {
        return labelsPresent && previous != current;
    }

    static Set<BlockPos> connectedVeinExtensions(Map<BlockPos, Integer> labels, OreType labelType,
            List<OreVisualizer.CachedOre> candidates) {
        Set<BlockPos> extensions = new LinkedHashSet<>();
        if (labels == null || labels.isEmpty() || labelType == null
                || candidates == null || candidates.isEmpty()) return extensions;
        Set<BlockPos> available = new HashSet<>();
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (candidate.type() == labelType && !labels.containsKey(candidate.pos())) {
                available.add(candidate.pos().toImmutable());
            }
        }
        if (available.isEmpty()) return extensions;
        List<Map.Entry<BlockPos, Integer>> seeds = new ArrayList<>(labels.entrySet());
        seeds.sort((left, right) -> {
            int label = Integer.compare(left.getValue(), right.getValue());
            return label != 0 ? label : compareBlockPositions(left.getKey(), right.getKey());
        });
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> seed : seeds) {
            BlockPos pos = seed.getKey().toImmutable();
            queue.addLast(pos);
            visited.add(pos);
        }
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        neighbor.setPos(current.getX() + dx, current.getY() + dy,
                            current.getZ() + dz);
                        if (!available.remove(neighbor)) continue;
                        BlockPos extension = neighbor.toImmutable();
                        if (visited.add(extension)) queue.addLast(extension);
                        extensions.add(extension);
                    }
                }
            }
        }
        return extensions;
    }

    private void pruneTargetLabels() {
        if (targetLabels.isEmpty()) return;
        boolean configured = targetLabelType != null
            && ModConfig.isMineOreEnabled(targetLabelType);
        boolean changed = targetLabels.entrySet().removeIf(entry -> {
            boolean loaded = mc.world.isBlockLoaded(entry.getKey());
            OreType actual = loaded
                ? OreType.fromBlock(mc.world.getBlockState(entry.getKey()).getBlock()) : null;
            boolean pendingConfirmation = loaded && actual != targetLabelType
                && pendingCompletionPreservesLabel(entry.getKey(), targetLabelType);
            return !labelOreStillPresent(targetLabelType, actual, configured, loaded,
                pendingConfirmation);
        });
        if (targetLabels.isEmpty()) targetLabelType = null;
        if (changed) targetLabelsChanged();
    }

    private boolean pendingCompletionPreservesLabel(BlockPos pos, OreType type) {
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world && pending.absenceObserved
                    && completionOwnsWork(pending.pos, pending.type, pos, type)) return true;
        }
        return false;
    }

    static boolean labelOreStillPresent(OreType expected, OreType actual, boolean configured,
            boolean loaded, boolean pendingConfirmation) {
        return configured && expected != null
            && (!loaded || expected == actual || pendingConfirmation);
    }

    private void clearTargetLabels() {
        boolean changed = !targetLabels.isEmpty() || targetLabelType != null;
        targetLabels.clear();
        targetLabelType = null;
        if (changed) targetLabelsChanged();
    }

    private void targetLabelsChanged() {
        labeledVisibilityCursor = 0;
        labeledVisibilityCandidates.clear();
        labeledVisibilityCandidateSource = null;
        invalidateCurrentCandidateCache();
    }

    private void reorderRemainingTargetLabels() {
        if (targetLabels.size() < 2 || mc.player == null) return;
        Map<BlockPos, Double> distances = new HashMap<>();
        for (BlockPos pos : targetLabels.keySet()) {
            distances.put(pos, mc.player.getDistanceSqToCenter(pos));
        }
        Map<BlockPos, Integer> reordered = relabelRemainingTargets(targetLabels, distances);
        targetLabels.clear();
        targetLabels.putAll(reordered);
        targetLabelsChanged();
    }

    static Map<BlockPos, Integer> relabelRemainingTargets(Map<BlockPos, Integer> labels,
            Map<BlockPos, Double> distances) {
        Map<BlockPos, Integer> result = new HashMap<>();
        if (labels == null || labels.isEmpty()) return result;
        List<BlockPos> positions = new ArrayList<>(labels.keySet());
        positions.sort((left, right) -> {
            int distance = Double.compare(distances == null ? Double.POSITIVE_INFINITY
                    : distances.getOrDefault(left, Double.POSITIVE_INFINITY),
                distances == null ? Double.POSITIVE_INFINITY
                    : distances.getOrDefault(right, Double.POSITIVE_INFINITY));
            if (distance != 0) return distance;
            int oldLabel = Integer.compare(labels.getOrDefault(left, Integer.MAX_VALUE),
                labels.getOrDefault(right, Integer.MAX_VALUE));
            return oldLabel != 0 ? oldLabel : compareBlockPositions(left, right);
        });
        for (int index = 0; index < positions.size(); index++) {
            result.put(positions.get(index), index + 1);
        }
        return result;
    }

    static Map<BlockPos, Integer> labelConnectedVein(BlockPos seed, OreType type,
            List<OreVisualizer.CachedOre> candidates) {
        Map<BlockPos, Integer> labels = new HashMap<>();
        if (seed == null || type == null || candidates == null) return labels;
        Set<BlockPos> available = new HashSet<>();
        Map<BlockPos, Double> distances = new HashMap<>();
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (candidate.type() == type) {
                BlockPos pos = candidate.pos().toImmutable();
                available.add(pos);
                distances.put(pos, candidate.distanceSq());
            }
        }
        available.add(seed.toImmutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.toImmutable());
        List<BlockPos> connected = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!available.contains(current) || !visited.add(current)) continue;
            connected.add(current);
            List<BlockPos> neighbors = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (available.contains(neighbor) && !visited.contains(neighbor)) {
                            neighbors.add(neighbor);
                        }
                    }
                }
            }
            neighbors.sort(AutoMiner::compareBlockPositions);
            queue.addAll(neighbors);
        }
        connected.sort((left, right) -> compareVeinLabelOrder(left, right, seed, distances));
        for (int index = 0; index < connected.size(); index++) {
            labels.put(connected.get(index), index + 1);
        }
        return labels;
    }

    static int compareVeinLabelOrder(BlockPos left, BlockPos right, BlockPos seed,
            Map<BlockPos, Double> distances) {
        if (left.equals(seed) != right.equals(seed)) return left.equals(seed) ? -1 : 1;
        int distance = Double.compare(distances.getOrDefault(left, Double.POSITIVE_INFINITY),
            distances.getOrDefault(right, Double.POSITIVE_INFINITY));
        return distance != 0 ? distance : compareBlockPositions(left, right);
    }

    private static int compareBlockPositions(BlockPos left, BlockPos right) {
        int y = Integer.compare(left.getY(), right.getY());
        if (y != 0) return y;
        int x = Integer.compare(left.getX(), right.getX());
        return x != 0 ? x : Integer.compare(left.getZ(), right.getZ());
    }

    static int visibleTargetInspectionLimit() {
        return MAX_VISIBLE_TARGETS;
    }

    private MineTarget visibleTarget(BlockPos pos) {
        return visibleTarget(pos, true);
    }

    private MineTarget visibleExactTarget(BlockPos pos) {
        return visibleTarget(pos, false);
    }

    private MineTarget visibleTarget(BlockPos pos, boolean allowLabeledBlocker) {
        OreType type = targetType(pos);
        if (type == null || !visibilityContextReady(miningPlayerFeet, miningEyes, miningReachDistance)) return null;
        if (!withinMiningReach(miningEyes, pos, miningReachDistance)) return null;
        RayTraceResult hit = rayTraceTarget(miningEyes, pos, type, allowLabeledBlocker);
        if (hit == null) return null;
        BlockPos hitPos = hit.getBlockPos();
        if (!pos.equals(hitPos)) {
            pos = hitPos;
            type = targetType(hitPos);
            if (!withinMiningReach(miningEyes, pos, miningReachDistance)) return null;
        }
        if (!stableMiningPosition(miningPlayerFeet, pos)) return null;
        return new MineTarget(pos.toImmutable(), type, hit.sideHit);
    }

    private RayTraceResult rayTraceTarget(Vec3d eyes, BlockPos pos, OreType type,
            boolean allowLabeledBlocker) {
        RayTraceResult labeledBlocker = null;
        for (int sampleIndex = 0; sampleIndex < BLOCK_VISIBILITY_SAMPLE_COUNT; sampleIndex++) {
            Vec3d sample = blockVisibilitySample(pos, sampleIndex);
            RayTraceResult hit = mc.world.rayTraceBlocks(eyes, sample, false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && pos.equals(hit.getBlockPos())) return hit;
            if (labeledBlocker == null && allowLabeledBlocker && hit != null
                    && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && isLabeledVeinBlocker(pos, hit.getBlockPos(), type,
                        targetType(hit.getBlockPos()), targetLabels)) {
                labeledBlocker = hit;
            }
        }
        return labeledBlocker;
    }

    static boolean isLabeledVeinBlocker(BlockPos desired, BlockPos hit, OreType desiredType,
            OreType hitType, Map<BlockPos, Integer> labels) {
        return desired != null && hit != null && !desired.equals(hit)
            && desiredType != null && desiredType == hitType
            && labels != null && labels.containsKey(desired) && labels.containsKey(hit);
    }

    static boolean visibilityContextReady(BlockPos playerFeet, Vec3d eyes, double reach) {
        return playerFeet != null && eyes != null && reach > 0.0;
    }

    private double miningReach() {
        return mc.playerController.getBlockReachDistance();
    }

    static Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static List<Vec3d> blockVisibilitySamples(BlockPos pos) {
        List<Vec3d> samples = new ArrayList<>(BLOCK_VISIBILITY_SAMPLE_COUNT);
        for (int index = 0; index < BLOCK_VISIBILITY_SAMPLE_COUNT; index++) {
            samples.add(blockVisibilitySample(pos, index));
        }
        return samples;
    }

    static Vec3d blockVisibilitySample(BlockPos pos, int index) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double near = 0.001;
        double far = 0.999;
        switch (index) {
            case 0: return new Vec3d(x + 0.5, y + 0.5, z + 0.5);
            case 1: return new Vec3d(x + near, y + 0.5, z + 0.5);
            case 2: return new Vec3d(x + far, y + 0.5, z + 0.5);
            case 3: return new Vec3d(x + 0.5, y + near, z + 0.5);
            case 4: return new Vec3d(x + 0.5, y + far, z + 0.5);
            case 5: return new Vec3d(x + 0.5, y + 0.5, z + near);
            case 6: return new Vec3d(x + 0.5, y + 0.5, z + far);
            default: throw new IndexOutOfBoundsException("visibility sample " + index);
        }
    }

    static boolean stableMiningPosition(BlockPos playerFeet, BlockPos ore) {
        int dx = Math.abs(playerFeet.getX() - ore.getX());
        int dy = ore.getY() - playerFeet.getY();
        int dz = Math.abs(playerFeet.getZ() - ore.getZ());
        if (dx == 0 && dz == 0) return dy == -1 || dy == 2;
        return dx + dz == 1 && dy >= -1 && dy <= 2;
    }

    static boolean withinMiningReach(Vec3d eyes, BlockPos pos, double reach) {
        double nearestX = MathHelper.clamp(eyes.x, pos.getX(), pos.getX() + 1.0);
        double nearestY = MathHelper.clamp(eyes.y, pos.getY(), pos.getY() + 1.0);
        double nearestZ = MathHelper.clamp(eyes.z, pos.getZ(), pos.getZ() + 1.0);
        double dx = eyes.x - nearestX;
        double dy = eyes.y - nearestY;
        double dz = eyes.z - nearestZ;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private void face(BlockPos pos) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d point = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double x = point.x - eyes.x;
        double y = point.y - eyes.y;
        double z = point.z - eyes.z;
        double horizontal = Math.sqrt(x * x + z * z);
        mc.player.rotationYaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        mc.player.rotationPitch = (float) -Math.toDegrees(Math.atan2(y, horizontal));
    }

    private void drawRoute(BufferBuilder buffer, double viewerX, double viewerY, double viewerZ) {
        BlockPos playerCell = new BlockPos(mc.player.posX, mc.player.getEntityBoundingBox().minY, mc.player.posZ);
        List<BlockPos> rawPoints = new ArrayList<>();
        rawPoints.add(playerCell);
        rawPoints.addAll(plannedRoutePoints());
        List<BlockPos> routePoints = orthogonalRoutePoints(rawPoints);
        if (routePoints.isEmpty()) return;
        BlockPos first = routePoints.get(0);
        double lastX = first.getX() + 0.5 - viewerX;
        double lastY = first.getY() + 0.5 - viewerY;
        double lastZ = first.getZ() + 0.5 - viewerZ;
        for (int i = 1; i < routePoints.size(); i++) {
            BlockPos point = routePoints.get(i);
            double x = point.getX() + 0.5 - viewerX;
            double y = point.getY() + 0.5 - viewerY;
            double z = point.getZ() + 0.5 - viewerZ;
            routeLine(buffer, lastX, lastY, lastZ, x, y, z);
            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    static List<BlockPos> orthogonalRoutePoints(List<BlockPos> points) {
        if (points == null || points.isEmpty()) return java.util.Collections.emptyList();
        List<BlockPos> result = new ArrayList<>();
        BlockPos previous = null;
        for (BlockPos point : points) {
            if (point == null) continue;
            if (previous == null) {
                result.add(point);
                previous = point;
                continue;
            }
            int x = previous.getX();
            int y = previous.getY();
            int z = previous.getZ();
            while (x != point.getX()) {
                x += Integer.signum(point.getX() - x);
                result.add(new BlockPos(x, y, z));
            }
            while (z != point.getZ()) {
                z += Integer.signum(point.getZ() - z);
                result.add(new BlockPos(x, y, z));
            }
            while (y != point.getY()) {
                y += Integer.signum(point.getY() - y);
                result.add(new BlockPos(x, y, z));
            }
            previous = point;
        }
        return result;
    }

    private List<BlockPos> plannedRoutePoints() {
        return remainingRoutePoints(path, pathIndex, ROUTE_RENDER_LIMIT);
    }

    static List<BlockPos> remainingRoutePoints(List<BlockPos> path, int pathIndex, int limit) {
        if (path == null || path.isEmpty() || limit <= 0) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<BlockPos> points = new LinkedHashSet<>();
        int from = Math.max(0, pathIndex);
        int end = Math.min(path.size(), from + limit);
        for (int i = from; i < end; i++) {
            points.add(path.get(i));
        }
        return new ArrayList<>(points);
    }

    private void routeLine(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        float red = currentOreType == OreType.DIAMOND ? 0.30F : 0.95F;
        float green = currentOreType == OreType.DIAMOND ? 0.95F : 0.78F;
        float blue = currentOreType == OreType.DIAMOND ? 0.95F : 0.20F;
        buffer.pos(x1, y1, z1).color(red, green, blue, 0.95F).endVertex();
        buffer.pos(x2, y2, z2).color(red, green, blue, 0.95F).endVertex();
    }

    private static final class PathTarget {
        private final BlockPos ore;
        private final OreType type;
        private final List<BlockPos> path;

        private PathTarget(BlockPos ore, OreType type, List<BlockPos> path) {
            this.ore = ore;
            this.type = type;
            this.path = path;
        }
    }

    private static final class MineTarget {
        private final BlockPos pos;
        private final OreType type;
        private final EnumFacing side;

        private MineTarget(BlockPos pos, OreType type, EnumFacing side) {
            this.pos = pos;
            this.type = type;
            this.side = side;
        }
    }

    static final class RejectedMiningStands {
        final OreType type;
        final Map<BlockPos, Integer> stands = new HashMap<>();

        RejectedMiningStands(OreType type) {
            this.type = type;
        }
    }

    private static final class PendingCompletion {
        private final World world;
        private final BlockPos pos;
        private final OreType type;
        private int untilTick;
        private int missingTicks;
        private boolean absenceObserved;
        private boolean reservesQuota = true;
        private BlockPos routeOre;
        private OreType routeType;

        private PendingCompletion(World world, BlockPos pos, OreType type, int untilTick,
                BlockPos routeOre, OreType routeType) {
            this.world = world;
            this.pos = pos;
            this.type = type;
            this.untilTick = untilTick;
            this.routeOre = routeOre;
            this.routeType = routeType;
        }
    }

    private static final class ScaffoldPlacement {
        private final BlockPos neighbor;
        private final EnumFacing side;

        private ScaffoldPlacement(BlockPos neighbor, EnumFacing side) {
            this.neighbor = neighbor;
            this.side = side;
        }
    }
    private static final class PathRoute {
        private final List<BlockPos> points;
        private final int cost;

        private PathRoute(List<BlockPos> points, int cost) {
            this.points = points;
            this.cost = cost;
        }
    }

    private static final class PathSearch {
        private final BlockPos ore;
        private final BlockPos start;
        private final List<BlockPos> goals;
        private final Set<BlockPos> goalSet;
        private final PriorityQueue<PathNode> queue = new PriorityQueue<>(AutoMiner::comparePathNodes);
        private final Map<BlockPos, BlockPos> previous = new HashMap<>();
        private final Map<BlockPos, Integer> costs = new HashMap<>();
        private final Map<BlockPos, Integer> traversalCosts = new HashMap<>();
        private final Map<BlockPos, Integer> jumpClearanceCosts = new HashMap<>();
        private final double maxDistanceSq;
        private final int restarts;
        private int visited;
        private boolean validatingSuccess;
        private boolean successGoalsValidated;
        private int successValidationPass;
        private PathRoute successRoute;
        private SuccessPathValidation successValidation;
        private boolean validatingFailure;
        private int failureValidationPass;
        private boolean failureValidationTruncated;
        private boolean failureGoalsValidated;
        private List<Map.Entry<BlockPos, Integer>> failureTraversalEntries;
        private List<Map.Entry<BlockPos, Integer>> failureJumpEntries;
        private Iterator<Map.Entry<BlockPos, Integer>> failureTraversalValidation;
        private Iterator<Map.Entry<BlockPos, Integer>> failureJumpValidation;

        private PathSearch(BlockPos ore, BlockPos start, List<BlockPos> goals, double maxDistanceSq) {
            this(ore, start, goals, maxDistanceSq, 0);
        }

        private PathSearch(BlockPos ore, BlockPos start, List<BlockPos> goals,
                double maxDistanceSq, int restarts) {
            this.ore = ore.toImmutable();
            this.start = start.toImmutable();
            this.goals = goals;
            this.goalSet = new HashSet<>(goals);
            this.maxDistanceSq = maxDistanceSq;
            this.restarts = restarts;
            queue.add(new PathNode(start, 0, pathPriority(0, start, goals)));
            previous.put(start, null);
            costs.put(start, 0);
        }

        private void beginSuccessValidation(PathRoute route) {
            validatingSuccess = true;
            successValidationPass = 1;
            successRoute = route;
            successValidation = new SuccessPathValidation(start, route.points);
            queue.clear();
            previous.clear();
            costs.clear();
        }

        private void beginFailureValidation(boolean exhaustive) {
            validatingFailure = true;
            int countLimit = MAX_PATH_STATES_TO_VALIDATE + 1;
            int traversalCount = pathStateValidationCount(traversalCosts, exhaustive, countLimit);
            int jumpCount = pathStateValidationCount(jumpClearanceCosts, exhaustive,
                Math.max(1, countLimit - Math.min(countLimit, traversalCount)));
            int traversalLimit = Math.min(MAX_PATH_STATES_TO_VALIDATE, traversalCount);
            int jumpLimit = Math.max(0, MAX_PATH_STATES_TO_VALIDATE - traversalLimit);
            failureTraversalEntries = pathStateEntriesForValidation(
                traversalCosts, exhaustive, traversalLimit);
            failureJumpEntries = pathStateEntriesForValidation(jumpClearanceCosts, exhaustive, jumpLimit);
            failureValidationTruncated = traversalCount + jumpCount > MAX_PATH_STATES_TO_VALIDATE;
            beginNextFailureValidationPass();
        }

        private void beginNextFailureValidationPass() {
            failureValidationPass++;
            failureGoalsValidated = false;
            failureTraversalValidation = failureTraversalEntries.iterator();
            failureJumpValidation = failureJumpEntries.iterator();
        }

        private boolean matches(BlockPos targetOre, BlockPos playerStart, double rangeSq) {
            return ore.equals(targetOre) && start.equals(playerStart)
                && Double.compare(maxDistanceSq, rangeSq) == 0;
        }
    }

    static final class SuccessPathValidation {
        private final BlockPos start;
        private final List<BlockPos> points;
        private int pointIndex;
        private boolean checkingClearance;

        SuccessPathValidation(BlockPos start, List<BlockPos> points) {
            this.start = start;
            this.points = points == null ? java.util.Collections.emptyList() : points;
        }

        boolean hasNext() {
            return pointIndex < points.size();
        }

        BlockPos currentPos() {
            BlockPos point = points.get(pointIndex);
            if (!checkingClearance) return point;
            return routeTransitionClearance(previousPoint(), point);
        }

        boolean currentUsesTraversalCost() {
            return !checkingClearance;
        }

        void advance() {
            BlockPos point = points.get(pointIndex);
            if (!checkingClearance) {
                BlockPos clearance = routeTransitionClearance(previousPoint(), point);
                if (transitionNeedsSeparateClearance(clearance, point)) {
                    checkingClearance = true;
                    return;
                }
            }
            checkingClearance = false;
            pointIndex++;
        }

        void reset() {
            pointIndex = 0;
            checkingClearance = false;
        }

        private BlockPos previousPoint() {
            return pointIndex == 0 ? start : points.get(pointIndex - 1);
        }
    }

    private static final class PathSearchResult {
        private final boolean complete;
        private final PathRoute route;
        private final boolean restart;

        private PathSearchResult(boolean complete, PathRoute route, boolean restart) {
            this.complete = complete;
            this.route = route;
            this.restart = restart;
        }

        private static PathSearchResult pending() {
            return new PathSearchResult(false, null, false);
        }

        private static PathSearchResult complete(PathRoute route) {
            return new PathSearchResult(true, route, false);
        }

        private static PathSearchResult restart() {
            return new PathSearchResult(false, null, true);
        }
    }

    private static final class PathNode {
        private final BlockPos pos;
        private final int cost;
        private final int priority;

        private PathNode(BlockPos pos, int cost, int priority) {
            this.pos = pos;
            this.cost = cost;
            this.priority = priority;
        }
    }
}

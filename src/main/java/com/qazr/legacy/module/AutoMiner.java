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
import net.minecraft.block.BlockFalling;
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
    private static final int PATH_CANDIDATE_BATCH_SIZE = 4;
    private static final int MAX_FAILED_CANDIDATES_PER_SNAPSHOT = 8;
    private static final int MAX_CACHED_TARGETS = 96;
    private static final int PATH_RETRY_TICKS = 20;
    private static final int FAILED_ROUTE_RETRY_TICKS = 100;
    private static final double ROUTE_SPEED = 0.18;
    private static final int MAX_VISIBLE_TARGETS = 16;
    private static final int MAX_STALLED_ROUTE_TICKS = 30;
    private static final double ROUTE_PROGRESS_EPSILON = 0.0025;
    private static final double ROUTE_NODE_REACH_DISTANCE_SQ = 0.04;
    private static final int ROUTE_RENDER_LIMIT = 220;
    private static final int SCAFFOLD_TIMEOUT_TICKS = 40;
    private static final int MAX_SCAFFOLD_ATTEMPTS = 5;
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
    private final Map<BlockPos, Integer> targetLabels = new HashMap<>();
    private final Map<BlockPos, Integer> blockedTargetsUntil = new HashMap<>();
    private final Map<BlockPos, Integer> rejectedTargetsUntil = new HashMap<>();
    private final Map<BlockPos, Integer> rejectedObstaclesUntil = new HashMap<>();
    private List<BlockPos> path = java.util.Collections.emptyList();
    private BlockPos currentOre;
    private OreType currentOreType;
    private BlockPos miningPos;
    private OreType miningType;
    private int miningAttempts;
    private int miningAttemptBudget;
    private int miningDeadlineTick;
    private BlockPos clearingPos;
    private int clearingAttempts;
    private int clearingAttemptBudget;
    private int clearingDeadlineTick;
    private final Deque<PendingCompletion> pendingCompletions = new ArrayDeque<>();
    private BlockPos lastMinedOre;
    private OreType lastMinedType;
    private int pathIndex;
    private int delay;
    private int manualPause;
    private int pathRetryDelay;
    private int pathCandidateOffset;
    private List<OreVisualizer.CachedOre> pathCandidateBatch = java.util.Collections.emptyList();
    private PathTarget pendingPathTarget;
    private final Set<BlockPos> pendingFailedPathTargets = new HashSet<>();
    private int pendingPathTargetScore = Integer.MAX_VALUE;
    private boolean pendingPathTargetSameVein;
    private int pendingPathTargetLabel = Integer.MAX_VALUE;
    private PathSearch pendingPathSearch;
    private boolean pathSnapshotRefreshRequested;
    private boolean observedEnabled;
    private BlockPos miningPlayerFeet;
    private Vec3d miningEyes;
    private double miningReachDistance;
    private double lastRouteDistanceSq = Double.POSITIVE_INFINITY;
    private int stalledRouteTicks;
    private OreType targetLabelType;
    private BlockPos scaffoldPos;
    private BlockPos scaffoldOre;
    private int scaffoldStartedTick;
    private int scaffoldNextPlaceTick;
    private int scaffoldAttempts;
    private List<BlockPos> plannedObstacleCache = java.util.Collections.emptyList();
    private List<BlockPos> plannedObstacleCachePath = java.util.Collections.emptyList();
    private int plannedObstacleCacheTick = Integer.MIN_VALUE;
    private int plannedObstacleCacheIndex = -1;

    public AutoMiner(ModuleManager modules, OreVisualizer oreVisualizer) {
        this.modules = modules;
        this.oreVisualizer = oreVisualizer;
        reloadTargets();
    }

    public void reloadTargets() {
        delay = 0;
        manualPause = 0;
        minedCounts.clear();
        pathRetryDelay = 0;
        blockedTargetsUntil.clear();
        rejectedTargetsUntil.clear();
        rejectedObstaclesUntil.clear();
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
        if (mc.currentScreen != null) {
            stopAutomatedWork(true);
            return;
        }
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
        if (delay-- > 0) return;
        if (continueScaffoldAssist()) return;
        if (continueClearingObstacle()) return;
        if (continueMiningTarget()) return;
        if (tryMineCurrentOreDirectly()) return;
        if (ModConfig.mineScaffoldAssist && beginScaffoldAssist(currentOre, currentOreType)) return;
        if (hasActiveRoute()) {
            followPathToOre();
            return;
        }
        if (pathRetryDelay > 0) {
            pathRetryDelay--;
            mc.player.motionX = planningMotion(mc.player.motionX);
            mc.player.motionZ = planningMotion(mc.player.motionZ);
            return;
        }
        if (routeEndedBeforeMining(currentOre, pathIndex, path.size())) {
            prepareAndMineCurrentOre();
            return;
        }
        List<OreVisualizer.CachedOre> candidates = reusePathCandidateSnapshot(pathCandidateBatch)
            ? pathCandidateBatch : cachedMineCandidates();
        if (!targetLabels.isEmpty() && !containsLabeledCandidate(candidates, targetLabels)) {
            clearTargetLabels();
        }
        MineTarget visible = findNearestReachable(candidates);
        if (visible != null) {
            ensureTargetLabels(visible.pos, visible.type, candidates);
            mine(visible);
            return;
        }
        if (ModConfig.mineScaffoldAssist && beginScaffoldAssist(candidates)) return;
        followPathToOre(candidates);
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
            minedCounts.clear();
            lastMinedOre = null;
            lastMinedType = null;
            clearTargetLabels();
            clearPath();
            miningPlayerFeet = null;
            miningEyes = null;
            miningReachDistance = 0.0;
            clearScaffoldAssist();
            clearPendingCompletion();
        }
    }

    private void updateMinedCount() {
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
                    iterator.remove();
                }
                continue;
            }
            OreType remainingType = OreType.fromBlock(
                mc.world.getBlockState(pending.pos).getBlock());
            if (remainingType == pending.type) {
                if (completionRolledBack(pending.missingTicks)) {
                    iterator.remove();
                    oreVisualizer.restoreMarker(pending.pos, pending.type);
                    rejectedTargetsUntil.put(pending.pos,
                        mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
                    if (mc.playerController != null) mc.playerController.resetBlockRemoving();
                    if (completionOwnsCurrentWork(pending.pos, currentOre)) clearPath();
                    continue;
                }
                pending.missingTicks = 0;
                if (completionConfirmationExpired(mc.player.ticksExisted, pending.untilTick)) {
                    iterator.remove();
                }
                continue;
            }
            pending.reservesQuota = pendingQuotaReservationAfter(
                pending.reservesQuota, PendingQuotaEvent.BLOCK_MISSING);
            pending.missingTicks++;
            if (!completionAbsenceConfirmed(true, pending.missingTicks)) continue;
            iterator.remove();
            minedCounts.put(pending.type, minedCount(pending.type) + 1);
            lastMinedOre = pending.pos;
            lastMinedType = pending.type;
            oreVisualizer.removeMarker(pending.pos);
            targetLabels.remove(pending.pos);
            if (targetLabels.isEmpty()) targetLabelType = null;
            if (completionOwnsCurrentWork(pending.pos, currentOre)) clearPath();
        }
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
        selectBestPickaxe(target.pos);
        if (!target.pos.equals(miningPos) || target.type != miningType) {
            miningAttempts = 0;
            miningAttemptBudget = destructionAttemptBudget(
                mc.world.getBlockState(target.pos).getPlayerRelativeBlockHardness(
                    mc.player, mc.world, target.pos));
            miningDeadlineTick = destructionDeadlineTick(mc.player.ticksExisted,
                miningAttemptBudget, ModConfig.mineDelayTicks);
        }
        if (destructionWorkExhausted(miningAttempts, miningAttemptBudget,
                mc.player.ticksExisted, miningDeadlineTick)) {
            rejectMiningTarget(target.pos);
            return;
        }
        face(target.pos);
        mc.playerController.onPlayerDamageBlock(target.pos, target.side);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        miningPos = target.pos;
        miningType = target.type;
        miningAttempts++;
        rememberPendingCompletion(target.pos, target.type);
        if (!hasActiveRoute()) {
            currentOre = target.pos;
            currentOreType = target.type;
        }
        delay = ModConfig.mineDelayTicks;
    }

    private boolean continueMiningTarget() {
        if (miningPos == null || miningType == null) return false;
        if (OreType.fromBlock(mc.world.getBlockState(miningPos).getBlock()) != miningType) {
            clearMiningTarget();
            return true;
        }
        MineTarget target = visibleExactTarget(miningPos);
        if (target == null) {
            releasePendingQuotaReservation(miningPos);
            miningPos = null;
            miningType = null;
            return false;
        }
        mine(target);
        return true;
    }

    private void rejectMiningTarget(BlockPos target) {
        rejectedTargetsUntil.put(target.toImmutable(),
            mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
        forgetPendingCompletion(target);
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
        if (currentOre == null || targetType(currentOre) == null
                || !stableMiningPosition(miningPlayerFeet, currentOre)
                || !miningWorkAreaReady(isPassable(miningPlayerFeet),
                    isPassable(miningPlayerFeet.up()), hasSolidSupport(miningPlayerFeet))) {
            abandonCurrentRoute();
            return;
        }
        BlockPos faceNeighbor = miningFaceNeighbor(miningPlayerFeet, currentOre);
        if (faceNeighbor == null) {
            abandonCurrentRoute();
            return;
        }
        if (!isPassable(faceNeighbor)) {
            if (!clearCorridorCell(faceNeighbor, faceNeighbor)) {
                abandonCurrentRoute();
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        MineTarget routedTarget = visibleExactTarget(currentOre);
        if (routedTarget != null) {
            mine(routedTarget);
        } else {
            abandonCurrentRoute();
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
                || !mc.player.onGround || mc.player.capabilities.isFlying
                || mc.player.isInWater() || mc.player.isInLava()) return false;
        if (!scaffoldCandidate(miningPlayerFeet, ore, true, isPassable(miningPlayerFeet),
                isPassable(miningPlayerFeet.up()), isPassable(miningPlayerFeet.up(2)),
                hasSolidSupport(miningPlayerFeet))
                || !scaffoldRaisesIntoReach(miningEyes, ore, miningReachDistance)) return false;
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
            if (playerReachedScaffoldLevel(mc.player.getEntityBoundingBox().minY, scaffoldPos.getY())) {
                MineTarget target = visibleExactTarget(scaffoldOre);
                if (target == null) {
                    failScaffoldAssist();
                    return false;
                }
                clearScaffoldAssist();
                mine(target);
                return true;
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
        return dx + dz <= 1 && ore.getY() > feet.getY() + 1;
    }

    static boolean scaffoldRaisesIntoReach(Vec3d eyes, BlockPos ore, double reach) {
        return eyes != null && ore != null && reach > 0.0
            && !withinMiningReach(eyes, ore, reach)
            && withinMiningReach(new Vec3d(eyes.x, eyes.y + 1.0, eyes.z), ore, reach);
    }

    static boolean readyToPlaceScaffold(double playerFeetY, int scaffoldY) {
        return playerFeetY >= scaffoldY + 1.0;
    }

    static boolean playerReachedScaffoldLevel(double playerFeetY, int scaffoldY) {
        return playerFeetY >= scaffoldY + 0.95;
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
        return block != null && stableScaffoldBlock(block.getDefaultState().getMaterial().isSolid(),
            block instanceof BlockFalling) && block.canPlaceBlockAt(mc.world, pos);
    }

    static boolean stableScaffoldBlock(boolean solid, boolean falling) {
        return solid && !falling;
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
            blockedTargetsUntil.put(scaffoldOre.toImmutable(),
                mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
        }
        clearScaffoldAssist();
        clearPath();
    }

    private boolean continueClearingObstacle() {
        if (clearingPos == null) return false;
        if (isPassable(clearingPos)) {
            clearClearingTarget();
            return false;
        }
        if (destructionWorkExhausted(clearingAttempts, clearingAttemptBudget,
                mc.player.ticksExisted, clearingDeadlineTick)) {
            rejectedObstaclesUntil.put(clearingPos.toImmutable(),
                mc.player.ticksExisted + DESTRUCTION_RETRY_TICKS);
            mc.playerController.resetBlockRemoving();
            clearPath();
            delay = 2;
            return true;
        }
        if (!damageCorridorBlock(clearingPos)) {
            clearClearingTarget();
            abandonCurrentRoute();
            return true;
        }
        delay = ModConfig.mineDelayTicks;
        return true;
    }

    private boolean hasActiveRoute() {
        return routeOwnsTarget(currentOre, pathIndex, path.size());
    }

    static boolean routeOwnsTarget(BlockPos currentOre, int pathIndex, int pathSize) {
        return currentOre != null && pathIndex >= 0 && pathIndex < pathSize;
    }

    private void followPathToOre() {
        followPathToOre(null);
    }

    private void followPathToOre(List<OreVisualizer.CachedOre> candidates) {
        if (currentOre == null || targetType(currentOre) == null || pathIndex >= path.size()) {
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
                pathSnapshotRefreshRequested = false;
                return;
            }
            pathRetryDelay = 0;
            activatePathTarget(target);
            if (path.isEmpty()) {
                delay = 3;
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
        if (!routeTransitionContains(actualFeet, from, next)) {
            stopRouteMotion();
            abandonCurrentRoute();
            return;
        }
        BlockPos jumpStart = actualFeet.equals(next) ? from : actualFeet;
        if (next.getY() > jumpStart.getY() && !isPassable(jumpStart.up(2))) {
            stopRouteMotion();
            if (!clearCorridorCell(jumpStart.up(2), jumpStart.up(2))) {
                abandonCurrentRoute();
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        if (!isStandable(next)) {
            if (!clearBlockingObstacle(next)) {
                abandonCurrentRoute();
                return;
            }
            delay = ModConfig.mineDelayTicks;
            return;
        }
        double dx = next.getX() + 0.5 - mc.player.posX;
        double dz = next.getZ() + 0.5 - mc.player.posZ;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq > 16.0) {
            abandonCurrentRoute();
            return;
        }
        double verticalDistance = next.getY() - navigationFeetY;
        if (reachedPathNode(actualFeet, next, distanceSq, verticalDistance)) {
            pathIndex++;
            resetRouteProgress();
            if (pathIndex < path.size()) {
                followPathToOre(candidates);
            } else {
                stopRouteMotion();
            }
            return;
        }
        double nodeDistanceSq = routeNodeDistanceSq(distanceSq, verticalDistance);
        if (routeProgressed(lastRouteDistanceSq, nodeDistanceSq)) {
            lastRouteDistanceSq = nodeDistanceSq;
            stalledRouteTicks = 0;
        } else if (++stalledRouteTicks >= MAX_STALLED_ROUTE_TICKS) {
            abandonCurrentRoute();
            return;
        }
        if (waitingForAscendingClearance(next.getY(), physicalFeetY)) {
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
                if (!clearRouteStepObstacle(motionX, motionZ)) abandonCurrentRoute();
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

    static boolean routeProgressed(double previousDistanceSq, double currentDistanceSq) {
        return currentDistanceSq + ROUTE_PROGRESS_EPSILON < previousDistanceSq;
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
        return mc.world.getBlockState(surface).getMaterial().blocksMovement();
    }

    static boolean reachedPathNode(BlockPos actualFeet, BlockPos expectedFeet,
            double horizontalDistanceSq, double verticalDistance) {
        return actualFeet != null && actualFeet.equals(expectedFeet)
            && horizontalDistanceSq < ROUTE_NODE_REACH_DISTANCE_SQ
            && verticalDistance <= 0.05 && verticalDistance > -0.35;
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

    static boolean waitingForAscendingClearance(int nextY, double feetY) {
        return feetY + 0.01 < nextY;
    }

    private void stopRouteMotion() {
        mc.player.motionX = 0.0;
        mc.player.motionZ = 0.0;
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
            if (!isPassable(cell) && clearCorridorCell(cell, cell)) {
                delay = ModConfig.mineDelayTicks;
                return true;
            }
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
        if (pathCandidateBatch.isEmpty()) {
            pathCandidateBatch = snapshotPathCandidates(candidates, MAX_CACHED_TARGETS);
            pathCandidateOffset = 0;
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
                            clearTargetLabels();
                            resetPathCandidateBatch();
                            pathSnapshotRefreshRequested = true;
                            return null;
                        }
                    }
                }
            }
            if (pathCandidateOffset % PATH_CANDIDATE_BATCH_SIZE == 0
                    && pendingPathTarget != null) {
                PathTarget best = pendingPathTarget;
                if (best != null) ensureTargetLabels(best.ore, best.type, pathCandidateBatch);
                resetPathCandidateBatch();
                return best;
            }
            return null;
        }
        PathTarget best = pendingPathTarget;
        if (best != null) ensureTargetLabels(best.ore, best.type, pathCandidateBatch);
        Set<BlockPos> failedTargets = failedPathTargetsToBlock(pendingFailedPathTargets, best != null);
        if (!failedTargets.isEmpty()) {
            blockTargets(failedTargets, mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
            clearTargetLabels();
        }
        resetPathCandidateBatch();
        return best;
    }

    static List<OreVisualizer.CachedOre> snapshotPathCandidates(
            List<OreVisualizer.CachedOre> candidates, int limit) {
        int size = Math.min(candidates.size(), Math.max(0, limit));
        return new ArrayList<>(candidates.subList(0, size));
    }

    static int pathSearchSliceBudget(int visited) {
        return Math.max(0, Math.min(PATH_NODES_PER_TICK, MAX_PATH_NODES - Math.max(0, visited)));
    }

    static int pathSearchRetryDelay(int nextCandidateOffset, boolean searchPending) {
        if (searchPending) return 0;
        return nextCandidateOffset > 0 ? 1 : PATH_RETRY_TICKS;
    }

    static boolean pathSnapshotRefreshNeeded(int failedCandidates, boolean routeFound) {
        return !routeFound && failedCandidates >= MAX_FAILED_CANDIDATES_PER_SNAPSHOT;
    }

    static boolean reusePathCandidateSnapshot(List<OreVisualizer.CachedOre> snapshot) {
        return snapshot != null && !snapshot.isEmpty();
    }

    static boolean temporarilyBlocked(BlockPos candidate, Map<BlockPos, Integer> blockedUntil,
            int currentTick) {
        Integer until = blockedUntil == null ? null : blockedUntil.get(candidate);
        return until != null && currentTick < until;
    }

    static Set<BlockPos> failedPathTargetsToBlock(Set<BlockPos> failedTargets, boolean routeFound) {
        if (routeFound || failedTargets == null || failedTargets.isEmpty()) {
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
        if (result.complete) pendingPathSearch = null;
        return result;
    }

    private PathSearchResult advancePathSearch(PathSearch search, int nodeBudget) {
        if (search.goalSet.contains(search.start)) {
            return PathSearchResult.complete(new PathRoute(java.util.Collections.emptyList(), 0));
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
                return PathSearchResult.complete(new PathRoute(
                    reconstruct(search.previous, pos), node.cost));
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
        return search.queue.isEmpty() || search.visited >= MAX_PATH_NODES
            ? PathSearchResult.complete(null) : PathSearchResult.pending();
    }

    private void addPathNeighbor(PriorityQueue<PathNode> queue, Map<BlockPos, BlockPos> previous,
            Map<BlockPos, Integer> costs, Map<BlockPos, Integer> traversalCosts,
            Map<BlockPos, Integer> jumpClearanceCosts, BlockPos start, List<BlockPos> goals,
            double maxDistanceSq, BlockPos from, int currentCost, BlockPos next, int verticalPenalty) {
        if (start.distanceSq(next) > maxDistanceSq) return;
        Integer known = costs.get(next);
        if (knownPathCostCannotImprove(known, currentCost, verticalPenalty)) return;
        int stepCost = cachedPathCost(traversalCosts, next, this::traversalCost);
        if (stepCost < 0) return;
        int jumpExcavation = next.getY() > from.getY()
            ? cachedPathCost(jumpClearanceCosts, from.up(2),
                this::jumpClearanceCostAt)
            : 0;
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
            BlockPos stand = standPos(candidate);
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

    private List<BlockPos> reconstruct(Map<BlockPos, BlockPos> previous, BlockPos goal) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos cursor = goal; cursor != null; cursor = previous.get(cursor)) result.add(0, cursor);
        if (!result.isEmpty()) result.remove(0);
        return result;
    }

    private BlockPos standPos(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isStandable(BlockPos feet) {
        if (mc.world.getBlockState(feet.down()).getMaterial().isReplaceable()) return false;
        AxisAlignedBB box = new AxisAlignedBB(feet.getX() + 0.1, feet.getY(), feet.getZ() + 0.1,
            feet.getX() + 0.9, feet.getY() + 1.8, feet.getZ() + 0.9);
        return mc.world.getCollisionBoxes(mc.player, box).isEmpty();
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

    private int jumpClearanceCostAt(BlockPos pos) {
        boolean clear = isPassable(pos);
        return clear ? 0 : jumpClearanceCost(false, canClearForCorridor(pos));
    }

    private boolean hasSolidSupport(BlockPos feet) {
        IBlockState support = mc.world.getBlockState(feet.down());
        return !support.getMaterial().isReplaceable() && support.getMaterial().blocksMovement();
    }

    private boolean isPassable(BlockPos pos) {
        return mc.world.getBlockState(pos).getMaterial().isReplaceable();
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
        if (!isPassable(next.up()) && clearCorridorCell(next.up(), next)) return true;
        return !isPassable(next) && clearCorridorCell(next, next);
    }

    private boolean clearCorridorCell(BlockPos desired, BlockPos permittedLower) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d point = new Vec3d(desired.getX() + 0.5, desired.getY() + 0.5, desired.getZ() + 0.5);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, point, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos obstacle = hit.getBlockPos();
        if (!corridorObstacleAllowed(obstacle, desired, permittedLower, currentCorridorCells())) return false;
        if (temporarilyBlocked(obstacle, rejectedObstaclesUntil, mc.player.ticksExisted)) return false;
        OreType ore = targetType(obstacle);
        if (ore != null) {
            if (!withinMiningReach(eyes, obstacle, miningReach())) return false;
            mine(new MineTarget(obstacle.toImmutable(), ore, hit.sideHit));
            return true;
        }
        if (!obstacle.equals(clearingPos)) {
            selectBestPickaxe(obstacle);
            clearingPos = obstacle.toImmutable();
            clearingAttempts = 0;
            clearingAttemptBudget = destructionAttemptBudget(
                mc.world.getBlockState(obstacle).getPlayerRelativeBlockHardness(
                    mc.player, mc.world, obstacle));
            clearingDeadlineTick = destructionDeadlineTick(mc.player.ticksExisted,
                clearingAttemptBudget, ModConfig.mineDelayTicks);
        }
        if (!damageCorridorBlock(obstacle)) {
            clearClearingTarget();
            return false;
        }
        return true;
    }

    private List<BlockPos> currentCorridorCells() {
        if (path.isEmpty()) return java.util.Collections.emptyList();
        int start = Math.max(0, pathIndex - 1);
        BlockPos routeStart = start > 0 ? path.get(start - 1) : miningPlayerFeet;
        return corridorCells(path, start, ROUTE_RENDER_LIMIT, routeStart);
    }

    static boolean corridorObstacleAllowed(BlockPos obstacle, BlockPos desired,
            BlockPos permittedLower, List<BlockPos> corridor) {
        return obstacle != null && (obstacle.equals(desired) || obstacle.equals(permittedLower)
            || corridor != null && corridor.contains(obstacle));
    }

    private boolean damageCorridorBlock(BlockPos obstacle) {
        if (!isBreakableBlock(obstacle)) return false;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        if (!withinMiningReach(eyes, obstacle, miningReach())) return false;
        Vec3d point = blockCenter(obstacle);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, point, false, true, false);
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

    private void abandonCurrentRoute() {
        if (currentOre != null) {
            blockedTargetsUntil.put(currentOre.toImmutable(),
                mc.player.ticksExisted + FAILED_ROUTE_RETRY_TICKS);
        }
        clearPath();
        pathRetryDelay = 0;
        delay = 2;
    }

    private void activatePathTarget(PathTarget target) {
        resetPathCandidateBatch();
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

    private void pruneBlockedTargets(int currentTick) {
        blockedTargetsUntil.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    private void pruneRejectedBlocks(int currentTick) {
        rejectedTargetsUntil.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
        rejectedObstaclesUntil.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    private boolean targetTemporarilyUnavailable(BlockPos target) {
        int currentTick = mc.player.ticksExisted;
        return temporarilyBlocked(target, blockedTargetsUntil, currentTick)
            || temporarilyBlocked(target, rejectedTargetsUntil, currentTick);
    }

    private void blockTargets(Iterable<BlockPos> targets, int untilTick) {
        for (BlockPos target : targets) {
            blockedTargetsUntil.put(target.toImmutable(), untilTick);
        }
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
        invalidatePlannedObstacleCache();
    }

    private void invalidatePlannedObstacleCache() {
        plannedObstacleCache = java.util.Collections.emptyList();
        plannedObstacleCachePath = java.util.Collections.emptyList();
        plannedObstacleCacheTick = Integer.MIN_VALUE;
        plannedObstacleCacheIndex = -1;
    }

    private void stopAutomatedWork(boolean stopMotion) {
        if (stopMotion && mc.player != null) stopRouteMotion();
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        clearPath();
        clearTargetLabels();
    }

    private void clearClearingTarget() {
        clearingPos = null;
        clearingAttempts = 0;
        clearingAttemptBudget = 0;
        clearingDeadlineTick = 0;
    }

    private void clearMiningTarget() {
        miningPos = null;
        miningType = null;
        miningAttempts = 0;
        miningAttemptBudget = 0;
        miningDeadlineTick = 0;
    }

    private void clearPendingCompletion() {
        pendingCompletions.clear();
    }

    private void rememberPendingCompletion(BlockPos pos, OreType type) {
        int untilTick = mc.player.ticksExisted + COMPLETION_CONFIRM_TICKS;
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world && pending.pos.equals(pos) && pending.type == type) {
                pending.untilTick = untilTick;
                pending.reservesQuota = pendingQuotaReservationAfter(
                    pending.reservesQuota, PendingQuotaEvent.RETRY);
                return;
            }
        }
        while (pendingCompletions.size() >= MAX_PENDING_COMPLETIONS) {
            pendingCompletions.removeFirst();
        }
        pendingCompletions.addLast(new PendingCompletion(
            mc.world, pos.toImmutable(), type, untilTick));
    }

    private void forgetPendingCompletion(BlockPos pos) {
        pendingCompletions.removeIf(pending ->
            pending.world == mc.world && pending.pos.equals(pos));
    }

    private void releasePendingQuotaReservation(BlockPos pos) {
        for (PendingCompletion pending : pendingCompletions) {
            if (pending.world == mc.world && pending.pos.equals(pos)) {
                pending.reservesQuota = pendingQuotaReservationAfter(
                    pending.reservesQuota, PendingQuotaEvent.VISIBILITY_LOST);
            }
        }
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

    static boolean completionRolledBack(int consecutiveMissingTicks) {
        return consecutiveMissingTicks > 0;
    }

    static boolean pendingQuotaReservationAfter(boolean currentlyReserved, PendingQuotaEvent event) {
        if (event == PendingQuotaEvent.VISIBILITY_LOST) return false;
        return event == PendingQuotaEvent.RETRY
            || event == PendingQuotaEvent.BLOCK_MISSING
            || currentlyReserved;
    }

    enum PendingQuotaEvent {
        RETRY,
        VISIBILITY_LOST,
        BLOCK_MISSING
    }

    static boolean completionOwnsCurrentWork(BlockPos completed, BlockPos current) {
        return completed != null && completed.equals(current);
    }

    private void resetPathCandidateBatch() {
        pathCandidateOffset = 0;
        pathCandidateBatch = java.util.Collections.emptyList();
        pendingPathTarget = null;
        pendingPathTargetScore = Integer.MAX_VALUE;
        pendingPathTargetSameVein = false;
        pendingPathTargetLabel = Integer.MAX_VALUE;
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
        EnumMap<OreType, Boolean> availableTypes = new EnumMap<>(OreType.class);
        for (OreType type : OreType.values()) availableTypes.put(type, !quotaReached(type));
        return prioritizeCurrentVein(oreVisualizer.cachedMineOres(
            ModConfig.minePathRange, MAX_CACHED_TARGETS,
            type -> availableTypes.getOrDefault(type, false),
            pos -> !targetTemporarilyUnavailable(pos)));
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
        if (!targetLabels.isEmpty()) return labeledTarget;
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
        MineTarget best = null;
        int bestLabel = Integer.MAX_VALUE;
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            int label = targetLabels.getOrDefault(candidate.pos(), Integer.MAX_VALUE);
            if (label == Integer.MAX_VALUE || label >= bestLabel) continue;
            if (targetTemporarilyUnavailable(candidate.pos())) continue;
            OreType actual = OreType.fromBlock(mc.world.getBlockState(candidate.pos()).getBlock());
            if (!cachedOreStillPresent(candidate.type(), actual)) {
                oreVisualizer.reconcileMarker(candidate.pos(), actual);
                continue;
            }
            if (!candidateTypeAvailable(
                    ModConfig.isMineOreEnabled(candidate.type()), quotaReached(candidate.type()))) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) {
                best = visible;
                bestLabel = label;
            }
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return best;
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
    }

    private void pruneTargetLabels() {
        if (targetLabels.isEmpty()) return;
        targetLabels.entrySet().removeIf(entry -> !labelOreStillPresent(
            targetLabelType, OreType.fromBlock(mc.world.getBlockState(entry.getKey()).getBlock()),
            targetLabelType != null && ModConfig.isMineOreEnabled(targetLabelType)));
        if (targetLabels.isEmpty()) targetLabelType = null;
    }

    static boolean labelOreStillPresent(OreType expected, OreType actual, boolean configured) {
        return configured && expected != null && expected == actual;
    }

    private void clearTargetLabels() {
        targetLabels.clear();
        targetLabelType = null;
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
        return new MineTarget(pos.toImmutable(), type, hit.sideHit);
    }

    private RayTraceResult rayTraceTarget(Vec3d eyes, BlockPos pos, OreType type,
            boolean allowLabeledBlocker) {
        RayTraceResult labeledBlocker = null;
        for (Vec3d sample : blockVisibilitySamples(pos)) {
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
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double near = 0.001;
        double far = 0.999;
        List<Vec3d> samples = new ArrayList<>(7);
        samples.add(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
        samples.add(new Vec3d(x + near, y + 0.5, z + 0.5));
        samples.add(new Vec3d(x + far, y + 0.5, z + 0.5));
        samples.add(new Vec3d(x + 0.5, y + near, z + 0.5));
        samples.add(new Vec3d(x + 0.5, y + far, z + 0.5));
        samples.add(new Vec3d(x + 0.5, y + 0.5, z + near));
        samples.add(new Vec3d(x + 0.5, y + 0.5, z + far));
        return samples;
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

    private static final class PendingCompletion {
        private final World world;
        private final BlockPos pos;
        private final OreType type;
        private int untilTick;
        private int missingTicks;
        private boolean reservesQuota = true;

        private PendingCompletion(World world, BlockPos pos, OreType type, int untilTick) {
            this.world = world;
            this.pos = pos;
            this.type = type;
            this.untilTick = untilTick;
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
        private int visited;

        private PathSearch(BlockPos ore, BlockPos start, List<BlockPos> goals, double maxDistanceSq) {
            this.ore = ore.toImmutable();
            this.start = start.toImmutable();
            this.goals = goals;
            this.goalSet = new HashSet<>(goals);
            this.maxDistanceSq = maxDistanceSq;
            queue.add(new PathNode(start, 0, pathPriority(0, start, goals)));
            previous.put(start, null);
            costs.put(start, 0);
        }

        private boolean matches(BlockPos targetOre, BlockPos playerStart, double rangeSq) {
            return ore.equals(targetOre) && start.equals(playerStart)
                && Double.compare(maxDistanceSq, rangeSq) == 0;
        }
    }

    private static final class PathSearchResult {
        private final boolean complete;
        private final PathRoute route;

        private PathSearchResult(boolean complete, PathRoute route) {
            this.complete = complete;
            this.route = route;
        }

        private static PathSearchResult pending() {
            return new PathSearchResult(false, null);
        }

        private static PathSearchResult complete(PathRoute route) {
            return new PathSearchResult(true, route);
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

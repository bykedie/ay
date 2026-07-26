package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class AutoMiner {
    private static final int MAX_PATH_NODES = 1600;
    private static final int MAX_PATH_TARGETS = 6;
    private static final int MAX_CACHED_TARGETS = 96;
    private static final int PATH_RETRY_TICKS = 20;
    private static final int FAILED_ROUTE_RETRY_TICKS = 100;
    private static final double ROUTE_SPEED = 0.18;
    private static final int NEARBY_ROUTE_CHECK_TICKS = 5;
    private static final double NEARBY_ROUTE_RANGE = 6.0;
    private static final int MAX_NEARBY_PATH_TARGETS = 2;
    private static final int MAX_VISIBLE_TARGETS = 16;
    private static final int MAX_STALLED_ROUTE_TICKS = 30;
    private static final double ROUTE_PROGRESS_EPSILON = 0.0025;
    private static final int ROUTE_RENDER_LIMIT = 220;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final OreVisualizer oreVisualizer;
    private final EnumMap<OreType, Integer> minedCounts = new EnumMap<>(OreType.class);
    private List<BlockPos> path = java.util.Collections.emptyList();
    private BlockPos currentOre;
    private OreType currentOreType;
    private BlockPos miningPos;
    private OreType miningType;
    private BlockPos clearingPos;
    private BlockPos lastMinedOre;
    private OreType lastMinedType;
    private int pathIndex;
    private int delay;
    private int manualPause;
    private int pathRetryDelay;
    private int pathCandidateOffset;
    private BlockPos failedRouteOre;
    private int failedRouteRetryDelay;
    private int nearbyRouteCheckDelay;
    private boolean observedEnabled;
    private double lastRouteDistanceSq = Double.POSITIVE_INFINITY;
    private int stalledRouteTicks;

    public AutoMiner(ModuleManager modules, OreVisualizer oreVisualizer) {
        this.modules = modules;
        this.oreVisualizer = oreVisualizer;
        reloadTargets();
    }

    public void reloadTargets() {
        minedCounts.clear();
        pathRetryDelay = 0;
        pathCandidateOffset = 0;
        failedRouteOre = null;
        failedRouteRetryDelay = 0;
        nearbyRouteCheckDelay = 0;
        clearPath();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!modules.isEnabled(ModuleId.AUTO_MINE)) {
            observedEnabled = false;
            return;
        }
        if (!observedEnabled) {
            reloadTargets();
            observedEnabled = true;
        }
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
        updateFailedRouteCooldown();
        updateMinedCount();
        if (allFiniteQuotasReached()) {
            modules.setEnabled(ModuleId.AUTO_MINE, false);
            clearPath();
            return;
        }
        if (manualMovementRequested()) manualPause = Math.max(manualPause, ModConfig.mineManualPauseTicks);
        if (manualPause > 0) {
            manualPause--;
            return;
        }
        if (delay-- > 0) return;
        if (continueClearingObstacle()) return;
        if (continueMiningTarget()) return;
        if (hasActiveRoute()) {
            if (nearbyRouteCheckDelay-- <= 0) {
                nearbyRouteCheckDelay = NEARBY_ROUTE_CHECK_TICKS;
                List<OreVisualizer.CachedOre> nearby = oreVisualizer.cachedMineOres(
                    NEARBY_ROUTE_RANGE, MAX_CACHED_TARGETS);
                MineTarget visible = findNearestReachable(nearby);
                if (visible != null && !visible.pos.equals(currentOre)) {
                    mine(visible);
                    return;
                }
                PathTarget closer = findNearbyPathTarget(nearby);
                if (closer != null) {
                    activatePathTarget(closer);
                    if (path.isEmpty()) return;
                }
            }
            followPathToOre();
            return;
        }
        if (pathRetryDelay > 0) {
            pathRetryDelay--;
            if (nearbyRouteCheckDelay-- <= 0) {
                nearbyRouteCheckDelay = NEARBY_ROUTE_CHECK_TICKS;
                List<OreVisualizer.CachedOre> nearby = oreVisualizer.cachedMineOres(
                    NEARBY_ROUTE_RANGE, MAX_VISIBLE_TARGETS);
                MineTarget visible = findNearestReachable(nearby);
                if (visible != null) {
                    mine(visible);
                    return;
                }
            }
            return;
        }
        List<OreVisualizer.CachedOre> candidates = oreVisualizer.cachedMineOres(
            ModConfig.minePathRange, MAX_CACHED_TARGETS);
        MineTarget visible = findNearestReachable(candidates);
        if (visible != null) {
            mine(visible);
            return;
        }
        if (routeEndedBeforeMining(currentOre, pathIndex, path.size())) {
            abandonCurrentRoute();
            return;
        }
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
            pathCandidateOffset = 0;
            failedRouteOre = null;
            failedRouteRetryDelay = 0;
            nearbyRouteCheckDelay = 0;
            minedCounts.clear();
            lastMinedOre = null;
            lastMinedType = null;
            clearPath();
        }
    }

    private void updateMinedCount() {
        if (miningPos == null) return;
        OreType remainingType = OreType.fromBlock(mc.world.getBlockState(miningPos).getBlock());
        if (remainingType == miningType) return;
        BlockPos mined = miningPos;
        OreType type = miningType;
        if (type != null) minedCounts.put(type, minedCount(type) + 1);
        lastMinedOre = mined;
        lastMinedType = type;
        miningPos = null;
        miningType = null;
        oreVisualizer.removeMarker(mined);
        if (mined.equals(currentOre)) clearPath();
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
        return target > 0 && minedCount(type) >= target;
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
        selectBestPickaxe(mc.world.getBlockState(target.pos));
        face(target.pos);
        mc.playerController.onPlayerDamageBlock(target.pos, target.side);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        miningPos = target.pos;
        miningType = target.type;
        if (!hasActiveRoute()) {
            currentOre = target.pos;
            currentOreType = target.type;
        }
        delay = ModConfig.mineDelayTicks;
    }

    private boolean continueMiningTarget() {
        if (miningPos == null || miningType == null) return false;
        if (OreType.fromBlock(mc.world.getBlockState(miningPos).getBlock()) != miningType) return false;
        MineTarget target = visibleTarget(miningPos);
        if (target == null) {
            miningPos = null;
            miningType = null;
            return false;
        }
        mine(target);
        return true;
    }

    private boolean continueClearingObstacle() {
        if (clearingPos == null) return false;
        if (isPassable(clearingPos)) {
            clearingPos = null;
            return false;
        }
        if (!damageCorridorBlock(clearingPos)) {
            clearingPos = null;
            abandonCurrentRoute();
            return true;
        }
        delay = ModConfig.mineDelayTicks;
        return true;
    }

    private boolean hasActiveRoute() {
        return currentOre != null && pathIndex < path.size();
    }

    private void followPathToOre() {
        followPathToOre(null);
    }

    private void followPathToOre(List<OreVisualizer.CachedOre> candidates) {
        if (currentOre == null || targetType(currentOre) == null || pathIndex >= path.size()) {
            List<OreVisualizer.CachedOre> available = candidates == null
                ? oreVisualizer.cachedMineOres(ModConfig.minePathRange, MAX_CACHED_TARGETS)
                : candidates;
            PathTarget target = findNearestPathTarget(available);
            if (target == null) {
                pathRetryDelay = PATH_RETRY_TICKS;
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
        BlockPos from = pathIndex == 0
            ? standPos(new BlockPos(mc.player.posX, mc.player.getEntityBoundingBox().minY, mc.player.posZ))
            : path.get(pathIndex - 1);
        if (next.getY() > from.getY() && !isPassable(from.up(2))) {
            if (!clearCorridorCell(from.up(2), from.up(2))) {
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
        double verticalDistance = next.getY() - mc.player.getEntityBoundingBox().minY;
        if (reachedPathNode(distanceSq, verticalDistance)) {
            pathIndex++;
            resetRouteProgress();
            return;
        }
        if (routeProgressed(lastRouteDistanceSq, distanceSq)) {
            lastRouteDistanceSq = distanceSq;
            stalledRouteTicks = 0;
        } else if (++stalledRouteTicks >= MAX_STALLED_ROUTE_TICKS) {
            abandonCurrentRoute();
            return;
        }
        if (distanceSq > 0.0001) {
            double length = Math.sqrt(distanceSq);
            mc.player.motionX = routeMotion(mc.player.motionX, dx / length);
            mc.player.motionZ = routeMotion(mc.player.motionZ, dz / length);
        }
        if (next.getY() > MathHelper.floor(mc.player.getEntityBoundingBox().minY) && mc.player.onGround) {
            mc.player.jump();
        }
        delay = 0;
    }

    static double routeMotion(double current, double unitDirection) {
        return MathHelper.clamp(current * 0.5 + unitDirection * ROUTE_SPEED,
            -ROUTE_SPEED, ROUTE_SPEED);
    }

    static boolean routeProgressed(double previousDistanceSq, double currentDistanceSq) {
        return currentDistanceSq + ROUTE_PROGRESS_EPSILON < previousDistanceSq;
    }

    static boolean reachedPathNode(double horizontalDistanceSq, double verticalDistance) {
        return horizontalDistanceSq < 0.20 && Math.abs(verticalDistance) < 0.35;
    }

    private PathTarget findNearestPathTarget(List<OreVisualizer.CachedOre> candidates) {
        int attempts = 0;
        int eligible = 0;
        PathTarget best = null;
        int bestScore = Integer.MAX_VALUE;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (quotaReached(candidate.type())) continue;
            if (temporarilyBlocked(candidate.pos(), failedRouteOre, failedRouteRetryDelay)) continue;
            OreType currentType = targetType(candidate.pos());
            if (currentType != candidate.type()) {
                oreVisualizer.removeMarker(candidate.pos());
                continue;
            }
            if (eligible++ < pathCandidateOffset) continue;
            PathRoute route = pathToOre(candidate.pos());
            attempts++;
            if (route != null) {
                int score = pathTargetScore(route.cost, candidate.distanceSq(),
                    sameVein(lastMinedOre, candidate.pos(), lastMinedType, candidate.type()));
                if (score < bestScore) {
                    bestScore = score;
                    best = new PathTarget(candidate.pos(), candidate.type(), route.points);
                }
            }
            if (attempts >= MAX_PATH_TARGETS) break;
        }
        pathCandidateOffset = nextPathCandidateOffset(pathCandidateOffset, attempts,
            MAX_PATH_TARGETS, best != null);
        return best;
    }

    private PathTarget findNearbyPathTarget(List<OreVisualizer.CachedOre> candidates) {
        if (currentOre == null) return null;
        double currentDistanceSq = mc.player.getDistanceSqToCenter(currentOre);
        int attempts = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (candidate.pos().equals(currentOre) || quotaReached(candidate.type())) continue;
            if (!closerTargetWarrantsPreemption(candidate.distanceSq(), currentDistanceSq)) break;
            if (temporarilyBlocked(candidate.pos(), failedRouteOre, failedRouteRetryDelay)) continue;
            OreType currentType = targetType(candidate.pos());
            if (currentType != candidate.type()) {
                oreVisualizer.removeMarker(candidate.pos());
                continue;
            }
            PathRoute route = pathToOre(candidate.pos());
            attempts++;
            if (route != null) return new PathTarget(candidate.pos(), candidate.type(), route.points);
            if (attempts >= MAX_NEARBY_PATH_TARGETS) break;
        }
        return null;
    }

    static boolean closerTargetWarrantsPreemption(double nearbyDistanceSq, double currentDistanceSq) {
        return Math.sqrt(Math.max(0.0, nearbyDistanceSq)) + 2.0
            < Math.sqrt(Math.max(0.0, currentDistanceSq));
    }

    static int nextPathCandidateOffset(int currentOffset, int attempts, int batchSize, boolean found) {
        if (found || attempts < batchSize) return 0;
        return currentOffset + attempts;
    }

    static boolean temporarilyBlocked(BlockPos candidate, BlockPos failed, int retryDelay) {
        return retryDelay > 0 && failed != null && failed.equals(candidate);
    }

    static boolean routeEndedBeforeMining(BlockPos currentOre, int pathIndex, int pathSize) {
        return currentOre != null && pathIndex >= pathSize;
    }

    static int pathTargetScore(int routeCost, double distanceSq, boolean sameVein) {
        int blockDistance = (int) Math.ceil(Math.sqrt(Math.max(0.0, distanceSq)));
        return routeCost + blockDistance * 8 - (sameVein ? 2 : 0);
    }

    private PathRoute pathToOre(BlockPos ore) {
        BlockPos start = standPos(mc.player.getPosition());
        List<BlockPos> goals = standPositionsAround(ore);
        if (goals.isEmpty()) return null;
        Set<BlockPos> goalSet = new HashSet<>(goals);
        if (goalSet.contains(start)) return new PathRoute(java.util.Collections.emptyList(), 0);
        PriorityQueue<PathNode> queue = new PriorityQueue<>((left, right) -> {
            int priority = Integer.compare(left.priority, right.priority);
            return priority != 0 ? priority : Integer.compare(left.cost, right.cost);
        });
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> costs = new HashMap<>();
        queue.add(new PathNode(start, 0, pathPriority(0, start, goals)));
        previous.put(start, null);
        costs.put(start, 0);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_PATH_NODES) {
            PathNode node = queue.remove();
            BlockPos pos = node.pos;
            if (node.cost != costs.get(pos)) continue;
            if (goalSet.contains(pos)) return new PathRoute(reconstruct(previous, pos), node.cost);
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos next = standPos(pos.offset(facing).add(0, dy, 0));
                    addPathNeighbor(queue, previous, costs, start, goals, pos, node.cost, next,
                        Math.abs(dy) * 2);
                }
            }
            addPathNeighbor(queue, previous, costs, start, goals, pos, node.cost, pos.down(), 2);
        }
        return null;
    }

    private void addPathNeighbor(PriorityQueue<PathNode> queue, Map<BlockPos, BlockPos> previous,
            Map<BlockPos, Integer> costs, BlockPos start, List<BlockPos> goals, BlockPos from,
            int currentCost, BlockPos next, int verticalPenalty) {
        int stepCost = traversalCost(next);
        if (stepCost < 0) return;
        int jumpExcavation = next.getY() > from.getY()
            ? jumpClearanceCost(isPassable(from.up(2)), canClearForCorridor(from.up(2)))
            : 0;
        if (jumpExcavation < 0) return;
        if (start.distanceSq(next) > ModConfig.minePathRange * ModConfig.minePathRange) return;
        int nextCost = currentCost + stepCost + jumpExcavation * 5 + verticalPenalty;
        Integer known = costs.get(next);
        if (known != null && known <= nextCost) return;
        previous.put(next, from);
        costs.put(next, nextCost);
        queue.add(new PathNode(next, nextCost, pathPriority(nextCost, next, goals)));
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

    private List<BlockPos> standPositionsAround(BlockPos ore) {
        List<BlockPos> result = new ArrayList<>();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos base = ore.offset(facing);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos stand = standPos(base.add(0, dy, 0));
                if (canTraverse(stand)) result.add(stand);
            }
        }
        result.sort(java.util.Comparator.comparingDouble(pos -> mc.player.getDistanceSqToCenter(pos)));
        return result;
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
        int excavation = corridorExcavationCost(feetClear, headClear,
            canClearForCorridor(feet), canClearForCorridor(feet.up()));
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

    private boolean hasSolidSupport(BlockPos feet) {
        IBlockState support = mc.world.getBlockState(feet.down());
        return !support.getMaterial().isReplaceable() && support.getMaterial().blocksMovement();
    }

    private boolean isPassable(BlockPos pos) {
        return mc.world.getBlockState(pos).getMaterial().isReplaceable();
    }

    private boolean canClearForCorridor(BlockPos pos) {
        OreType ore = targetType(pos);
        return ore != null || isBreakableBlock(pos);
    }

    private boolean isBreakableBlock(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        if (state.getMaterial().isReplaceable()) return false;
        if (OreType.fromBlock(state.getBlock()) != null) return false;
        if (state.getBlock().getBlockHardness(state, mc.world, pos) < 0.0F) return false;
        return state.getBlock().canHarvestBlock(mc.world, pos, mc.player);
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
        if (!obstacle.equals(desired) && !obstacle.equals(permittedLower)) return false;
        OreType ore = targetType(obstacle);
        if (ore != null) {
            if (!withinMiningReach(eyes, obstacle, miningReach())) return false;
            mine(new MineTarget(obstacle.toImmutable(), ore, hit.sideHit));
            return true;
        }
        if (!damageCorridorBlock(obstacle)) return false;
        clearingPos = obstacle.toImmutable();
        return true;
    }

    private boolean damageCorridorBlock(BlockPos obstacle) {
        if (!isBreakableBlock(obstacle)) return false;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        if (!withinMiningReach(eyes, obstacle, miningReach())) return false;
        Vec3d point = blockCenter(obstacle);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, point, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK
                || !obstacle.equals(hit.getBlockPos())) return false;
        selectBestPickaxe(mc.world.getBlockState(obstacle));
        face(obstacle);
        mc.playerController.onPlayerDamageBlock(obstacle, hit.sideHit);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        return true;
    }

    private List<BlockPos> plannedObstacles() {
        List<BlockPos> result = new ArrayList<>();
        BlockPos routeStart = pathIndex == 0
            ? standPos(new BlockPos(mc.player.posX, mc.player.getEntityBoundingBox().minY, mc.player.posZ))
            : path.get(pathIndex - 1);
        for (BlockPos cell : corridorCells(path, pathIndex, ROUTE_RENDER_LIMIT, routeStart)) {
            if (!isPassable(cell)) result.add(cell);
        }
        return result;
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
        IBlockState state = mc.world.getBlockState(pos);
        OreType type = OreType.fromBlock(state.getBlock());
        if (type == null || !ModConfig.isMineOreEnabled(type) || quotaReached(type)) return null;
        return type;
    }

    private void abandonCurrentRoute() {
        if (currentOre != null) {
            failedRouteOre = currentOre.toImmutable();
            failedRouteRetryDelay = FAILED_ROUTE_RETRY_TICKS;
        }
        clearPath();
        pathRetryDelay = 0;
        pathCandidateOffset = 0;
        nearbyRouteCheckDelay = 0;
        delay = 2;
    }

    private void activatePathTarget(PathTarget target) {
        currentOre = target.ore;
        currentOreType = target.type;
        path = target.path;
        pathIndex = 0;
        nearbyRouteCheckDelay = NEARBY_ROUTE_CHECK_TICKS;
        resetRouteProgress();
    }

    private void resetRouteProgress() {
        lastRouteDistanceSq = Double.POSITIVE_INFINITY;
        stalledRouteTicks = 0;
    }

    private void updateFailedRouteCooldown() {
        if (failedRouteRetryDelay <= 0) return;
        failedRouteRetryDelay--;
        if (failedRouteRetryDelay == 0) failedRouteOre = null;
    }

    private void clearPath() {
        path = java.util.Collections.emptyList();
        currentOre = null;
        currentOreType = null;
        miningPos = null;
        miningType = null;
        clearingPos = null;
        pathIndex = 0;
        resetRouteProgress();
    }

    private void selectBestPickaxe(IBlockState state) {
        int bestSlot = mc.player.inventory.currentItem;
        float bestSpeed = toolSpeed(mc.player.inventory.getStackInSlot(bestSlot), state);
        for (int slot = 0; slot < 9; slot++) {
            float speed = toolSpeed(mc.player.inventory.getStackInSlot(slot), state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        if (bestSlot != mc.player.inventory.currentItem) {
            mc.player.inventory.currentItem = bestSlot;
            mc.playerController.updateController();
        }
    }

    private float toolSpeed(ItemStack stack, IBlockState state) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemPickaxe)) return -1.0F;
        return stack.getDestroySpeed(state);
    }

    private MineTarget findNearestReachable(List<OreVisualizer.CachedOre> candidates) {
        MineTarget veinTarget = findVisibleVeinTarget(candidates);
        if (veinTarget != null) return veinTarget;
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (quotaReached(candidate.type())) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return null;
    }

    private MineTarget findVisibleVeinTarget(List<OreVisualizer.CachedOre> candidates) {
        if (lastMinedOre == null || lastMinedType == null) return null;
        int inspected = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (!sameVein(lastMinedOre, candidate.pos(), lastMinedType, candidate.type())) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
            if (++inspected >= MAX_VISIBLE_TARGETS) break;
        }
        return null;
    }

    static int visibleTargetInspectionLimit() {
        return MAX_VISIBLE_TARGETS;
    }

    private MineTarget visibleTarget(BlockPos pos) {
        OreType type = targetType(pos);
        if (type == null) return null;
        BlockPos playerFeet = new BlockPos(mc.player.posX, mc.player.getEntityBoundingBox().minY, mc.player.posZ);
        if (!stableMiningPosition(playerFeet, pos)) return null;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        if (!withinMiningReach(eyes, pos, miningReach())) return null;
        Vec3d center = blockCenter(pos);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, center, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return null;
        return new MineTarget(pos.toImmutable(), type, hit.sideHit);
    }

    private double miningReach() {
        return mc.playerController.getBlockReachDistance();
    }

    static Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static boolean stableMiningPosition(BlockPos playerFeet, BlockPos ore) {
        int dx = Math.abs(playerFeet.getX() - ore.getX());
        int dy = ore.getY() - playerFeet.getY();
        int dz = Math.abs(playerFeet.getZ() - ore.getZ());
        if (dx == 0 && dz == 0) return dy == -1;
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
        LinkedHashSet<BlockPos> points = new LinkedHashSet<>();
        int from = Math.max(0, pathIndex);
        int end = Math.min(path.size(), from + ROUTE_RENDER_LIMIT);
        for (int i = from; i < end; i++) {
            BlockPos feet = path.get(i);
            if (!isPassable(feet.up())) points.add(feet.up());
            if (!isPassable(feet)) points.add(feet);
            points.add(feet);
        }
        points.add(currentOre);
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
    private static final class PathRoute {
        private final List<BlockPos> points;
        private final int cost;

        private PathRoute(List<BlockPos> points, int cost) {
            this.points = points;
            this.cost = cost;
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

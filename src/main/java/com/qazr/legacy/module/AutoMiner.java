package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    private static final int MAX_PATH_NODES = 3000;
    private static final int MAX_PATH_TARGETS = 12;
    private static final double REACHABLE_MINE_DISTANCE_SQ = 25.0;
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
    private int pathIndex;
    private int delay;
    private int manualPause;

    public AutoMiner(ModuleManager modules, OreVisualizer oreVisualizer) {
        this.modules = modules;
        this.oreVisualizer = oreVisualizer;
        reloadTargets();
    }

    public void reloadTargets() {
        minedCounts.clear();
        clearPath();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.AUTO_MINE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
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
        MineTarget visible = findNearestReachable();
        if (visible != null) {
            mine(visible);
            return;
        }
        followPathToOre();
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
            minedCounts.clear();
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
        currentOre = target.pos;
        currentOreType = target.type;
        delay = ModConfig.mineDelayTicks;
    }

    private void followPathToOre() {
        if (currentOre == null || targetType(currentOre) == null || pathIndex >= path.size()) {
            PathTarget target = findNearestPathTarget();
            if (target == null) {
                delay = 8;
                return;
            }
            currentOre = target.ore;
            currentOreType = target.type;
            path = target.path;
            pathIndex = 0;
            if (path.isEmpty()) {
                delay = 3;
                return;
            }
        }
        BlockPos next = path.get(pathIndex);
        if (clearBlockingObstacle(next)) {
            delay = ModConfig.mineDelayTicks;
            return;
        }
        double dx = next.getX() + 0.5 - mc.player.posX;
        double dz = next.getZ() + 0.5 - mc.player.posZ;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq > 16.0) {
            clearPath();
            delay = 2;
            return;
        }
        if (distanceSq < 0.20) {
            pathIndex++;
            return;
        }
        double length = Math.sqrt(distanceSq);
        mc.player.motionX += MathHelper.clamp(dx / length * 0.18, -0.18, 0.18);
        mc.player.motionZ += MathHelper.clamp(dz / length * 0.18, -0.18, 0.18);
        if (next.getY() > MathHelper.floor(mc.player.getEntityBoundingBox().minY) && mc.player.onGround) {
            mc.player.jump();
        }
        delay = 1;
    }

    private PathTarget findNearestPathTarget() {
        List<OreVisualizer.CachedOre> candidates = oreVisualizer.cachedMineOres(ModConfig.minePathRange);
        int attempts = 0;
        for (OreVisualizer.CachedOre candidate : candidates) {
            if (quotaReached(candidate.type())) continue;
            OreType currentType = targetType(candidate.pos());
            if (currentType != candidate.type()) {
                oreVisualizer.removeMarker(candidate.pos());
                continue;
            }
            List<BlockPos> route = pathToOre(candidate.pos());
            attempts++;
            if (route != null) return new PathTarget(candidate.pos(), candidate.type(), route);
            if (attempts >= MAX_PATH_TARGETS) break;
        }
        return null;
    }

    private List<BlockPos> pathToOre(BlockPos ore) {
        BlockPos start = standPos(mc.player.getPosition());
        List<BlockPos> goals = standPositionsAround(ore);
        if (goals.isEmpty()) return null;
        Set<BlockPos> goalSet = new HashSet<>(goals);
        if (goalSet.contains(start)) return java.util.Collections.emptyList();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, null);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_PATH_NODES) {
            BlockPos pos = queue.remove();
            if (goalSet.contains(pos)) return reconstruct(previous, pos);
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos next = standPos(pos.offset(facing).add(0, dy, 0));
                    if (previous.containsKey(next) || !canTraverse(next)) continue;
                    if (start.distanceSq(next) > ModConfig.minePathRange * ModConfig.minePathRange) continue;
                    previous.put(next, pos);
                    queue.add(next);
                }
            }
        }
        return null;
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
        if (isStandable(feet)) return true;
        return isBreakableBlock(feet) || isBreakableBlock(feet.up()) || isBreakableBlock(feet.down());
    }

    private boolean isBreakableBlock(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        if (state.getMaterial().isReplaceable()) return false;
        if (OreType.fromBlock(state.getBlock()) != null) return false;
        if (state.getBlock().getBlockHardness(state, mc.world, pos) < 0.0F) return false;
        return state.getBlock().canHarvestBlock(mc.world, pos, mc.player);
    }

    private boolean clearBlockingObstacle(BlockPos next) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d point = new Vec3d(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, point, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos obstacle = hit.getBlockPos();
        if (OreType.fromBlock(mc.world.getBlockState(obstacle).getBlock()) != null) return false;
        if (!isBreakableBlock(obstacle)) return false;
        selectBestPickaxe(mc.world.getBlockState(obstacle));
        mc.playerController.onPlayerDamageBlock(obstacle, hit.sideHit);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        return true;
    }

    private OreType targetType(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        OreType type = OreType.fromBlock(state.getBlock());
        if (type == null || !ModConfig.isMineOreEnabled(type) || quotaReached(type)) return null;
        return type;
    }

    private void clearPath() {
        path = java.util.Collections.emptyList();
        currentOre = null;
        currentOreType = null;
        miningPos = null;
        miningType = null;
        pathIndex = 0;
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

    private MineTarget findNearestReachable() {
        for (OreVisualizer.CachedOre candidate : oreVisualizer.cachedMineOres(ModConfig.minePathRange)) {
            if (candidate.distanceSq() > REACHABLE_MINE_DISTANCE_SQ) break;
            if (quotaReached(candidate.type())) continue;
            MineTarget visible = visibleTarget(candidate.pos());
            if (visible != null) return visible;
        }
        return null;
    }

    private MineTarget visibleTarget(BlockPos pos) {
        OreType type = targetType(pos);
        if (type == null) return null;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, center, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return null;
        return new MineTarget(pos.toImmutable(), type, hit.sideHit);
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
        double lastX = mc.player.posX - viewerX;
        double lastY = mc.player.getEntityBoundingBox().minY + 0.10 - viewerY;
        double lastZ = mc.player.posZ - viewerZ;
        int end = Math.min(path.size(), Math.max(pathIndex, 0) + ROUTE_RENDER_LIMIT);
        for (int i = Math.max(0, pathIndex); i < end; i++) {
            BlockPos point = path.get(i);
            double x = point.getX() + 0.5 - viewerX;
            double y = point.getY() + 0.10 - viewerY;
            double z = point.getZ() + 0.5 - viewerZ;
            routeLine(buffer, lastX, lastY, lastZ, x, y, z);
            lastX = x;
            lastY = y;
            lastZ = z;
        }
        double targetX = currentOre.getX() + 0.5 - viewerX;
        double targetY = currentOre.getY() + 0.5 - viewerY;
        double targetZ = currentOre.getZ() + 0.5 - viewerZ;
        routeLine(buffer, lastX, lastY, lastZ, targetX, targetY, targetZ);
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
}

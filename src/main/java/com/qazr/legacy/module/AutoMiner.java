package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AutoMiner {
    private static final int MAX_PATH_NODES = 5000;
    private static final int MAX_PATH_TARGETS = 32;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Set<Block> legacyTargets = new HashSet<>();
    private List<BlockPos> path = java.util.Collections.emptyList();
    private BlockPos currentOre;
    private BlockPos miningPos;
    private int pathIndex;
    private int delay;
    private int minedCount;

    public AutoMiner(ModuleManager modules) {
        this.modules = modules;
        reloadTargets();
    }

    public void reloadTargets() {
        legacyTargets.clear();
        for (String name : ModConfig.mineBlocks) {
            try {
                Block block = Block.REGISTRY.getObject(new ResourceLocation(name));
                if (block != null) legacyTargets.add(block);
            } catch (RuntimeException ignored) {
            }
        }
        clearPath();
        minedCount = 0;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.AUTO_MINE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
        updateMinedCount();
        if (quotaReached()) {
            modules.setEnabled(ModuleId.AUTO_MINE, false);
            clearPath();
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
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            delay = 0;
            minedCount = 0;
            clearPath();
        }
    }

    private void updateMinedCount() {
        if (miningPos == null) return;
        if (isTargetOre(miningPos)) return;
        BlockPos mined = miningPos;
        minedCount++;
        miningPos = null;
        if (mined.equals(currentOre)) clearPath();
    }

    private boolean quotaReached() {
        return ModConfig.mineTargetCount > 0 && minedCount >= ModConfig.mineTargetCount;
    }

    private void mine(MineTarget target) {
        selectBestPickaxe(mc.world.getBlockState(target.pos));
        face(target.pos);
        mc.playerController.onPlayerDamageBlock(target.pos, target.side);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        miningPos = target.pos;
        currentOre = target.pos;
        delay = ModConfig.mineDelayTicks;
    }

    private void followPathToOre() {
        if (currentOre == null || !isTargetOre(currentOre) || pathIndex >= path.size()) {
            PathTarget target = findNearestPathTarget();
            if (target == null) {
                delay = 5;
                return;
            }
            currentOre = target.ore;
            path = target.path;
            pathIndex = 0;
            if (path.isEmpty()) {
                delay = 3;
                return;
            }
        }
        BlockPos next = path.get(pathIndex);
        double dx = next.getX() + 0.5 - mc.player.posX;
        double dz = next.getZ() + 0.5 - mc.player.posZ;
        double distanceSq = dx * dx + dz * dz;
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
        BlockPos origin = mc.player.getPosition();
        int range = ModConfig.minePathRange;
        double rangeSq = range * range;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-range, -range, -range), origin.add(range, range, range))) {
            if (!isTargetOre(pos)) continue;
            if (origin.distanceSq(pos) > rangeSq) continue;
            candidates.add(pos.toImmutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> mc.player.getDistanceSqToCenter(pos)));
        int attempts = Math.min(MAX_PATH_TARGETS, candidates.size());
        for (int i = 0; i < attempts; i++) {
            BlockPos ore = candidates.get(i);
            List<BlockPos> route = pathToOre(ore);
            if (route != null) return new PathTarget(ore, route);
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
                    if (previous.containsKey(next) || !isStandable(next)) continue;
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
                if (isStandable(stand)) result.add(stand);
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

    private boolean isTargetOre(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        OreType type = OreType.fromBlock(state.getBlock());
        if (type != null) return ModConfig.isMineOreEnabled(type);
        return legacyTargets.contains(state.getBlock());
    }

    private void clearPath() {
        path = java.util.Collections.emptyList();
        currentOre = null;
        miningPos = null;
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
        BlockPos origin = mc.player.getPosition();
        MineTarget best = null;
        double bestDistance = 25.0;
        int radius = ModConfig.mineRadius;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            if (!isTargetOre(pos)) continue;
            double distance = mc.player.getDistanceSqToCenter(pos);
            MineTarget visible = visibleTarget(pos);
            if (visible != null && distance < bestDistance) {
                bestDistance = distance;
                best = visible;
            }
        }
        return best;
    }

    private MineTarget visibleTarget(BlockPos pos) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        RayTraceResult hit = mc.world.rayTraceBlocks(eyes, center, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return null;
        return new MineTarget(pos.toImmutable(), hit.sideHit);
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

    private static final class PathTarget {
        private final BlockPos ore;
        private final List<BlockPos> path;

        private PathTarget(BlockPos ore, List<BlockPos> path) {
            this.ore = ore;
            this.path = path;
        }
    }

    private static final class MineTarget {
        private final BlockPos pos;
        private final EnumFacing side;

        private MineTarget(BlockPos pos, EnumFacing side) {
            this.pos = pos;
            this.side = side;
        }
    }
}

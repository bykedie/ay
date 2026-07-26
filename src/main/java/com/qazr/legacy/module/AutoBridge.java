package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AutoBridge {
    private static final int PENDING_CONFIRM_TICKS = 60;
    private static final int MAX_PENDING_PLACEMENTS = 64;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Deque<PendingPlacement> pendingPlacements = new ArrayDeque<>();
    private int delay;
    private int lastPlayerTick = -1;
    private double lastMotionY;
    private boolean observedEnabled;

    public AutoBridge(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!modules.isEnabled(ModuleId.AUTO_BRIDGE)) {
            if (observedEnabled) resetState();
            observedEnabled = false;
            return;
        }
        if (!observedEnabled) {
            resetState();
            observedEnabled = true;
        }
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        int tick = mc.player.ticksExisted;
        if (playerTickResetNeeded(lastPlayerTick, tick)) resetState();
        lastPlayerTick = tick;
        double motionY = mc.player.motionY;
        boolean atApex = isJumpApex(lastMotionY, motionY, mc.player.onGround);
        lastMotionY = motionY;
        if (mc.player.capabilities.isFlying) {
            resetState();
            return;
        }
        prunePendingPlacements(tick);
        BlockPos currentSupport = supportPosition(atApex);
        PendingPlacement oldestPending = oldestMissingPlacement();
        BlockPos placePos = preferredPlacement(currentSupport,
            oldestPending == null ? null : oldestPending.pos,
            oldestPending != null && placementRetryDue(tick, oldestPending.nextRetryTick));
        if (placePos == null) {
            if (delay > 0) delay--;
            return;
        }
        PendingPlacement pending = findPendingPlacement(placePos);
        boolean retryUnconfirmed = pending != null;
        if (retryUnconfirmed && ModConfig.bridgeAvoidFeet && collidesWithBody(placePos)) {
            schedulePlacementRetry(pending, tick);
            return;
        }
        if (shouldWaitForPlacementDelay(delay, atApex, retryUnconfirmed)) {
            delay--;
            return;
        }
        Placement placement = placementFor(placePos);
        if (placement == null || !withinPlacementReach(placement)) {
            if (retryUnconfirmed) schedulePlacementRetry(pending, tick);
            return;
        }
        int slot = findHotbarBlock(placePos);
        boolean swapped = false;
        int original = mc.player.inventory.currentItem;
        int inventorySlot = -1;
        if (slot < 0) {
            slot = findInventoryBlock(placePos);
            if (slot < 0) {
                if (retryUnconfirmed) schedulePlacementRetry(pending, tick);
                return;
            }
            inventorySlot = slot;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot, original,
                ClickType.SWAP, mc.player);
            swapped = true;
            slot = original;
        }
        boolean placed = false;
        try {
            mc.player.inventory.currentItem = slot;
            mc.playerController.updateController();
            Vec3d hit = new Vec3d(placement.neighbor.getX() + 0.5, placement.neighbor.getY() + 0.5,
                placement.neighbor.getZ() + 0.5);
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world,
                placement.neighbor, placement.side, hit, EnumHand.MAIN_HAND);
            placed = result == EnumActionResult.SUCCESS;
            if (placed) mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            mc.player.inventory.currentItem = original;
            mc.playerController.updateController();
            if (swapped) {
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot, original,
                    ClickType.SWAP, mc.player);
            }
        }
        if (placed || retryUnconfirmed) recordPlacementAttempt(placePos, tick);
        if (placed) {
            delay = ModConfig.bridgeDelayTicks;
        } else {
            delay = 0;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            resetState();
            observedEnabled = false;
        }
    }

    private void resetState() {
        delay = 0;
        lastPlayerTick = -1;
        lastMotionY = 0.0;
        pendingPlacements.clear();
    }

    private void prunePendingPlacements(int tick) {
        Iterator<PendingPlacement> iterator = pendingPlacements.iterator();
        while (iterator.hasNext()) {
            PendingPlacement pending = iterator.next();
            if (!retainPendingPlacement(isReplaceable(pending.pos), tick, pending.expiresAt)) iterator.remove();
        }
    }

    private PendingPlacement oldestMissingPlacement() {
        Iterator<PendingPlacement> iterator = pendingPlacements.iterator();
        while (iterator.hasNext()) {
            PendingPlacement pending = iterator.next();
            if (!isReplaceable(pending.pos)) continue;
            double reach = mc.playerController.getBlockReachDistance() + 1.5;
            if (repairablePendingPlacement(mc.player.getDistanceSqToCenter(pending.pos), reach)) return pending;
            iterator.remove();
        }
        return null;
    }

    private void recordPlacementAttempt(BlockPos pos, int tick) {
        PendingPlacement pending = findPendingPlacement(pos);
        if (pending == null) {
            if (pendingPlacements.size() >= MAX_PENDING_PLACEMENTS) pendingPlacements.removeFirst();
            pending = new PendingPlacement(pos.toImmutable());
            pendingPlacements.addLast(pending);
        }
        pending.expiresAt = placementConfirmationExpiry(tick);
        schedulePlacementRetry(pending, tick);
    }

    private PendingPlacement findPendingPlacement(BlockPos pos) {
        for (PendingPlacement pending : pendingPlacements) {
            if (pending.pos.equals(pos)) return pending;
        }
        return null;
    }

    private void schedulePlacementRetry(PendingPlacement pending, int tick) {
        if (pending == null) return;
        pending.attempts++;
        pending.nextRetryTick = tick + placementRetryDelay(pending.attempts);
    }

    private BlockPos supportPosition(boolean atApex) {
        double[] offset = movementOffset(mc.player.rotationYaw,
            mc.player.movementInput.moveForward, mc.player.movementInput.moveStrafe);
        double[] lookaheads = candidateLookaheads(ModConfig.bridgeLookahead, atApex || mc.player.motionY < 0.0);
        int firstY = MathHelper.floor(mc.player.getEntityBoundingBox().minY) - 1;
        int scanDepth = scanDepth(ModConfig.bridgeDownScan, atApex || mc.player.motionY < 0.0);
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (int dy = 0; dy < scanDepth; dy++) {
            for (double lookahead : lookaheads) {
                double motionScale = lookahead <= 0.0 ? 0.0 : Math.min(1.5, lookahead / 0.35);
                double predictedX = mc.player.posX + offset[0] * lookahead + mc.player.motionX * motionScale;
                double predictedZ = mc.player.posZ + offset[1] * lookahead + mc.player.motionZ * motionScale;
                addSupportCandidates(candidates, mc.player.posX, mc.player.posZ,
                    predictedX, predictedZ, firstY - dy);
            }
        }
        for (BlockPos candidate : candidates) {
            if (!isReplaceable(candidate)) continue;
            if (findPendingPlacement(candidate) != null) continue;
            if (ModConfig.bridgeAvoidFeet && collidesWithBody(candidate)) continue;
            if (placementFor(candidate) != null) return candidate;
        }
        return null;
    }

    static double[] candidateLookaheads(double configured, boolean feetFirst) {
        double near = Math.min(0.35, configured);
        double middle = Math.min(0.70, configured);
        return feetFirst
            ? new double[] {0.0, near, middle, configured}
            : new double[] {near, middle, configured, 0.0};
    }

    static void addSupportCandidates(Set<BlockPos> candidates, double currentX, double currentZ,
            double predictedX, double predictedZ, int y) {
        int currentBlockX = MathHelper.floor(currentX);
        int currentBlockZ = MathHelper.floor(currentZ);
        int predictedBlockX = MathHelper.floor(predictedX);
        int predictedBlockZ = MathHelper.floor(predictedZ);
        if (predictedBlockX != currentBlockX && predictedBlockZ != currentBlockZ) {
            candidates.add(new BlockPos(predictedBlockX, y, currentBlockZ));
            candidates.add(new BlockPos(predictedBlockX, y, predictedBlockZ));
            candidates.add(new BlockPos(currentBlockX, y, predictedBlockZ));
            return;
        }
        candidates.add(new BlockPos(predictedBlockX, y, predictedBlockZ));
    }

    static boolean isJumpApex(double previousMotionY, double motionY, boolean onGround) {
        return previousMotionY > 0.0 && motionY <= 0.0 && !onGround;
    }

    static int scanDepth(int configuredDepth, boolean airborne) {
        return airborne ? Math.max(1, configuredDepth) : 1;
    }

    static boolean shouldWaitForPlacementDelay(int delay, boolean atApex, boolean retryUnconfirmed) {
        return delay > 0 && !atApex && (!retryUnconfirmed || delay > 1);
    }

    static int placementRetryDelay(int attempts) {
        int shift = Math.max(0, Math.min(2, attempts - 1));
        return 1 << shift;
    }

    static boolean placementRetryDue(int tick, int nextRetryTick) {
        return tick >= nextRetryTick;
    }

    static BlockPos preferredPlacement(BlockPos currentSupport, BlockPos pendingRepair,
            boolean repairDue) {
        if (currentSupport != null) return currentSupport;
        return repairDue ? pendingRepair : null;
    }

    static boolean playerTickResetNeeded(int previousTick, int currentTick) {
        return previousTick >= 0 && currentTick < previousTick;
    }

    static int placementConfirmationExpiry(int tick) {
        return tick + PENDING_CONFIRM_TICKS;
    }

    static boolean retainPendingPlacement(boolean replaceable, int tick, int expiresAt) {
        return replaceable || tick <= expiresAt;
    }

    static boolean repairablePendingPlacement(double distanceSq, double reach) {
        return distanceSq <= reach * reach;
    }

    static double[] movementOffset(float yaw, float forward, float strafe) {
        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) return new double[] {0.0, 0.0};
        float length = MathHelper.sqrt(forward * forward + strafe * strafe);
        forward /= length;
        strafe /= length;
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = forward * -sin + strafe * cos;
        double z = forward * cos + strafe * sin;
        return new double[] {x, z};
    }

    static int candidateY(double feetY, int downOffset) {
        return MathHelper.floor(feetY) - 1 - Math.max(0, downOffset);
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock().isReplaceable(mc.world, pos);
    }

    private boolean collidesWithBody(BlockPos pos) {
        AxisAlignedBB block = new AxisAlignedBB(pos);
        AxisAlignedBB body = mc.player.getEntityBoundingBox().grow(-0.02, -0.01, -0.02);
        return block.maxY > body.minY + 0.05 && block.intersects(body);
    }

    private Placement placementFor(BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(side);
            if (!mc.world.getBlockState(neighbor).getMaterial().isReplaceable()
                    && mc.world.getBlockState(neighbor).getMaterial().blocksMovement()) {
                return new Placement(neighbor, side.getOpposite());
            }
        }
        return null;
    }

    private int findHotbarBlock(BlockPos pos) {
        for (int slot = 0; slot < 9; slot++) {
            if (canPlace(mc.player.inventory.getStackInSlot(slot), pos)) return slot;
        }
        return -1;
    }

    private int findInventoryBlock(BlockPos pos) {
        for (int slot = 9; slot < 36; slot++) {
            if (canPlace(mc.player.inventory.getStackInSlot(slot), pos)) return slot;
        }
        return -1;
    }

    private boolean canPlace(ItemStack stack, BlockPos pos) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null && stableBridgeBlock(block.getDefaultState().getMaterial().isSolid(),
            block instanceof BlockFalling) && block.canPlaceBlockAt(mc.world, pos);
    }

    static boolean stableBridgeBlock(boolean solid, boolean falling) {
        return solid && !falling;
    }

    private boolean withinPlacementReach(Placement placement) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d hit = new Vec3d(placement.neighbor.getX() + 0.5, placement.neighbor.getY() + 0.5,
            placement.neighbor.getZ() + 0.5);
        double reach = mc.playerController.getBlockReachDistance() + 0.5;
        return eyes.squareDistanceTo(hit) <= reach * reach;
    }

    private static final class Placement {
        private final BlockPos neighbor;
        private final EnumFacing side;

        private Placement(BlockPos neighbor, EnumFacing side) {
            this.neighbor = neighbor;
            this.side = side;
        }
    }

    private static final class PendingPlacement {
        private final BlockPos pos;
        private int expiresAt;
        private int attempts;
        private int nextRetryTick;

        private PendingPlacement(BlockPos pos) {
            this.pos = pos;
        }
    }
}

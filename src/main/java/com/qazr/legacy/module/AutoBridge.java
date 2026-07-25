package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.block.Block;
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
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private int delay;
    private double lastMotionY;
    private BlockPos lastPlaced;

    public AutoBridge(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.AUTO_BRIDGE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        double motionY = mc.player.motionY;
        boolean atApex = isJumpApex(lastMotionY, motionY, mc.player.onGround);
        lastMotionY = motionY;
        if (mc.player.capabilities.isFlying) return;
        BlockPos placePos = supportPosition(atApex);
        if (placePos == null) {
            if (delay > 0) delay--;
            return;
        }
        if (delay > 0 && !atApex && placePos.equals(lastPlaced)) {
            delay--;
            return;
        }
        Placement placement = placementFor(placePos);
        if (placement == null) return;
        int slot = findHotbarBlock(placePos);
        boolean swapped = false;
        int original = mc.player.inventory.currentItem;
        int inventorySlot = -1;
        if (slot < 0) {
            slot = findInventoryBlock(placePos);
            if (slot < 0) return;
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
        if (placed) {
            lastPlaced = placePos;
            delay = ModConfig.bridgeDelayTicks;
        } else {
            delay = 0;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            delay = 0;
            lastMotionY = 0.0;
            lastPlaced = null;
        }
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
        return block != null && block.getDefaultState().getMaterial().isSolid() && block.canPlaceBlockAt(mc.world, pos);
    }

    private static final class Placement {
        private final BlockPos neighbor;
        private final EnumFacing side;

        private Placement(BlockPos neighbor, EnumFacing side) {
            this.neighbor = neighbor;
            this.side = side;
        }
    }
}

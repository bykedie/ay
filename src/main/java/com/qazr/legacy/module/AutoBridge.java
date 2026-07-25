package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AutoBridge {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private int delay;
    private double lastMotionY;

    public AutoBridge(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.AUTO_BRIDGE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.connection == null || mc.currentScreen != null) return;
        double motionY = mc.player.motionY;
        boolean atApex = lastMotionY > 0.0D && motionY <= 0.0D && !mc.player.onGround;
        lastMotionY = motionY;
        if (delay > 0) {
            delay--;
            if (!atApex) return;
        }
        if (mc.player.capabilities.isFlying) return;
        BlockPos placePos = supportPosition();
        if (placePos == null && atApex) placePos = apexSupportPosition();
        if (placePos == null) return;
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
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot, original, ClickType.SWAP, mc.player);
            swapped = true;
            slot = original;
        }
        try {
            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(placement.neighbor,
                placement.side, EnumHand.MAIN_HAND, 0.5F, 0.5F, 0.5F));
            mc.player.swingArm(EnumHand.MAIN_HAND);
            mc.player.connection.sendPacket(new CPacketHeldItemChange(original));
        } finally {
            if (swapped) {
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, inventorySlot, original,
                    ClickType.SWAP, mc.player);
            }
        }
        delay = ModConfig.bridgeDelayTicks;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            delay = 0;
            lastMotionY = 0.0;
        }
    }

    private BlockPos supportPosition() {
        double[] offset = movementOffset(mc.player.rotationYaw,
            mc.player.movementInput.moveForward, mc.player.movementInput.moveStrafe);
        if (offset[0] == 0.0 && offset[1] == 0.0) return null;
        int x = MathHelper.floor(mc.player.posX + offset[0] * ModConfig.bridgeLookahead);
        int z = MathHelper.floor(mc.player.posZ + offset[1] * ModConfig.bridgeLookahead);
        int firstY = MathHelper.floor(mc.player.getEntityBoundingBox().minY) - 1;
        for (int dy = 0; dy < ModConfig.bridgeDownScan; dy++) {
            BlockPos candidate = new BlockPos(x, firstY - dy, z);
            if (!isReplaceable(candidate)) continue;
            if (ModConfig.bridgeAvoidFeet && collidesWithPlayer(candidate)) continue;
            if (placementFor(candidate) != null) return candidate;
        }
        return null;
    }

    private BlockPos apexSupportPosition() {
        int x = MathHelper.floor(mc.player.posX);
        int z = MathHelper.floor(mc.player.posZ);
        int y = MathHelper.floor(mc.player.getEntityBoundingBox().minY) - 1;
        BlockPos candidate = new BlockPos(x, y, z);
        if (!isReplaceable(candidate)) return null;
        if (ModConfig.bridgeAvoidFeet && collidesWithPlayer(candidate)) return null;
        return placementFor(candidate) != null ? candidate : null;
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

    private boolean collidesWithPlayer(BlockPos pos) {
        AxisAlignedBB block = new AxisAlignedBB(pos);
        return block.intersects(mc.player.getEntityBoundingBox().grow(0.02, 0.0, 0.02));
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

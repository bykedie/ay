package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class AutoMiner {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Set<Block> targets = new HashSet<>();
    private int delay;

    public AutoMiner(ModuleManager modules) {
        this.modules = modules;
        reloadTargets();
    }

    public void reloadTargets() {
        targets.clear();
        for (String name : ModConfig.mineBlocks) {
            Block block = Block.REGISTRY.getObject(new ResourceLocation(name));
            if (block != null) targets.add(block);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.AUTO_MINE)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
        if (delay-- > 0) return;
        MineTarget target = findNearestReachable();
        if (target == null) return;
        selectBestPickaxe(mc.world.getBlockState(target.pos));
        face(target.pos);
        mc.playerController.onPlayerDamageBlock(target.pos, target.side);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        delay = ModConfig.mineDelayTicks;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) delay = 0;
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
            IBlockState state = mc.world.getBlockState(pos);
            if (!targets.contains(state.getBlock())) continue;
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

    private static final class MineTarget {
        private final BlockPos pos;
        private final EnumFacing side;

        private MineTarget(BlockPos pos, EnumFacing side) {
            this.pos = pos;
            this.side = side;
        }
    }
}

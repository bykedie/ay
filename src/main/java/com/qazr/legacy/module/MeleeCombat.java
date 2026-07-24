package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import java.util.List;
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class MeleeCombat {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private int delay;
    private boolean auraAttack;

    public MeleeCombat(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !modules.isEnabled(ModuleId.MELEE_AURA)) return;
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
        if (delay-- > 0 || mc.player.getCooledAttackStrength(0.0F) < 0.95F) return;
        int targetLimit = ModConfig.meleeMultiTarget ? ModConfig.meleeMaxTargets : 1;
        List<EntityLivingBase> targets = CombatSupport.findTargets(mc, ModuleId.MELEE_AURA, targetLimit);
        if (targets.isEmpty()) return;
        if (ModConfig.meleeAutoWeapon) CombatSupport.selectBestWeapon(mc);
        if (ModConfig.meleeRotate) face(targets.get(0));
        if (modules.isEnabled(ModuleId.CRITICALS)) sendCriticalSequence();
        auraAttack = true;
        try {
            for (EntityLivingBase target : targets) {
                mc.playerController.attackEntity(mc.player, target);
                mc.player.swingArm(EnumHand.MAIN_HAND);
            }
        } finally {
            auraAttack = false;
        }
        delay = ModConfig.meleeDelayTicks;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (auraAttack || !modules.isEnabled(ModuleId.CRITICALS) || event.getEntityPlayer() != mc.player) return;
        if (mc.player == null || CombatSupport.weaponDamage(mc.player.getHeldItemMainhand()) < 0.0) return;
        sendCriticalSequence();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        delay = 0;
        auraAttack = false;
    }

    private void face(EntityLivingBase target) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        float[] rotations = CombatSupport.rotations(eyes, target, ModConfig.meleeAttackPoint);
        mc.player.rotationYaw = rotations[0];
        mc.player.rotationPitch = rotations[1];
    }

    private void sendCriticalSequence() {
        if (mc.player == null || mc.player.connection == null || !mc.player.onGround) return;
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isOnLadder() || mc.player.isRiding()
                || mc.player.isPotionActive(MobEffects.BLINDNESS)) return;
        double x = mc.player.posX;
        double y = mc.player.posY;
        double z = mc.player.posZ;
        mc.player.connection.sendPacket(new CPacketPlayer.Position(x, y + 0.0625, z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(x, y + 0.0125, z, false));
        mc.player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, false));
    }
}

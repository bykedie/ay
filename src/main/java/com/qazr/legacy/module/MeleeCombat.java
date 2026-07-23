package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.util.CombatMath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
        EntityLivingBase target = findTarget();
        if (target == null) return;
        if (ModConfig.meleeAutoWeapon) selectBestWeapon();
        face(target);
        if (modules.isEnabled(ModuleId.CRITICALS)) sendCriticalSequence();
        auraAttack = true;
        try {
            mc.playerController.attackEntity(mc.player, target);
            mc.player.swingArm(EnumHand.MAIN_HAND);
        } finally {
            auraAttack = false;
        }
        delay = ModConfig.meleeDelayTicks;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (auraAttack || !modules.isEnabled(ModuleId.CRITICALS) || event.getEntityPlayer() != mc.player) return;
        sendCriticalSequence();
    }

    private EntityLivingBase findTarget() {
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        double maxDistance = ModConfig.meleeRange * ModConfig.meleeRange;
        for (net.minecraft.entity.Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!canAttack(living)) continue;
            double distance = mc.player.getDistanceSq(living);
            if (distance > maxDistance) continue;
            double score = CombatMath.score(distance, living.getHealth(), ModConfig.meleePriority);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean canAttack(EntityLivingBase target) {
        if (target == mc.player || target.isDead || target.getHealth() <= 0.0F || target.isInvisible()) return false;
        if (!mc.player.canEntityBeSeen(target)) return false;
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return ModConfig.meleePlayers && !player.isSpectator() && !player.capabilities.isCreativeMode
                && !mc.player.isOnSameTeam(player);
        }
        if (target instanceof IMob) return ModConfig.meleeHostiles;
        return target instanceof EntityAnimal && ModConfig.meleeAnimals;
    }

    private void selectBestWeapon() {
        int bestSlot = mc.player.inventory.currentItem;
        double bestDamage = weaponDamage(mc.player.inventory.getStackInSlot(bestSlot));
        for (int slot = 0; slot < 9; slot++) {
            double damage = weaponDamage(mc.player.inventory.getStackInSlot(slot));
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }
        if (bestSlot != mc.player.inventory.currentItem) {
            mc.player.inventory.currentItem = bestSlot;
            mc.playerController.updateController();
        }
    }

    private double weaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return -1.0;
        Item item = stack.getItem();
        if (!(item instanceof ItemSword) && !(item instanceof ItemAxe)) return -1.0;
        double damage = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND)
                .get(SharedMonsterAttributes.ATTACK_DAMAGE.getName())) {
            damage += modifier.getAmount();
        }
        return damage;
    }

    private void face(EntityLivingBase target) {
        double dx = target.posX - mc.player.posX;
        double dy = target.posY + target.getEyeHeight() - (mc.player.posY + mc.player.getEyeHeight());
        double dz = target.posZ - mc.player.posZ;
        mc.player.rotationYaw = CombatMath.yaw(dx, dz);
        mc.player.rotationPitch = CombatMath.pitch(dx, dy, dz);
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

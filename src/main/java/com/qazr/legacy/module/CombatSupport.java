package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.util.CombatMath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class CombatSupport {
    private CombatSupport() {
    }

    static EntityLivingBase findTarget(Minecraft mc, double range, boolean requireVisible) {
        return findTarget(mc, range, requireVisible, true);
    }

    static EntityLivingBase findPositionTarget(Minecraft mc, double range, boolean requireVisible) {
        return findTarget(mc, range, requireVisible, false);
    }

    private static EntityLivingBase findTarget(Minecraft mc, double range, boolean requireVisible, boolean hitboxRange) {
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        double maxDistanceSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!canAttack(mc, living, requireVisible)) continue;
            double distanceSq = hitboxRange
                ? distanceSqToHitbox(mc.player.getPositionEyes(1.0F), living.getEntityBoundingBox())
                : mc.player.getDistanceSq(living);
            if (distanceSq > maxDistanceSq) continue;
            double score = CombatMath.score(distanceSq, living.getHealth(), ModConfig.meleePriority);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    static boolean canAttack(Minecraft mc, EntityLivingBase target, boolean requireVisible) {
        if (target == mc.player || target.isDead || target.getHealth() <= 0.0F || target.isInvisible()) return false;
        if (requireVisible && !mc.player.canEntityBeSeen(target)) return false;
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return ModConfig.meleePlayers && !player.isSpectator() && !player.capabilities.isCreativeMode
                && !mc.player.isOnSameTeam(player);
        }
        if (target instanceof IMob) return ModConfig.meleeHostiles;
        return target instanceof EntityAnimal && ModConfig.meleeAnimals;
    }

    static double distanceSqToHitbox(Vec3d point, AxisAlignedBB box) {
        double x = MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return point.squareDistanceTo(x, y, z);
    }

    static void selectBestWeapon(Minecraft mc) {
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

    static double weaponDamage(ItemStack stack) {
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

    static float[] rotations(Vec3d fromEyes, EntityLivingBase target) {
        double dx = target.posX - fromEyes.x;
        double dy = target.posY + target.getEyeHeight() - fromEyes.y;
        double dz = target.posZ - fromEyes.z;
        return new float[] {CombatMath.yaw(dx, dz), CombatMath.pitch(dx, dy, dz)};
    }
}

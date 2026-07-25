package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.AttackPoint;
import com.qazr.legacy.util.CombatMath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityList;
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
import net.minecraft.util.ResourceLocation;

final class CombatSupport {
    private CombatSupport() {
    }

    static List<EntityLivingBase> findTargets(Minecraft mc, ModuleId module, int limit) {
        boolean melee = module == ModuleId.MELEE_AURA;
        if (!melee && module != ModuleId.BLINK_STRIKE) throw new IllegalArgumentException("Unsupported combat module: " + module);
        double range = melee ? ModConfig.meleeRange : ModConfig.blinkRange;
        return findTargets(mc, module, range, melee, melee, limit);
    }

    static List<EntityLivingBase> findTargets(Minecraft mc, ModuleId module, double range,
            boolean requireVisible, boolean hitboxRange, int limit) {
        List<ScoredTarget> candidates = new ArrayList<>();
        double maxDistanceSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!canAttack(mc, living, module, requireVisible)) continue;
            double distanceSq = hitboxRange
                ? distanceSqToHitbox(mc.player.getPositionEyes(1.0F), living.getEntityBoundingBox())
                : mc.player.getDistanceSq(living);
            if (distanceSq > maxDistanceSq) continue;
            String priority = module == ModuleId.MELEE_AURA ? ModConfig.meleePriority : ModConfig.blinkPriority;
            candidates.add(new ScoredTarget(living, CombatMath.score(distanceSq, living.getHealth(), priority), distanceSq));
        }
        candidates.sort(Comparator.comparingDouble((ScoredTarget target) -> target.score)
            .thenComparingDouble(target -> target.distanceSq)
            .thenComparingInt(target -> target.entity.getEntityId()));
        int count = Math.min(Math.max(0, limit), candidates.size());
        List<EntityLivingBase> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(candidates.get(i).entity);
        return result;
    }

    static List<EntityLivingBase> findVisualizationTargets(Minecraft mc, double range, int limit) {
        List<EntityLivingBase> result = new ArrayList<>();
        double maxDistanceSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase target = (EntityLivingBase) entity;
            if (target == mc.player || target.isDead || target.getHealth() <= 0.0F || target.isInvisible()) continue;
            if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                if (player.isSpectator() || player.capabilities.isCreativeMode || mc.player.isOnSameTeam(player)) continue;
            }
            if (mc.player.getDistanceSq(target) <= maxDistanceSq) result.add(target);
        }
        result.sort(Comparator.comparingDouble((EntityLivingBase target) -> mc.player.getDistanceSq(target))
            .thenComparingInt(EntityLivingBase::getEntityId));
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    static int countVisualizationTargets(Minecraft mc, double range) {
        int count = 0;
        double maxDistanceSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase target = (EntityLivingBase) entity;
            if (target == mc.player || target.isDead || target.getHealth() <= 0.0F || target.isInvisible()) continue;
            if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                if (player.isSpectator() || player.capabilities.isCreativeMode || mc.player.isOnSameTeam(player)) continue;
            }
            if (mc.player.getDistanceSq(target) <= maxDistanceSq) count++;
        }
        return count;
    }

    static boolean canAttack(Minecraft mc, EntityLivingBase target, ModuleId module, boolean requireVisible) {
        if (target == mc.player || target.isDead || target.getHealth() <= 0.0F || target.isInvisible()) return false;
        if (requireVisible && !mc.player.canEntityBeSeen(target)) return false;
        boolean melee = module == ModuleId.MELEE_AURA;
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            boolean players = melee ? ModConfig.meleePlayers : ModConfig.blinkPlayers;
            return players && !player.isSpectator() && !player.capabilities.isCreativeMode
                && !mc.player.isOnSameTeam(player);
        }
        if (isModdedEntity(target)) {
            ResourceLocation key = EntityList.getKey(target);
            boolean enabled = melee ? ModConfig.meleeModded : ModConfig.blinkModded;
            return enabled && key != null && ModConfig.isModEntityEnabled(module, key.toString());
        }
        if (target instanceof IMob) return melee ? ModConfig.meleeHostiles : ModConfig.blinkHostiles;
        if (target instanceof EntityAnimal) return melee ? ModConfig.meleeAnimals : ModConfig.blinkAnimals;
        return target instanceof EntityLiving && (melee ? ModConfig.meleePeaceful : ModConfig.blinkPeaceful);
    }

    static boolean isModdedEntity(net.minecraft.entity.Entity entity) {
        ResourceLocation key = EntityList.getKey(entity);
        return isModdedRegistry(key);
    }

    static boolean isModdedRegistry(ResourceLocation key) {
        return key != null && !"minecraft".equals(key.getNamespace());
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
        return rotations(fromEyes, target, AttackPoint.HEAD);
    }

    static float[] rotations(Vec3d fromEyes, EntityLivingBase target, AttackPoint point) {
        Vec3d targetPoint = point.point(target);
        double dx = targetPoint.x - fromEyes.x;
        double dy = targetPoint.y - fromEyes.y;
        double dz = targetPoint.z - fromEyes.z;
        return new float[] {CombatMath.yaw(dx, dz), CombatMath.pitch(dx, dy, dz)};
    }

    private static final class ScoredTarget {
        private final EntityLivingBase entity;
        private final double score;
        private final double distanceSq;

        private ScoredTarget(EntityLivingBase entity, double score, double distanceSq) {
            this.entity = entity;
            this.score = score;
            this.distanceSq = distanceSq;
        }
    }
}

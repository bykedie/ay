package com.qazr.legacy.module;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.model.ModelCow;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelRabbit;
import net.minecraft.client.model.ModelSpider;
import net.minecraft.client.model.ModelSquid;
import net.minecraft.client.model.ModelVillager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CombatTargetRendererTest {
    @Test
    public void selectsSkeletonFromActualRenderModel() {
        assertEquals(CombatTargetRenderer.SkeletonType.HUMANOID,
            CombatTargetRenderer.skeletonType(new ModelBiped()));
        assertEquals(CombatTargetRenderer.SkeletonType.HUMANOID,
            CombatTargetRenderer.skeletonType(new ModelVillager(0.0F)));
        assertEquals(CombatTargetRenderer.SkeletonType.QUADRUPED,
            CombatTargetRenderer.skeletonType(new ModelCow()));
        assertEquals(CombatTargetRenderer.SkeletonType.QUADRUPED,
            CombatTargetRenderer.skeletonType(new ModelRabbit()));
        assertEquals(CombatTargetRenderer.SkeletonType.HORSE,
            CombatTargetRenderer.skeletonType(new ModelHorse()));
        assertEquals(CombatTargetRenderer.SkeletonType.SPIDER,
            CombatTargetRenderer.skeletonType(new ModelSpider()));
        assertEquals(CombatTargetRenderer.SkeletonType.BIRD,
            CombatTargetRenderer.skeletonType(new ModelChicken()));
        assertEquals(CombatTargetRenderer.SkeletonType.AQUATIC,
            CombatTargetRenderer.skeletonType(new ModelSquid()));
    }

    @Test
    public void quadrupedsPreferHeadYawForFacing() {
        assertEquals(90.0F, CombatTargetRenderer.facingYaw(CombatTargetRenderer.SkeletonType.QUADRUPED, 10.0F, 90.0F), 0.0F);
        assertEquals(45.0F, CombatTargetRenderer.facingYaw(CombatTargetRenderer.SkeletonType.HORSE, 10.0F, 45.0F), 0.0F);
        assertEquals(10.0F, CombatTargetRenderer.facingYaw(CombatTargetRenderer.SkeletonType.HUMANOID, 10.0F, 90.0F), 0.0F);
    }

    @Test
    public void interpolatesHeadYawAcrossWrappedAngles() {
        assertEquals(180.0F, CombatTargetRenderer.interpolateAngle(170.0F, -170.0F, 0.5F), 0.0001F);
        assertEquals(-180.0F, CombatTargetRenderer.interpolateAngle(-170.0F, 170.0F, 0.5F), 0.0001F);
    }
}

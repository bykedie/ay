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
}

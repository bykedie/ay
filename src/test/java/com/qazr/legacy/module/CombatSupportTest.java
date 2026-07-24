package com.qazr.legacy.module;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatSupportTest {
    private final AxisAlignedBB box = new AxisAlignedBB(2.0, 0.0, 2.0, 3.0, 2.0, 3.0);

    @Test
    public void measuresToNearestPointOfHitbox() {
        assertEquals(4.0, CombatSupport.distanceSqToHitbox(new Vec3d(0.0, 1.0, 2.0), box), 0.0);
    }

    @Test
    public void reportsZeroForPointInsideHitbox() {
        assertEquals(0.0, CombatSupport.distanceSqToHitbox(new Vec3d(2.5, 1.0, 2.5), box), 0.0);
    }

    @Test
    public void distinguishesModdedEntityRegistryNamespaces() {
        assertFalse(CombatSupport.isModdedRegistry(new ResourceLocation("minecraft:zombie")));
        assertTrue(CombatSupport.isModdedRegistry(new ResourceLocation("examplemod:boss")));
        assertFalse(CombatSupport.isModdedRegistry(null));
    }
}

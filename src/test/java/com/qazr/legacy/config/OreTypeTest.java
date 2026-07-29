package com.qazr.legacy.config;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OreTypeTest {
    @BeforeClass
    public static void initializeMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    public void resolvesVanillaOreBlocksByRegisteredInstance() {
        assertEquals(OreType.COAL, OreType.fromBlock(Blocks.COAL_ORE));
        assertEquals(OreType.IRON, OreType.fromBlock(Blocks.IRON_ORE));
        assertEquals(OreType.GOLD, OreType.fromBlock(Blocks.GOLD_ORE));
        assertEquals(OreType.REDSTONE, OreType.fromBlock(Blocks.REDSTONE_ORE));
        assertEquals(OreType.REDSTONE, OreType.fromBlock(Blocks.LIT_REDSTONE_ORE));
        assertEquals(OreType.LAPIS, OreType.fromBlock(Blocks.LAPIS_ORE));
        assertEquals(OreType.DIAMOND, OreType.fromBlock(Blocks.DIAMOND_ORE));
        assertEquals(OreType.EMERALD, OreType.fromBlock(Blocks.EMERALD_ORE));
        assertEquals(OreType.QUARTZ, OreType.fromBlock(Blocks.QUARTZ_ORE));
        assertNull(OreType.fromBlock(Blocks.STONE));
        assertNull(OreType.fromBlock(null));
    }
}

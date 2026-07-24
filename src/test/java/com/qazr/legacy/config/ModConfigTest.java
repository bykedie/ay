package com.qazr.legacy.config;

import com.qazr.legacy.module.ModuleManager;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModConfigTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void initializeForgeHome() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, folder.getRoot());
    }

    @Test
    public void loadsConservativeCombatDefaults() throws Exception {
        ModConfig.load(configFile());

        assertFalse(ModConfig.meleeAura);
        assertFalse(ModConfig.blinkStrike);
        assertEquals(3.0, ModConfig.meleeRange, 0.0);
        assertEquals(12.0, ModConfig.blinkRange, 0.0);
        assertEquals(4.0, ModConfig.blinkStep, 0.0);
        assertEquals(2.5, ModConfig.blinkAttackDistance, 0.0);
    }

    @Test
    public void persistsBothCombatRanges() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveRange(ModuleId.MELEE_AURA, 4.5);
        ModConfig.saveRange(ModuleId.BLINK_STRIKE, 30.0);

        ModConfig.reload();

        assertEquals(4.5, ModConfig.meleeRange, 0.0);
        assertEquals(30.0, ModConfig.blinkRange, 0.0);
        assertEquals(4.5, ModConfig.getRange(ModuleId.MELEE_AURA), 0.0);
        assertEquals(30.0, ModConfig.getRange(ModuleId.BLINK_STRIKE), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfBoundsRange() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveRange(ModuleId.BLINK_STRIKE, 201.0);
    }

    @Test
    public void resolvesLegacyDoubleEnabledCombatConfig() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveModule(ModuleId.MELEE_AURA, true);
        ModConfig.saveModule(ModuleId.BLINK_STRIKE, true);
        ModConfig.reload();

        ModuleManager modules = new ModuleManager();

        assertTrue(modules.isEnabled(ModuleId.MELEE_AURA));
        assertFalse(modules.isEnabled(ModuleId.BLINK_STRIKE));
        ModConfig.reload();
        assertFalse(ModConfig.blinkStrike);
    }

    private File configFile() throws Exception {
        return new File(folder.getRoot(), "qazrlegacy.cfg");
    }
}

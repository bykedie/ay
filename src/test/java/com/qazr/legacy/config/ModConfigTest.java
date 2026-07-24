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
        assertTrue(ModConfig.meleePlayers);
        assertTrue(ModConfig.blinkPlayers);
        assertFalse(ModConfig.meleeRotate);
        assertFalse(ModConfig.blinkRotate);
        assertFalse(ModConfig.meleeMultiTarget);
        assertFalse(ModConfig.blinkMultiTarget);
        assertFalse(ModConfig.meleeVisualize);
        assertFalse(ModConfig.blinkVisualize);
        assertFalse(ModConfig.meleeModded);
        assertFalse(ModConfig.blinkModded);
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

    @Test
    public void persistsExpandablePanelSettings() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveNumber(ModuleSetting.MELEE_RANGE, 4.37);
        ModConfig.saveNumber(ModuleSetting.BLINK_STEP, 5.26);
        ModConfig.saveNumber(ModuleSetting.GG_MIN_DELAY, 90.0);
        ModConfig.saveNumber(ModuleSetting.GG_MAX_DELAY, 40.0);
        ModConfig.toggle(ModuleSetting.MELEE_ANIMALS);
        ModConfig.cycleChoice(ModuleSetting.MELEE_PRIORITY);
        ModConfig.reload();

        assertEquals(4.4, ModConfig.meleeRange, 0.001);
        assertEquals(5.3, ModConfig.blinkStep, 0.001);
        assertEquals(40, ModConfig.ggMinDelayTicks);
        assertEquals(40, ModConfig.ggMaxDelayTicks);
        assertTrue(ModConfig.meleeAnimals);
        assertEquals("health", ModConfig.meleePriority);
    }

    @Test
    public void persistsIndependentModEntitySelections() throws Exception {
        ModConfig.load(configFile());
        assertTrue(ModConfig.isModEntityEnabled(ModuleId.MELEE_AURA, "example:boss"));
        assertFalse(ModConfig.toggleModEntity(ModuleId.MELEE_AURA, "example:boss"));
        assertTrue(ModConfig.isModEntityEnabled(ModuleId.BLINK_STRIKE, "example:boss"));
        ModConfig.reload();

        assertFalse(ModConfig.isModEntityEnabled(ModuleId.MELEE_AURA, "example:boss"));
        assertTrue(ModConfig.isModEntityEnabled(ModuleId.BLINK_STRIKE, "example:boss"));
        assertTrue(ModConfig.toggleModEntity(ModuleId.MELEE_AURA, "example:boss"));
    }

    @Test
    public void persistsIndependentCombatTargetAndPresentationSettings() throws Exception {
        ModConfig.load(configFile());
        ModConfig.toggle(ModuleSetting.MELEE_ANIMALS);
        ModConfig.toggle(ModuleSetting.MELEE_ROTATE);
        ModConfig.toggle(ModuleSetting.MELEE_MULTI);
        ModConfig.toggle(ModuleSetting.MELEE_VISUALIZE);
        ModConfig.saveNumber(ModuleSetting.MELEE_MAX_TARGETS, 5.0);
        ModConfig.toggle(ModuleSetting.BLINK_PLAYERS);
        ModConfig.toggle(ModuleSetting.BLINK_MODDED);
        ModConfig.toggle(ModuleSetting.BLINK_ROTATE);
        ModConfig.toggle(ModuleSetting.BLINK_MULTI);
        ModConfig.toggle(ModuleSetting.BLINK_VISUALIZE);
        ModConfig.saveNumber(ModuleSetting.BLINK_MAX_TARGETS, 4.0);
        ModConfig.cycleChoice(ModuleSetting.BLINK_PRIORITY);
        ModConfig.reload();

        assertTrue(ModConfig.meleeAnimals);
        assertTrue(ModConfig.meleeRotate);
        assertTrue(ModConfig.meleeMultiTarget);
        assertTrue(ModConfig.meleeVisualize);
        assertEquals(5, ModConfig.meleeMaxTargets);
        assertFalse(ModConfig.blinkPlayers);
        assertTrue(ModConfig.blinkModded);
        assertTrue(ModConfig.blinkRotate);
        assertTrue(ModConfig.blinkMultiTarget);
        assertTrue(ModConfig.blinkVisualize);
        assertEquals(4, ModConfig.blinkMaxTargets);
        assertEquals("health", ModConfig.blinkPriority);
        assertFalse(ModConfig.blinkAnimals);
        assertFalse(ModConfig.meleeModded);
    }

    private File configFile() throws Exception {
        return new File(folder.getRoot(), "qazrlegacy.cfg");
    }
}

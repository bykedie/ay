package com.qazr.legacy.config;

import com.qazr.legacy.module.ModuleManager;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraftforge.common.config.Configuration;
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
        assertFalse(ModConfig.flight);
        assertFalse(ModConfig.panelGraveKeyMigrated);
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
        assertFalse(ModConfig.meleePeaceful);
        assertFalse(ModConfig.blinkPeaceful);
        assertEquals(AttackPoint.CHEST, ModConfig.meleeAttackPoint);
        assertEquals(AttackPoint.CHEST, ModConfig.blinkAttackPoint);
        assertEquals(FlightMode.STATIC, ModConfig.flightMode);
        assertEquals(1.0, ModConfig.flightSpeed, 0.001);
        assertFalse(ModConfig.targetVisualizer);
        assertFalse(ModConfig.oreVisualizer);
        assertFalse(ModConfig.autoBridge);
        assertEquals(32, ModConfig.minePathRange);
        assertEquals(30, ModConfig.mineManualPauseTicks);
        assertTrue(ModConfig.mineVisualizePath);
        assertFalse(ModConfig.mineScaffoldAssist);
        assertEquals(0, ModConfig.getMineTargetCount(OreType.COAL));
        assertEquals(0, ModConfig.getMineTargetCount(OreType.DIAMOND));
        assertEquals(0.95, ModConfig.bridgeLookahead, 0.001);
        assertEquals(2, ModConfig.bridgeDownScan);
        assertEquals(1, ModConfig.bridgeDelayTicks);
        assertTrue(ModConfig.bridgeAvoidFeet);
        assertTrue(ModConfig.targetSkeleton);
        assertTrue(ModConfig.targetBox);
        assertFalse(ModConfig.targetRays);
        assertFalse(ModConfig.targetCountHud);
        assertFalse(ModConfig.oreCountHud);
        assertEquals(HudPosition.TOP_LEFT, ModConfig.countHudPosition);
        assertEquals(150.0, ModConfig.targetVisualizerRange, 0.0);
        assertEquals(150.0, ModConfig.oreVisualizerRange, 0.0);
        assertTrue(ModConfig.isMineOreEnabled(OreType.COAL));
        assertTrue(ModConfig.isMineOreEnabled(OreType.DIAMOND));
        assertFalse(ModConfig.isMineOreEnabled(OreType.QUARTZ));
        for (OreType type : OreType.values()) {
            assertTrue(ModConfig.isOreEnabled(type));
            assertEquals(type.defaultColor(), ModConfig.getOreColor(type));
        }
        assertEquals(5, ModConfig.ggMessages.length);
        assertEquals(5, ModConfig.replyMessages.length);
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
        ModConfig.cycleChoice(ModuleSetting.MELEE_ATTACK_POINT);
        ModConfig.cycleChoice(ModuleSetting.BLINK_ATTACK_POINT);
        ModConfig.reload();

        assertEquals(4.4, ModConfig.meleeRange, 0.001);
        assertEquals(5.3, ModConfig.blinkStep, 0.001);
        assertEquals(40, ModConfig.ggMinDelayTicks);
        assertEquals(40, ModConfig.ggMaxDelayTicks);
        assertTrue(ModConfig.meleeAnimals);
        assertEquals("health", ModConfig.meleePriority);
        assertEquals(AttackPoint.LEGS, ModConfig.meleeAttackPoint);
        assertEquals(AttackPoint.LEGS, ModConfig.blinkAttackPoint);
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
        ModConfig.toggle(ModuleSetting.MELEE_PEACEFUL);
        ModConfig.saveNumber(ModuleSetting.MELEE_MAX_TARGETS, 50.0);
        ModConfig.toggle(ModuleSetting.BLINK_PLAYERS);
        ModConfig.toggle(ModuleSetting.BLINK_MODDED);
        ModConfig.toggle(ModuleSetting.BLINK_ROTATE);
        ModConfig.toggle(ModuleSetting.BLINK_MULTI);
        ModConfig.toggle(ModuleSetting.BLINK_VISUALIZE);
        ModConfig.toggle(ModuleSetting.BLINK_PEACEFUL);
        ModConfig.saveNumber(ModuleSetting.BLINK_MAX_TARGETS, 49.0);
        ModConfig.cycleChoice(ModuleSetting.BLINK_PRIORITY);
        ModConfig.reload();

        assertTrue(ModConfig.meleeAnimals);
        assertTrue(ModConfig.meleeRotate);
        assertTrue(ModConfig.meleeMultiTarget);
        assertTrue(ModConfig.meleeVisualize);
        assertTrue(ModConfig.meleePeaceful);
        assertEquals(50, ModConfig.meleeMaxTargets);
        assertFalse(ModConfig.blinkPlayers);
        assertTrue(ModConfig.blinkModded);
        assertTrue(ModConfig.blinkRotate);
        assertTrue(ModConfig.blinkMultiTarget);
        assertTrue(ModConfig.blinkVisualize);
        assertTrue(ModConfig.blinkPeaceful);
        assertEquals(49, ModConfig.blinkMaxTargets);
        assertEquals("health", ModConfig.blinkPriority);
        assertFalse(ModConfig.blinkAnimals);
        assertFalse(ModConfig.meleeModded);
    }

    @Test
    public void persistsFiveEditableMessagesAndVisualizerSettings() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveGgMessages(new String[] {"a", "b", "c", "d", "e"});
        ModConfig.saveReplySettings("Alex", new String[] {"1", "2", "3", "4", "5"});
        ModConfig.saveModule(ModuleId.TARGET_VISUALIZER, true);
        ModConfig.toggle(ModuleSetting.TARGET_SKELETON);
        ModConfig.toggle(ModuleSetting.TARGET_BOX);
        ModConfig.toggle(ModuleSetting.TARGET_RAYS);
        ModConfig.toggle(ModuleSetting.TARGET_COUNT_HUD);
        ModConfig.cycleChoice(ModuleSetting.TARGET_COUNT_POSITION);
        ModConfig.saveNumber(ModuleSetting.TARGET_RANGE, 500.0);
        ModConfig.reload();

        assertEquals(5, ModConfig.ggMessages.length);
        assertEquals("e", ModConfig.ggMessages[4]);
        assertEquals("Alex", ModConfig.replyTarget);
        assertEquals("5", ModConfig.replyMessages[4]);
        assertTrue(ModConfig.targetVisualizer);
        assertFalse(ModConfig.targetSkeleton);
        assertFalse(ModConfig.targetBox);
        assertTrue(ModConfig.targetRays);
        assertTrue(ModConfig.targetCountHud);
        assertEquals(HudPosition.BOTTOM_LEFT, ModConfig.countHudPosition);
        assertEquals(500.0, ModConfig.targetVisualizerRange, 0.0);
    }

    @Test
    public void persistsOreVisualizerDistanceTypesAndColors() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveModule(ModuleId.ORE_VISUALIZER, true);
        ModConfig.saveNumber(ModuleSetting.ORE_RANGE, 500.0);
        ModConfig.toggle(ModuleSetting.ORE_DIAMOND);
        ModConfig.toggle(ModuleSetting.ORE_COUNT_HUD);
        ModConfig.cycleChoice(ModuleSetting.ORE_COUNT_POSITION);
        ModConfig.saveOreColor(OreType.DIAMOND, 0x123ABC);
        ModConfig.reload();

        assertTrue(ModConfig.oreVisualizer);
        assertEquals(500.0, ModConfig.oreVisualizerRange, 0.0);
        assertFalse(ModConfig.isOreEnabled(OreType.DIAMOND));
        assertTrue(ModConfig.isOreEnabled(OreType.IRON));
        assertTrue(ModConfig.oreCountHud);
        assertEquals(HudPosition.BOTTOM_LEFT, ModConfig.countHudPosition);
        assertEquals(0x123ABC, ModConfig.getOreColor(OreType.DIAMOND));
    }

    @Test
    public void persistsAutoMiningRouteSettingsAndOrePreset() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveModule(ModuleId.AUTO_BRIDGE, true);
        ModConfig.saveNumber(ModuleSetting.MINE_PATH_RANGE, 64.0);
        ModConfig.saveNumber(ModuleSetting.MINE_MANUAL_PAUSE, 55.0);
        ModConfig.toggle(ModuleSetting.MINE_VISUALIZE_PATH);
        ModConfig.toggle(ModuleSetting.MINE_SCAFFOLD_ASSIST);
        ModConfig.saveNumber(ModuleSetting.MINE_COAL_COUNT, 12.0);
        ModConfig.saveNumber(ModuleSetting.MINE_DIAMOND_COUNT, 3.0);
        ModConfig.saveNumber(ModuleSetting.BRIDGE_LOOKAHEAD, 1.24);
        ModConfig.saveNumber(ModuleSetting.BRIDGE_DOWN_SCAN, 4.0);
        ModConfig.saveNumber(ModuleSetting.BRIDGE_DELAY, 2.0);
        ModConfig.toggle(ModuleSetting.BRIDGE_AVOID_FEET);
        ModConfig.toggle(ModuleSetting.MINE_DIAMOND);
        ModConfig.toggle(ModuleSetting.MINE_QUARTZ);
        ModConfig.reload();

        assertTrue(ModConfig.autoBridge);
        assertEquals(64, ModConfig.minePathRange);
        assertEquals(55, ModConfig.mineManualPauseTicks);
        assertFalse(ModConfig.mineVisualizePath);
        assertTrue(ModConfig.mineScaffoldAssist);
        assertEquals(12, ModConfig.getMineTargetCount(OreType.COAL));
        assertEquals(3, ModConfig.getMineTargetCount(OreType.DIAMOND));
        assertEquals(1.25, ModConfig.bridgeLookahead, 0.001);
        assertEquals(4, ModConfig.bridgeDownScan);
        assertEquals(2, ModConfig.bridgeDelayTicks);
        assertFalse(ModConfig.bridgeAvoidFeet);
        assertFalse(ModConfig.isMineOreEnabled(OreType.DIAMOND));
        assertTrue(ModConfig.isMineOreEnabled(OreType.QUARTZ));
        assertTrue(ModConfig.isMineOreEnabled(OreType.IRON));
    }

    @Test
    public void revisionsTrackOnlyAutoMiningCandidateSettings() throws Exception {
        ModConfig.load(configFile());
        long revision = ModConfig.autoMineSelectionRevision();

        ModConfig.saveNumber(ModuleSetting.MINE_DELAY, 5.0);
        ModConfig.toggle(ModuleSetting.MINE_VISUALIZE_PATH);
        assertEquals(revision, ModConfig.autoMineSelectionRevision());

        ModConfig.saveNumber(ModuleSetting.MINE_PATH_RANGE, 64.0);
        assertEquals(++revision, ModConfig.autoMineSelectionRevision());
        ModConfig.saveNumber(ModuleSetting.MINE_PATH_RANGE, 64.0);
        assertEquals(revision, ModConfig.autoMineSelectionRevision());

        ModConfig.saveNumber(ModuleSetting.MINE_DIAMOND_COUNT, 3.0);
        assertEquals(++revision, ModConfig.autoMineSelectionRevision());
        ModConfig.toggle(ModuleSetting.MINE_DIAMOND);
        assertEquals(++revision, ModConfig.autoMineSelectionRevision());

        ModConfig.reload();
        assertEquals(++revision, ModConfig.autoMineSelectionRevision());
    }

    @Test
    public void reportsWhetherAnyAutoMiningOreTypeIsEnabled() throws Exception {
        ModConfig.load(configFile());
        assertTrue(ModConfig.hasEnabledMineOre());
        ModuleSetting[] oreToggles = {
            ModuleSetting.MINE_COAL, ModuleSetting.MINE_IRON,
            ModuleSetting.MINE_GOLD, ModuleSetting.MINE_REDSTONE,
            ModuleSetting.MINE_LAPIS, ModuleSetting.MINE_DIAMOND,
            ModuleSetting.MINE_EMERALD, ModuleSetting.MINE_QUARTZ
        };
        for (ModuleSetting setting : oreToggles) {
            if (ModConfig.getToggle(setting)) ModConfig.toggle(setting);
        }
        assertFalse(ModConfig.hasEnabledMineOre());

        ModConfig.toggle(ModuleSetting.MINE_QUARTZ);
        assertTrue(ModConfig.hasEnabledMineOre());
    }

    @Test
    public void persistsWweFlightModeAndSpeed() throws Exception {
        ModConfig.load(configFile());
        ModConfig.saveModule(ModuleId.FLIGHT, true);
        ModConfig.cycleChoice(ModuleSetting.FLIGHT_MODE);
        ModConfig.cycleChoice(ModuleSetting.FLIGHT_MODE);
        ModConfig.saveNumber(ModuleSetting.FLIGHT_SPEED, 4.46);
        ModConfig.reload();

        assertTrue(ModConfig.flight);
        assertEquals(FlightMode.HYPIXEL, ModConfig.flightMode);
        assertEquals(4.5, ModConfig.flightSpeed, 0.001);
        assertEquals("Hypixel", ModConfig.getChoice(ModuleSetting.FLIGHT_MODE));
    }

    @Test
    public void removesLegacyFlightModesAndResetsTheirSpeed() throws Exception {
        File file = configFile();
        Configuration legacy = new Configuration(file);
        legacy.get("flight", "elytraPackets", true).set(true);
        legacy.get("flight", "boatPackets", false).set(false);
        legacy.get("flight", "verticalSpeed", 0.2).set(0.2);
        legacy.get("flight", "speed", 0.32).set(0.32);
        legacy.save();

        ModConfig.load(file);

        assertEquals(FlightMode.STATIC, ModConfig.flightMode);
        assertEquals(1.0, ModConfig.flightSpeed, 0.001);
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse(saved.contains("elytraPackets"));
        assertFalse(saved.contains("boatPackets"));
        assertFalse(saved.contains("verticalSpeed"));
    }

    private File configFile() throws Exception {
        return new File(folder.getRoot(), "qazrlegacy.cfg");
    }
}

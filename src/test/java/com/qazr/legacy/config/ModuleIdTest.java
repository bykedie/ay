package com.qazr.legacy.config;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModuleIdTest {
    @Test
    public void parsesDisplayAndConfigNames() {
        assertEquals(ModuleId.AUTO_GG, ModuleId.parse("auto-gg"));
        assertEquals(ModuleId.MELEE_AURA, ModuleId.parse("meleeAura"));
        assertEquals(ModuleId.BLINK_STRIKE, ModuleId.parse("blink-strike"));
        assertEquals(ModuleId.FLIGHT, ModuleId.parse("flight"));
        assertEquals(ModuleId.CRITICALS, ModuleId.parse("criticals"));
    }

    @Test
    public void unknownModuleErrorsAreChinese() {
        try {
            ModuleId.parse("missing");
        } catch (IllegalArgumentException ex) {
            assertEquals("未知功能：missing", ex.getMessage());
            return;
        }
        throw new AssertionError("Expected an unknown module error");
    }

    @Test
    public void exposesStableTranslationKeysWithoutChangingConfigKeys() {
        assertEquals("meleeAura", ModuleId.MELEE_AURA.key());
        assertEquals("module.qazr.meleeAura", ModuleId.MELEE_AURA.translationKey());
        assertEquals("module.qazr.blinkStrike", ModuleId.BLINK_STRIKE.translationKey());
        assertEquals("module.qazr.flight", ModuleId.FLIGHT.translationKey());
    }

    @Test
    public void providesBuiltInChineseDisplayNames() {
        assertEquals("自动近战", ModuleId.MELEE_AURA.displayName());
        assertEquals("闪现攻击", ModuleId.BLINK_STRIKE.displayName());
        assertEquals("飞行", ModuleId.FLIGHT.displayName());
        assertEquals("自动暴击", ModuleId.CRITICALS.displayName());
        assertEquals("目标可视化", ModuleId.TARGET_VISUALIZER.displayName());
        assertEquals("自动搭路", ModuleId.AUTO_BRIDGE.displayName());
        assertEquals("矿物可视化", ModuleId.ORE_VISUALIZER.displayName());
    }

    @Test
    public void mapsSettingsToTheirOwningModules() {
        assertEquals(14, ModuleSetting.forModule(ModuleId.MELEE_AURA).length);
        assertEquals(17, ModuleSetting.forModule(ModuleId.BLINK_STRIKE).length);
        assertEquals(4, ModuleSetting.forModule(ModuleId.FLIGHT).length);
        assertEquals(0, ModuleSetting.forModule(ModuleId.CRITICALS).length);
        assertEquals(6, ModuleSetting.forModule(ModuleId.TARGET_VISUALIZER).length);
        assertEquals(19, ModuleSetting.forModule(ModuleId.ORE_VISUALIZER).length);
        assertEquals(20, ModuleSetting.forModule(ModuleId.AUTO_MINE).length);
        assertEquals(4, ModuleSetting.forModule(ModuleId.AUTO_BRIDGE).length);
        assertEquals(3, ModuleSetting.forModule(ModuleId.AUTO_GG).length);
        assertEquals(2, ModuleSetting.forModule(ModuleId.AUTO_REPLY).length);
        assertTrue(ModuleSetting.MELEE_RANGE.type() == ModuleSetting.Type.NUMBER);
    }

    @Test
    public void providesHelpForEverySetting() {
        for (ModuleSetting setting : ModuleSetting.values()) {
            assertTrue(setting.name(), setting.description() != null && !setting.description().trim().isEmpty());
        }
    }
}

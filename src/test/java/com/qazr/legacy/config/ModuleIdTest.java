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
        assertEquals(ModuleId.CRITICALS, ModuleId.parse("criticals"));
    }

    @Test
    public void exposesStableTranslationKeysWithoutChangingConfigKeys() {
        assertEquals("meleeAura", ModuleId.MELEE_AURA.key());
        assertEquals("module.qazr.meleeAura", ModuleId.MELEE_AURA.translationKey());
        assertEquals("module.qazr.blinkStrike", ModuleId.BLINK_STRIKE.translationKey());
    }

    @Test
    public void providesBuiltInChineseDisplayNames() {
        assertEquals("自动近战", ModuleId.MELEE_AURA.displayName());
        assertEquals("闪现攻击", ModuleId.BLINK_STRIKE.displayName());
        assertEquals("自动暴击", ModuleId.CRITICALS.displayName());
    }

    @Test
    public void mapsSettingsToTheirOwningModules() {
        assertEquals(12, ModuleSetting.forModule(ModuleId.MELEE_AURA).length);
        assertEquals(15, ModuleSetting.forModule(ModuleId.BLINK_STRIKE).length);
        assertEquals(0, ModuleSetting.forModule(ModuleId.CRITICALS).length);
        assertTrue(ModuleSetting.MELEE_RANGE.type() == ModuleSetting.Type.NUMBER);
    }
}

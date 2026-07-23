package com.qazr.legacy.config;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ModuleIdTest {
    @Test
    public void parsesDisplayAndConfigNames() {
        assertEquals(ModuleId.AUTO_GG, ModuleId.parse("auto-gg"));
        assertEquals(ModuleId.MELEE_AURA, ModuleId.parse("meleeAura"));
        assertEquals(ModuleId.CRITICALS, ModuleId.parse("criticals"));
    }
}

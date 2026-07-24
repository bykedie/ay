package com.qazr.legacy.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalizationTest {
    @Test
    public void providesChineseNamesForEveryModuleAndMenuState() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("assets/qazrlegacy/lang/zh_cn.lang");
        assertNotNull(stream);
        Properties translations = new Properties();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            translations.load(reader);
        }

        for (ModuleId id : ModuleId.values()) assertNotNull(translations.getProperty(id.translationKey()));
        assertEquals("自动近战", translations.getProperty(ModuleId.MELEE_AURA.translationKey()));
        assertEquals("闪现攻击", translations.getProperty(ModuleId.BLINK_STRIKE.translationKey()));
        assertEquals("已开启", translations.getProperty("gui.qazr.enabled"));
        assertEquals("已关闭", translations.getProperty("gui.qazr.disabled"));
        assertEquals("范围", translations.getProperty("command.qazr.range"));
    }
}

package com.qazr.legacy.gui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ColorEditorScreenTest {
    @Test
    public void parsesSixDigitRgbColors() {
        assertEquals(Integer.valueOf(0x4DE7E7), ColorEditorScreen.parseColor("#4DE7E7"));
        assertEquals(Integer.valueOf(0x123ABC), ColorEditorScreen.parseColor("123abc"));
        assertNull(ColorEditorScreen.parseColor("#123"));
        assertNull(ColorEditorScreen.parseColor("#GGGGGG"));
    }
}

package com.qazr.legacy.control;

import org.junit.Test;
import org.lwjgl.input.Keyboard;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientControlsTest {
    @Test
    public void onlyMigratesTheOldRightShiftPanelDefault() {
        assertTrue(ClientControls.DEFAULT_MENU_KEY == Keyboard.KEY_GRAVE);
        assertTrue(ClientControls.shouldMigrateMenuKey(Keyboard.KEY_RSHIFT));
        assertFalse(ClientControls.shouldMigrateMenuKey(Keyboard.KEY_GRAVE));
        assertFalse(ClientControls.shouldMigrateMenuKey(Keyboard.KEY_P));
    }
}

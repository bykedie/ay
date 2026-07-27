package com.qazr.legacy.gui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ModuleControlScreenTest {
    @Test
    public void gameModeControlSwitchesBetweenSurvivalAndCreative() {
        assertEquals("/gamemode 1", ModuleControlScreen.nextGameModeCommand(false));
        assertEquals("/gamemode 0", ModuleControlScreen.nextGameModeCommand(true));
    }
}

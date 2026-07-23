package com.qazr.legacy.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CombatMathTest {
    @Test
    public void calculatesCardinalRotations() {
        assertEquals(-90.0F, CombatMath.yaw(1.0, 0.0), 0.001F);
        assertEquals(0.0F, CombatMath.yaw(0.0, 1.0), 0.001F);
        assertEquals(-45.0F, CombatMath.pitch(1.0, 1.0, 0.0), 0.001F);
    }

    @Test
    public void scoresConfiguredPriority() {
        assertEquals(2.0, CombatMath.score(2.0, 10.0F, "distance"), 0.0);
        assertEquals(10.0, CombatMath.score(2.0, 10.0F, "health"), 0.0);
    }
}

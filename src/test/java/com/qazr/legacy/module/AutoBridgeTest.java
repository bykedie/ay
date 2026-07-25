package com.qazr.legacy.module;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AutoBridgeTest {
    @Test
    public void mapsForwardMovementToYawDirection() {
        assertOffset(0.0, 1.0, AutoBridge.movementOffset(0.0F, 1.0F, 0.0F));
        assertOffset(-1.0, 0.0, AutoBridge.movementOffset(90.0F, 1.0F, 0.0F));
        assertOffset(0.0, -1.0, AutoBridge.movementOffset(180.0F, 1.0F, 0.0F));
    }

    @Test
    public void normalizesDiagonalMovement() {
        double[] offset = AutoBridge.movementOffset(0.0F, 1.0F, 1.0F);

        assertEquals(1.0, Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]), 0.0001);
        assertEquals(offset[0], offset[1], 0.0001);
    }

    @Test
    public void supportsJumpAndFallPlacementOffsets() {
        assertEquals(63, AutoBridge.candidateY(64.8, 0));
        assertEquals(62, AutoBridge.candidateY(64.8, 1));
        assertEquals(60, AutoBridge.candidateY(64.8, 3));
    }

    private static void assertOffset(double x, double z, double[] offset) {
        assertEquals(x, offset[0], 0.0001);
        assertEquals(z, offset[1], 0.0001);
    }
}

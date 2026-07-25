package com.qazr.legacy.module;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightControllerTest {
    @Test
    public void mapsForwardMovementToYawDirection() {
        assertOffset(0.0, 0.32, FlightController.movementFor(0.0F, 1.0, 0.0, 0.32));
        assertOffset(-0.32, 0.0, FlightController.movementFor(90.0F, 1.0, 0.0, 0.32));
        assertOffset(0.0, -0.32, FlightController.movementFor(180.0F, 1.0, 0.0, 0.32));
    }

    @Test
    public void normalizesDiagonalMovementBeforeApplyingSpeed() {
        double[] movement = FlightController.movementFor(0.0F, 1.0, 1.0, 0.40);

        assertEquals(0.40, Math.sqrt(movement[0] * movement[0] + movement[1] * movement[1]), 0.0001);
        assertEquals(movement[0], movement[1], 0.0001);
    }

    private static void assertOffset(double x, double z, double[] movement) {
        assertEquals(x, movement[0], 0.0001);
        assertEquals(z, movement[1], 0.0001);
    }
}

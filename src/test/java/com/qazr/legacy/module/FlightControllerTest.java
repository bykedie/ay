package com.qazr.legacy.module;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightControllerTest {
    @Test
    public void restoresFlyingOnlyWhenTheCurrentModeStillAllowsIt() {
        assertEquals(true, FlightController.restoredFlying(true, true, true, true));
        assertEquals(false, FlightController.restoredFlying(true, true, false, true));
        assertEquals(false, FlightController.restoredFlying(false, true, true, true));
        assertEquals(true, FlightController.restoredFlying(false, false, true, true));
    }

    @Test
    public void mapsConfiguredSpeedToVanillaFlySpeed() {
        assertEquals(0.032F, FlightController.flySpeedFor(0.32), 0.0001F);
        assertEquals(0.12F, FlightController.flySpeedFor(1.20), 0.0001F);
    }

    @Test
    public void clearsInjectedMotionWhenFlightControlEnds() {
        assertEquals(0.0, FlightController.clearedMotion()[0], 0.0);
        assertEquals(0.0, FlightController.clearedMotion()[1], 0.0);
        assertEquals(0.0, FlightController.clearedMotion()[2], 0.0);
    }

    @Test
    public void clearsBoatControlWhenSwitchingToNormalFlight() {
        assertEquals(true, FlightController.shouldClearBoatForNormalFlight(true, true));
        assertEquals(false, FlightController.shouldClearBoatForNormalFlight(false, true));
        assertEquals(false, FlightController.shouldClearBoatForNormalFlight(true, false));
    }

    @Test
    public void resolvesVerticalKeysWithoutDrift() {
        assertEquals(0.20, FlightController.verticalMotion(true, false, 0.20), 0.0001);
        assertEquals(-0.20, FlightController.verticalMotion(false, true, 0.20), 0.0001);
        assertEquals(0.0, FlightController.verticalMotion(false, false, 0.20), 0.0001);
        assertEquals(0.0, FlightController.verticalMotion(true, true, 0.20), 0.0001);
    }

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

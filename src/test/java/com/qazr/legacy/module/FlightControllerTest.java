package com.qazr.legacy.module;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightControllerTest {
    @Test
    public void mapsWweSpeedToVanillaFlySpeed() {
        assertEquals(0.10F, FlightController.flySpeedFor(1.0), 0.0001F);
        assertEquals(1.0F, FlightController.flySpeedFor(10.0), 0.0001F);
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

    @Test
    public void reproducesHypixelThreeTickPositionPulse() {
        assertEquals(3.0E-9, FlightController.hypixelOffsetForTick(2), 1.0E-20);
        assertEquals(0.0, FlightController.hypixelOffsetForTick(3), 0.0);
    }

    @Test
    public void safeLandingCapsFastDescentAndSlowsNearTheGround() {
        assertEquals(-0.35, FlightController.safeLandingMotion(-4.0, Double.POSITIVE_INFINITY), 0.0001);
        assertEquals(-0.35, FlightController.safeLandingMotion(0.0, Double.POSITIVE_INFINITY), 0.0001);
        assertEquals(-0.35, FlightController.safeLandingMotion(-1.0, 3.0), 0.0001);
        assertEquals(-0.08, FlightController.safeLandingMotion(-1.0, 0.5), 0.0001);
        assertEquals(-0.03, FlightController.safeLandingMotion(-1.0, 0.03), 0.0001);
        assertEquals(0.0, FlightController.safeLandingMotion(-1.0, 0.0), 0.0001);
    }

    @Test
    public void confirmsGroundOnlyWhenTheNextDescentStepCanTouchIt() {
        assertEquals(false, FlightController.shouldConfirmLanding(Double.POSITIVE_INFINITY, -0.08));
        assertEquals(false, FlightController.shouldConfirmLanding(0.25, -0.08));
        assertEquals(true, FlightController.shouldConfirmLanding(0.09, -0.08));
        assertEquals(true, FlightController.shouldConfirmLanding(0.0, 0.0));
        assertEquals(64.0, FlightController.landingPositionY(64.09, 0.09), 0.0001);
        assertEquals(64.0, FlightController.landingPositionY(64.0, Double.POSITIVE_INFINITY), 0.0);
        assertEquals(true, FlightController.shouldResetLandingConfirmation(true, 0.3));
        assertEquals(true, FlightController.shouldResetLandingConfirmation(
            true, Double.POSITIVE_INFINITY));
        assertEquals(false, FlightController.shouldResetLandingConfirmation(true, 0.1));
        assertEquals(false, FlightController.shouldResetLandingConfirmation(false, 2.0));
    }

    private static void assertOffset(double x, double z, double[] movement) {
        assertEquals(x, movement[0], 0.0001);
        assertEquals(z, movement[1], 0.0001);
    }
}

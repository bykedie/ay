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
        assertEquals(true, FlightController.landingRequested(false, true));
        assertEquals(false, FlightController.landingRequested(true, true));
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
    public void hypixelPreservesPhysicalGroundState() {
        assertEquals(false, FlightController.hypixelPacketOnGround(false));
        assertEquals(true, FlightController.hypixelPacketOnGround(true));
    }

    @Test
    public void safeLandingDescendsQuicklyAndStopsAtTheSurface() {
        assertEquals(-1.0, FlightController.safeLandingMotion(
            -4.0, Double.POSITIVE_INFINITY, 4.0), 0.0001);
        assertEquals(-0.35, FlightController.safeLandingMotion(
            0.0, Double.POSITIVE_INFINITY, 0.0), 0.0001);
        assertEquals(-1.0, FlightController.safeLandingMotion(-1.0, 3.0, 1.0), 0.0001);
        assertEquals(-0.5, FlightController.safeLandingMotion(-1.0, 0.5, 1.0), 0.0001);
        assertEquals(-0.03, FlightController.safeLandingMotion(-1.0, 0.03, 1.0), 0.0001);
        assertEquals(0.0, FlightController.safeLandingMotion(-1.0, 0.0, 1.0), 0.0001);
    }

    @Test
    public void disablingFlightKeepsControlUntilRealGroundContact() {
        assertEquals(true, FlightController.shouldContinueLandingAfterDisable(
            true, false, false, Double.POSITIVE_INFINITY));
        assertEquals(true, FlightController.shouldContinueLandingAfterDisable(
            true, false, false, 0.25));
        assertEquals(false, FlightController.shouldContinueLandingAfterDisable(
            true, false, false, 0.0));
        assertEquals(false, FlightController.shouldContinueLandingAfterDisable(
            true, true, false, Double.POSITIVE_INFINITY));
        assertEquals(false, FlightController.shouldContinueLandingAfterDisable(
            false, false, false, Double.POSITIVE_INFINITY));
        assertEquals(false, FlightController.shouldContinueLandingAfterDisable(
            true, false, true, Double.POSITIVE_INFINITY));
    }

    private static void assertOffset(double x, double z, double[] movement) {
        assertEquals(x, movement[0], 0.0001);
        assertEquals(z, movement[1], 0.0001);
    }
}

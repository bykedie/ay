package com.qazr.legacy.module;

import com.qazr.legacy.config.FlightMode;
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
        assertEquals(-0.35, FlightController.verticalMotion(
            false, true, 0.20, 0.35), 0.0001);
        assertEquals(0.0, FlightController.verticalMotion(false, false, 0.20), 0.0001);
        assertEquals(0.0, FlightController.verticalMotion(true, true, 0.20), 0.0001);
        assertEquals(true, FlightController.landingRequested(false, true));
        assertEquals(false, FlightController.landingRequested(true, true));
    }

    @Test
    public void exposesOnlySupportedFlightModes() {
        assertEquals(FlightMode.VANILLA, FlightMode.next(FlightMode.STATIC));
        assertEquals(FlightMode.STATIC, FlightMode.next(FlightMode.VANILLA));
        assertEquals(FlightMode.STATIC, FlightMode.fromKey("hypixel"));
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
    public void staticFrameOwnsCurrentTickMovementWithoutVanillaAcceleration() {
        FlightController.StaticFrame stopped = FlightController.staticFrameFor(
            0.0F, 1.0F, 0.0F, false, false, 0.0);
        assertEquals(0.0, stopped.motionX, 0.0001);
        assertEquals(0.0, stopped.motionY, 0.0001);
        assertEquals(0.0, stopped.motionZ, 0.0001);
        assertEquals(0.0F, stopped.vanillaForward, 0.0F);
        assertEquals(0.0F, stopped.vanillaStrafe, 0.0F);
        assertEquals(false, stopped.vanillaJump);

        FlightController.StaticFrame moving = FlightController.staticFrameFor(
            0.0F, 1.0F, 0.0F, false, false, 0.4);
        assertEquals(0.0, moving.motionX, 0.0001);
        assertEquals(0.4, moving.motionZ, 0.0001);
        assertEquals(0.4, Math.sqrt(moving.motionX * moving.motionX
            + moving.motionZ * moving.motionZ), 0.0001);
    }

    @Test
    public void staticFrameAppliesReleaseReverseAndNeutralVerticalInputImmediately() {
        FlightController.StaticFrame released = FlightController.staticFrameFor(
            0.0F, 0.0F, 0.0F, false, false, 0.4);
        assertEquals(0.0, released.motionX, 0.0001);
        assertEquals(0.0, released.motionZ, 0.0001);

        FlightController.StaticFrame reversed = FlightController.staticFrameFor(
            0.0F, -1.0F, 0.0F, false, false, 0.4);
        assertEquals(-0.4, reversed.motionZ, 0.0001);

        FlightController.StaticFrame neutral = FlightController.staticFrameFor(
            0.0F, 0.0F, 0.0F, true, true, 0.4, 0.7);
        assertEquals(0.0, neutral.motionY, 0.0001);
        assertEquals(false, neutral.vanillaJump);
    }

    @Test
    public void safeLandingDescendsQuicklyAndStopsAtTheSurface() {
        assertEquals(-1.0, FlightController.safeLandingMotion(
            -4.0, Double.POSITIVE_INFINITY, 0.8), 0.0001);
        assertEquals(-0.8, FlightController.safeLandingMotion(
            0.0, Double.POSITIVE_INFINITY, 0.8), 0.0001);
        assertEquals(-0.35, FlightController.safeLandingMotion(
            0.0, Double.POSITIVE_INFINITY, 0.35), 0.0001);
        assertEquals(-1.0, FlightController.safeLandingMotion(-1.0, 3.0, 1.0), 0.0001);
        assertEquals(-0.5, FlightController.safeLandingMotion(-1.0, 0.5, 1.0), 0.0001);
        assertEquals(-0.03, FlightController.safeLandingMotion(-1.0, 0.03, 1.0), 0.0001);
        assertEquals(0.0, FlightController.safeLandingMotion(-1.0, 0.0, 1.0), 0.0001);
    }

    @Test
    public void vanillaLandingControlStartsBeforeTravel() {
        assertEquals(true, FlightController.preTravelControlRequired(
            FlightMode.VANILLA, false, true));
        assertEquals(false, FlightController.preTravelControlRequired(
            FlightMode.VANILLA, true, true));
        assertEquals(false, FlightController.preTravelControlRequired(
            FlightMode.VANILLA, false, false));
        assertEquals(false, FlightController.preTravelControlRequired(
            FlightMode.STATIC, false, true));
        assertEquals(-0.05, FlightController.vanillaPreTravelMotion(-0.35, 0.10F), 0.0001);
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

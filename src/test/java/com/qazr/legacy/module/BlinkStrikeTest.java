package com.qazr.legacy.module;

import com.qazr.legacy.util.BlinkPath;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlinkStrikeTest {
    @Test
    public void rejectsOrdinaryFreeFallButAllowsControlledAirborneOrigins() {
        assertEquals(false, BlinkStrike.safeAirborneOrigin(false, false, false, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(true, false, false, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(false, false, true, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(false, false, false, true));
    }

    @Test
    public void groundsTransportPacketsForGroundAndFlightOrigins() {
        assertEquals(true, BlinkStrike.transportOnGround(true, false));
        assertEquals(true, BlinkStrike.transportOnGround(false, true));
        assertEquals(false, BlinkStrike.transportOnGround(false, false));
    }

    @Test
    public void offersDirectAndDoglegRoutesForUnevenTargets() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        BlinkPath.Point destination = new BlinkPath.Point(8.0, 58.0, 4.0);
        List<List<BlinkPath.Point>> routes = BlinkStrike.routeWaypoints(origin, destination);

        assertEquals(4, routes.size());
        assertEquals(1, routes.get(0).size());
        assertEquals(64.0, routes.get(1).get(0).y, 0.0);
        assertEquals(58.0, routes.get(2).get(0).y, 0.0);
        assertEquals(65.0, routes.get(3).get(0).y, 0.0);
    }

    @Test
    public void buildsEveryDoglegSegmentWithBoundedPacketSteps() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        BlinkPath.Point destination = new BlinkPath.Point(8.0, 58.0, 0.0);
        List<BlinkPath.Point> path = BlinkStrike.buildPath(origin,
            BlinkStrike.routeWaypoints(origin, destination).get(1), 3.0);

        BlinkPath.Point previous = origin;
        for (BlinkPath.Point point : path) {
            assertTrue(previous.distanceTo(point) <= 3.0);
            previous = point;
        }
        assertEquals(destination.x, previous.x, 0.0);
        assertEquals(destination.y, previous.y, 0.0);
        assertEquals(destination.z, previous.z, 0.0);
    }

    @Test
    public void generatesNearSideCandidateFirst() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(origin, 10.0, 61.0, 0.0, 2.5);

        assertEquals(16, candidates.size());
        assertTrue(candidates.get(0).x < 10.0);
        assertEquals(61.0, candidates.get(0).y, 0.0);
    }

    @Test
    public void includesOriginHeightFallbackForUnevenTerrain() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(origin, 10.0, 50.0, 0.0, 2.5);

        assertEquals(64.0, candidates.get(8).y, 0.0);
    }

    @Test
    public void keepsCandidatesWithinConfiguredAttackRadius() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(origin, 10.0, 64.0, 4.0, 3.0);

        for (int i = 0; i < 8; i++) {
            double dx = candidates.get(i).x - 10.0;
            double dz = candidates.get(i).z - 4.0;
            assertEquals(2.16, Math.sqrt(dx * dx + dz * dz), 0.0001);
        }
    }

    @Test
    public void boundsExpensivePathPlanningPerTick() {
        assertEquals(4, BlinkStrike.planningBudget(1));
        assertEquals(6, BlinkStrike.planningBudget(3));
        assertEquals(50, BlinkStrike.planningBudget(50));
    }

    @Test
    public void scansPastBlockedPriorityTargets() {
        assertEquals(50, BlinkStrike.candidateScanLimit(1));
        assertEquals(50, BlinkStrike.candidateScanLimit(50));
    }

    @Test
    public void retracesTheAcceptedRouteAfterARemoteCorrection() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin,
            new BlinkPath.Point(20.0, 64.0, 0.0), 4.0);
        List<BlinkPath.Point> returning = BlinkStrike.recoveryReturnPath(origin, origin,
            new BlinkPath.Point(20.5, 64.0, 0.0),
            java.util.Collections.singletonList(outward), 4.0);

        assertTrue(!returning.isEmpty());
        assertEquals(origin.x, returning.get(returning.size() - 1).x, 0.0);
        BlinkPath.Point previous = new BlinkPath.Point(20.5, 64.0, 0.0);
        for (BlinkPath.Point point : returning) {
            assertTrue(previous.distanceTo(point) <= 4.0);
            previous = point;
        }
    }

    @Test
    public void recoversFromAnIntermediateOutboundPacket() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin,
            new BlinkPath.Point(20.0, 64.0, 0.0), 4.0);
        List<BlinkPath.Point> returning = BlinkStrike.recoveryReturnPath(origin, origin,
            new BlinkPath.Point(12.5, 64.0, 0.0),
            java.util.Collections.singletonList(outward), 4.0);

        assertTrue(!returning.isEmpty());
        assertEquals(origin.x, returning.get(returning.size() - 1).x, 0.0);
    }

    @Test
    public void recoversWhenTheServerAcceptsOnlyTheFirstDefaultStep() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin,
            new BlinkPath.Point(12.0, 64.0, 0.0), 4.0);
        List<BlinkPath.Point> returning = BlinkStrike.recoveryReturnPath(origin, origin,
            outward.get(0), java.util.Collections.singletonList(outward), 4.0);

        assertTrue(!returning.isEmpty());
        assertEquals(origin.x, returning.get(returning.size() - 1).x, 0.0);
    }

    @Test
    public void ignoresOrdinaryMovementAndUnrelatedTeleports() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<List<BlinkPath.Point>> routes = java.util.Collections.singletonList(
            BlinkPath.interpolate(origin, new BlinkPath.Point(20.0, 64.0, 0.0), 4.0));

        assertTrue(BlinkStrike.recoveryReturnPath(origin, origin,
            new BlinkPath.Point(2.5, 64.0, 0.0), routes, 4.0).isEmpty());
        assertTrue(BlinkStrike.recoveryReturnPath(origin, origin,
            new BlinkPath.Point(8.0, 64.0, 8.0), routes, 4.0).isEmpty());
        assertTrue(BlinkStrike.recoveryReturnPath(origin,
            new BlinkPath.Point(19.0, 64.0, 0.0),
            new BlinkPath.Point(20.5, 64.0, 0.0), routes, 4.0).isEmpty());
    }

    @Test
    public void skipsExpiredStrikePlansInsteadOfCountingThemAsHits() {
        assertEquals(false, BlinkStrike.strikePlanStillUsable(false, true, true));
        assertEquals(false, BlinkStrike.strikePlanStillUsable(true, false, true));
        assertEquals(false, BlinkStrike.strikePlanStillUsable(true, true, false));
        assertEquals(true, BlinkStrike.strikePlanStillUsable(true, true, true));
    }
}

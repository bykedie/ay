package com.qazr.legacy.module;

import com.qazr.legacy.util.BlinkPath;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlinkStrikeTest {
    @Test
    public void rejectsOrdinaryFreeFallButAllowsControlledAirborneOrigins() {
        assertEquals(false, BlinkStrike.safeAirborneOrigin(false, false, false, false, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(true, false, false, false, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(false, false, true, false, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(false, false, false, true, false));
        assertEquals(true, BlinkStrike.safeAirborneOrigin(false, false, false, false, true));
    }

    @Test
    public void groundsTransportPacketsOnlyForActuallyGroundedOrigins() {
        assertEquals(true, BlinkStrike.transportOnGround(true));
        assertEquals(false, BlinkStrike.transportOnGround(false));
        assertEquals(true, BlinkStrike.actualGrounded(true, false, false));
        assertEquals(false, BlinkStrike.actualGrounded(true, true, false));
        assertEquals(true, BlinkStrike.actualGrounded(false, true, true));
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
    public void returnPacketsContainTheOriginExactlyOnce() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin,
            new BlinkPath.Point(12.0, 64.0, 0.0), 4.0);
        List<BlinkPath.Point> returning = BlinkStrike.completeReturnPath(origin, outward, outward.size());

        assertEquals(3, returning.size());
        assertEquals(origin, returning.get(returning.size() - 1));
        assertEquals(1, returning.stream().filter(point -> point == origin).count());
        assertEquals(origin, BlinkStrike.completeReturnPath(origin, outward, 0).get(0));
    }

    @Test
    public void generatesNearSideCandidateFirst() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(origin, 10.0, 61.0, 0.0, 2.5);

        assertEquals(16, candidates.size());
        assertTrue(candidates.get(0).x < 10.0);
        assertEquals(64.0, candidates.get(0).y, 0.0);
    }

    @Test
    public void includesOriginHeightFallbackForUnevenTerrain() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.0);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(origin, 10.0, 50.0, 0.0, 2.5);

        assertEquals(64.0, candidates.get(0).y, 0.0);
        assertEquals(50.0, candidates.get(8).y, 0.0);
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
    public void predictsHorizontalMotionWithoutOvershootingFloatingTargetsVertically() {
        BlinkPath.Point predicted = BlinkStrike.predictedTargetPosition(10.0, 61.0, 4.0,
            0.25, -0.5, 3);

        assertEquals(10.75, predicted.x, 0.0);
        assertEquals(61.0, predicted.y, 0.0);
        assertEquals(2.5, predicted.z, 0.0);
        assertEquals(61.0, BlinkStrike.predictedTargetPosition(10.0, 61.0, 4.0,
            0.25, -0.5, -2).y, 0.0);
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
    public void reachabilityCacheIsBoundToFeetCellAndFlightState() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertEquals(true, BlinkStrike.sameReachabilityContext(feet, false, feet, false));
        assertEquals(false, BlinkStrike.sameReachabilityContext(
            feet, false, feet.up(2), true));
        assertEquals(false, BlinkStrike.sameReachabilityContext(feet, false, feet, true));
        assertEquals(false, BlinkStrike.sameReachabilityContext(null, false, feet, false));
    }

    @Test
    public void usesVanillaEntityReachForDirectAttacks() {
        assertEquals(3.0, BlinkStrike.directAttackReach(false), 0.0);
        assertEquals(6.0, BlinkStrike.directAttackReach(true), 0.0);
    }

    @Test
    public void mirrorsVanillaServerAttackEnvelopeBoundaries() {
        assertEquals(true, BlinkStrike.serverAttackEnvelopeAllows(35.999D, true));
        assertEquals(false, BlinkStrike.serverAttackEnvelopeAllows(36.0D, true));
        assertEquals(true, BlinkStrike.serverAttackEnvelopeAllows(8.999D, false));
        assertEquals(false, BlinkStrike.serverAttackEnvelopeAllows(9.0D, false));

        assertEquals(false, BlinkStrike.serverAttackEnvelopeAllows(10.8025D, false));
        assertEquals(true, BlinkStrike.serverAttackEnvelopeAllows(3.24D, true));
    }

    @Test
    public void skipsOriginHeightCandidateThatVanillaWouldReject() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 64.0, 0.5);
        List<BlinkPath.Point> candidates = BlinkStrike.candidatePositions(
            origin, 10.81, 66.75, 0.5, 2.5);

        assertEquals(false, BlinkStrike.serverAttackCandidateAllows(
            candidates.get(0), 10.81, 66.75, 0.5, false));
        assertEquals(true, BlinkStrike.serverAttackCandidateAllows(
            candidates.get(8), 10.81, 66.75, 0.5, true));
    }

    @Test
    public void skipsExpiredStrikePlansInsteadOfCountingThemAsHits() {
        assertEquals(false, BlinkStrike.strikePlanStillUsable(false, true, true));
        assertEquals(false, BlinkStrike.strikePlanStillUsable(true, false, true));
        assertEquals(false, BlinkStrike.strikePlanStillUsable(true, true, false));
        assertEquals(true, BlinkStrike.strikePlanStillUsable(true, true, true));
    }
}

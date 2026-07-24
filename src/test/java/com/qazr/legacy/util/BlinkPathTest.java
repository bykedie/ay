package com.qazr.legacy.util;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlinkPathTest {
    @Test
    public void splitsMovementIntoBoundedStepsAndEndsAtTarget() {
        BlinkPath.Point start = new BlinkPath.Point(0.0, 4.0, 0.0);
        BlinkPath.Point target = new BlinkPath.Point(10.0, 4.0, 0.0);
        List<BlinkPath.Point> path = BlinkPath.interpolate(start, target, 3.0);

        assertEquals(4, path.size());
        BlinkPath.Point previous = start;
        for (BlinkPath.Point point : path) {
            assertTrue(previous.distanceTo(point) <= 3.0);
            previous = point;
        }
        assertEquals(10.0, path.get(path.size() - 1).x, 0.0);
    }

    @Test
    public void returnsNoPacketsForAnUnchangedPosition() {
        BlinkPath.Point point = new BlinkPath.Point(1.0, 2.0, 3.0);
        assertTrue(BlinkPath.interpolate(point, point, 4.0).isEmpty());
    }

    @Test
    public void boundsDiagonalMovementByThreeDimensionalDistance() {
        BlinkPath.Point start = new BlinkPath.Point(0.0, 0.0, 0.0);
        List<BlinkPath.Point> path = BlinkPath.interpolate(start, new BlinkPath.Point(6.0, 6.0, 6.0), 4.0);
        BlinkPath.Point previous = start;
        for (BlinkPath.Point point : path) {
            assertTrue(previous.distanceTo(point) <= 4.0);
            previous = point;
        }
    }

    @Test
    public void retracesTheOutwardPathAndReturnsToOrigin() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 0.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin, new BlinkPath.Point(9.0, 0.0, 0.0), 4.0);
        List<BlinkPath.Point> roundTrip = BlinkPath.roundTrip(origin, outward);

        assertEquals(6, roundTrip.size());
        assertEquals(9.0, roundTrip.get(2).x, 0.0);
        assertEquals(0.0, roundTrip.get(roundTrip.size() - 1).x, 0.0);
        for (int i = 1; i < roundTrip.size(); i++) {
            assertTrue(roundTrip.get(i - 1).distanceTo(roundTrip.get(i)) <= 4.0);
        }
    }

    @Test
    public void returnsSafelyAfterOnlyPartOfThePathWasSent() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 0.0, 0.0);
        List<BlinkPath.Point> outward = BlinkPath.interpolate(origin, new BlinkPath.Point(12.0, 0.0, 0.0), 4.0);
        List<BlinkPath.Point> returning = BlinkPath.returnPath(origin, outward, 2);

        assertEquals(2, returning.size());
        assertEquals(outward.get(0).x, returning.get(0).x, 0.0);
        assertEquals(origin.x, returning.get(1).x, 0.0);
    }

    @Test
    public void approachesTargetAtConfiguredAttackDistance() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 0.0, 0.0);
        BlinkPath.Point target = new BlinkPath.Point(12.0, 0.0, 0.0);
        BlinkPath.Point destination = BlinkPath.approach(origin, target, 2.5);

        assertEquals(9.5, origin.distanceTo(destination), 0.0);
        assertEquals(2.5, destination.distanceTo(target), 0.0);
        assertEquals(origin.x, BlinkPath.approach(origin, new BlinkPath.Point(2.0, 0.0, 0.0), 2.5).x, 0.0);
    }

    @Test
    public void limitsPredictedTargetsToConfiguredRange() {
        BlinkPath.Point origin = new BlinkPath.Point(0.0, 0.0, 0.0);
        BlinkPath.Point predicted = new BlinkPath.Point(15.0, 0.0, 0.0);
        BlinkPath.Point limited = BlinkPath.limitDistance(origin, predicted, 12.0);
        BlinkPath.Point destination = BlinkPath.approach(origin, limited, 2.5);

        assertEquals(12.0, origin.distanceTo(limited), 0.0);
        assertEquals(9.5, origin.distanceTo(destination), 0.0);
        assertEquals(2.5, destination.distanceTo(limited), 0.0);
        assertEquals(predicted, BlinkPath.limitDistance(origin, predicted, 20.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidStepLength() {
        BlinkPath.interpolate(new BlinkPath.Point(0.0, 0.0, 0.0),
            new BlinkPath.Point(1.0, 0.0, 0.0), 0.0);
    }
}

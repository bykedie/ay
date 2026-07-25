package com.qazr.legacy.module;

import com.qazr.legacy.util.BlinkPath;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlinkStrikeTest {
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
}

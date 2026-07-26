package com.qazr.legacy.module;

import com.qazr.legacy.config.OreType;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoMinerTest {
    @Test
    public void requiresBothPlayerHeightCellsToBeClearable() {
        assertEquals(0, AutoMiner.corridorExcavationCost(true, true, false, false));
        assertEquals(1, AutoMiner.corridorExcavationCost(false, true, true, false));
        assertEquals(1, AutoMiner.corridorExcavationCost(true, false, false, true));
        assertEquals(2, AutoMiner.corridorExcavationCost(false, false, true, true));
        assertEquals(-1, AutoMiner.corridorExcavationCost(true, false, false, false));
    }

    @Test
    public void recognizesAdjacentBlocksAsTheSameOreVein() {
        BlockPos origin = new BlockPos(10, 20, 30);

        assertTrue(AutoMiner.sameVein(origin, origin.add(1, 1, -1), OreType.IRON, OreType.IRON));
        assertFalse(AutoMiner.sameVein(origin, origin.add(2, 0, 0), OreType.IRON, OreType.IRON));
        assertFalse(AutoMiner.sameVein(origin, origin.add(1, 0, 0), OreType.IRON, OreType.GOLD));
    }

    @Test
    public void verticalPathNodesRequireThePlayerToDropIntoTheCell() {
        assertFalse(AutoMiner.reachedPathNode(0.0, -1.0));
        assertTrue(AutoMiner.reachedPathNode(0.0, -0.20));
    }

    @Test
    public void pathPriorityGuidesSearchTowardTheNearestGoal() {
        BlockPos goal = new BlockPos(10, 20, 30);
        assertEquals(4, AutoMiner.pathPriority(1, new BlockPos(8, 20, 29), Arrays.asList(goal)));
    }

    @Test
    public void knownCheaperNeighborSkipsWorldTraversalChecks() {
        assertTrue(AutoMiner.knownPathCostCannotImprove(5, 4, 0));
        assertTrue(AutoMiner.knownPathCostCannotImprove(7, 4, 2));
        assertFalse(AutoMiner.knownPathCostCannotImprove(6, 4, 0));
        assertFalse(AutoMiner.knownPathCostCannotImprove(null, 4, 0));
    }

    @Test
    public void corridorCellsKeepHeadAndFeetObstaclesInRouteOrder() {
        List<BlockPos> cells = AutoMiner.corridorCells(Arrays.asList(
            new BlockPos(1, 63, 0), new BlockPos(1, 62, 0)), 0, 10);

        assertEquals(Arrays.asList(
            new BlockPos(1, 64, 0), new BlockPos(1, 63, 0), new BlockPos(1, 62, 0)), cells);
    }

    @Test
    public void ascendingRoutesIncludeTheJumpStartHeadClearance() {
        BlockPos start = new BlockPos(0, 64, 0);
        List<BlockPos> cells = AutoMiner.corridorCells(Arrays.asList(
            new BlockPos(1, 65, 0), new BlockPos(2, 65, 0)), 0, 10, start);

        assertEquals(Arrays.asList(
            new BlockPos(0, 66, 0), new BlockPos(1, 66, 0),
            new BlockPos(1, 65, 0), new BlockPos(2, 66, 0),
            new BlockPos(2, 65, 0)), cells);
    }

    @Test
    public void jumpClearanceMustBeOpenOrBreakable() {
        assertEquals(0, AutoMiner.jumpClearanceCost(true, false));
        assertEquals(1, AutoMiner.jumpClearanceCost(false, true));
        assertEquals(-1, AutoMiner.jumpClearanceCost(false, false));
    }

    @Test
    public void directMiningRequiresAnOrthogonallyAdjacentPosition() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertTrue(AutoMiner.stableMiningPosition(feet, new BlockPos(1, 64, 0)));
        assertTrue(AutoMiner.stableMiningPosition(feet, new BlockPos(0, 63, 0)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(1, 64, 1)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(4, 64, 0)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(0, 66, 0)));
    }

    @Test
    public void miningReachUsesTheNearestPointOnTheBlock() {
        BlockPos target = new BlockPos(5, 64, 0);
        Vec3d eyes = new Vec3d(0.5, 64.5, 0.5);

        assertTrue(AutoMiner.withinMiningReach(eyes, target, 4.5));
        assertFalse(AutoMiner.withinMiningReach(eyes, target, 4.49));
        assertEquals(new Vec3d(5.5, 64.5, 0.5), AutoMiner.blockCenter(target));
    }

    @Test
    public void nearbyReachableOreWinsOverAClearerDistantRoute() {
        int nearby = AutoMiner.pathTargetScore(8, 2.0, false);
        int distant = AutoMiner.pathTargetScore(1, 36.0, true);

        assertTrue(nearby < distant);
    }

    @Test
    public void unreachableCandidateBatchesAdvanceBeforeWrapping() {
        assertEquals(6, AutoMiner.nextPathCandidateOffset(0, 6, 6, false));
        assertEquals(12, AutoMiner.nextPathCandidateOffset(6, 6, 6, false));
        assertEquals(0, AutoMiner.nextPathCandidateOffset(12, 3, 6, false));
        assertEquals(0, AutoMiner.nextPathCandidateOffset(6, 2, 6, true));
    }

    @Test
    public void failedRouteIsSkippedOnlyDuringItsRetryWindow() {
        BlockPos failed = new BlockPos(4, 20, 7);

        assertTrue(AutoMiner.temporarilyBlocked(failed, failed, 1));
        assertFalse(AutoMiner.temporarilyBlocked(failed, failed, 0));
        assertFalse(AutoMiner.temporarilyBlocked(new BlockPos(5, 20, 7), failed, 100));
    }

    @Test
    public void routeThatEndsWithoutAVisibleOreIsAbandoned() {
        BlockPos ore = new BlockPos(4, 20, 7);

        assertTrue(AutoMiner.routeEndedBeforeMining(ore, 3, 3));
        assertFalse(AutoMiner.routeEndedBeforeMining(ore, 2, 3));
        assertFalse(AutoMiner.routeEndedBeforeMining(null, 0, 0));
    }

    @Test
    public void routeMotionIsDampedAndLimitedEveryTick() {
        assertEquals(0.18, AutoMiner.routeMotion(0.40, 1.0), 0.0001);
        assertEquals(-0.18, AutoMiner.routeMotion(-0.40, -1.0), 0.0001);
        assertEquals(0.10, AutoMiner.routeMotion(0.20, 0.0), 0.0001);
    }

    @Test
    public void nearbyOrePreemptsOnlyWhenMeaningfullyCloser() {
        assertTrue(AutoMiner.closerTargetWarrantsPreemption(4.0, 36.0));
        assertFalse(AutoMiner.closerTargetWarrantsPreemption(16.0, 25.0));
        assertFalse(AutoMiner.closerTargetWarrantsPreemption(9.0, 25.0));
    }

    @Test
    public void routeProgressRequiresMeaningfulDistanceReduction() {
        assertTrue(AutoMiner.routeProgressed(Double.POSITIVE_INFINITY, 1.0));
        assertTrue(AutoMiner.routeProgressed(1.0, 0.99));
        assertFalse(AutoMiner.routeProgressed(1.0, 0.999));
        assertFalse(AutoMiner.routeProgressed(1.0, 1.01));
    }

    @Test
    public void directMiningChecksStayBoundedInDenseVeins() {
        assertEquals(16, AutoMiner.visibleTargetInspectionLimit());
    }

    @Test
    public void visibilityContextRequiresAValidPlayerSnapshot() {
        BlockPos feet = new BlockPos(0, 64, 0);
        Vec3d eyes = new Vec3d(0.5, 65.6, 0.5);

        assertTrue(AutoMiner.visibilityContextReady(feet, eyes, 4.5));
        assertFalse(AutoMiner.visibilityContextReady(null, eyes, 4.5));
        assertFalse(AutoMiner.visibilityContextReady(feet, null, 4.5));
        assertFalse(AutoMiner.visibilityContextReady(feet, eyes, 0.0));
    }

    @Test
    public void renderedRouteSplitsDiagonalJumpNodesIntoOrthogonalCenters() {
        List<BlockPos> route = AutoMiner.orthogonalRoutePoints(Arrays.asList(
            new BlockPos(0, 64, 0), new BlockPos(1, 65, 0), new BlockPos(1, 65, 2)));

        assertEquals(Arrays.asList(
            new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
            new BlockPos(1, 65, 0), new BlockPos(1, 65, 1),
            new BlockPos(1, 65, 2)), route);
    }
}

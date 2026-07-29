package com.qazr.legacy.module;

import com.qazr.legacy.config.OreType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
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
        assertFalse(AutoMiner.sameVein(null, origin, OreType.IRON, OreType.IRON));
    }

    @Test
    public void verticalPathNodesRequireThePlayersFeetToEnterTheCell() {
        BlockPos expected = new BlockPos(1, 64, 0);

        assertFalse(AutoMiner.reachedPathNode(new BlockPos(0, 64, 0), expected, 0.01, 0.0));
        assertFalse(AutoMiner.reachedPathNode(expected, expected, 0.0, -1.0));
        assertTrue(AutoMiner.reachedPathNode(expected, expected, 0.0, -0.20));
        assertTrue(AutoMiner.reachedPathNode(expected, expected, 0.0, 0.0));
        assertTrue(AutoMiner.reachedPathNode(expected, expected, 0.0, 0.04));
        assertFalse(AutoMiner.reachedPathNode(expected, expected, 0.05, 0.0));
        assertFalse(AutoMiner.reachedPathNode(expected, expected, 0.0, 0.20));
        assertTrue(AutoMiner.reachedPathNode(expected, expected, 0.009, 0.0, 0.01));
        assertFalse(AutoMiner.reachedPathNode(expected, expected, 0.011, 0.0, 0.01));
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos node = new BlockPos(1, 64, 0);
        assertEquals(0.04, AutoMiner.routeNodeReachDistanceSq(
            from, node, new BlockPos(2, 64, 0)), 0.0);
        assertEquals(0.01, AutoMiner.routeNodeReachDistanceSq(
            from, node, new BlockPos(1, 64, 1)), 0.0);
        assertEquals(0.01, AutoMiner.routeNodeReachDistanceSq(
            from, node, new BlockPos(2, 65, 0)), 0.0);
        assertEquals(0.01, AutoMiner.routeNodeReachDistanceSq(from, node, null), 0.0);
    }

    @Test
    public void playerFeetCellToleratesTinyVerticalRoundingErrors() {
        assertEquals(new BlockPos(3, 64, -3), AutoMiner.playerFeetCell(3.9, 63.999, -2.1));
        assertEquals(new BlockPos(4, 65, -2), AutoMiner.playerFeetCell(4.0, 65.0, -2.0));
        assertEquals(64.0, AutoMiner.navigationFeetY(63.5, true, true), 0.0);
        assertEquals(63.5, AutoMiner.navigationFeetY(63.5, false, true), 0.0);
        assertEquals(64.0625, AutoMiner.navigationFeetY(64.0625, true, false), 0.0);
        assertEquals(new BlockPos(3, 64, -3),
            AutoMiner.navigationFeetCell(3.9, 63.5, -2.1, true, true));
    }

    @Test
    public void routeTransitionRejectsAPlayerOutsideTheCurrentPathStep() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos next = new BlockPos(1, 65, 0);

        assertTrue(AutoMiner.routeTransitionContains(from, from, next));
        assertTrue(AutoMiner.routeTransitionContains(new BlockPos(1, 64, 0), from, next));
        assertTrue(AutoMiner.routeTransitionContains(next, from, next));
        assertFalse(AutoMiner.routeTransitionContains(new BlockPos(0, 63, 0), from, next));
        assertFalse(AutoMiner.routeTransitionContains(new BlockPos(1, 65, 1), from, next));
    }

    @Test
    public void descentRouteStepsStayCardinalAndWaitForAControlledLanding() {
        BlockPos origin = new BlockPos(0, 64, 0);

        assertTrue(AutoMiner.routeTransitionIsControlled(origin, origin.east()));
        assertTrue(AutoMiner.routeTransitionIsControlled(origin, origin.down()));
        assertTrue(AutoMiner.routeTransitionIsControlled(origin, origin.up()));
        assertFalse(AutoMiner.routeTransitionIsControlled(origin, origin.down(2)));
        assertFalse(AutoMiner.routeTransitionIsControlled(origin, origin.add(1, -1, 1)));
        assertFalse(AutoMiner.routeTransitionIsControlled(null, origin));
        assertTrue(AutoMiner.routeRequiresSupportRemoval(origin, origin.down()));
        assertFalse(AutoMiner.routeRequiresSupportRemoval(origin, origin.east().down()));
        assertFalse(AutoMiner.routeRequiresSupportRemoval(origin, origin.up()));
        assertEquals(origin.down(), AutoMiner.routeTransitionClearance(origin, origin.down()));
        assertEquals(origin.up(2), AutoMiner.routeTransitionClearance(origin, origin.up()));
        assertEquals(null, AutoMiner.routeTransitionClearance(origin, origin.east()));

        assertTrue(AutoMiner.routeLandingConfirmed(false, false, -0.8));
        assertTrue(AutoMiner.routeLandingConfirmed(true, true, -0.8));
        assertTrue(AutoMiner.routeLandingConfirmed(true, false, -0.05));
        assertFalse(AutoMiner.routeLandingConfirmed(true, false, -0.06));
    }

    @Test
    public void ascendingRouteWaitsUntilThePlayersFeetClearTheLandingSupport() {
        assertTrue(AutoMiner.waitingForAscendingClearance(64, 65, 64.0));
        assertTrue(AutoMiner.waitingForAscendingClearance(64, 65, 64.98));
        assertFalse(AutoMiner.waitingForAscendingClearance(64, 65, 64.99));
        assertFalse(AutoMiner.waitingForAscendingClearance(64, 65, 65.0));
        assertFalse(AutoMiner.waitingForAscendingClearance(65, 65, 64.5));
    }

    @Test
    public void pathPriorityGuidesSearchTowardTheNearestGoal() {
        BlockPos goal = new BlockPos(10, 20, 30);
        assertEquals(4, AutoMiner.pathPriority(1, new BlockPos(8, 20, 29), Arrays.asList(goal)));
    }

    @Test
    public void equalCostEstimatesAdvanceTowardTheGoalBeforeFanningOut() {
        assertTrue(AutoMiner.comparePathOrder(9, 1, 10, 8) < 0);
        assertTrue(AutoMiner.comparePathOrder(10, 8, 10, 1) < 0);
        assertTrue(AutoMiner.comparePathOrder(10, 1, 10, 8) > 0);
    }

    @Test
    public void knownCheaperNeighborSkipsWorldTraversalChecks() {
        assertTrue(AutoMiner.knownPathCostCannotImprove(5, 4, 0));
        assertTrue(AutoMiner.knownPathCostCannotImprove(7, 4, 2));
        assertFalse(AutoMiner.knownPathCostCannotImprove(6, 4, 0));
        assertFalse(AutoMiner.knownPathCostCannotImprove(null, 4, 0));
    }

    @Test
    public void pathSearchReusesExpensiveWorldCostsWithinOnePlan() {
        Map<BlockPos, Integer> costs = new HashMap<>();
        BlockPos cell = new BlockPos(3, 20, 7);
        int[] resolutions = {0};

        assertEquals(-1, AutoMiner.cachedPathCost(costs, cell, pos -> {
            resolutions[0]++;
            return -1;
        }));
        assertEquals(-1, AutoMiner.cachedPathCost(costs, cell, pos -> {
            resolutions[0]++;
            return 5;
        }));
        assertEquals(1, resolutions[0]);
    }

    @Test
    public void completedRouteValidationIsSlicedAndFollowsTransitionOrder() {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos horizontal = start.east();
        BlockPos ascending = horizontal.east().up();
        BlockPos descending = ascending.east().down();
        AutoMiner.SuccessPathValidation validation = new AutoMiner.SuccessPathValidation(start,
            Arrays.asList(horizontal, ascending, descending));
        List<BlockPos> checks = new java.util.ArrayList<>();
        List<Boolean> traversal = new java.util.ArrayList<>();

        while (validation.hasNext()) {
            checks.add(validation.currentPos());
            traversal.add(validation.currentUsesTraversalCost());
            validation.advance();
        }

        assertEquals(Arrays.asList(horizontal, ascending, horizontal.up(2), descending), checks);
        assertEquals(Arrays.asList(true, true, false, true), traversal);
        assertEquals(0, AutoMiner.pathValidationSliceBudget(-1));
        assertEquals(32, AutoMiner.pathValidationSliceBudget(32));
        assertEquals(64, AutoMiner.pathValidationSliceBudget(100));
        assertTrue(AutoMiner.pathValidationRequiresAnotherPass(1));
        assertFalse(AutoMiner.pathValidationRequiresAnotherPass(2));
        validation.reset();
        assertTrue(validation.hasNext());
        assertEquals(horizontal, validation.currentPos());
    }

    @Test
    public void routeReconstructionPreservesForwardOrderWithoutFrontInsertion() {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos first = start.east();
        BlockPos second = first.east();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        previous.put(start, null);
        previous.put(first, start);
        previous.put(second, first);

        assertEquals(Arrays.asList(first, second), AutoMiner.reconstruct(previous, second));
        assertTrue(AutoMiner.reconstruct(previous, start).isEmpty());
    }

    @Test
    public void corridorCellsKeepHeadAndFeetObstaclesInRouteOrder() {
        List<BlockPos> cells = AutoMiner.corridorCells(Arrays.asList(
            new BlockPos(1, 63, 0), new BlockPos(1, 62, 0)), 0, 10);

        assertEquals(Arrays.asList(
            new BlockPos(1, 64, 0), new BlockPos(1, 63, 0), new BlockPos(1, 62, 0)), cells);
        BlockPos start = new BlockPos(0, 64, 0);
        assertTrue(AutoMiner.reuseRouteCorridorCache(true, 2, 2, start, start));
        assertFalse(AutoMiner.reuseRouteCorridorCache(false, 2, 2, start, start));
        assertFalse(AutoMiner.reuseRouteCorridorCache(true, 1, 2, start, start));
        assertFalse(AutoMiner.reuseRouteCorridorCache(true, 2, 2, start, start.east()));
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
        BlockPos origin = new BlockPos(0, 64, 0);
        assertTrue(AutoMiner.transitionNeedsSeparateClearance(origin.up(2), origin.up().east()));
        assertFalse(AutoMiner.transitionNeedsSeparateClearance(origin.down(), origin.down()));
        assertFalse(AutoMiner.transitionNeedsSeparateClearance(null, origin.east()));
    }

    @Test
    public void directMiningRequiresAnOrthogonallyAdjacentPosition() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertTrue(AutoMiner.stableMiningPosition(feet, new BlockPos(1, 64, 0)));
        assertTrue(AutoMiner.stableMiningPosition(feet, new BlockPos(0, 63, 0)));
        assertTrue(AutoMiner.stableMiningPosition(feet, new BlockPos(0, 66, 0)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(1, 64, 1)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(4, 64, 0)));
        assertFalse(AutoMiner.stableMiningPosition(feet, new BlockPos(0, 67, 0)));
    }

    @Test
    public void pathGoalsIncludeEveryStableOrthogonalMiningPosition() {
        BlockPos ore = new BlockPos(0, 64, 0);
        List<BlockPos> candidates = AutoMiner.miningStandCandidates(ore);

        assertEquals(18, candidates.size());
        assertTrue(candidates.contains(ore.up()));
        assertTrue(candidates.contains(ore.down(2)));
        assertTrue(candidates.contains(new BlockPos(1, 62, 0)));
        assertTrue(candidates.contains(new BlockPos(1, 65, 0)));
        assertFalse(candidates.contains(new BlockPos(1, 64, 1)));
        for (BlockPos candidate : candidates) {
            assertTrue(AutoMiner.stableMiningPosition(candidate, ore));
        }
        assertTrue(AutoMiner.compareMiningStandPriority(new BlockPos(4, 64, 0),
            ore.up(), ore, 16.0, 1.0) < 0);
        assertFalse(AutoMiner.safeRemoteMiningStand(ore.up(), ore));
        assertTrue(AutoMiner.safeRemoteMiningStand(ore.down(2), ore));
        assertTrue(AutoMiner.safeRemoteMiningStand(ore.east(), ore));
    }

    @Test
    public void miningFaceUsesAStableExposedSideWithoutRemovingPlayerSupport() {
        BlockPos ore = new BlockPos(0, 64, 0);

        assertEquals(ore.up(), AutoMiner.miningFaceNeighbor(new BlockPos(1, 65, 0), ore));
        assertEquals(ore.down(), AutoMiner.miningFaceNeighbor(new BlockPos(1, 62, 0), ore));
        assertEquals(ore.east(), AutoMiner.miningFaceNeighbor(new BlockPos(1, 64, 0), ore));
        assertEquals(ore.east(), AutoMiner.miningFaceNeighbor(new BlockPos(1, 63, 0), ore));
        assertEquals(null, AutoMiner.miningFaceNeighbor(new BlockPos(1, 64, 1), ore));
    }

    @Test
    public void miningWorkAreaRequiresClearPlayerCellsAndSolidSupport() {
        assertTrue(AutoMiner.miningWorkAreaReady(true, true, true));
        assertFalse(AutoMiner.miningWorkAreaReady(false, true, true));
        assertFalse(AutoMiner.miningWorkAreaReady(true, false, true));
        assertFalse(AutoMiner.miningWorkAreaReady(true, true, false));
        assertTrue(AutoMiner.routeSupportShapeUsable(true, true, false, false));
        assertTrue(AutoMiner.routeSupportShapeUsable(true, false, true, false));
        assertFalse(AutoMiner.routeSupportShapeUsable(true, false, true, true));
        assertFalse(AutoMiner.routeSupportShapeUsable(true, false, false, false));
        assertFalse(AutoMiner.routeSupportShapeUsable(false, true, true, false));
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
    public void harvestCapablePickaxeWinsOverAFasterWrongTool() {
        assertTrue(AutoMiner.betterMiningTool(true, 6.0F, false, 12.0F));
        assertFalse(AutoMiner.betterMiningTool(false, 12.0F, true, 6.0F));
        assertTrue(AutoMiner.betterMiningTool(true, 8.0F, true, 6.0F));
        assertFalse(AutoMiner.betterMiningTool(true, 6.0F, true, 8.0F));
    }

    @Test
    public void nearbyReachableOreWinsOverAClearerUnrelatedRoute() {
        int nearby = AutoMiner.pathTargetScore(8, 2.0, false);
        int distant = AutoMiner.pathTargetScore(1, 36.0, false);

        assertTrue(nearby < distant);
    }

    @Test
    public void connectedVeinRouteFinishesBeforeAnyNewGlobalTarget() {
        int connected = AutoMiner.pathTargetScore(20, 9.0, true);
        int unrelated = AutoMiner.pathTargetScore(0, 1.0, false);

        assertTrue(AutoMiner.betterPathTarget(connected, true, unrelated, false));
        assertFalse(AutoMiner.betterPathTarget(unrelated, false, connected, true));
        assertTrue(AutoMiner.betterPathTarget(2, true, 3, true));
    }

    @Test
    public void connectedVeinCandidatesSortAheadOfCloserUnrelatedOre() {
        BlockPos mined = new BlockPos(10, 20, 30);
        OreVisualizer.CachedOre connected = new OreVisualizer.CachedOre(
            mined.east(), OreType.IRON, 25.0);
        OreVisualizer.CachedOre unrelated = new OreVisualizer.CachedOre(
            new BlockPos(0, 20, 0), OreType.IRON, 1.0);

        assertTrue(AutoMiner.compareVeinPriority(connected, unrelated, mined, OreType.IRON) < 0);
        assertTrue(AutoMiner.compareVeinPriority(unrelated, connected, mined, OreType.IRON) > 0);
    }

    @Test
    public void invisibleLabelsKeepAConnectedVeinInOneNearestFirstOrder() {
        BlockPos seed = new BlockPos(10, 20, 30);
        OreVisualizer.CachedOre second = new OreVisualizer.CachedOre(
            seed.east(), OreType.IRON, 1.0);
        OreVisualizer.CachedOre diagonal = new OreVisualizer.CachedOre(
            seed.east().up().south(), OreType.IRON, 2.0);
        OreVisualizer.CachedOre otherType = new OreVisualizer.CachedOre(
            seed.west(), OreType.GOLD, 1.0);
        OreVisualizer.CachedOre detached = new OreVisualizer.CachedOre(
            seed.add(4, 0, 0), OreType.IRON, 1.0);

        Map<BlockPos, Integer> labels = AutoMiner.labelConnectedVein(seed, OreType.IRON,
            Arrays.asList(second, diagonal, otherType, detached));

        assertEquals(Integer.valueOf(1), labels.get(seed));
        assertEquals(Integer.valueOf(2), labels.get(second.pos()));
        assertEquals(Integer.valueOf(3), labels.get(diagonal.pos()));
        assertFalse(labels.containsKey(otherType.pos()));
        assertFalse(labels.containsKey(detached.pos()));
    }

    @Test
    public void confirmedOreReordersOnlyTheRemainingVeinByCurrentDistance() {
        BlockPos lower = new BlockPos(0, 20, 0);
        BlockPos nearby = new BlockPos(1, 21, 0);
        BlockPos upper = new BlockPos(0, 23, 0);
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(lower, 1);
        labels.put(upper, 2);
        labels.put(nearby, 3);
        Map<BlockPos, Double> distances = new HashMap<>();
        distances.put(lower, 9.0);
        distances.put(upper, 16.0);
        distances.put(nearby, 1.0);

        Map<BlockPos, Integer> reordered = AutoMiner.relabelRemainingTargets(labels, distances);

        assertEquals(Integer.valueOf(1), reordered.get(nearby));
        assertEquals(Integer.valueOf(2), reordered.get(lower));
        assertEquals(Integer.valueOf(3), reordered.get(upper));
        assertTrue(AutoMiner.relabelRemainingTargets(java.util.Collections.emptyMap(),
            distances).isEmpty());
    }

    @Test
    public void directReachAllowsVisibleOreInAnyRelativeDirection() {
        Vec3d eyes = new Vec3d(0.5, 65.62, 0.5);

        assertTrue(AutoMiner.withinMiningReach(eyes, new BlockPos(0, 66, 0), 4.5));
        assertTrue(AutoMiner.withinMiningReach(eyes, new BlockPos(1, 65, 1), 4.5));
        assertFalse(AutoMiner.withinMiningReach(eyes, new BlockPos(0, 71, 0), 4.5));
    }

    @Test
    public void directMiningSamplesTheCenterAndEveryExposedFace() {
        BlockPos ore = new BlockPos(3, 64, 7);
        List<Vec3d> samples = AutoMiner.blockVisibilitySamples(ore);

        assertEquals(7, samples.size());
        assertEquals(new Vec3d(3.5, 64.5, 7.5), samples.get(0));
        assertEquals(new Vec3d(3.001, 64.5, 7.5), samples.get(1));
        assertEquals(new Vec3d(3.999, 64.5, 7.5), samples.get(2));
        assertEquals(new Vec3d(3.5, 64.001, 7.5), samples.get(3));
        assertEquals(new Vec3d(3.5, 64.999, 7.5), samples.get(4));
        assertEquals(new Vec3d(3.5, 64.5, 7.001), samples.get(5));
        assertEquals(new Vec3d(3.5, 64.5, 7.999), samples.get(6));
        for (int index = 0; index < samples.size(); index++) {
            assertEquals(samples.get(index), AutoMiner.blockVisibilitySample(ore, index));
        }
    }

    @Test
    public void scaffoldAssistOnlyRaisesAStablePlayerForAnOverheadOre() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertTrue(AutoMiner.scaffoldCandidate(feet, feet.up(3), true,
            true, true, true, true));
        assertTrue(AutoMiner.scaffoldCandidate(feet, feet.east().up(3), true,
            true, true, true, true));
        assertTrue(AutoMiner.scaffoldCandidate(feet, feet.up(2), true,
            true, true, true, true));
        assertFalse(AutoMiner.scaffoldCandidate(feet, feet.up(3), false,
            true, true, true, true));
        assertFalse(AutoMiner.scaffoldCandidate(feet, feet.up(3), true,
            true, true, false, true));
        assertFalse(AutoMiner.scaffoldCandidate(feet, feet.add(2, 3, 0), true,
            true, true, true, true));
    }

    @Test
    public void scaffoldAssistOnlyStartsWhenOneBlockOfHeightAddsReach() {
        Vec3d eyes = new Vec3d(0.5, 65.62, 0.5);
        BlockPos ore = new BlockPos(0, 70, 0);

        assertTrue(AutoMiner.scaffoldRaisesIntoReach(eyes, ore, 4.0));
        assertFalse(AutoMiner.scaffoldRaisesIntoReach(eyes, ore, 3.0));
        assertFalse(AutoMiner.scaffoldRaisesIntoReach(eyes, ore, 4.5));
    }

    @Test
    public void scaffoldPlacementWaitsUntilThePlayerClearsTheNewBlock() {
        assertFalse(AutoMiner.readyToPlaceScaffold(64.99, 64));
        assertTrue(AutoMiner.readyToPlaceScaffold(65.0, 64));
        assertFalse(AutoMiner.playerReachedScaffoldLevel(64.98, 64));
        assertTrue(AutoMiner.playerReachedScaffoldLevel(64.99, 64));
        assertTrue(AutoMiner.shouldRetryScaffoldAscent(20, 20, 64.2, 64, -0.1, false));
        assertTrue(AutoMiner.shouldRetryScaffoldAscent(20, 20, 64.2, 64, 0.1, true));
        assertFalse(AutoMiner.shouldRetryScaffoldAscent(19, 20, 64.2, 64, -0.1, false));
        assertFalse(AutoMiner.shouldRetryScaffoldAscent(20, 20, 64.99, 64, -0.1, true));
        assertFalse(AutoMiner.shouldRetryScaffoldAscent(20, 20, 64.2, 64, 0.1, false));
        assertTrue(AutoMiner.stableScaffoldBlock(true, false));
        assertFalse(AutoMiner.stableScaffoldBlock(true, true));
        assertFalse(AutoMiner.stableScaffoldBlock(false, false));
    }

    @Test
    public void scaffoldAllowsTheFinalPlacementAttemptToBeConfirmed() {
        assertFalse(AutoMiner.scaffoldAttemptsExhausted(4, true));
        assertTrue(AutoMiner.scaffoldAttemptsExhausted(5, true));
        assertFalse(AutoMiner.scaffoldAttemptsExhausted(5, false));
    }

    @Test
    public void scaffoldAssistCancelsWhenThePlayerLeavesTheOriginalColumn() {
        BlockPos scaffold = new BlockPos(3, 64, 7);

        assertTrue(AutoMiner.scaffoldColumnContains(scaffold, scaffold));
        assertTrue(AutoMiner.scaffoldColumnContains(scaffold, scaffold.up()));
        assertFalse(AutoMiner.scaffoldColumnContains(scaffold, scaffold.east()));
        assertFalse(AutoMiner.scaffoldColumnContains(scaffold, scaffold.up(2)));
    }

    @Test
    public void scaffoldClicksTheCenterOfTheSupportingFace() {
        BlockPos support = new BlockPos(3, 63, 7);

        assertEquals(new Vec3d(3.5, 64.0, 7.5),
            AutoMiner.scaffoldHitVec(support, EnumFacing.UP));
        assertEquals(new Vec3d(4.0, 63.5, 7.5),
            AutoMiner.scaffoldHitVec(support, EnumFacing.EAST));
    }

    @Test
    public void lowerInvisibleLabelWinsOverDistanceAndRouteCost() {
        OreVisualizer.CachedOre first = new OreVisualizer.CachedOre(
            new BlockPos(5, 20, 0), OreType.IRON, 25.0);
        OreVisualizer.CachedOre second = new OreVisualizer.CachedOre(
            new BlockPos(1, 20, 0), OreType.IRON, 1.0);
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(first.pos(), 1);
        labels.put(second.pos(), 2);

        assertTrue(AutoMiner.compareTargetPriority(first, second, labels, null, null) < 0);
        assertTrue(AutoMiner.betterLabeledPathTarget(1, 100, false, 2, 1, true));
        assertFalse(AutoMiner.betterLabeledPathTarget(2, 1, true, 1, 100, false));
    }

    @Test
    public void onlyAnOreFromTheSameLabeledVeinMayExposeTheQueuedTarget() {
        BlockPos desired = new BlockPos(10, 20, 30);
        BlockPos blocker = desired.east();
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(desired, 1);
        labels.put(blocker, 2);

        assertTrue(AutoMiner.isLabeledVeinBlocker(desired, blocker, OreType.IRON,
            OreType.IRON, labels));
        assertFalse(AutoMiner.isLabeledVeinBlocker(desired, blocker, OreType.IRON,
            OreType.GOLD, labels));
        assertFalse(AutoMiner.isLabeledVeinBlocker(desired, desired.west(), OreType.IRON,
            OreType.IRON, labels));
    }

    @Test
    public void onlyNewlyExposedOreConnectedToTheLockedVeinMayJoinItsQueue() {
        BlockPos labeled = new BlockPos(10, 20, 30);
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(labeled, 1);

        assertTrue(AutoMiner.connectedToLabeledVein(
            labeled.up().east(), OreType.IRON, labels, OreType.IRON));
        assertFalse(AutoMiner.connectedToLabeledVein(
            labeled.add(2, 0, 0), OreType.IRON, labels, OreType.IRON));
        assertFalse(AutoMiner.connectedToLabeledVein(
            labeled.east(), OreType.GOLD, labels, OreType.IRON));
        assertFalse(AutoMiner.connectedToLabeledVein(
            labeled, OreType.IRON, labels, OreType.IRON));
    }

    @Test
    public void pathPlanningSpreadsUnreachableCandidatesAcrossTicks() {
        assertEquals(128, AutoMiner.pathSearchSliceBudget(0));
        assertEquals(128, AutoMiner.pathSearchSliceBudget(256));
        assertEquals(64, AutoMiner.pathSearchSliceBudget(1536));
        assertEquals(0, AutoMiner.pathSearchSliceBudget(1600));
        assertEquals(0, AutoMiner.pathSearchRetryDelay(0, true));
        assertEquals(1, AutoMiner.pathSearchRetryDelay(1, false));
        assertEquals(20, AutoMiner.pathSearchRetryDelay(0, false));
        Set<BlockPos> planned = new HashSet<>(Arrays.asList(
            new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)));
        assertFalse(AutoMiner.pathGoalsChanged(planned, new java.util.ArrayList<>(planned)));
        assertTrue(AutoMiner.pathGoalsChanged(planned, Arrays.asList(new BlockPos(1, 64, 0))));
        Map<BlockPos, Integer> cached = new HashMap<>();
        BlockPos corridor = new BlockPos(3, 64, 0);
        cached.put(corridor, -1);
        assertFalse(AutoMiner.cachedPathStateChanged(cached, corridor, -1));
        assertTrue(AutoMiner.cachedPathStateChanged(cached, corridor, 1));
        assertFalse(AutoMiner.cachedPathStateChanged(cached, corridor.east(), 1));
        assertTrue(AutoMiner.pathCacheEntryNeedsValidation(-1, false));
        assertFalse(AutoMiner.pathCacheEntryNeedsValidation(1, false));
        assertTrue(AutoMiner.pathCacheEntryNeedsValidation(1, true));
        cached.put(corridor.east(), 1);
        assertEquals(1, AutoMiner.pathStateEntriesForValidation(cached, false, 8).size());
        assertEquals(2, AutoMiner.pathStateEntriesForValidation(cached, true, 8).size());
        assertEquals(1, AutoMiner.pathStateEntriesForValidation(cached, true, 1).size());
        assertEquals(1, AutoMiner.pathStateValidationCount(cached, false, 8));
        assertEquals(2, AutoMiner.pathStateValidationCount(cached, true, 8));
        assertEquals(1, AutoMiner.pathStateValidationCount(cached, true, 1));
    }

    @Test
    public void pathCandidateSnapshotDoesNotReorderWhilePlanning() {
        OreVisualizer.CachedOre front = new OreVisualizer.CachedOre(
            new BlockPos(5, 20, 0), OreType.IRON, 25.0);
        OreVisualizer.CachedOre behind = new OreVisualizer.CachedOre(
            new BlockPos(-2, 19, 0), OreType.IRON, 36.0);
        List<OreVisualizer.CachedOre> live = new java.util.ArrayList<>(Arrays.asList(front, behind));
        List<OreVisualizer.CachedOre> snapshot = AutoMiner.snapshotPathCandidates(live, 96);

        java.util.Collections.reverse(live);

        assertEquals(front.pos(), snapshot.get(0).pos());
        assertEquals(behind.pos(), snapshot.get(1).pos());
        assertEquals(2, snapshot.size());
    }

    @Test
    public void pathCandidateSnapshotHonorsItsWorkLimit() {
        List<OreVisualizer.CachedOre> candidates = Arrays.asList(
            new OreVisualizer.CachedOre(new BlockPos(1, 20, 0), OreType.IRON, 1.0),
            new OreVisualizer.CachedOre(new BlockPos(2, 20, 0), OreType.IRON, 4.0),
            new OreVisualizer.CachedOre(new BlockPos(3, 20, 0), OreType.IRON, 9.0));

        assertEquals(2, AutoMiner.snapshotPathCandidates(candidates, 2).size());
        assertTrue(AutoMiner.snapshotPathCandidates(candidates, 0).isEmpty());
    }

    @Test
    public void failedRouteIsSkippedOnlyDuringItsRetryWindow() {
        BlockPos failed = new BlockPos(4, 20, 7);
        Map<BlockPos, Integer> blocked = new HashMap<>();
        assertTrue(AutoMiner.extendTargetCooldown(blocked, failed, 100));
        assertFalse(AutoMiner.extendTargetCooldown(blocked, failed, 90));
        assertFalse(AutoMiner.extendTargetCooldown(blocked, failed, 100));
        assertTrue(AutoMiner.extendTargetCooldown(blocked, failed, 120));

        assertTrue(AutoMiner.temporarilyBlocked(failed, blocked, 119));
        assertFalse(AutoMiner.temporarilyBlocked(failed, blocked, 120));
        assertFalse(AutoMiner.temporarilyBlocked(new BlockPos(5, 20, 7), blocked, 0));
        assertFalse(AutoMiner.pruneExpiredTargets(blocked, 119));
        assertTrue(AutoMiner.pruneExpiredTargets(blocked, 120));
        assertTrue(blocked.isEmpty());
        assertEquals(20, AutoMiner.retryDelayAfterCooldownExpiry(20, false));
        assertEquals(0, AutoMiner.retryDelayAfterCooldownExpiry(20, true));
        assertEquals(-1, AutoMiner.retryDelayAfterCooldownExpiry(-1, false));
        assertFalse(AutoMiner.pruneExpiredTargets(null, 100));
        assertFalse(AutoMiner.extendTargetCooldown(null, failed, 100));
        assertFalse(AutoMiner.extendTargetCooldown(blocked, null, 100));
    }

    @Test
    public void scaffoldFailureCooldownDoesNotHideTheMiningTarget() {
        BlockPos ore = new BlockPos(4, 23, 7);
        Map<BlockPos, Integer> blockedTargets = new HashMap<>();
        Map<BlockPos, Integer> rejectedTargets = new HashMap<>();
        Map<BlockPos, Integer> rejectedScaffolds = new HashMap<>();
        AutoMiner.extendTargetCooldown(rejectedScaffolds, ore, 120);

        assertFalse(AutoMiner.targetTemporarilyUnavailable(
            ore, blockedTargets, rejectedTargets, 119));
        assertTrue(AutoMiner.scaffoldTemporarilyUnavailable(ore, rejectedScaffolds, 119));
        assertFalse(AutoMiner.scaffoldTemporarilyUnavailable(ore, rejectedScaffolds, 120));
    }

    @Test
    public void pathFailureCooldownIncludesOnlyTargetsActuallySearchedAndFailed() {
        BlockPos failed = new BlockPos(4, 20, 7);
        BlockPos untouched = new BlockPos(5, 20, 7);
        Set<BlockPos> failures = new java.util.HashSet<>();
        failures.add(failed);

        Set<BlockPos> blocked = AutoMiner.failedPathTargetsToBlock(failures);

        assertTrue(blocked.contains(failed));
        assertFalse(blocked.contains(untouched));
        assertTrue(AutoMiner.failedPathTargetsToBlock(java.util.Collections.emptySet()).isEmpty());
    }

    @Test
    public void staleVeinLockIsReleasedAfterThePlayerLeavesItsCandidateRange() {
        BlockPos labeled = new BlockPos(30, 20, 0);
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(labeled, 1);
        List<OreVisualizer.CachedOre> nearby = Arrays.asList(
            new OreVisualizer.CachedOre(new BlockPos(1, 20, 0), OreType.IRON, 1.0));

        assertFalse(AutoMiner.containsLabeledCandidate(nearby, labels));
        assertTrue(AutoMiner.containsLabeledCandidate(Arrays.asList(
            new OreVisualizer.CachedOre(labeled, OreType.IRON, 900.0)), labels));
        assertTrue(AutoMiner.preserveExistingLabelsForVisibleTarget(labels, new BlockPos(1, 20, 0)));
        assertFalse(AutoMiner.preserveExistingLabelsForVisibleTarget(labels, labeled));
        assertFalse(AutoMiner.preserveExistingLabelsForVisibleTarget(
            java.util.Collections.emptyMap(), new BlockPos(1, 20, 0)));
    }

    @Test
    public void routeThatEndsWithoutAVisibleOreIsAbandoned() {
        BlockPos ore = new BlockPos(4, 20, 7);

        assertTrue(AutoMiner.routeEndedBeforeMining(ore, 3, 3));
        assertFalse(AutoMiner.routeEndedBeforeMining(ore, 2, 3));
        assertFalse(AutoMiner.routeEndedBeforeMining(null, 0, 0));
    }

    @Test
    public void routeMotionFacesOnlyTheCurrentNodeAndSlowsBeforeItsCenter() {
        assertEquals(0.18, AutoMiner.routeMotionTowardNode(1.0, 1.0), 0.0001);
        assertEquals(-0.18, AutoMiner.routeMotionTowardNode(-1.0, 1.0), 0.0001);
        assertEquals(0.04, AutoMiner.routeMotionTowardNode(1.0, 0.04), 0.0001);
        assertEquals(0.0, AutoMiner.routeMotionTowardNode(0.0, 1.0), 0.0001);
        assertEquals(0.18, AutoMiner.routeMotionComponent(1.0, 1.0), 0.0001);
        assertEquals(0.0, AutoMiner.routeMotionComponent(0.0, 0.0), 0.0001);
    }

    @Test
    public void stalePathSnapshotsRefreshAfterEightFailedCandidates() {
        assertFalse(AutoMiner.pathSnapshotRefreshNeeded(7, false));
        assertTrue(AutoMiner.pathSnapshotRefreshNeeded(8, false));
        assertFalse(AutoMiner.pathSnapshotRefreshNeeded(8, true));
        assertFalse(AutoMiner.reusePathCandidateSnapshot(java.util.Collections.emptyList()));
        assertTrue(AutoMiner.reusePathCandidateSnapshot(Arrays.asList(
            new OreVisualizer.CachedOre(new BlockPos(1, 20, 0), OreType.IRON, 1.0))));
        BlockPos feet = new BlockPos(1, 64, 1);
        assertTrue(AutoMiner.reuseCurrentCandidateCache(8, 8, feet, feet));
        assertFalse(AutoMiner.reuseCurrentCandidateCache(8, 9, feet, feet));
        assertFalse(AutoMiner.reuseCurrentCandidateCache(8, 8, feet, feet.east()));
        assertTrue(AutoMiner.continuePathRetryDelay(20, 4L, 4L));
        assertFalse(AutoMiner.continuePathRetryDelay(20, 4L, 5L));
        assertFalse(AutoMiner.continuePathRetryDelay(0, 4L, 4L));
        assertTrue(AutoMiner.pathRetryInterruptedByMarkerChange(20, 4L, 5L));
        assertFalse(AutoMiner.pathRetryInterruptedByMarkerChange(20, 4L, 4L));
        assertFalse(AutoMiner.pathRetryInterruptedByMarkerChange(0, 4L, 5L));
        assertTrue(AutoMiner.pathRetryInterruptedByFeetChange(20, feet, feet.east()));
        assertFalse(AutoMiner.pathRetryInterruptedByFeetChange(20, feet, feet));
        assertTrue(AutoMiner.pathRetryInterruptedByFeetChange(20, null, feet));
        assertFalse(AutoMiner.pathRetryInterruptedByFeetChange(20, null, null));
        assertFalse(AutoMiner.pathRetryInterruptedByFeetChange(0, feet, feet.east()));
        assertFalse(AutoMiner.routeComparisonExpired(0));
        assertFalse(AutoMiner.routeComparisonExpired(3));
        assertTrue(AutoMiner.routeComparisonExpired(4));
        assertTrue(AutoMiner.routeComparisonExpired(8));
        assertTrue(AutoMiner.pathTargetAvailable(OreType.IRON, OreType.IRON, false));
        assertFalse(AutoMiner.pathTargetAvailable(OreType.IRON, OreType.GOLD, false));
        assertFalse(AutoMiner.pathTargetAvailable(OreType.IRON, OreType.IRON, true));
        assertFalse(AutoMiner.pathTargetAvailable(null, OreType.IRON, false));
        assertTrue(AutoMiner.pathTargetRefreshNeeded(true, false));
        assertFalse(AutoMiner.pathTargetRefreshNeeded(true, true));
        assertFalse(AutoMiner.pathTargetRefreshNeeded(false, false));
    }

    @Test
    public void nearerRouteObstacleMayBeClearedBeforeTheRequestedCell() {
        BlockPos desired = new BlockPos(3, 65, 0);
        BlockPos nearer = new BlockPos(2, 65, 0);
        List<BlockPos> corridor = Arrays.asList(new BlockPos(1, 65, 0), nearer, desired);

        assertTrue(AutoMiner.corridorObstacleAllowed(nearer, desired, desired, corridor));
        assertFalse(AutoMiner.corridorObstacleAllowed(new BlockPos(2, 65, 1),
            desired, desired, corridor));
        assertTrue(AutoMiner.reusePlannedObstacleCache(20, 20, 3, 3, true));
        assertFalse(AutoMiner.reusePlannedObstacleCache(20, 21, 3, 3, true));
        assertFalse(AutoMiner.reusePlannedObstacleCache(20, 20, 3, 4, true));
        assertFalse(AutoMiner.reusePlannedObstacleCache(20, 20, 3, 3, false));
    }

    @Test
    public void staleCachedOresDoNotConsumeDirectInspectionSlots() {
        assertTrue(AutoMiner.cachedOreStillPresent(OreType.IRON, OreType.IRON));
        assertFalse(AutoMiner.cachedOreStillPresent(OreType.IRON, OreType.GOLD));
        assertFalse(AutoMiner.cachedOreStillPresent(OreType.IRON, null));
        assertTrue(AutoMiner.labelOreStillPresent(
            OreType.IRON, OreType.IRON, true, true, false));
        assertFalse(AutoMiner.labelOreStillPresent(
            OreType.IRON, OreType.GOLD, true, true, false));
        assertFalse(AutoMiner.labelOreStillPresent(
            OreType.IRON, OreType.IRON, false, true, false));
        assertTrue(AutoMiner.labelOreStillPresent(
            OreType.IRON, null, true, false, false));
        assertTrue(AutoMiner.labelOreStillPresent(
            OreType.IRON, null, true, true, true));
        assertFalse(AutoMiner.labelOreStillPresent(
            OreType.IRON, null, true, true, false));
    }

    @Test
    public void destructionAttemptBudgetScalesWithBlockHardnessAndStaysBounded() {
        assertEquals(12, AutoMiner.destructionAttemptBudget(1.0F));
        assertEquals(18, AutoMiner.destructionAttemptBudget(0.1F));
        assertEquals(109, AutoMiner.destructionAttemptBudget(0.01F));
        assertEquals(240, AutoMiner.destructionAttemptBudget(0.0F));
        assertEquals(240, AutoMiner.destructionAttemptBudget(0.001F));
    }

    @Test
    public void destructionAttemptLimitStopsOnlyAfterBudgetIsConsumed() {
        assertFalse(AutoMiner.destructionAttemptsExhausted(11, 12));
        assertTrue(AutoMiner.destructionAttemptsExhausted(12, 12));
        assertFalse(AutoMiner.destructionAttemptsExhausted(100, 0));
        assertFalse(AutoMiner.destructionWorkExhausted(11, 12, 75, 76));
        assertTrue(AutoMiner.destructionWorkExhausted(12, 12, 75, 76));
        assertTrue(AutoMiner.destructionWorkExhausted(1, 12, 77, 76));
        assertEquals(76, AutoMiner.destructionDeadlineTick(0, 12, 2));
    }

    @Test
    public void completionConfirmationKeepsTheDeadlineTick() {
        assertFalse(AutoMiner.completionConfirmationExpired(140, 140));
        assertTrue(AutoMiner.completionConfirmationExpired(141, 140));
        assertFalse(AutoMiner.completionAbsenceConfirmed(true, 2));
        assertTrue(AutoMiner.completionAbsenceConfirmed(true, 3));
        assertFalse(AutoMiner.completionAbsenceConfirmed(false, 3));
        assertEquals(1, AutoMiner.nextClearingMissingTicks(true, true, 0));
        assertEquals(2, AutoMiner.nextClearingMissingTicks(true, true, 1));
        assertEquals(3, AutoMiner.nextClearingMissingTicks(true, true, 2));
        assertEquals(3, AutoMiner.nextClearingMissingTicks(true, true, 20));
        assertEquals(0, AutoMiner.nextClearingMissingTicks(true, false, 2));
        assertEquals(0, AutoMiner.nextClearingMissingTicks(false, true, 2));
        assertFalse(AutoMiner.completionRolledBack(false));
        assertTrue(AutoMiner.completionRolledBack(true));
        boolean reserved = true;
        reserved = AutoMiner.pendingQuotaReservationAfter(reserved,
            AutoMiner.PendingQuotaEvent.VISIBILITY_LOST);
        assertFalse(reserved);
        reserved = AutoMiner.pendingQuotaReservationAfter(reserved,
            AutoMiner.PendingQuotaEvent.BLOCK_MISSING);
        assertTrue(reserved);
        reserved = AutoMiner.pendingQuotaReservationAfter(false,
            AutoMiner.PendingQuotaEvent.RETRY);
        assertTrue(reserved);
        assertTrue(AutoMiner.pendingReservationMayRelease(false));
        assertFalse(AutoMiner.pendingReservationMayRelease(true));
        BlockPos oldOre = new BlockPos(1, 20, 0);
        BlockPos newOre = new BlockPos(2, 20, 0);
        assertTrue(AutoMiner.completionOwnsWork(
            oldOre, OreType.IRON, oldOre, OreType.IRON));
        assertFalse(AutoMiner.completionOwnsWork(
            oldOre, OreType.IRON, oldOre, OreType.GOLD));
        assertFalse(AutoMiner.completionOwnsWork(
            oldOre, OreType.IRON, newOre, OreType.IRON));
        assertFalse(AutoMiner.completionOwnsWork(
            oldOre, null, oldOre, OreType.IRON));
        assertTrue(AutoMiner.completionInvalidatesCurrentRoute(true, false));
        assertTrue(AutoMiner.completionInvalidatesCurrentRoute(false, true));
        assertFalse(AutoMiner.completionInvalidatesCurrentRoute(false, false));
        Map<BlockPos, Integer> labels = new HashMap<>();
        labels.put(oldOre, 1);
        labels.put(newOre, 2);
        assertTrue(AutoMiner.preserveQueuedVeinTarget(
            oldOre, OreType.IRON, newOre, OreType.IRON, labels));
        assertFalse(AutoMiner.preserveQueuedVeinTarget(
            oldOre, OreType.GOLD, newOre, OreType.IRON, labels));
        assertFalse(AutoMiner.preserveQueuedVeinTarget(
            oldOre, OreType.IRON, oldOre, OreType.IRON, labels));
        assertTrue(AutoMiner.completionAwaitsRoute(
            newOre, OreType.IRON, newOre, OreType.IRON, true));
        assertTrue(AutoMiner.completionAwaitsRoute(
            newOre, OreType.IRON, newOre, OreType.IRON, true));
        assertFalse(AutoMiner.completionAwaitsRoute(
            newOre, OreType.IRON, newOre, OreType.IRON, false));
        assertFalse(AutoMiner.completionAwaitsRoute(
            oldOre, OreType.IRON, newOre, OreType.IRON, true));
        assertTrue(AutoMiner.quotaSatisfied(2, 1, 1));
        assertFalse(AutoMiner.quotaSatisfied(2, 1, 0));
        assertFalse(AutoMiner.quotaSatisfied(0, 99, 1));
        assertFalse(AutoMiner.quotaBlocksTarget(true, true));
        assertTrue(AutoMiner.quotaBlocksTarget(true, false));
    }

    @Test
    public void pendingCompletionEvictionProtectsCurrentReservedWork() {
        assertEquals(0, AutoMiner.pendingCompletionEvictionPriority(
            false, false, true, true));
        assertEquals(0, AutoMiner.pendingCompletionEvictionPriority(
            true, true, true, true));
        assertEquals(1, AutoMiner.pendingCompletionEvictionPriority(
            true, false, false, false));
        assertEquals(2, AutoMiner.pendingCompletionEvictionPriority(
            true, false, true, false));
        assertEquals(3, AutoMiner.pendingCompletionEvictionPriority(
            true, false, false, true));
        assertEquals(4, AutoMiner.pendingCompletionEvictionPriority(
            true, false, true, true));
    }

    @Test
    public void routeCollisionCellsCoverThePlayersHeadAndBothSidesAtABoundary() {
        AxisAlignedBB current = new AxisAlignedBB(0.85, 64.0, 0.2, 1.45, 65.8, 0.8);
        AxisAlignedBB swept = AutoMiner.routeStepBounds(current, 0.18, 0.0);
        List<BlockPos> cells = AutoMiner.routeOccupiedCells(swept);

        assertEquals(Arrays.asList(new BlockPos(0, 65, 0), new BlockPos(1, 65, 0),
            new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)), cells);
        assertEquals(0.85, swept.minX, 0.0);
        assertEquals(1.63, swept.maxX, 0.0);
    }

    @Test
    public void pathPlanningStopsResidualHorizontalDrift() {
        assertEquals(0.04, AutoMiner.planningMotion(0.20), 0.0001);
        assertEquals(-0.04, AutoMiner.planningMotion(-0.20), 0.0001);
        assertEquals(0.0, AutoMiner.planningMotion(0.02), 0.0001);
    }

    @Test
    public void routeOwnsItsTargetFromTheFirstNodeUntilArrival() {
        BlockPos ore = new BlockPos(5, 20, 0);

        assertTrue(AutoMiner.routeOwnsTarget(ore, 0, 4));
        assertTrue(AutoMiner.routeOwnsTarget(ore, 3, 4));
        assertFalse(AutoMiner.routeOwnsTarget(ore, 4, 4));
        assertFalse(AutoMiner.routeOwnsTarget(null, 0, 4));
        assertTrue(AutoMiner.invalidActiveRouteTarget(ore, null));
        assertFalse(AutoMiner.invalidActiveRouteTarget(ore, OreType.IRON));
        assertFalse(AutoMiner.invalidActiveRouteTarget(null, null));
    }

    @Test
    public void routeProgressRequiresMeaningfulDistanceReduction() {
        assertTrue(AutoMiner.routeProgressed(Double.POSITIVE_INFINITY, 1.0));
        assertTrue(AutoMiner.routeProgressed(1.0, 0.99));
        assertFalse(AutoMiner.routeProgressed(1.0, 0.999));
        assertFalse(AutoMiner.routeProgressed(1.0, 1.01));
        assertFalse(AutoMiner.routeStallLimitReached(29));
        assertTrue(AutoMiner.routeStallLimitReached(30));
        assertFalse(AutoMiner.routeProgressResetsStallRecovery(
            Double.POSITIVE_INFINITY, 1.0));
        assertTrue(AutoMiner.routeProgressResetsStallRecovery(1.0, 0.99));
        BlockPos ore = new BlockPos(4, 20, 7);
        assertTrue(AutoMiner.stalledRouteReplanAvailable(
            ore, OreType.IRON, null, null, 0));
        assertTrue(AutoMiner.stalledRouteReplanAvailable(
            ore, OreType.IRON, ore, OreType.IRON, 0));
        assertFalse(AutoMiner.stalledRouteReplanAvailable(
            ore, OreType.IRON, ore, OreType.IRON, 1));
        assertTrue(AutoMiner.stalledRouteReplanAvailable(
            ore.east(), OreType.IRON, ore, OreType.IRON, 1));
        assertEquals(1, AutoMiner.nextStalledRouteReplanCount(
            ore, OreType.IRON, null, null, 0));
        assertEquals(2, AutoMiner.nextStalledRouteReplanCount(
            ore, OreType.IRON, ore, OreType.IRON, 1));
    }

    @Test
    public void verticalDescentCountsAsRouteProgress() {
        assertEquals(1.0, AutoMiner.routeNodeDistanceSq(0.0, -1.0), 0.0);
        assertEquals(0.25, AutoMiner.routeNodeDistanceSq(0.0, -0.5), 0.0);
        assertTrue(AutoMiner.routeProgressed(1.0,
            AutoMiner.routeNodeDistanceSq(0.0, -0.5)));
    }

    @Test
    public void directMiningChecksStayBoundedInDenseVeins() {
        assertEquals(16, AutoMiner.visibleTargetInspectionLimit());
        assertTrue(AutoMiner.skipPreviouslyInspectedVein(true, 0, 16));
        assertTrue(AutoMiner.skipPreviouslyInspectedVein(true, 15, 16));
        assertFalse(AutoMiner.skipPreviouslyInspectedVein(true, 16, 16));
        assertFalse(AutoMiner.skipPreviouslyInspectedVein(false, 0, 16));
        assertTrue(AutoMiner.candidateTypeAvailable(true, false));
        assertFalse(AutoMiner.candidateTypeAvailable(false, false));
        assertFalse(AutoMiner.candidateTypeAvailable(true, true));
    }

    @Test
    public void labeledVisibilityKeepsTheNearestHalfAndRotatesAcrossTheVeinTail() {
        int candidateCount = 20;
        int limit = 16;
        int fixed = 8;
        Set<Integer> inspected = new HashSet<>();
        int cursor = 0;

        for (int tick = 0; tick < 2; tick++) {
            int count = AutoMiner.labeledVisibilityInspectionCount(candidateCount, limit);
            int fixedCount = AutoMiner.fixedLabeledVisibilityInspections(
                candidateCount, limit, fixed);
            for (int inspection = 0; inspection < count; inspection++) {
                int index = AutoMiner.labeledVisibilityIndex(inspection, candidateCount,
                    limit, fixed, cursor);
                inspected.add(index);
                if (inspection < fixedCount) assertEquals(inspection, index);
            }
            cursor = AutoMiner.advanceLabeledVisibilityCursor(cursor,
                candidateCount - fixedCount, count - fixedCount);
        }

        assertEquals(20, inspected.size());
        assertEquals(0, AutoMiner.labeledVisibilityInspectionCount(-1, limit));
        assertEquals(4, AutoMiner.labeledVisibilityInspectionCount(20, 4));
        assertEquals(-1, AutoMiner.labeledVisibilityIndex(16, candidateCount, limit, fixed, 0));
        assertEquals(0, AutoMiner.advanceLabeledVisibilityCursor(8, 0, 8));
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

    @Test
    public void renderedRouteContainsOnlyRemainingStandNodes() {
        List<BlockPos> path = Arrays.asList(
            new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
            new BlockPos(2, 64, 0), new BlockPos(3, 64, 0));

        assertEquals(Arrays.asList(new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)),
            AutoMiner.remainingRoutePoints(path, 2, 10));
        assertTrue(AutoMiner.remainingRoutePoints(path, path.size(), 10).isEmpty());
        assertTrue(AutoMiner.remainingRoutePoints(java.util.Collections.emptyList(), 0, 10).isEmpty());
    }
}

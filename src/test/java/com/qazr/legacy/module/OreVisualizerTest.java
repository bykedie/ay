package com.qazr.legacy.module;

import com.qazr.legacy.config.OreType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class OreVisualizerTest {
    @Test
    public void scansOnlyTheRangesNeededByEnabledFeatures() {
        assertEquals(150.0, OreVisualizer.effectiveCacheRange(true, 150.0, false, 96.0), 0.0);
        assertEquals(96.0, OreVisualizer.effectiveCacheRange(false, 150.0, true, 96.0), 0.0);
        assertEquals(150.0, OreVisualizer.effectiveCacheRange(true, 150.0, true, 96.0), 0.0);
        assertEquals(0.0, OreVisualizer.effectiveCacheRange(false, 150.0, false, 96.0), 0.0);
    }

    @Test
    public void scansPlayerHeightBeforeAlternatingDownAndUp() {
        assertArrayEquals(new int[] {4, 3, 5, 2, 6, 1, 7, 0},
            OreVisualizer.sectionOrder(8, 4));
    }

    @Test
    public void autoMineUsesASmallerPerTickScanBudget() {
        assertEquals(12, OreVisualizer.scanBudget(false));
        assertEquals(2, OreVisualizer.scanBudget(true));
        assertEquals(16384, OreVisualizer.scanBlockBudget(false));
        assertEquals(4096, OreVisualizer.scanBlockBudget(true));
        assertEquals(1024, OreVisualizer.scanSliceChecks(0, 4096, 1024));
        assertEquals(1096, OreVisualizer.scanSliceChecks(3000, 4096, 4096));
        assertEquals(0, OreVisualizer.scanSliceChecks(4096, 4096, 1024));
        assertEquals(0, OreVisualizer.scanSliceChecks(0, 4096, 0));
    }

    @Test
    public void completedOreQuotasAreFilteredBeforeTheNearestWindow() {
        assertTrue(OreVisualizer.mineTypeEligible(true, true));
        assertTrue(!OreVisualizer.mineTypeEligible(true, false));
        assertTrue(!OreVisualizer.mineTypeEligible(false, true));
    }

    @Test
    public void unchangedSeedStateSkipsRepeatedQueueTraversal() {
        assertTrue(OreVisualizer.sameSeedState(
            true, 3, 32.0, 10, -4, 4, 3, 32.0, 10, -4, 4));
        assertTrue(!OreVisualizer.sameSeedState(
            false, 3, 32.0, 10, -4, 4, 3, 32.0, 10, -4, 4));
        assertTrue(!OreVisualizer.sameSeedState(
            true, 3, 32.0, 10, -4, 4, 3, 33.0, 10, -4, 4));
        assertTrue(!OreVisualizer.sameSeedState(
            true, 3, 32.0, 10, -4, 4, 3, 32.0, 11, -4, 4));
        assertTrue(!OreVisualizer.sameSeedState(
            true, 3, 32.0, 10, -4, 4, 3, 32.0, 10, -4, 5));
    }

    @Test
    public void verticalMovementReordersOnlyUnscannedSectionTails() {
        int[] order = {4, 3, 5, 2, 6, 1, 7, 0};
        OreVisualizer.prioritizeRemainingSections(order, 1, 200, 7);
        assertArrayEquals(new int[] {4, 3, 7, 6, 5, 2, 1, 0}, order);

        int[] untouchedCurrent = {4, 3, 5, 2, 6, 1, 7, 0};
        OreVisualizer.prioritizeRemainingSections(untouchedCurrent, 1, 0, 7);
        assertArrayEquals(new int[] {4, 7, 6, 5, 3, 2, 1, 0}, untouchedCurrent);
    }

    @Test
    public void nearbyChunkSectionsStayAheadOfFartherScanTasks() {
        assertTrue(OreVisualizer.scanTaskPrecedesResumed(0.25, 0.0));
        assertTrue(OreVisualizer.scanTaskPrecedesResumed(4.0, 0.0));
        assertTrue(!OreVisualizer.scanTaskPrecedesResumed(4.01, 0.0));
        assertTrue(OreVisualizer.scanTaskPrecedesResumed(144.0, 100.0));
        assertTrue(!OreVisualizer.scanTaskPrecedesResumed(144.01, 100.0));
        assertTrue(OreVisualizer.scanTaskPrecedesResumed(5.0, 9.0));
    }

    @Test
    public void cacheIncludesDiagonalAndEdgeChunksThatCanEnterMiningRange() {
        assertEquals(3, OreVisualizer.chunkSearchRadius(32.0));
        assertTrue(OreVisualizer.chunkCouldEnterRange(ChunkPos.asLong(2, 2), 0, 0, 32.0));
        assertTrue(OreVisualizer.chunkCouldEnterRange(ChunkPos.asLong(3, 0), 0, 0, 32.0));
        assertTrue(!OreVisualizer.chunkCouldEnterRange(ChunkPos.asLong(3, 2), 0, 0, 32.0));
        assertTrue(!OreVisualizer.chunkCouldEnterRange(ChunkPos.asLong(0, 0), 10, 0, 32.0));
    }

    @Test
    public void cacheValidationNeverExceedsItsMarkerBudget() {
        assertEquals(128, OreVisualizer.validationChecksForSlice(500, 0, 128));
        assertEquals(20, OreVisualizer.validationChecksForSlice(500, 480, 128));
        assertEquals(0, OreVisualizer.validationChecksForSlice(0, 0, 128));
        assertEquals(0, OreVisualizer.validationChecksForSlice(500, 0, 0));
        assertEquals(128, OreVisualizer.validationTaskVisitLimit(500, 128));
        assertEquals(2, OreVisualizer.validationTaskVisitLimit(2, 128));
        assertEquals(0, OreVisualizer.validationTaskVisitLimit(500, 0));
        assertTrue(OreVisualizer.reuseVisibleOreCount(20, 20));
        assertTrue(!OreVisualizer.reuseVisibleOreCount(20, 21));
        assertTrue(OreVisualizer.markerRestoreNeeded(OreType.IRON, 0, 0));
        assertTrue(OreVisualizer.markerRestoreNeeded(OreType.IRON, 0, 1));
        assertTrue(OreVisualizer.markerRestoreNeeded(OreType.IRON, 2, 2));
        assertTrue(!OreVisualizer.markerRestoreNeeded(OreType.IRON, 1, 1));
        assertTrue(OreVisualizer.markerCacheOwnsChunk(true, false, false));
        assertTrue(OreVisualizer.markerCacheOwnsChunk(false, true, false));
        assertTrue(OreVisualizer.markerCacheOwnsChunk(false, false, true));
        assertTrue(!OreVisualizer.markerCacheOwnsChunk(false, false, false));
        assertTrue(OreVisualizer.scannedMarkerChangesCache(false, null, OreType.IRON));
        assertTrue(!OreVisualizer.scannedMarkerChangesCache(true, OreType.IRON, OreType.IRON));
        assertTrue(OreVisualizer.scannedMarkerChangesCache(true, OreType.IRON, OreType.GOLD));
    }

    @Test
    public void equallyDistantCachedOresKeepAStableCoordinateOrder() {
        OreVisualizer.CachedOre first = new OreVisualizer.CachedOre(
            new BlockPos(-2, 12, 3), OreType.DIAMOND, 25.0);
        OreVisualizer.CachedOre second = new OreVisualizer.CachedOre(
            new BlockPos(2, 12, 3), OreType.DIAMOND, 25.0);
        List<OreVisualizer.CachedOre> forward = new ArrayList<>(Arrays.asList(first, second));
        List<OreVisualizer.CachedOre> reverse = new ArrayList<>(Arrays.asList(second, first));

        forward.sort(OreVisualizer::compareCachedOres);
        reverse.sort(OreVisualizer::compareCachedOres);

        assertEquals(forward.get(0).pos(), reverse.get(0).pos());
        assertEquals(forward.get(1).pos(), reverse.get(1).pos());
        assertTrue(OreVisualizer.compareCachedOres(forward.get(0), forward.get(1)) < 0);
        assertTrue(OreVisualizer.candidatePrecedesFarthest(24.0, second.pos(), first));
        assertTrue(!OreVisualizer.candidatePrecedesFarthest(26.0, first.pos(), second));
        assertEquals(OreVisualizer.compareCachedOres(first, second) < 0,
            OreVisualizer.candidatePrecedesFarthest(first.distanceSq(), first.pos(), second));
    }

    @Test
    public void drawsEveryEdgeForSingleOreBlock() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos block = new BlockPos(0, 0, 0);
        positions.add(block);

        assertEquals(12, OreVisualizer.boundaryLineCount(positions, block));
    }

    @Test
    public void skipsInternalLinesForAdjacentOreBlocks() {
        Set<BlockPos> positions = new HashSet<>();
        positions.add(new BlockPos(0, 0, 0));
        positions.add(new BlockPos(1, 0, 0));

        assertEquals(16, totalLines(positions));
    }

    @Test
    public void skipsCenterLinesInFlatNineBlockCluster() {
        Set<BlockPos> positions = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) positions.add(new BlockPos(x, 0, z));
        }

        assertEquals(0, OreVisualizer.boundaryLineCount(positions, new BlockPos(1, 0, 1)));
        assertEquals(28, totalLines(positions));
        assertTrue(totalLines(positions) < 9 * 12);
    }

    @Test
    public void cullsChunksOutsideHorizontalVisualizationRange() {
        assertTrue(OreVisualizer.chunkPossiblyInRange(ChunkPos.asLong(0, 0), 8.0, 8.0, 16.0));
        assertTrue(OreVisualizer.chunkPossiblyInRange(ChunkPos.asLong(1, 0), 8.0, 8.0, 16.0));
        assertTrue(!OreVisualizer.chunkPossiblyInRange(ChunkPos.asLong(4, 0), 8.0, 8.0, 16.0));
        assertEquals(0.0,
            OreVisualizer.chunkHorizontalDistanceSq(ChunkPos.asLong(0, 0), 8.0, 8.0), 0.0);
        assertEquals(2.25,
            OreVisualizer.chunkHorizontalDistanceSq(ChunkPos.asLong(0, 0), -1.0, 8.0), 0.0);
        assertEquals(4.5,
            OreVisualizer.chunkHorizontalDistanceSq(ChunkPos.asLong(0, 0), -1.0, -1.0), 0.0);
        assertEquals(0.25,
            OreVisualizer.chunkHorizontalDistanceSq(ChunkPos.asLong(-1, 0), 0.0, 8.0), 0.0);
        assertTrue(OreVisualizer.chunkCannotImproveNearest(257.0, 256.0, 0, 96,
            Double.POSITIVE_INFINITY));
        assertTrue(!OreVisualizer.chunkCannotImproveNearest(256.0, 256.0, 0, 96,
            Double.POSITIVE_INFINITY));
        assertTrue(!OreVisualizer.chunkCannotImproveNearest(100.0, 256.0, 95, 96, 64.0));
        assertTrue(OreVisualizer.chunkCannotImproveNearest(100.0, 256.0, 96, 96, 64.0));
        assertTrue(!OreVisualizer.chunkCannotImproveNearest(64.0, 256.0, 96, 96, 64.0));
        assertTrue(!OreVisualizer.distanceCannotImproveNearest(100.0, 95, 96, 64.0));
        assertTrue(OreVisualizer.distanceCannotImproveNearest(100.0, 96, 96, 64.0));
        assertTrue(!OreVisualizer.distanceCannotImproveNearest(64.0, 96, 96, 64.0));
    }

    private static int totalLines(Set<BlockPos> positions) {
        int total = 0;
        for (BlockPos pos : positions) total += OreVisualizer.boundaryLineCount(positions, pos);
        return total;
    }
}

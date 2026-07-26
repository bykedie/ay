package com.qazr.legacy.module;

import java.util.HashSet;
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
    }

    private static int totalLines(Set<BlockPos> positions) {
        int total = 0;
        for (BlockPos pos : positions) total += OreVisualizer.boundaryLineCount(positions, pos);
        return total;
    }
}

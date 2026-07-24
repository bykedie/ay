package com.qazr.legacy.module;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OreVisualizerTest {
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

    private static int totalLines(Set<BlockPos> positions) {
        int total = 0;
        for (BlockPos pos : positions) total += OreVisualizer.boundaryLineCount(positions, pos);
        return total;
    }
}

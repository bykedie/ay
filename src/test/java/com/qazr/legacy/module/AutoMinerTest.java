package com.qazr.legacy.module;

import com.qazr.legacy.config.OreType;
import java.util.Arrays;
import net.minecraft.util.math.BlockPos;
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
}

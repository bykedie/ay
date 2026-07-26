package com.qazr.legacy.module;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AutoBridgeTest {
    @Test
    public void scansOnlyTheBridgeLevelUnlessJumpingOrFalling() {
        assertEquals(1, AutoBridge.scanDepth(4, false));
        assertEquals(4, AutoBridge.scanDepth(4, true));
    }

    @Test
    public void retriesAnUnconfirmedPlacementDuringCooldown() {
        assertEquals(false, AutoBridge.shouldWaitForPlacementDelay(1, false, true));
        assertEquals(true, AutoBridge.shouldWaitForPlacementDelay(2, false, true));
        assertEquals(true, AutoBridge.shouldWaitForPlacementDelay(1, false, false));
        assertEquals(false, AutoBridge.shouldWaitForPlacementDelay(1, true, false));
        assertEquals(false, AutoBridge.shouldWaitForPlacementDelay(0, false, false));
    }

    @Test
    public void backsOffRepeatedPlacementRetriesWithoutLongGaps() {
        assertEquals(1, AutoBridge.placementRetryDelay(1));
        assertEquals(2, AutoBridge.placementRetryDelay(2));
        assertEquals(4, AutoBridge.placementRetryDelay(3));
        assertEquals(4, AutoBridge.placementRetryDelay(4));
        assertEquals(4, AutoBridge.placementRetryDelay(8));
        assertEquals(false, AutoBridge.placementRetryDue(19, 20));
        assertEquals(true, AutoBridge.placementRetryDue(20, 20));
        assertEquals(true, AutoBridge.placementRetryDue(21, 20));
    }

    @Test
    public void keepsMissingPlacementsAndRecentConfirmations() {
        assertEquals(80, AutoBridge.placementConfirmationExpiry(20));
        assertEquals(true, AutoBridge.retainPendingPlacement(true, 80, 60));
        assertEquals(true, AutoBridge.retainPendingPlacement(false, 20, 60));
        assertEquals(false, AutoBridge.retainPendingPlacement(false, 61, 60));
        assertEquals(false, AutoBridge.playerTickResetNeeded(-1, 0));
        assertEquals(false, AutoBridge.playerTickResetNeeded(20, 21));
        assertEquals(true, AutoBridge.playerTickResetNeeded(20, 0));
    }

    @Test
    public void retriesOnlyGapsThatRemainWithinRepairDistance() {
        assertEquals(true, AutoBridge.repairablePendingPlacement(24.9, 5.0));
        assertEquals(true, AutoBridge.repairablePendingPlacement(25.0, 5.0));
        assertEquals(false, AutoBridge.repairablePendingPlacement(25.1, 5.0));
    }

    @Test
    public void rejectsFallingBlocksAsPermanentBridgeSupports() {
        assertEquals(true, AutoBridge.stableBridgeBlock(true, false));
        assertEquals(false, AutoBridge.stableBridgeBlock(true, true));
        assertEquals(false, AutoBridge.stableBridgeBlock(false, false));
    }

    @Test
    public void prioritizesFeetWhileFallingAndLookaheadWhileWalking() {
        assertEquals(0.0, AutoBridge.candidateLookaheads(1.2, true)[0], 0.0);
        assertEquals(1.2, AutoBridge.candidateLookaheads(1.2, false)[2], 0.0);
    }

    @Test
    public void detectsTheJumpApexOnlyWhileAirborne() {
        assertEquals(true, AutoBridge.isJumpApex(0.12, -0.01, false));
        assertEquals(false, AutoBridge.isJumpApex(0.12, -0.01, true));
        assertEquals(false, AutoBridge.isJumpApex(-0.02, -0.08, false));
    }

    @Test
    public void mapsForwardMovementToYawDirection() {
        assertOffset(0.0, 1.0, AutoBridge.movementOffset(0.0F, 1.0F, 0.0F));
        assertOffset(-1.0, 0.0, AutoBridge.movementOffset(90.0F, 1.0F, 0.0F));
        assertOffset(0.0, -1.0, AutoBridge.movementOffset(180.0F, 1.0F, 0.0F));
    }

    @Test
    public void normalizesDiagonalMovement() {
        double[] offset = AutoBridge.movementOffset(0.0F, 1.0F, 1.0F);

        assertEquals(1.0, Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]), 0.0001);
        assertEquals(offset[0], offset[1], 0.0001);
    }

    @Test
    public void supportsJumpAndFallPlacementOffsets() {
        assertEquals(63, AutoBridge.candidateY(64.8, 0));
        assertEquals(62, AutoBridge.candidateY(64.8, 1));
        assertEquals(60, AutoBridge.candidateY(64.8, 3));
    }

    @Test
    public void diagonalPredictionAddsAxisSupportsBeforeTheCorner() {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        AutoBridge.addSupportCandidates(candidates, 0.8, 0.8, 1.2, 1.2, 63);

        BlockPos[] positions = candidates.toArray(new BlockPos[0]);
        assertEquals(new BlockPos(1, 63, 0), positions[0]);
        assertEquals(new BlockPos(1, 63, 1), positions[1]);
        assertEquals(new BlockPos(0, 63, 1), positions[2]);
    }

    private static void assertOffset(double x, double z, double[] offset) {
        assertEquals(x, offset[0], 0.0001);
        assertEquals(z, offset[1], 0.0001);
    }
}

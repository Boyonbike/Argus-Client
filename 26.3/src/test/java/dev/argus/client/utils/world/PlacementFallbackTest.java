package dev.argus.client.utils.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlacementFallbackTest {
    private static final Vec3 EYE = new Vec3(0.5, 0.5, 0.5);

    @Test
    void leavesClicksInsideTheServerEnvelopeAlone() {
        assertNull(PlacementFallback.resolveTarget(new BlockPos(5, 0, 0), new BlockPos(4, 0, 0), EYE, false));
    }

    @Test
    void rewritesAnOutOfRangeClickToThePlacementPosition() {
        assertEquals(new BlockPos(5, 0, 0), PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(5, 0, 0), EYE, false));
    }

    @Test
    void rewritesEvenWhenThePlacementPositionIsBeyondTheAssumedEnvelope() {
        // The server decides whether the placement position is reachable; AirPlace already proves this path works.
        assertEquals(new BlockPos(7, 0, 0), PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(7, 0, 0), EYE, false));
    }

    @Test
    void measuresFromTheNearestFaceOfTheBlock() {
        // A block at x=6 has its nearest face at 5.5, exactly the survival envelope, so the click is outside it.
        assertEquals(new BlockPos(6, 0, 0), PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(6, 0, 0), EYE, false));
    }

    @Test
    void envelopeMatchesVanillaRangePlusTheServerPadding() {
        assertEquals(5.5, PlacementFallback.serverEnvelope(false), 1e-9);
        assertEquals(6.0, PlacementFallback.serverEnvelope(true), 1e-9);
    }

    @Test
    void placementPositionReplacesTheClickedBlockWhenReplaceable() {
        BlockPos clicked = new BlockPos(7, 0, 0);

        assertEquals(clicked, PlacementFallback.placementPosition(clicked, Direction.UP, true));
    }

    @Test
    void placementPositionUsesTheClickedFaceOtherwise() {
        BlockPos clicked = new BlockPos(7, 0, 0);

        assertEquals(new BlockPos(7, 1, 0), PlacementFallback.placementPosition(clicked, Direction.UP, false));
    }
}

package dev.argus.client.utils.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlacementFallbackTest {
    private static final Vec3 EYE = new Vec3(0.5, 0.5, 0.5);

    @Test
    void leavesClicksInsideTheServerEnvelopeAlone() {
        assertNull(PlacementFallback.resolveTarget(new BlockPos(5, 0, 0), new BlockPos(4, 0, 0), EYE, false));
    }

    @Test
    void rewritesAnOutOfRangeClickToALegalPlacementPosition() {
        assertEquals(new BlockPos(5, 0, 0), PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(5, 0, 0), EYE, false));
    }

    @Test
    void refusesWhenBothPositionsAreOutOfRange() {
        assertNull(PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(7, 0, 0), EYE, false));
    }

    @Test
    void measuresFromTheNearestFaceOfTheBlock() {
        // A block at x=6 has its nearest face at 5.5, exactly the survival envelope, so it is outside.
        assertNull(PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(6, 0, 0), EYE, false));

        // The creative envelope is 6.0, so the same position is inside it.
        assertEquals(new BlockPos(6, 0, 0), PlacementFallback.resolveTarget(new BlockPos(7, 0, 0), new BlockPos(6, 0, 0), EYE, true));
    }

    @Test
    void envelopeMatchesVanillaRangePlusTheServerPadding() {
        assertEquals(5.5, PlacementFallback.serverEnvelope(false), 1e-9);
        assertEquals(6.0, PlacementFallback.serverEnvelope(true), 1e-9);
    }
}

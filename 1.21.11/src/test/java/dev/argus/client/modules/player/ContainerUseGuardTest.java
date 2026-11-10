package dev.argus.client.modules.player;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerUseGuardTest {
    private static final BlockPos POS = new BlockPos(10, 64, 10);
    private final ContainerUseGuard guard = new ContainerUseGuard();

    @Test void blocksAContainerPlacedDuringTheCurrentHold() {
        guard.placed(POS, true);
        assertTrue(guard.shouldBlock(POS, true, false, false));
        assertTrue(guard.shouldBlock(POS, true, false, true));
    }

    @Test void ignoresNonContainerPositions() {
        guard.placed(POS, true);
        assertFalse(guard.shouldBlock(POS, false, false, true));
    }

    @Test void releaseClearsRememberedPlacements() {
        guard.placed(POS, true);
        guard.update(false);
        assertFalse(guard.shouldBlock(POS, true, false, true));
    }

    @Test void doesNotRememberPlacementsWhileUseIsReleased() {
        guard.placed(POS, false);
        assertFalse(guard.shouldBlock(POS, true, false, true));
    }

    @Test void sneakWithAnItemBypassesTheGuard() {
        guard.placed(POS, true);
        assertFalse(guard.shouldBlock(POS, true, true, true));
    }

    @Test void sneakWithEmptyHandsIsStillGuarded() {
        guard.placed(POS, true);
        assertTrue(guard.shouldBlock(POS, true, true, false));
    }

    @Test void clearForgetsEverything() {
        guard.placed(POS, true);
        guard.clear();
        assertFalse(guard.shouldBlock(POS, true, false, true));
    }
}

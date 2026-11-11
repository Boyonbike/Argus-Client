package dev.argus.client.modules.player.rekit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RekitButtonPlacementTest {
    @Test void usesTheSideWhenDoubleChestLeavesNoRoomAbove() {
        assertEquals(new RekitButtonPlacement.Position(304, 9), RekitButtonPlacement.place(426, 125, 9, 176));
    }
    @Test void usesTheSpaceAboveWhenAvailable() {
        assertEquals(new RekitButtonPlacement.Position(125, 47), RekitButtonPlacement.place(426, 125, 69, 176));
    }
}

package dev.argus.client.modules.player.rekit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RekitEquipmentTransferTest {
    private static class Inventory implements RekitEquipmentTransfer.Access {
        String storage = "new helmet", hand = "pickaxe", worn = "damaged helmet";
        boolean blocked, rejected;
        public boolean stage() {
            if (blocked) return false;
            String previous = hand; hand = storage; storage = previous; return true;
        }
        public boolean equip() {
            if (rejected) return false;
            assertEquals("new helmet", hand);
            String previous = worn; worn = hand; hand = previous; return true;
        }
        public void restore() { String previous = hand; hand = storage; storage = previous; }
    }
    @Test void delayedUsePacketsNeverStageInAnotherSelectedSlot() {
        assertEquals(-1, RekitEquipmentTransfer.stagingSlot(0, false, slot -> slot == 1));
        assertEquals(0, RekitEquipmentTransfer.stagingSlot(0, false, slot -> slot == 0));
        assertEquals(1, RekitEquipmentTransfer.stagingSlot(0, true, slot -> slot == 1));
    }
    @Test void equipsReplacementAndReturnsOldArmourWhileRestoringTheHand() {
        var inv = new Inventory();
        assertTrue(RekitEquipmentTransfer.execute(inv));
        assertEquals("new helmet", inv.worn); assertEquals("damaged helmet", inv.storage); assertEquals("pickaxe", inv.hand);
    }
    @Test void rejectedEquipRestoresTheHandAndStorage() {
        var inv = new Inventory(); inv.rejected = true;
        assertFalse(RekitEquipmentTransfer.execute(inv));
        assertEquals("damaged helmet", inv.worn); assertEquals("new helmet", inv.storage); assertEquals("pickaxe", inv.hand);
    }
    @Test void cancelledStagingNeverUsesTheOriginalHeldItem() {
        var inv = new Inventory(); inv.blocked = true;
        assertFalse(RekitEquipmentTransfer.execute(inv));
        assertEquals("damaged helmet", inv.worn); assertEquals("new helmet", inv.storage); assertEquals("pickaxe", inv.hand);
    }
}

package dev.argus.client.modules.player.rekit;

/** Equip via the normal held-item action, restoring the temporary hotbar swap on success or rejection. */
public final class RekitEquipmentTransfer {
    private RekitEquipmentTransfer() {}
    /** Delayed use-item queues require the selected slot to stay unchanged until the queue flushes. */
    public static int stagingSlot(int selected, boolean selectionCanChange, java.util.function.IntPredicate canStoreHand) {
        for (int offset = 0; offset < (selectionCanChange ? 9 : 1); offset++) {
            int slot = (selected + offset) % 9;
            if (canStoreHand.test(slot)) return slot;
        }
        return -1;
    }
    public interface Access {
        /** False means staging was cancelled: never use the original held item. */
        boolean stage();
        boolean equip();
        void restore();
    }
    public static boolean execute(Access access) {
        if (!access.stage()) return false;
        try { return access.equip(); }
        finally { access.restore(); }
    }
}

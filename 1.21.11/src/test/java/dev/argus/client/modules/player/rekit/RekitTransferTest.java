package dev.argus.client.modules.player.rekit;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RekitTransferTest {
    record Pile(String name, int count) {}
    private void run(List<RekitTransfer.Click> clicks, Map<Integer, Pile> slots) {
        Pile carried = null;
        for (var click : clicks) {
            Pile slot = slots.get(click.slot());
            if (click.swap()) {
                assertNull(carried);
                Pile offhand = slots.get(40);
                slots.put(40, slot); slots.put(click.slot(), offhand);
            } else if (click.button() == 1) {
                assertNotNull(carried);
                assertTrue(slot == null || slot.name.equals(carried.name));
                slots.put(click.slot(), new Pile(carried.name, slot == null ? 1 : slot.count + 1));
                carried = carried.count == 1 ? null : new Pile(carried.name, carried.count - 1);
            } else if (carried != null && slot != null && slot.name.equals(carried.name)) {
                int n = Math.min(carried.count, 64 - slot.count);
                slots.put(click.slot(), new Pile(slot.name, slot.count + n));
                carried = carried.count == n ? null : new Pile(carried.name, carried.count - n);
            } else {
                slots.put(click.slot(), carried); carried = slot;
            }
        }
        assertNull(carried, "A complete transaction must leave the cursor empty");
    }
    @Test void offhandRefillRestoresTheHotbarAndPreservesSourceRemainder() {
        var slots = new HashMap<Integer, Pile>();
        slots.put(0, new Pile("stone", 64)); slots.put(1, new Pile("pickaxe", 1)); slots.put(40, new Pile("stone", 8));
        run(RekitTransfer.offhandPlan(0, 1, 64, 12, false, -1), slots);
        assertEquals(new Pile("stone", 52), slots.get(0)); assertEquals(new Pile("stone", 20), slots.get(40));
        assertEquals(new Pile("pickaxe", 1), slots.get(1));
    }
    @Test void offhandReplacementReturnsDisplacedItemsAndRestoresTheHotbar() {
        var slots = new HashMap<Integer, Pile>();
        slots.put(0, new Pile("renamed stone", 64)); slots.put(1, new Pile("pickaxe", 1)); slots.put(40, new Pile("old stone", 8));
        run(RekitTransfer.offhandPlan(0, 1, 64, 64, true, -1), slots);
        assertEquals(new Pile("old stone", 8), slots.get(0)); assertEquals(new Pile("renamed stone", 64), slots.get(40));
        assertEquals(new Pile("pickaxe", 1), slots.get(1));
    }
    @Test void partialRefillLeavesTheSourceRemainderAndHitsTheExactTarget() {
        var slots = new HashMap<Integer, Pile>(); slots.put(0, new Pile("stone", 64)); slots.put(1, new Pile("stone", 8));
        run(RekitTransfer.plan(0, 1, 64, 12, false, -1), slots);
        assertEquals(new Pile("stone", 52), slots.get(0)); assertEquals(new Pile("stone", 20), slots.get(1));
    }
    @Test void fullReplacementReturnsTheDisplacedStackToStorage() {
        var slots = new HashMap<Integer, Pile>(); slots.put(0, new Pile("renamed stone", 64)); slots.put(1, new Pile("old stone", 8));
        run(RekitTransfer.plan(0, 1, 64, 64, true, -1), slots);
        assertEquals(new Pile("old stone", 8), slots.get(0)); assertEquals(new Pile("renamed stone", 64), slots.get(1));
    }
    @Test void partialIncompatibleReplacementPreservesAllThreeStacks() {
        var slots = new HashMap<Integer, Pile>(); slots.put(0, new Pile("renamed stone", 64)); slots.put(1, new Pile("old stone", 8));
        run(RekitTransfer.plan(0, 1, 64, 20, true, 2), slots);
        assertEquals(new Pile("renamed stone", 44), slots.get(0)); assertEquals(new Pile("renamed stone", 20), slots.get(1));
        assertEquals(new Pile("old stone", 8), slots.get(2));
    }
    @Test void partialReplacementWithoutRoomDoesNothing() {
        assertTrue(RekitTransfer.plan(0, 1, 64, 20, true, -1).isEmpty());
    }
    @Test void fullFillIntoEmptySlotLeavesNoCursorStack() {
        var slots = new HashMap<Integer, Pile>(); slots.put(0, new Pile("stone", 64));
        run(RekitTransfer.plan(0, 1, 64, 64, false, -1), slots);
        assertNull(slots.get(0)); assertEquals(new Pile("stone", 64), slots.get(1));
    }
}

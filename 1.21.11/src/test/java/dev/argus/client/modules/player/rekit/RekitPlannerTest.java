package dev.argus.client.modules.player.rekit;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RekitPlannerTest {
    private RekitPlanner.Stack stack(String match, String merge, int count) {
        return new RekitPlanner.Stack(match, merge, count, 64, -1);
    }
    private RekitPlanner.Stack gear(String match, int durability) {
        return new RekitPlanner.Stack(match, match + durability, 1, 1, durability);
    }
    private RekitPlanner.Move choose(RekitPlanner.Stack saved, RekitPlanner.Stack current,
                                     List<RekitPlanner.Stack> sources, boolean full, boolean larger, boolean durability) {
        return RekitPlanner.choose(saved, current, sources, full, larger, durability);
    }
    @Test void topsUpOnlyTheSavedCount() {
        assertEquals(new RekitPlanner.Move(0, 12, false), choose(stack("stone", "a", 20), stack("stone", "a", 8),
                List.of(stack("stone", "a", 64)), false, false, false));
    }
    @Test void fullStacksUsesTheItemsOwnMaximum() {
        var saved = new RekitPlanner.Stack("pearl", "a", 3, 16, -1);
        assertEquals(new RekitPlanner.Move(0, 16, false), choose(saved, RekitPlanner.Stack.EMPTY,
                List.of(new RekitPlanner.Stack("pearl", "a", 16, 16, -1)), true, false, false));
    }
    @Test void differentNamesRequireTheLargerStackToggle() {
        var saved = stack("stone", "saved name", 64);
        var current = stack("stone", "first name", 12);
        var sources = List.of(stack("stone", "second name", 40));
        assertNull(choose(saved, current, sources, true, false, false));
        assertEquals(new RekitPlanner.Move(0, 40, true), choose(saved, current, sources, true, true, false));
    }
    @Test void incompatibleReplacementNeverMakesAStackSmallerOrLoopsAtTarget() {
        assertNull(choose(stack("stone", "saved", 12), stack("stone", "a", 12),
                List.of(stack("stone", "b", 64)), false, true, false));
        assertNull(choose(stack("stone", "saved", 64), stack("stone", "a", 32),
                List.of(stack("stone", "b", 16)), true, true, false));
    }
    @Test void prefersMergeableItemsBeforeReplacingRenamedStacks() {
        assertEquals(new RekitPlanner.Move(1, 8, false), choose(stack("stone", "saved", 20), stack("stone", "a", 12),
                List.of(stack("stone", "b", 64), stack("stone", "a", 30)), false, true, false));
    }
    @Test void replacesWrongItemsButNotWithDifferentEnchantmentsOrPotions() {
        assertEquals(new RekitPlanner.Move(1, 1, true), choose(gear("sword sharp5", 100), gear("pick", 100),
                List.of(gear("sword sharp4", 200), gear("sword sharp5", 200)), false, false, false));
        assertNull(choose(stack("healing2", "a", 1), RekitPlanner.Stack.EMPTY,
                List.of(stack("healing1", "b", 1)), false, true, false));
    }
    @Test void durabilityToggleChoosesTheBestExactMatchAndNeverDowngrades() {
        var saved = gear("sword sharp5", 100);
        var sources = List.of(gear("sword sharp4", 500), gear("sword sharp5", 200), gear("sword sharp5", 400));
        assertNull(choose(saved, gear("sword sharp5", 100), sources, false, false, false));
        assertEquals(new RekitPlanner.Move(2, 1, true), choose(saved, gear("sword sharp5", 100), sources, false, false, true));
        assertNull(choose(saved, gear("sword sharp5", 450), sources, false, false, true));
    }
    @Test void fullStorageStillUsesAWholeStackWhenAnEarlierSourceNeedsSplitting() {
        assertEquals(new RekitPlanner.Move(1, 16, true), RekitPlanner.choose(
                stack("stone", "saved", 16), stack("dirt", "dirt", 8),
                List.of(stack("stone", "a", 64), stack("stone", "b", 16)), false, true, false, false));
    }
    @Test void ignoresEmptyPresetSlotsAndDoesNotTrimExistingExcess() {
        assertNull(choose(RekitPlanner.Stack.EMPTY, stack("stone", "a", 12), List.of(stack("stone", "a", 64)), true, true, true));
        assertNull(choose(stack("stone", "a", 12), stack("stone", "a", 32), List.of(stack("stone", "a", 64)), false, true, false));
    }
}

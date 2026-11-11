package dev.argus.client.chat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscordSyncApplyTest {
    private static DiscordIgn.Result result(List<String> names, boolean unreadable) {
        return new DiscordIgn.Result(Map.of(), names, names.size(), 0, List.of(), unreadable, Map.of(), Map.of());
    }

    private static final DiscordSyncApply.Lists BEFORE = new DiscordSyncApply.Lists(
        List.of("Manual", "OldSynced", "StillHere"), List.of("OldSynced", "StillHere"),
        List.of("WhitelistManual"), List.of());

    @Test void onlyNamesTheSyncAddedAreRemoved() {
        var outcome = DiscordSyncApply.apply(result(List.of("StillHere", "Newcomer"), false), BEFORE, true, false, true);
        assertEquals(DiscordSyncApply.Kind.APPLIED, outcome.kind());
        assertEquals(List.of("Manual", "StillHere", "Newcomer"), outcome.lists().clan(), "Hand-added names stay; left members go");
        assertEquals(List.of("StillHere", "Newcomer"), outcome.lists().clanSynced());
        assertEquals(List.of("clan list +1/-1"), outcome.changes());
        assertEquals(BEFORE.whitelist(), outcome.lists().whitelist(), "A destination that's switched off isn't touched");
    }

    @Test void aNameAddedByHandIsNeverTakenOverBySync() {
        var outcome = DiscordSyncApply.apply(result(List.of("WhitelistManual", "Other"), false), BEFORE, false, true, true);
        assertEquals(List.of("WhitelistManual", "Other"), outcome.lists().whitelist());
        assertEquals(List.of("Other"), outcome.lists().whitelistSynced(), "Overlap with a manual entry stays manual");
        var later = DiscordSyncApply.apply(result(List.of("Other2"), false), outcome.lists(), false, true, true);
        assertEquals(List.of("WhitelistManual", "Other2"), later.lists().whitelist(), "So a later sync never removes it");
    }

    @Test void unreadableOrEmptyReadsLeaveEverythingAsItWas() {
        var unreadable = DiscordSyncApply.apply(result(List.of("Someone"), true), BEFORE, true, true, true);
        assertEquals(DiscordSyncApply.Kind.UNREADABLE, unreadable.kind());
        assertEquals(BEFORE, unreadable.lists());
        var empty = DiscordSyncApply.apply(result(List.of(), false), BEFORE, true, true, true);
        assertEquals(DiscordSyncApply.Kind.EMPTY, empty.kind());
        assertEquals(BEFORE, empty.lists(), "The sync never empties a list");
    }

    @Test void untrustedOldTrackingKeepsEverythingAsManual() {
        var outcome = DiscordSyncApply.apply(result(List.of("Newcomer"), false), BEFORE, true, false, false);
        assertTrue(outcome.upgradedOwnership());
        assertEquals(List.of("Manual", "OldSynced", "StillHere", "Newcomer"), outcome.lists().clan(),
            "Old tracking may have listed manual names, so nothing is removed");
        assertEquals(List.of("Newcomer"), outcome.lists().clanSynced());
        assertEquals(List.of(), outcome.lists().whitelistSynced(), "Untrusted tracking for a switched-off list is dropped");
    }
}

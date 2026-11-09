package dev.argus.client.chat;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ChatToolsStoreTest {
    @TempDir Path dir;

    private Path file() { return dir.resolve("argusclient/chat.json"); }

    @Test void listsAndTextsSurviveReload() throws IOException {
        var store = new ChatToolsStore(file());
        store.set(ChatToolsStore.Lists.HOMES, List.of("6b6t.org/a|base", "6b6t.org/a|farm"));
        store.set(ChatToolsStore.Lists.CLAN_MEMBERS, List.of("Alice", "Bob"));
        store.set(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE, "guild-1");
        store.save();

        var loaded = new ChatToolsStore(file());
        assertEquals(List.of("6b6t.org/a|base", "6b6t.org/a|farm"), loaded.get(ChatToolsStore.Lists.HOMES));
        assertEquals(List.of("Alice", "Bob"), loaded.get(ChatToolsStore.Lists.CLAN_MEMBERS));
        assertEquals("guild-1", loaded.get(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE));
        assertEquals(List.of(), loaded.get(ChatToolsStore.Lists.RECENT_PLAYERS), "Unset lists read as empty");
        assertEquals("", loaded.get(ChatToolsStore.Texts.DISCORD_USERNAMES), "Unset texts read as empty");
    }

    @Test void missingFileStartsEmptyAndIsOnlyCreatedByAChange() throws IOException {
        var store = new ChatToolsStore(file());
        store.save();
        assertFalse(Files.exists(file()), "Nothing to save, so no file");
        store.set(ChatToolsStore.Lists.RECENT_PLAYERS, List.of("x|Alice"));
        store.save();
        assertTrue(Files.exists(file()));
    }

    @Test void onlyRealChangesMarkTheStoreDirty() throws IOException {
        var store = new ChatToolsStore(file());
        assertTrue(store.set(ChatToolsStore.Lists.HOMES, List.of("a|base")));
        assertTrue(store.dirty());
        store.save();
        assertFalse(store.dirty());
        assertFalse(store.set(ChatToolsStore.Lists.HOMES, List.of("a|base")), "Same value is not a change");
        assertFalse(store.set(ChatToolsStore.Texts.DISCORD_ROLES, ""), "Empty text equals unset");
        assertFalse(store.dirty());
    }

    @Test void anEmptiedListIsToldApartFromAnUnsetOne() throws IOException {
        var store = new ChatToolsStore(file());
        assertFalse(store.has(ChatToolsStore.Lists.COMMAND_OPTIONS), "Unset lists fall back to the module's defaults");
        store.set(ChatToolsStore.Lists.COMMAND_OPTIONS, List.of("Message | /msg {player} "));
        store.set(ChatToolsStore.Lists.COMMAND_OPTIONS, List.of());
        store.save();
        var loaded = new ChatToolsStore(file());
        assertTrue(loaded.has(ChatToolsStore.Lists.COMMAND_OPTIONS), "Emptied on purpose stays empty after reload");
        assertEquals(List.of(), loaded.get(ChatToolsStore.Lists.COMMAND_OPTIONS));
    }

    @Test void anInMemoryStoreNeverWrites() throws IOException {
        var store = ChatToolsStore.inMemory();
        store.set(ChatToolsStore.Lists.HOMES, List.of("a|base"));
        store.save();
        assertEquals(List.of("a|base"), store.get(ChatToolsStore.Lists.HOMES));
        assertFalse(Files.exists(file()));
    }

    @Test void returnedAndStoredListsAreCopies() throws IOException {
        var store = new ChatToolsStore(file());
        var input = new ArrayList<>(List.of("a|base"));
        store.set(ChatToolsStore.Lists.HOMES, input);
        input.add("a|farm");
        assertEquals(List.of("a|base"), store.get(ChatToolsStore.Lists.HOMES));
        assertThrows(UnsupportedOperationException.class, () -> store.get(ChatToolsStore.Lists.HOMES).add("x"));
    }

    @Test void malformedFileIsReportedAndNeverOverwritten() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{not json");
        assertThrows(IOException.class, () -> new ChatToolsStore(file()));
        assertEquals("{not json", Files.readString(file()));

        Files.writeString(file(), "[1,2]");
        assertThrows(IOException.class, () -> new ChatToolsStore(file()), "The root must be an object");
    }

    @Test void wrongTypedEntriesAreSkippedAndUnknownKeysKept() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), """
            {"version":1,
             "homes":["a|base", 5, {"x":1}, null, "a|farm"],
             "clan-members":"Alice",
             "discord-role-source":["nope"],
             "future-key":{"kept":true}}
            """);
        var store = new ChatToolsStore(file());
        assertEquals(List.of("a|base", "a|farm"), store.get(ChatToolsStore.Lists.HOMES), "Only strings are kept");
        assertEquals(List.of(), store.get(ChatToolsStore.Lists.CLAN_MEMBERS), "A non-list value is ignored");
        assertEquals("", store.get(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE), "A non-text value is ignored");

        store.set(ChatToolsStore.Lists.RECENT_PLAYERS, List.of("a|Bob"));
        store.save();
        var root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertTrue(root.getAsJsonObject("future-key").get("kept").getAsBoolean(), "Keys from newer versions survive a save");
        assertEquals(1, root.get("version").getAsInt());
    }

    @Test void savingLeavesNoTemporaryFiles() throws IOException {
        var store = new ChatToolsStore(file());
        store.set(ChatToolsStore.Lists.HOMES, List.of("a|base"));
        store.save();
        store.set(ChatToolsStore.Lists.HOMES, List.of("a|farm"));
        store.save();
        try (var files = Files.list(file().getParent())) {
            assertEquals(List.of(file()), files.toList());
        }
    }

    @Test void failedImportPreservesLiveListsAndPendingEdits() throws IOException {
        var store = new ChatToolsStore(file());
        var key = ChatToolsStore.Lists.CLAN_MEMBERS;
        store.set(key, List.of("Manual"));
        Files.createDirectories(file());

        assertThrows(IOException.class, () -> store.importData(
            Map.of(key, List.of("Manual", "Imported")), Map.of(ChatToolsStore.Texts.OWNERSHIP_VERSION, "1")));

        assertEquals(List.of("Manual"), store.get(key));
        assertEquals("", store.get(ChatToolsStore.Texts.OWNERSHIP_VERSION));
        assertTrue(store.dirty());
        try (var siblings = Files.list(file().getParent())) {
            assertEquals(List.of(file()), siblings.toList());
        }
    }

    @Test void keysMatchTheOldSettingNamesForImport() {
        assertEquals("homes", ChatToolsStore.Lists.HOMES.key());
        assertEquals("favorite-homes", ChatToolsStore.Lists.FAVORITE_HOMES.key());
        assertEquals("saved-cooldowns", ChatToolsStore.Lists.SAVED_COOLDOWNS.key());
        assertEquals("recent-players", ChatToolsStore.Lists.RECENT_PLAYERS.key());
        assertEquals("clan-members", ChatToolsStore.Lists.CLAN_MEMBERS.key());
        assertEquals("auto-tpy-whitelist", ChatToolsStore.Lists.AUTO_TPY_WHITELIST.key());
        assertEquals("auto-tpy-blocklist", ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST.key());
        assertEquals("auto-tpy-discord-role-ids", ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS.key());
        assertEquals("clan-synced", ChatToolsStore.Lists.CLAN_SYNCED.key());
        assertEquals("auto-tpy-synced", ChatToolsStore.Lists.AUTO_TPY_SYNCED.key());
        assertEquals("discord-role-roster", ChatToolsStore.Texts.DISCORD_ROLES.key());
        assertEquals("discord-role-source", ChatToolsStore.Texts.DISCORD_ROLE_SOURCE.key());
        assertEquals("discord-username-roster", ChatToolsStore.Texts.DISCORD_USERNAMES.key());
        assertEquals("command-options", ChatToolsStore.Lists.COMMAND_OPTIONS.key());
        assertEquals("ownership-version", ChatToolsStore.Texts.OWNERSHIP_VERSION.key());
        assertEquals("incoming-dm-patterns", ChatToolsStore.Lists.INCOMING_PATTERNS.key());
        assertEquals("outgoing-dm-patterns", ChatToolsStore.Lists.OUTGOING_PATTERNS.key());
    }
}

package dev.argus.client.config.importing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.argus.client.chat.ChatToolsStore;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.argus.client.config.importing.ConfigImportModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Importing Comet's Chat Tools and Discord IGN Sync: settings through the module preset, data into the chat store. */
class ChatToolsImportTest {
    private static JsonArray strings(String... values) { JsonArray a = new JsonArray(); for (String v : values) a.add(v); return a; }
    private static SourceModule module(String id, Map<String, JsonElement> settings) {
        return new SourceModule(id, true, settings, new JsonObject(), Optional.empty());
    }

    private static SourceConfig comet() {
        Map<String, JsonElement> chat = new LinkedHashMap<>();
        chat.put("General/homes", strings("base"));
        chat.put("General/command-options", strings("Message | /msg {player} ", "Duel | /duel {player}"));
        chat.put("General/favorite-homes", strings("6b6t.org/u|base"));
        chat.put("Teleport Cooldowns/saved-cooldowns", strings("A|Home|600000", "B|TPA|30000"));
        chat.put("Messages/recent-players", strings("A|Alice", "B|Bob"));
        chat.put("Messages/incoming-dm-patterns", strings("^(\\w+) whispers: (.*)$"));
        chat.put("Clan/clan-members", strings("Alice", "Carol"));
        chat.put("Clan/discord-role-roster", new JsonPrimitive("{\"alice\":[]}"));
        chat.put("Clan/discord-role-source", new JsonPrimitive("relay:https://r.example.workers.dev"));
        chat.put("Clan/discord-username-roster", new JsonPrimitive("{}"));
        chat.put("Auto TPY/auto-tpy-whitelist", strings("Alice", "Dave"));
        chat.put("Auto TPY/auto-tpy-blocklist", strings());
        chat.put("Clan/clan-chat-color", new JsonPrimitive("not a list"));
        Map<String, JsonElement> sync = new LinkedHashMap<>();
        sync.put("General/relay-url", new JsonPrimitive(" https://r.example.workers.dev "));
        sync.put("Applying/clan-synced", strings("Carol"));
        sync.put("Applying/auto-tpy-synced", strings("Dave"));
        sync.put("Applying/ownership-version", new JsonPrimitive(1));
        return new SourceConfig(Format.METEOR, List.of(module("chat-tools", chat), module("discord-ign-sync", sync), module("sprint", Map.of())));
    }

    private static ChatToolsDataImport.Current empty() {
        return current(Map.of(), Map.of(), "");
    }
    private static ChatToolsDataImport.Current current(Map<ChatToolsStore.Lists, List<String>> lists, Map<ChatToolsStore.Texts, String> texts, String relay) {
        return new ChatToolsDataImport.Current(l -> lists.getOrDefault(l, List.of()), lists::containsKey, t -> texts.getOrDefault(t, ""),
            relay, List.of("Message | /msg {player} ", "Whisper | /w {player} "));
    }

    @Test void extractsCometListsTextsAndTheRelay() {
        var data = ChatToolsDataImport.extract(comet());
        assertEquals(List.of("Alice", "Carol"), data.lists().get(ChatToolsStore.Lists.CLAN_MEMBERS));
        assertEquals(List.of("Carol"), data.lists().get(ChatToolsStore.Lists.CLAN_SYNCED));
        assertFalse(data.lists().containsKey(ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST), "Empty lists are left out");
        assertEquals("1", data.texts().get(ChatToolsStore.Texts.OWNERSHIP_VERSION));
        assertFalse(data.texts().containsKey(ChatToolsStore.Texts.DISCORD_USERNAMES), "Comet's empty {} roster is nothing to import");
        assertEquals("https://r.example.workers.dev", data.relay());
        assertEquals(ChatToolsDataImport.ChatData.EMPTY.count(), ChatToolsDataImport.extract(new SourceConfig(Format.METEOR, List.of())).count());
    }

    @Test void everythingComesOverIntoAnEmptyArgus() {
        var plan = ChatToolsDataImport.plan(ChatToolsDataImport.extract(comet()), empty());
        assertEquals(List.of("Alice", "Carol"), plan.lists().get(ChatToolsStore.Lists.CLAN_MEMBERS));
        assertEquals(List.of("Message | /msg {player} ", "Whisper | /w {player} ", "Duel | /duel {player}"),
            plan.lists().get(ChatToolsStore.Lists.COMMAND_OPTIONS), "Comet's extra options join Argus's defaults without duplicates");
        assertEquals("1", plan.texts().get(ChatToolsStore.Texts.OWNERSHIP_VERSION));
        assertEquals("https://r.example.workers.dev", plan.relay());
        assertTrue(plan.added() > 0);
        assertTrue(plan.issues().isEmpty(), plan.issues().toString());
    }

    @Test void importsOnlyAddAndNeverReplaceWhatArgusHas() {
        Map<ChatToolsStore.Lists, List<String>> lists = new EnumMap<>(ChatToolsStore.Lists.class);
        lists.put(ChatToolsStore.Lists.CLAN_MEMBERS, List.of("alice", "Zed"));
        lists.put(ChatToolsStore.Lists.SAVED_COOLDOWNS, List.of("A|Home|1000|manual"));
        lists.put(ChatToolsStore.Lists.RECENT_PLAYERS, List.of("A|Zed"));
        lists.put(ChatToolsStore.Lists.CLAN_SYNCED, List.of("Zed"));
        var plan = ChatToolsDataImport.plan(ChatToolsDataImport.extract(comet()),
            current(lists, Map.of(ChatToolsStore.Texts.DISCORD_ROLES, "{}"), "https://mine.example.workers.dev"));
        assertEquals(List.of("alice", "Zed", "Carol"), plan.lists().get(ChatToolsStore.Lists.CLAN_MEMBERS), "Names compare without case");
        assertEquals(List.of("A|Home|1000|manual", "B|TPA|30000"), plan.lists().get(ChatToolsStore.Lists.SAVED_COOLDOWNS),
            "A server Argus already has cooldowns for keeps them; other servers come over");
        assertEquals(List.of("A|Zed", "B|Bob"), plan.lists().get(ChatToolsStore.Lists.RECENT_PLAYERS));
        assertFalse(plan.lists().containsKey(ChatToolsStore.Lists.CLAN_SYNCED), "Argus's own sync tracking is kept");
        assertFalse(plan.lists().containsKey(ChatToolsStore.Lists.AUTO_TPY_SYNCED));
        assertFalse(plan.texts().containsKey(ChatToolsStore.Texts.OWNERSHIP_VERSION));
        assertFalse(plan.texts().containsKey(ChatToolsStore.Texts.DISCORD_ROLES));
        assertFalse(plan.texts().containsKey(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE), "Roles and their source only come over together");
        assertEquals("", plan.relay(), "A different relay in Argus is kept");
        assertTrue(plan.issues().stream().anyMatch(i -> i.field().equals("relay-url")));
        assertTrue(plan.issues().stream().anyMatch(i -> i.field().equals("discord-role-roster") && i.severity() == Severity.WARNING));
    }

    @Test void importingTheSameDataTwiceAddsNothing() {
        var data = ChatToolsDataImport.extract(comet());
        var first = ChatToolsDataImport.plan(data, empty());
        Map<ChatToolsStore.Lists, List<String>> lists = new EnumMap<>(first.lists());
        Map<ChatToolsStore.Texts, String> texts = new EnumMap<>(ChatToolsStore.Texts.class);
        texts.putAll(first.texts());
        var second = ChatToolsDataImport.plan(data, current(lists, texts, first.relay()));
        assertTrue(second.isEmpty(), "Second import: " + second);
    }

    @Test void importCannotClaimOwnershipOfExistingManualNames() {
        var data = ChatToolsDataImport.extract(comet());
        var plan = ChatToolsDataImport.plan(data, current(Map.of(
            ChatToolsStore.Lists.CLAN_MEMBERS, List.of("carol"),
            ChatToolsStore.Lists.AUTO_TPY_WHITELIST, List.of("DAVE")), Map.of(), ""));
        assertFalse(plan.lists().containsKey(ChatToolsStore.Lists.CLAN_SYNCED));
        assertFalse(plan.lists().containsKey(ChatToolsStore.Lists.AUTO_TPY_SYNCED));
        assertFalse(plan.texts().containsKey(ChatToolsStore.Texts.OWNERSHIP_VERSION));
    }

    @Test void unreadableDiscordDataIsLeftBehind() {
        Map<String, JsonElement> chat = Map.of("Clan/discord-username-roster", new JsonPrimitive("{broken"));
        var plan = ChatToolsDataImport.plan(ChatToolsDataImport.extract(new SourceConfig(Format.METEOR, List.of(module("chat-tools", chat)))), empty());
        assertTrue(plan.isEmpty());
        assertTrue(plan.issues().stream().anyMatch(i -> i.severity() == Severity.WARNING));
    }

    // ---- settings, through the module preset ----

    @Test void cometSettingsMapOntoChatTools() {
        var catalog = new ArgusModuleCatalog(Map.of("ChatTools", new ArgusModuleCatalog.Target("ChatTools", Map.of(
            "Queue chat", ArgusModuleCatalog.describe(new BooleanSetting("Queue chat", true)),
            "Chat cooldown", ArgusModuleCatalog.describe(new NumberSetting<>("Chat cooldown", 5000, 0, 30000)),
            "Accept automatically", ArgusModuleCatalog.describe(new BooleanSetting("Accept automatically", false))), new JsonObject())));
        Map<String, JsonElement> settings = Map.of("Messages/queue-chat", new JsonPrimitive(0), "Messages/chat-cooldown-ms", new JsonPrimitive(2500),
            "Auto TPY/auto-tpy", new JsonPrimitive(1), "General/homes", strings("base"));
        var result = ConfigImportConverter.convert(new SourceConfig(Format.METEOR, List.of(module("chat-tools", settings))), catalog, false);
        var out = result.preset().getAsJsonObject("ChatTools").getAsJsonObject("settings");
        assertFalse(out.get("Queue chat").getAsBoolean(), "Meteor's NBT byte 0 becomes false");
        assertTrue(out.get("Accept automatically").getAsBoolean());
        assertEquals(2500, out.get("Chat cooldown").getAsInt());
        assertTrue(result.preset().getAsJsonObject("ChatTools").get("enabled").getAsBoolean());
    }

    @Test void meteorColoursBecomeRgba() {
        JsonObject colour = new JsonObject();
        colour.addProperty("r", 255); colour.addProperty("g", 170); colour.addProperty("b", 60); colour.addProperty("a", 96); colour.addProperty("rainbow", false);
        assertEquals(strings().getClass(), MeteorMappings.colour(colour).getClass());
        assertEquals("[255,170,60,96]", MeteorMappings.colour(colour).toString());
        assertThrows(IllegalArgumentException.class, () -> MeteorMappings.bool(new JsonPrimitive(2)));
    }

    /** Every target setting name must be a real ChatTools setting; descriptions.tsv lists each one. */
    @Test void everyMappedSettingExistsInChatTools() throws IOException {
        Set<String> described = new HashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/assets/argusclient/descriptions.tsv")) {
            assertNotNull(in);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3 && parts[0].equals("ChatTools") && !parts[1].isEmpty()) described.add(parts[1]);
            }
        }
        var mapping = MeteorMappings.modules().stream().filter(m -> m.source().equals("chat-tools")).findFirst().orElseThrow();
        assertEquals("ChatTools", mapping.target());
        for (var field : mapping.settings()) assertTrue(described.contains(field.target()), "Unknown ChatTools setting: " + field.target());
    }
}

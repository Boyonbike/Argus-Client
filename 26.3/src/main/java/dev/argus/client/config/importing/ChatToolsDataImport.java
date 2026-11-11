package dev.argus.client.config.importing;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.argus.client.chat.ChatToolsStore;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.config.importing.ConfigImportModel.Issue;
import dev.argus.client.config.importing.ConfigImportModel.Severity;
import dev.argus.client.config.importing.ConfigImportModel.SourceConfig;
import dev.argus.client.config.importing.ConfigImportModel.SourceModule;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Brings Comet's Chat Tools and Discord IGN Sync data (homes, contacts, clan, Auto TPY lists, Discord roster, DM
 * patterns, command options) into Argus's chat data. Comet kept these as module settings in modules.nbt; Argus keeps
 * them in {@link ChatToolsStore}, under the same names. Like the other Meteor imports this only adds: nothing already
 * in Argus is changed or removed, and every entry left behind gets a reason.
 */
public final class ChatToolsDataImport {
    private ChatToolsDataImport() {}

    static final String CHAT_TOOLS = "chat-tools", DISCORD_SYNC = "discord-ign-sync";
    static final int MAX_ENTRIES = 16_384, MAX_TEXT = 1 << 20;

    /** Everything found in a Comet config. Empty lists and texts are left out. */
    public record ChatData(Map<ChatToolsStore.Lists, List<String>> lists, Map<ChatToolsStore.Texts, String> texts, String relay) {
        public ChatData {
            Map<ChatToolsStore.Lists, List<String>> l = new EnumMap<>(ChatToolsStore.Lists.class);
            lists.forEach((k, v) -> l.put(k, List.copyOf(v)));
            lists = java.util.Collections.unmodifiableMap(l);
            texts = java.util.Collections.unmodifiableMap(new EnumMap<>(texts.isEmpty() ? new EnumMap<>(ChatToolsStore.Texts.class) : texts));
            relay = relay == null ? "" : relay;
        }
        public static final ChatData EMPTY = new ChatData(Map.of(), Map.of(), "");
        /** Entries found, for the import list: list entries plus one per text value and the relay. */
        public int count() { return lists.values().stream().mapToInt(List::size).sum() + texts.size() + (relay.isEmpty() ? 0 : 1); }
    }

    /** What Argus currently holds, read through small functions so planning needs no live client. */
    public record Current(Function<ChatToolsStore.Lists, List<String>> lists, Predicate<ChatToolsStore.Lists> has,
                          Function<ChatToolsStore.Texts, String> texts, String relay, List<String> defaultCommandOptions) {}

    /** The lists and texts to write (only those that change), the relay to set (or empty), and why anything was left. */
    public record Plan(Map<ChatToolsStore.Lists, List<String>> lists, Map<ChatToolsStore.Texts, String> texts, String relay,
                       int added, List<Issue> issues) {
        public Plan {
            Map<ChatToolsStore.Lists, List<String>> l = new EnumMap<>(ChatToolsStore.Lists.class);
            lists.forEach((k, v) -> l.put(k, List.copyOf(v)));
            lists = java.util.Collections.unmodifiableMap(l);
            texts = java.util.Collections.unmodifiableMap(new EnumMap<>(texts.isEmpty() ? new EnumMap<>(ChatToolsStore.Texts.class) : texts));
            relay = relay == null ? "" : relay;
            issues = List.copyOf(issues);
        }
        public static final Plan EMPTY = new Plan(Map.of(), Map.of(), "", 0, List.of());
        public boolean isEmpty() { return added == 0; }
    }

    // ---- reading --------------------------------------------------------------------------------------------------

    private static final Map<String, ChatToolsStore.Lists> CHAT_LISTS = Map.ofEntries(
        Map.entry("General/homes", ChatToolsStore.Lists.HOMES),
        Map.entry("General/favorite-homes", ChatToolsStore.Lists.FAVORITE_HOMES),
        Map.entry("General/command-options", ChatToolsStore.Lists.COMMAND_OPTIONS),
        Map.entry("Teleport Cooldowns/saved-cooldowns", ChatToolsStore.Lists.SAVED_COOLDOWNS),
        Map.entry("Messages/incoming-dm-patterns", ChatToolsStore.Lists.INCOMING_PATTERNS),
        Map.entry("Messages/outgoing-dm-patterns", ChatToolsStore.Lists.OUTGOING_PATTERNS),
        Map.entry("Messages/recent-players", ChatToolsStore.Lists.RECENT_PLAYERS),
        Map.entry("Clan/clan-members", ChatToolsStore.Lists.CLAN_MEMBERS),
        Map.entry("Auto TPY/auto-tpy-whitelist", ChatToolsStore.Lists.AUTO_TPY_WHITELIST),
        Map.entry("Auto TPY/auto-tpy-blocklist", ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST),
        Map.entry("Auto TPY/auto-tpy-discord-role-ids", ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS));
    private static final Map<String, ChatToolsStore.Texts> CHAT_TEXTS = Map.of(
        "Clan/discord-role-roster", ChatToolsStore.Texts.DISCORD_ROLES,
        "Clan/discord-role-source", ChatToolsStore.Texts.DISCORD_ROLE_SOURCE,
        "Clan/discord-username-roster", ChatToolsStore.Texts.DISCORD_USERNAMES);
    private static final Map<String, ChatToolsStore.Lists> SYNC_LISTS = Map.of(
        "Applying/clan-synced", ChatToolsStore.Lists.CLAN_SYNCED,
        "Applying/auto-tpy-synced", ChatToolsStore.Lists.AUTO_TPY_SYNCED);

    /** Pulls the data out of a Meteor module config; a module or field that's missing or malformed is just skipped. */
    public static ChatData extract(SourceConfig source) {
        Map<ChatToolsStore.Lists, List<String>> lists = new EnumMap<>(ChatToolsStore.Lists.class);
        Map<ChatToolsStore.Texts, String> texts = new EnumMap<>(ChatToolsStore.Texts.class);
        String relay = "";
        for (SourceModule module : source.modules()) {
            Map<String, JsonElement> settings = module.settings();
            if (module.id().equals(CHAT_TOOLS)) {
                CHAT_LISTS.forEach((key, list) -> strings(settings.get(key)).ifPresent(v -> { if (!v.isEmpty()) lists.put(list, v); }));
                CHAT_TEXTS.forEach((key, text) -> string(settings.get(key)).ifPresent(v -> { if (!v.isBlank() && !v.equals("{}")) texts.put(text, v); }));
            } else if (module.id().equals(DISCORD_SYNC)) {
                SYNC_LISTS.forEach((key, list) -> strings(settings.get(key)).ifPresent(v -> { if (!v.isEmpty()) lists.put(list, v); }));
                JsonElement version = settings.get("Applying/ownership-version");
                if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber() && version.getAsInt() >= 1)
                    texts.put(ChatToolsStore.Texts.OWNERSHIP_VERSION, "1");
                relay = string(settings.get("General/relay-url")).map(String::strip).orElse("");
            }
        }
        return new ChatData(lists, texts, relay);
    }

    private static java.util.Optional<List<String>> strings(JsonElement value) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > MAX_ENTRIES) return java.util.Optional.empty();
        List<String> out = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return java.util.Optional.empty();
            String text = item.getAsString();
            if (!text.isBlank() && text.length() <= 4096) out.add(text);
        }
        return java.util.Optional.of(out);
    }
    private static java.util.Optional<String> string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > MAX_TEXT) return java.util.Optional.empty();
        return java.util.Optional.of(value.getAsString());
    }

    // ---- planning -------------------------------------------------------------------------------------------------

    /** Lists keyed by server ("server|…"): a server Argus already has entries for keeps its own. */
    private static final Set<ChatToolsStore.Lists> PER_SERVER = Set.of(ChatToolsStore.Lists.SAVED_COOLDOWNS, ChatToolsStore.Lists.RECENT_PLAYERS);
    /** Player-name lists, compared without case. */
    private static final Set<ChatToolsStore.Lists> NAMES = Set.of(ChatToolsStore.Lists.CLAN_MEMBERS, ChatToolsStore.Lists.AUTO_TPY_WHITELIST,
        ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST);
    /** Discord sync ownership only makes sense as a whole with its tracking version. */
    private static final Map<ChatToolsStore.Lists, ChatToolsStore.Lists> OWNERSHIP = Map.of(
        ChatToolsStore.Lists.CLAN_SYNCED, ChatToolsStore.Lists.CLAN_MEMBERS,
        ChatToolsStore.Lists.AUTO_TPY_SYNCED, ChatToolsStore.Lists.AUTO_TPY_WHITELIST);

    public static Plan plan(ChatData data, Current current) {
        List<Issue> issues = new ArrayList<>();
        Map<ChatToolsStore.Lists, List<String>> lists = new EnumMap<>(ChatToolsStore.Lists.class);
        Map<ChatToolsStore.Texts, String> texts = new EnumMap<>(ChatToolsStore.Texts.class);
        int added = 0;
        boolean ownershipEmpty = OWNERSHIP.keySet().stream().allMatch(l -> current.lists().apply(l).isEmpty())
            && current.texts().apply(ChatToolsStore.Texts.OWNERSHIP_VERSION).isEmpty();
        boolean sameRelay = !data.relay().isEmpty() && (current.relay().isEmpty() || current.relay().equals(data.relay()));
        boolean trustedOwnership = "1".equals(data.texts().get(ChatToolsStore.Texts.OWNERSHIP_VERSION)) && sameRelay;
        for (var entry : data.lists().entrySet()) {
            ChatToolsStore.Lists key = entry.getKey();
            List<String> before = key == ChatToolsStore.Lists.COMMAND_OPTIONS && !current.has().test(key)
                ? current.defaultCommandOptions() : current.lists().apply(key);
            List<String> incoming = entry.getValue();
            if (OWNERSHIP.containsKey(key)) {
                if (!ownershipEmpty) { issues.add(info(key.key(), "Argus already tracks Discord sync names; kept Argus's tracking")); continue; }
                if (!trustedOwnership) { issues.add(info(key.key(), "Kept these names without Discord ownership")); continue; }
                ChatToolsStore.Lists members = OWNERSHIP.get(key);
                Set<String> introduced = new HashSet<>();
                for (String name : data.lists().getOrDefault(members, List.of())) introduced.add(identity(members, name));
                for (String name : current.lists().apply(members)) introduced.remove(identity(members, name));
                List<String> imported = incoming.stream().filter(name -> introduced.remove(identity(members, name))).toList();
                if (!imported.isEmpty()) {
                    lists.put(key, imported);
                    added += imported.size();
                }
                continue;
            }
            List<String> after = new ArrayList<>(before);
            Set<String> seen = new HashSet<>();
            for (String item : before) seen.add(identity(key, item));
            Set<String> serversInArgus = new HashSet<>();
            if (PER_SERVER.contains(key)) for (String item : before) serversInArgus.add(server(item));
            int skipped = 0;
            for (String item : incoming) {
                if (PER_SERVER.contains(key) && serversInArgus.contains(server(item))) { skipped++; continue; }
                if (seen.add(identity(key, item))) after.add(item);
            }
            int count = after.size() - before.size();
            if (skipped > 0) issues.add(info(key.key(), skipped + " entr" + (skipped == 1 ? "y" : "ies") + " for servers Argus already has data for were kept as Argus has them"));
            if (count > 0) { lists.put(key, after); added += count; }
        }
        for (var entry : data.texts().entrySet()) {
            ChatToolsStore.Texts key = entry.getKey();
            if (key == ChatToolsStore.Texts.OWNERSHIP_VERSION) {
                if (OWNERSHIP.keySet().stream().anyMatch(lists::containsKey)) texts.put(key, entry.getValue());
                continue;
            }
            if (!current.texts().apply(key).isEmpty()) { issues.add(info(key.key(), "Argus already has this from its own Discord sync; kept")); continue; }
            if (key != ChatToolsStore.Texts.DISCORD_ROLE_SOURCE && !json(entry.getValue())) { issues.add(warn(key.key(), "Not readable Discord data; skipped")); continue; }
            texts.put(key, entry.getValue());
            added++;
        }
        // Role access is tied to the relay that granted it: bring the roster and its source only together.
        boolean roster = texts.containsKey(ChatToolsStore.Texts.DISCORD_ROLES), source = texts.containsKey(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE);
        if (roster != source) {
            if (roster) added--; if (source) added--;
            texts.remove(ChatToolsStore.Texts.DISCORD_ROLES); texts.remove(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE);
            issues.add(warn("discord-role-roster", "Discord roles and their source must come over together; sync again in Argus"));
        }
        String relay = "";
        if (!data.relay().isEmpty()) {
            if (current.relay().isEmpty()) { relay = data.relay(); added++; }
            else if (!current.relay().equals(data.relay())) issues.add(info("relay-url", "Argus already has a different Discord relay; kept Argus's"));
        }
        return new Plan(lists, texts, relay, added, issues);
    }

    private static String identity(ChatToolsStore.Lists list, String item) { return NAMES.contains(list) ? item.toLowerCase(Locale.ROOT) : item; }
    private static String server(String entry) { int bar = entry.indexOf('|'); return bar < 0 ? entry : entry.substring(0, bar); }
    private static Issue info(String field, String message) { return new Issue("Chat Tools", field, Severity.INFO, message); }
    private static Issue warn(String field, String message) { return new Issue("Chat Tools", field, Severity.WARNING, message); }

    /** The Discord texts are JSON written by Comet; anything else is left behind rather than trusted. */
    static boolean json(String text) {
        try { JsonParser.parseString(text); return true; } catch (RuntimeException e) { return false; }
    }

    // ---- applying (client thread) ---------------------------------------------------------------------------------

    /** What Argus holds now; empty when Chat Tools isn't registered. */
    public static Current current() {
        var tools = ChatToolsModule.get();
        var sync = DiscordSyncModule.get();
        String relay = sync == null ? "" : sync.relaySetting();
        if (tools == null) return new Current(list -> List.of(), list -> false, text -> "", relay, List.of());
        ChatToolsStore store = tools.store();
        return new Current(store::get, store::has, store::get, relay, ChatToolsModule.DEFAULT_COMMAND_OPTIONS);
    }

    /** Backs up the chat data, then writes the plan. Nothing is written if the backup fails. */
    public static ArgusDataStore.Result apply(Plan plan) {
        if (plan.isEmpty()) return new ArgusDataStore.Result(true, "Nothing new to add.", null);
        var tools = ChatToolsModule.get();
        if (tools == null) return new ArgusDataStore.Result(false, "Chat Tools isn't available, so nothing was imported.", null);
        if (tools.storeProblem() != null) return new ArgusDataStore.Result(false, tools.storeProblem(), null);
        var sync = DiscordSyncModule.get();
        boolean importingRelay = !plan.relay().isEmpty();
        if (importingRelay && (sync == null || ClientContext.configurationManager == null))
            return new ArgusDataStore.Result(false, "Discord Sync isn't available, so nothing was imported.", null);
        String previousRelay = importingRelay ? sync.relaySetting() : "";
        java.nio.file.Path backup;
        try {
            backup = ArgusDataStore.writeBackup(ConfigurationManager.ROOT_DIRECTORY.resolve(ArgusDataStore.BACKUP_FOLDER),
                Map.of("chat", tools.store().toJson()));
        } catch (Exception e) { return new ArgusDataStore.Result(false, "Couldn't back up chat data. Nothing was imported. " + e.getMessage(), null); }
        boolean savedRelay = false;
        try {
            if (importingRelay) {
                sync.setRelay(plan.relay());
                ClientContext.configurationManager.saveChecked(ClientContext.moduleRegistry);
                savedRelay = true;
            }
            tools.store().importData(plan.lists(), plan.texts());
        } catch (Exception failure) {
            String message = "Couldn't save imported chat data. " + failure.getMessage();
            if (importingRelay) {
                sync.setRelay(previousRelay);
                if (savedRelay) try { ClientContext.configurationManager.saveChecked(ClientContext.moduleRegistry); }
                catch (Exception rollback) { message += " Couldn't restore the saved Discord relay. " + rollback.getMessage(); }
            }
            return new ArgusDataStore.Result(false, message, backup);
        }
        tools.dataImported();
        return new ArgusDataStore.Result(true, "Imported " + summary(plan) + ".", backup);
    }

    public static String summary(Plan plan) { return plan.added() + " Chat Tools entr" + (plan.added() == 1 ? "y" : "ies"); }
}

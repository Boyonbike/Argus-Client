package dev.argus.client.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Data the chat window collects and edits, kept apart from module settings so that loading a settings preset never
 * swaps out homes, contacts or the clan. Per-server lists keep the "server|value" entries the core helpers read and
 * write. Keys match the old setting names, so imported configs map across one to one.
 *
 * <p>A file that can't be read is reported and left alone, never overwritten. Keys this version doesn't know are kept
 * when saving. Changes stay in memory until {@link #save()}, so the module can save in batches.
 */
public final class ChatToolsStore {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Lists of text, mostly per-server entries. */
    public enum Lists {
        HOMES("homes"), FAVORITE_HOMES("favorite-homes"), SAVED_COOLDOWNS("saved-cooldowns"), RECENT_PLAYERS("recent-players"),
        CLAN_MEMBERS("clan-members"), AUTO_TPY_WHITELIST("auto-tpy-whitelist"), AUTO_TPY_BLOCKLIST("auto-tpy-blocklist"),
        AUTO_TPY_ROLE_IDS("auto-tpy-discord-role-ids"), CLAN_SYNCED("clan-synced"), AUTO_TPY_SYNCED("auto-tpy-synced"),
        COMMAND_OPTIONS("command-options"), INCOMING_PATTERNS("incoming-dm-patterns"), OUTGOING_PATTERNS("outgoing-dm-patterns");
        private final String key;
        Lists(String key) { this.key = key; }
        public String key() { return key; }
    }

    /** Single text values, such as the encoded Discord role roster. */
    public enum Texts {
        DISCORD_ROLES("discord-role-roster"), DISCORD_ROLE_SOURCE("discord-role-source"), DISCORD_USERNAMES("discord-username-roster"),
        /** "1" once the synced-name lists only hold names the sync itself added. */
        OWNERSHIP_VERSION("ownership-version");
        private final String key;
        Texts(String key) { this.key = key; }
        public String key() { return key; }
    }

    private final Path path;
    private final Map<Lists, List<String>> lists = new EnumMap<>(Lists.class);
    private final Map<Texts, String> texts = new EnumMap<>(Texts.class);
    /** Everything read from the file, so unknown keys are written back unchanged. */
    private final JsonObject unknown = new JsonObject();
    private boolean dirty;

    /** A store that is never written, used when the file couldn't be read so the broken file stays untouched. */
    public static ChatToolsStore inMemory() {
        try { return new ChatToolsStore(null); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Reads {@code path} if it exists; a missing file is an empty store. A null path never touches the disk. */
    public ChatToolsStore(Path path) throws IOException {
        this.path = path;
        if (path == null || !Files.exists(path)) return;
        JsonElement parsed;
        try { parsed = JsonParser.parseString(Files.readString(path)); }
        catch (JsonParseException failure) { throw new IOException("Chat data is not valid JSON: " + path, failure); }
        if (parsed == null || !parsed.isJsonObject()) throw new IOException("Chat data must be a JSON object: " + path);
        JsonObject root = parsed.getAsJsonObject();
        for (var entry : root.entrySet()) unknown.add(entry.getKey(), entry.getValue().deepCopy());
        for (Lists key : Lists.values()) {
            JsonElement value = unknown.remove(key.key);
            if (value != null && value.isJsonArray()) {
                List<String> items = new ArrayList<>();
                for (JsonElement item : value.getAsJsonArray()) if (text(item)) items.add(item.getAsString());
                lists.put(key, List.copyOf(items));
            }
        }
        for (Texts key : Texts.values()) {
            JsonElement value = unknown.remove(key.key);
            if (value != null && text(value) && !value.getAsString().isEmpty()) texts.put(key, value.getAsString());
        }
        unknown.remove("version");
    }

    private static boolean text(JsonElement element) { return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString(); }

    public synchronized List<String> get(Lists key) { return lists.getOrDefault(key, List.of()); }
    public synchronized String get(Texts key) { return texts.getOrDefault(key, ""); }

    /** Whether the list was ever set, so a list emptied on purpose is told apart from one that falls back to defaults. */
    public synchronized boolean has(Lists key) { return lists.containsKey(key); }

    /** Stores the list, even when empty. @return whether the stored value changed */
    public synchronized boolean set(Lists key, List<String> value) {
        List<String> copy = value == null ? List.of() : List.copyOf(value);
        if (lists.containsKey(key) && copy.equals(lists.get(key))) return false;
        lists.put(key, copy);
        dirty = true;
        return true;
    }

    /** @return whether the value changed */
    public synchronized boolean set(Texts key, String value) {
        String text = value == null ? "" : value;
        if (text.equals(get(key))) return false;
        if (text.isEmpty()) texts.remove(key); else texts.put(key, text);
        dirty = true;
        return true;
    }

    public synchronized boolean dirty() { return dirty; }

    /** Applies an import only when the changed values can be saved. */
    public synchronized void importData(Map<Lists, List<String>> importedLists, Map<Texts, String> importedTexts) throws IOException {
        if (path == null) throw new IOException("Chat storage is unavailable.");
        var previousLists = new EnumMap<>(lists);
        var previousTexts = new EnumMap<>(texts);
        boolean wasDirty = dirty;
        try {
            importedLists.forEach(this::set);
            importedTexts.forEach(this::set);
            save();
        } catch (IOException | RuntimeException failure) {
            lists.clear();
            lists.putAll(previousLists);
            texts.clear();
            texts.putAll(previousTexts);
            dirty = wasDirty;
            throw failure;
        }
    }

    /** The store as it would be saved, for backups. */
    public synchronized JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        for (var entry : lists.entrySet()) {
            JsonArray array = new JsonArray();
            entry.getValue().forEach(array::add);
            root.add(entry.getKey().key, array);
        }
        for (var entry : texts.entrySet()) root.add(entry.getKey().key, new JsonPrimitive(entry.getValue()));
        for (var entry : unknown.entrySet()) root.add(entry.getKey(), entry.getValue().deepCopy());
        return root;
    }

    /** Writes the store if anything changed, replacing the file in one step. */
    public synchronized void save() throws IOException {
        if (!dirty || path == null) return;
        JsonObject root = toJson();

        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".chat-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(root));
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

package dev.argus.client.config.importing;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;

/** Reads the Meteor data files that have an Argus equivalent. Read-only; never touches the source. */
public final class MeteorDataReader {
    private MeteorDataReader() {}
    public enum DataKind { FRIENDS, ENEMIES, WAYPOINTS, MACROS }
    /** {@code dimension} is Argus's name (overworld/nether/end), or null when Meteor's has no clear match. */
    public record SourceWaypoint(String name, int x, int y, int z, String dimension, String server) {}
    public record SourceMacro(String name, List<String> messages, Optional<SourceBind> bind) {
        public SourceMacro { messages = List.copyOf(messages); bind = Objects.requireNonNull(bind); }
    }
    public record DataSource(List<String> friends, List<String> enemies, List<SourceWaypoint> waypoints, List<SourceMacro> macros) {
        public DataSource { friends = List.copyOf(friends); enemies = List.copyOf(enemies); waypoints = List.copyOf(waypoints); macros = List.copyOf(macros); }
        public static final DataSource EMPTY = new DataSource(List.of(), List.of(), List.of(), List.of());
    }
    static final int MAX_ENTRIES = 4096, MAX_WAYPOINT_FILES = 256;

    /** friends.nbt ({@code friends}) or enemies.nbt ({@code enemies}): a list of {name, id}. */
    public static List<String> readPlayers(Path file, String key) throws IOException {
        ListTag list = list(MeteorConfigReader.readNbt(file), key);
        List<String> names = new ArrayList<>();
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag player)) throw new IOException("Invalid " + key + " entry");
            names.add(player.getString("name").orElseThrow(() -> new IOException("Player name missing")));
        }
        return List.copyOf(names);
    }

    /** waypoints/&lt;world&gt;.nbt — Meteor names each file after the server address (or singleplayer world). */
    public static List<SourceWaypoint> readWaypoints(Path directory) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt")).sorted().limit(MAX_WAYPOINT_FILES + 1).toList();
        }
        if (files.size() > MAX_WAYPOINT_FILES) throw new IOException("Too many waypoint files");
        List<SourceWaypoint> result = new ArrayList<>();
        for (Path file : files) {
            String server = server(file.getFileName().toString().replaceFirst("\\.nbt$", ""));
            for (Tag tag : list(MeteorConfigReader.readNbt(file), "waypoints")) {
                if (!(tag instanceof CompoundTag entry)) throw new IOException("Invalid waypoint entry");
                Map<String, Tag> s = flatten(entry);
                String name = string(s.get("Visual/name"), "Home");
                int[] pos = position(s.get("Position/location"));
                result.add(new SourceWaypoint(name, pos[0], pos[1], pos[2], dimension(string(s.get("Position/dimension"), "Overworld")), server));
                if (result.size() > MAX_ENTRIES) throw new IOException("Too many waypoints");
            }
        }
        return List.copyOf(result);
    }

    /** macros.nbt: each macro has General/name, General/messages and General/keybind. */
    public static List<SourceMacro> readMacros(Path file) throws IOException {
        List<SourceMacro> result = new ArrayList<>();
        for (Tag tag : list(MeteorConfigReader.readNbt(file), "macros")) {
            if (!(tag instanceof CompoundTag entry)) throw new IOException("Invalid macro entry");
            Map<String, Tag> s = flatten(entry);
            List<String> messages = new ArrayList<>();
            if (s.get("General/messages") instanceof ListTag list) for (Tag m : list) {
                if (!(m instanceof StringTag text)) throw new IOException("Invalid macro message");
                messages.add(text.asString().orElseThrow());
            } else if (s.containsKey("General/messages")) throw new IOException("Invalid macro messages");
            Optional<SourceBind> bind = Optional.empty();
            if (s.get("General/keybind") instanceof CompoundTag key) {
                int value = key.getIntOr("value", -1);
                if (value >= 0) bind = Optional.of(new SourceBind(!key.getBooleanOr("isKey", true), value, key.getIntOr("modifiers", 0), "toggle"));
            }
            result.add(new SourceMacro(string(s.get("General/name"), ""), messages, bind));
            if (result.size() > MAX_ENTRIES) throw new IOException("Too many macros");
        }
        return List.copyOf(result);
    }

    private static ListTag list(CompoundTag root, String key) throws IOException {
        if (!(root.get(key) instanceof ListTag list)) throw new IOException("Expected Meteor " + key + " file");
        if (list.size() > MAX_ENTRIES) throw new IOException("Too many " + key);
        return list;
    }
    /** Meteor Settings: settings.groups[].{name, settings[].{name, value}} → "group/name" → value. */
    private static Map<String, Tag> flatten(CompoundTag entry) throws IOException {
        Map<String, Tag> out = new LinkedHashMap<>();
        for (Tag g : entry.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(g instanceof CompoundTag group)) throw new IOException("Invalid setting group");
            String groupName = group.getString("name").orElseThrow(() -> new IOException("Group name missing"));
            for (Tag f : group.getListOrEmpty("settings")) {
                if (!(f instanceof CompoundTag field)) throw new IOException("Invalid setting entry");
                String key = groupName + "/" + field.getString("name").orElseThrow(() -> new IOException("Setting name missing"));
                if (field.get("value") == null || out.putIfAbsent(key, field.get("value")) != null) throw new IOException("Missing or duplicate setting: " + key);
            }
        }
        return out;
    }
    private static String string(Tag tag, String fallback) throws IOException {
        if (tag == null) return fallback;
        if (!(tag instanceof StringTag text)) throw new IOException("Expected text setting");
        return text.asString().orElseThrow();
    }
    private static int[] position(Tag tag) throws IOException {
        if (tag == null) return new int[3];
        if (tag instanceof IntArrayTag ints && ints.getAsIntArray().length == 3) return ints.getAsIntArray();
        if (tag instanceof CompoundTag c) return new int[]{c.getIntOr("x", 0), c.getIntOr("y", 0), c.getIntOr("z", 0)};
        throw new IOException("Invalid waypoint position");
    }
    static String dimension(String meteor) {
        return switch (meteor.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "")) {
            case "overworld" -> "overworld";
            case "nether", "the_nether" -> "nether";
            case "end", "the_end" -> "end";
            default -> null;
        };
    }
    /** Argus keys servers by lower-case host; Meteor singleplayer files are named after the world. */
    static String server(String fileName) {
        String host = fileName.toLowerCase(Locale.ROOT).replaceFirst("_\\d{1,5}$", "");
        return host.contains(".") ? host.replaceFirst("\\.$", "") : "singleplayer";
    }
}

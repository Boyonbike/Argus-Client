package dev.argus.client.config.importing;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static dev.argus.client.config.importing.MeteorDataReader.DataKind;

/** Finds Meteor folders in sibling launcher instances and lists what each one holds. Read-only. */
public final class MeteorInstanceScanner {
    private MeteorInstanceScanner() {}
    public record Instance(String name, Path meteorFolder) {}
    public record ModuleSource(String label, Path path) {}
    public record Detection(Path folder, List<ModuleSource> moduleSources, Map<DataKind, Path> data, List<String> skipped) {
        public Detection { moduleSources = List.copyOf(moduleSources); data = Collections.unmodifiableMap(new EnumMap<>(data.isEmpty() ? new EnumMap<>(DataKind.class) : data)); skipped = List.copyOf(skipped); }
        public boolean isEmpty() { return moduleSources.isEmpty() && data.isEmpty(); }
    }
    private static final int MAX_LISTED = 512;
    private static final Set<String> MARKERS = Set.of("modules.nbt", "friends.nbt", "enemies.nbt", "macros.nbt", "waypoints", "profiles");
    private static final Set<String> HANDLED = Set.of("modules.nbt", "friends.nbt", "enemies.nbt", "macros.nbt", "waypoints", "profiles", "macros");
    /** Meteor files with no Argus equivalent, named so the screen can say what stays behind. */
    private static final Map<String, String> NAMED_SKIPS = new LinkedHashMap<>();
    static {
        NAMED_SKIPS.put("hud.nbt", "HUD layout"); NAMED_SKIPS.put("config.nbt", "Meteor settings"); NAMED_SKIPS.put("gui", "GUI and themes");
        NAMED_SKIPS.put("accounts.nbt", "Accounts"); NAMED_SKIPS.put("proxies.nbt", "Proxies");
    }

    /**
     * Prism/MultiMC layout: {@code instances/<name>/minecraft} (or {@code .minecraft}). Lists every sibling
     * instance that has a Meteor folder; returns nothing when the game directory isn't in that layout.
     */
    public static List<Instance> siblings(Path gameDirectory) throws IOException {
        Path instance = gameDirectory.toAbsolutePath().normalize().getParent();
        Path root = instance == null ? null : instance.getParent();
        if (root == null || !Files.isDirectory(root)) return List.of();
        List<Instance> found = new ArrayList<>();
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).limit(MAX_LISTED).toList()) {
                for (String game : List.of("minecraft", ".minecraft")) {
                    Path meteor = dir.resolve(game).resolve("meteor-client");
                    if (looksLikeMeteor(meteor)) { found.add(new Instance(dir.getFileName().toString(), meteor)); break; }
                }
            }
        }
        found.sort(Comparator.comparing((Instance i) -> i.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(found);
    }

    /** Accepts an instance folder, its minecraft folder, or the meteor-client folder itself. */
    public static Optional<Path> resolve(Path any) {
        Path base = any.toAbsolutePath().normalize();
        for (Path candidate : List.of(base, base.resolve("meteor-client"), base.resolve("minecraft").resolve("meteor-client"), base.resolve(".minecraft").resolve("meteor-client")))
            if (looksLikeMeteor(candidate)) return Optional.of(candidate);
        return Optional.empty();
    }

    public static Detection detect(Path meteor) throws IOException {
        List<ModuleSource> modules = new ArrayList<>();
        if (Files.isRegularFile(meteor.resolve("modules.nbt"))) modules.add(new ModuleSource("Main config", meteor.resolve("modules.nbt")));
        Path profiles = meteor.resolve("profiles");
        if (Files.isDirectory(profiles)) try (var dirs = Files.list(profiles)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().limit(MAX_LISTED).toList())
                if (Files.isRegularFile(dir.resolve("modules.nbt"))) modules.add(new ModuleSource("Profile · " + dir.getFileName(), dir.resolve("modules.nbt")));
        }
        Map<DataKind, Path> data = new EnumMap<>(DataKind.class);
        if (Files.isRegularFile(meteor.resolve("friends.nbt"))) data.put(DataKind.FRIENDS, meteor.resolve("friends.nbt"));
        if (Files.isRegularFile(meteor.resolve("enemies.nbt"))) data.put(DataKind.ENEMIES, meteor.resolve("enemies.nbt"));
        if (Files.isDirectory(meteor.resolve("waypoints"))) data.put(DataKind.WAYPOINTS, meteor.resolve("waypoints"));
        if (Files.isRegularFile(meteor.resolve("macros.nbt"))) data.put(DataKind.MACROS, meteor.resolve("macros.nbt"));
        List<String> skipped = new ArrayList<>(); int addon = 0;
        try (var entries = Files.list(meteor)) {
            for (Path entry : entries.limit(MAX_LISTED).toList()) {
                String name = entry.getFileName().toString();
                if (HANDLED.contains(name) || NAMED_SKIPS.containsKey(name) || name.endsWith(".bak") || name.contains(".bak") || name.startsWith(".")) continue;
                addon++;
            }
        }
        for (var e : NAMED_SKIPS.entrySet()) if (Files.exists(meteor.resolve(e.getKey()))) skipped.add(e.getValue());
        if (addon > 0) skipped.add("Addon data (" + addon + (addon == 1 ? " item)" : " items)"));
        return new Detection(meteor, modules, data, skipped);
    }

    private static boolean looksLikeMeteor(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        for (String marker : MARKERS) if (Files.exists(dir.resolve(marker))) return true;
        return false;
    }
}

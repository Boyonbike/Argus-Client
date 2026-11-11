package dev.argus.client.config.importing;

import com.google.gson.*;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.misc.Macro;
import dev.argus.client.modules.misc.SocialEntry;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.utils.world.Waypoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static dev.argus.client.config.importing.MeteorDataReader.*;

/**
 * Applies an {@link ArgusDataPlan} to the live friends, waypoints and macros. Client thread only.
 * Writes a backup of all three stores first (and stops if it can't); on failure, removes what it added.
 */
public final class ArgusDataStore {
    private ArgusDataStore() {}
    public record Result(boolean success, String message, Path backup) {}
    static final String BACKUP_FOLDER = "import-backups";

    public static ArgusDataPlan.Existing existing() {
        Map<String, String> socials = new HashMap<>();
        for (Object o : ClientContext.socialRegistry.getRegistry()) if (o instanceof SocialEntry s) socials.put(s.getName().toLowerCase(Locale.ROOT), s.role().name());
        Set<String> waypoints = new HashSet<>();
        for (Object o : ClientContext.waypointRegistry.getRegistry()) if (o instanceof Waypoint w) waypoints.add((w.getName() + "|" + w.getServer()).toLowerCase(Locale.ROOT));
        Set<String> macros = new HashSet<>();
        for (Object o : ClientContext.macroRegistry.getRegistry()) if (o instanceof Macro m) macros.add(m.getName().toLowerCase(Locale.ROOT));
        return new ArgusDataPlan.Existing(socials, waypoints, macros);
    }

    public static Result apply(ArgusDataPlan.Plan plan) {
        if (plan.total() == 0) return new Result(true, "Nothing new to add.", null);
        Path backup;
        try {
            Map<String, JsonElement> snapshot = new LinkedHashMap<>();
            snapshot.put("socials", ClientContext.socialRegistry.toJson()); snapshot.put("waypoints", ClientContext.waypointRegistry.toJson()); snapshot.put("macros", ClientContext.macroRegistry.toJson());
            backup = writeBackup(ConfigurationManager.ROOT_DIRECTORY.resolve(BACKUP_FOLDER), snapshot);
        } catch (Exception e) { return new Result(false, "Couldn’t back up friends, waypoints and macros. Nothing was imported. " + e.getMessage(), null); }
        ArgusDataPlan.Existing before = existing();
        List<JsonConfigurable> changed = new ArrayList<>();
        if (!plan.friends().isEmpty() || !plan.enemies().isEmpty()) changed.add(ClientContext.socialRegistry);
        if (!plan.waypoints().isEmpty()) changed.add(ClientContext.waypointRegistry);
        if (!plan.macros().isEmpty()) changed.add(ClientContext.macroRegistry);
        JsonConfigurable[] stores = changed.toArray(JsonConfigurable[]::new);
        boolean saving = false;
        try {
            for (String name : plan.friends()) ClientContext.socialRegistry.addFriend(name);
            for (String name : plan.enemies()) ClientContext.socialRegistry.addEnemy(name);
            for (SourceWaypoint w : plan.waypoints()) ClientContext.waypointRegistry.registerWaypoint(new Waypoint(w.name(), w.x(), w.y(), w.z(), w.dimension(), w.server()));
            if (!plan.macros().isEmpty()) ClientContext.macroRegistry.fromJson(plan.macroJson());
            verify(plan, existing());
            saving = true;
            ClientContext.configurationManager.saveChecked(stores);
            return new Result(true, "Imported " + summary(plan) + ".", backup);
        } catch (Exception e) {
            String message = "Couldn’t import: " + e.getMessage();
            try {
                removeAdded(before);
                if (!before.equals(existing())) throw new IllegalStateException("The previous lists could not be restored.");
                if (saving) ClientContext.configurationManager.saveChecked(stores);
                message += " Changes were undone.";
            }
            catch (Exception undo) { message += " Undo failed: " + undo.getMessage() + ". Your previous lists are in " + backup.getFileName() + "."; }
            return new Result(false, message, backup);
        }
    }

    public static String summary(ArgusDataPlan.Plan plan) {
        List<String> parts = new ArrayList<>();
        count(parts, plan.friends().size(), "friend"); count(parts, plan.enemies().size(), "enemy", "enemies");
        count(parts, plan.waypoints().size(), "waypoint"); count(parts, plan.macros().size(), "macro");
        return parts.isEmpty() ? "nothing new" : String.join(", ", parts);
    }
    private static void count(List<String> parts, int n, String one) { count(parts, n, one, one + "s"); }
    private static void count(List<String> parts, int n, String one, String many) { if (n > 0) parts.add(n + " " + (n == 1 ? one : many)); }

    /** Every planned entry must now exist; the base loaders swallow some failures, so check. */
    static void verify(ArgusDataPlan.Plan plan, ArgusDataPlan.Existing now) {
        for (String f : plan.friends()) if (!"FRIEND".equalsIgnoreCase(now.socials().get(f.toLowerCase(Locale.ROOT)))) throw new IllegalStateException("Friend " + f + " wasn’t added");
        for (String e : plan.enemies()) if (!"ENEMY".equalsIgnoreCase(now.socials().get(e.toLowerCase(Locale.ROOT)))) throw new IllegalStateException("Enemy " + e + " wasn’t added");
        for (SourceWaypoint w : plan.waypoints()) if (!now.waypointKeys().contains((w.name() + "|" + w.server()).toLowerCase(Locale.ROOT))) throw new IllegalStateException("Waypoint " + w.name() + " wasn’t added");
        for (SourceMacro m : plan.macros()) if (!now.macroNames().contains(m.name().toLowerCase(Locale.ROOT))) throw new IllegalStateException("Macro " + m.name() + " wasn’t added");
    }

    private static void removeAdded(ArgusDataPlan.Existing before) {
        for (Object o : List.copyOf(ClientContext.socialRegistry.getRegistry()))
            if (o instanceof SocialEntry s && !before.socials().containsKey(s.getName().toLowerCase(Locale.ROOT))) ClientContext.socialRegistry.unregister(s);
        for (Object o : List.copyOf(ClientContext.waypointRegistry.getRegistry()))
            if (o instanceof Waypoint w && !before.waypointKeys().contains((w.getName() + "|" + w.getServer()).toLowerCase(Locale.ROOT))) ClientContext.waypointRegistry.unregisterWaypoint(w);
        for (Object o : List.copyOf(ClientContext.macroRegistry.getRegistry()))
            if (o instanceof Macro m && !before.macroNames().contains(m.getName().toLowerCase(Locale.ROOT))) ClientContext.macroRegistry.unregisterEntry(m);
    }

    /** Writes each store's JSON into a new timestamped folder; never overwrites an existing backup. */
    static Path writeBackup(Path root, Map<String, JsonElement> stores) throws IOException {
        String base = "Meteor import " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss"));
        Files.createDirectories(root);
        Path dir = root.resolve(base); int suffix = 2;
        while (Files.exists(dir)) dir = root.resolve(base + " " + suffix++);
        Files.createDirectory(dir);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (var e : stores.entrySet()) Files.writeString(dir.resolve(e.getKey() + ".json"), gson.toJson(e.getValue()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return dir;
    }
}

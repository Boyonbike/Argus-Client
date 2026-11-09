package dev.argus.client.config.importing;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;
import static dev.argus.client.config.importing.ConfigImportModel.*;
import static dev.argus.client.config.importing.MeteorDataReader.*;

/**
 * Pure: decides what Meteor friends, enemies, waypoints and macros to add to Argus. Imports only add —
 * nothing already in Argus is changed or removed — and every entry left behind gets a reason.
 */
public final class ArgusDataPlan {
    private ArgusDataPlan() {}
    /** Lower-case keys: social name → role ("FRIEND"/"ENEMY"), "name|server" waypoints, macro names. */
    public record Existing(Map<String, String> socials, Set<String> waypointKeys, Set<String> macroNames) {
        public Existing { socials = Map.copyOf(socials); waypointKeys = Set.copyOf(waypointKeys); macroNames = Set.copyOf(macroNames); }
    }
    public record Plan(List<String> friends, List<String> enemies, List<SourceWaypoint> waypoints, List<SourceMacro> macros, List<Issue> issues) {
        public Plan { friends = List.copyOf(friends); enemies = List.copyOf(enemies); waypoints = List.copyOf(waypoints); macros = List.copyOf(macros); issues = List.copyOf(issues); }
        public int total() { return friends.size() + enemies.size() + waypoints.size() + macros.size(); }
        /** Argus MacroRegistry JSON (name, type, key, mouse, commands, toggled); its loader only adds. */
        public JsonObject macroJson() {
            JsonArray list = new JsonArray();
            for (SourceMacro m : macros) {
                JsonObject o = new JsonObject(); o.addProperty("name", m.name()); o.addProperty("type", "simple");
                o.addProperty("key", m.bind().map(SourceBind::code).orElse(-1)); o.addProperty("mouse", m.bind().map(SourceBind::mouse).orElse(false));
                JsonArray commands = new JsonArray(); m.messages().forEach(commands::add); o.add("commands", commands); o.addProperty("toggled", true);
                list.add(o);
            }
            JsonObject root = new JsonObject(); root.add("macros", list); return root;
        }
    }
    private static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_]{1,16}");
    static final int WORLD_LIMIT = 30_000_000, MIN_Y = -2048, MAX_Y = 4096, MAX_MESSAGES = 64, MAX_MESSAGE = 256;

    public static Plan plan(DataSource source, Existing existing, Set<DataKind> selected) {
        List<Issue> issues = new ArrayList<>();
        Set<String> friendNames = lower(source.friends()), enemyNames = lower(source.enemies());
        List<String> friends = selected.contains(DataKind.FRIENDS) ? players(source.friends(), "FRIEND", enemyNames, existing, issues) : List.of();
        List<String> enemies = selected.contains(DataKind.ENEMIES) ? players(source.enemies(), "ENEMY", friendNames, existing, issues) : List.of();
        List<SourceWaypoint> waypoints = new ArrayList<>();
        if (selected.contains(DataKind.WAYPOINTS)) {
            Set<String> seen = new HashSet<>(existing.waypointKeys());
            for (SourceWaypoint w : source.waypoints()) {
                String key = (w.name() + "|" + w.server()).toLowerCase(Locale.ROOT);
                if (w.name().isBlank() || w.name().length() > 64) issues.add(warn(w.name(), "waypoint", "Waypoint name is empty or too long"));
                else if (w.dimension() == null) issues.add(warn(w.name(), "waypoint", "Dimension has no Argus match; skipped"));
                else if (Math.abs((long) w.x()) > WORLD_LIMIT || Math.abs((long) w.z()) > WORLD_LIMIT || w.y() < MIN_Y || w.y() > MAX_Y) issues.add(warn(w.name(), "waypoint", "Position is outside the world; skipped"));
                else if (!seen.add(key)) issues.add(info(w.name(), "waypoint", "Already in Argus for " + w.server()));
                else waypoints.add(w);
            }
        }
        List<SourceMacro> macros = new ArrayList<>();
        if (selected.contains(DataKind.MACROS)) {
            Set<String> seen = new HashSet<>(existing.macroNames());
            for (SourceMacro m : source.macros()) {
                String name = m.name().trim();
                if (name.isEmpty() || name.length() > 32) { issues.add(warn(m.name(), "macro", "Macro name is empty or too long")); continue; }
                if (m.messages().isEmpty()) { issues.add(warn(name, "macro", "Macro has no messages; skipped")); continue; }
                if (m.messages().size() > MAX_MESSAGES || m.messages().stream().anyMatch(s -> s.isBlank() || s.length() > MAX_MESSAGE)) { issues.add(warn(name, "macro", "Macro messages are empty or too long; skipped")); continue; }
                if (!seen.add(name.toLowerCase(Locale.ROOT))) { issues.add(info(name, "macro", "A macro with this name is already in Argus")); continue; }
                Optional<SourceBind> bind = m.bind();
                if (bind.isPresent() && (bind.get().modifiers() != 0 || bind.get().code() < 0 || bind.get().code() > (bind.get().mouse() ? 7 : 348))) {
                    issues.add(warn(name, "macro", "Key combination can't be represented in Argus; imported without a bind")); bind = Optional.empty();
                }
                if (m.messages().stream().anyMatch(s -> s.startsWith("."))) issues.add(info(name, "macro", "Uses Meteor's . command prefix; check it works in Argus"));
                macros.add(new SourceMacro(name, m.messages(), bind));
            }
        }
        return new Plan(friends, enemies, waypoints, macros, issues);
    }

    private static List<String> players(List<String> names, String role, Set<String> otherList, Existing existing, List<Issue> issues) {
        List<String> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        String kind = role.equals("FRIEND") ? "friend" : "enemy";
        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!PLAYER.matcher(name).matches()) { issues.add(warn(name, kind, "Not a valid Minecraft name; skipped")); continue; }
            if (!seen.add(key)) continue;
            if (otherList.contains(key)) { issues.add(warn(name, kind, "Listed as both friend and enemy in Meteor; skipped")); continue; }
            String current = existing.socials().get(key);
            if (current != null) { issues.add(current.equalsIgnoreCase(role) ? info(name, kind, "Already in Argus") : warn(name, kind, "Already " + (current.equalsIgnoreCase("FRIEND") ? "a friend" : "an enemy") + " in Argus; left unchanged")); continue; }
            out.add(name);
        }
        return out;
    }
    private static Set<String> lower(List<String> names) { Set<String> s = new HashSet<>(); names.forEach(n -> s.add(n.toLowerCase(Locale.ROOT))); return s; }
    private static Issue warn(String id, String field, String message) { return new Issue(id, field, Severity.WARNING, message); }
    private static Issue info(String id, String field, String message) { return new Issue(id, field, Severity.INFO, message); }
}

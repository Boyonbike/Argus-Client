package dev.argus.client.config.importing;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.argus.client.config.importing.ConfigImportModel.*;
import static dev.argus.client.config.importing.MeteorDataReader.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic Meteor files only; real player folders are never read by tests. */
class MeteorDataImportTest {
    @TempDir Path temp;

    static void write(Path file, CompoundTag root) throws IOException {
        Files.createDirectories(file.getParent());
        try (var out = new DataOutputStream(Files.newOutputStream(file))) { NbtIo.write(root, out); }
    }
    static CompoundTag players(String key, String... names) {
        CompoundTag root = new CompoundTag(); ListTag list = new ListTag();
        for (String n : names) { CompoundTag p = new CompoundTag(); p.putString("name", n); p.putString("id", "0000"); list.add(p); }
        root.put(key, list); return root;
    }
    static CompoundTag setting(String name, Tag value) { CompoundTag s = new CompoundTag(); s.putString("name", name); s.put("value", value); return s; }
    static CompoundTag group(String name, CompoundTag... settings) {
        CompoundTag g = new CompoundTag(); g.putString("name", name); ListTag list = new ListTag(); list.addAll(List.of(settings)); g.put("settings", list); return g;
    }
    static CompoundTag settings(CompoundTag... groups) {
        CompoundTag s = new CompoundTag(); ListTag list = new ListTag(); list.addAll(List.of(groups)); s.put("groups", list);
        CompoundTag holder = new CompoundTag(); holder.put("settings", s); return holder;
    }
    static CompoundTag waypoint(String name, int x, int y, int z, String dimension) {
        return settings(group("Visual", setting("name", StringTag.valueOf(name))),
                group("Position", setting("location", new IntArrayTag(new int[]{x, y, z})), setting("dimension", StringTag.valueOf(dimension))));
    }
    static CompoundTag list(String key, CompoundTag... entries) { CompoundTag root = new CompoundTag(); ListTag l = new ListTag(); l.addAll(List.of(entries)); root.put(key, l); return root; }
    static CompoundTag macro(String name, int key, int modifiers, String... messages) {
        ListTag msgs = new ListTag(); for (String m : messages) msgs.add(StringTag.valueOf(m));
        CompoundTag bind = new CompoundTag(); bind.putBoolean("isKey", true); bind.putInt("value", key); bind.putInt("modifiers", modifiers);
        return settings(group("General", setting("name", StringTag.valueOf(name)), setting("messages", msgs), setting("keybind", bind)));
    }

    @Test void readsFriendsEnemiesWaypointsAndMacros() throws Exception {
        Path meteor = temp.resolve("meteor-client");
        write(meteor.resolve("friends.nbt"), players("friends", "Alice", "bob_2"));
        write(meteor.resolve("enemies.nbt"), players("enemies", "Mallory"));
        write(meteor.resolve("waypoints/6b6t.org.nbt"), list("waypoints", waypoint("Base", 10, 64, -20, "minecraft:the_nether")));
        write(meteor.resolve("waypoints/My World.nbt"), list("waypoints", waypoint("Spawn", 0, 70, 0, "Overworld")));
        write(meteor.resolve("macros.nbt"), list("macros", macro("Greet", 71, 0, "hello", "/home")));
        assertEquals(List.of("Alice", "bob_2"), readPlayers(meteor.resolve("friends.nbt"), "friends"));
        assertEquals(List.of("Mallory"), readPlayers(meteor.resolve("enemies.nbt"), "enemies"));
        var waypoints = readWaypoints(meteor.resolve("waypoints"));
        assertEquals(List.of(new SourceWaypoint("Base", 10, 64, -20, "nether", "6b6t.org"), new SourceWaypoint("Spawn", 0, 70, 0, "overworld", "singleplayer")), waypoints);
        var macros = readMacros(meteor.resolve("macros.nbt"));
        assertEquals(1, macros.size()); assertEquals("Greet", macros.getFirst().name());
        assertEquals(List.of("hello", "/home"), macros.getFirst().messages());
        assertEquals(new SourceBind(false, 71, 0, "toggle"), macros.getFirst().bind().orElseThrow());
    }

    @Test void rejectsWrongRootsAndUnknownDimensionsAreReportedNotGuessed() throws Exception {
        Path bad = temp.resolve("friends.nbt"); write(bad, players("enemies", "x"));
        assertThrows(IOException.class, () -> readPlayers(bad, "friends"));
        Path dir = temp.resolve("wp"); write(dir.resolve("a.b.nbt"), list("waypoints", waypoint("Odd", 1, 2, 3, "custom:moon")));
        var read = readWaypoints(dir);
        assertEquals(1, read.size()); assertNull(read.getFirst().dimension());
    }

    @Test void findsSiblingPrismInstancesAndResolvesAnyFolderLevel() throws Exception {
        Path instances = temp.resolve("instances");
        Path argus = Files.createDirectories(instances.resolve("Argus 1.21.11/minecraft"));
        write(instances.resolve("Keo/minecraft/meteor-client/modules.nbt"), list("modules"));
        write(instances.resolve("Dot/.minecraft/meteor-client/friends.nbt"), players("friends", "A"));
        Files.createDirectories(instances.resolve("Vanilla/minecraft"));
        var found = MeteorInstanceScanner.siblings(argus);
        assertEquals(List.of("Dot", "Keo"), found.stream().map(MeteorInstanceScanner.Instance::name).toList());
        Path keo = instances.resolve("Keo/minecraft/meteor-client");
        for (Path p : List.of(instances.resolve("Keo"), instances.resolve("Keo/minecraft"), keo)) assertEquals(keo, MeteorInstanceScanner.resolve(p).orElseThrow());
        assertTrue(MeteorInstanceScanner.resolve(instances.resolve("Vanilla")).isEmpty());
    }

    @Test void detectsImportableFilesProfilesAndNamesWhatIsSkipped() throws Exception {
        Path meteor = temp.resolve("meteor-client");
        write(meteor.resolve("modules.nbt"), list("modules"));
        write(meteor.resolve("profiles/Normal/modules.nbt"), list("modules"));
        write(meteor.resolve("friends.nbt"), players("friends", "A"));
        write(meteor.resolve("waypoints/x.nbt"), list("waypoints"));
        for (String f : List.of("hud.nbt", "config.nbt", "accounts.nbt", "proxies.nbt", "stash-hunter.properties"))
            Files.writeString(meteor.resolve(f), "x");
        Files.createDirectories(meteor.resolve("gui")); Files.createDirectories(meteor.resolve("comet"));
        var detection = MeteorInstanceScanner.detect(meteor);
        assertEquals(List.of(meteor.resolve("modules.nbt"), meteor.resolve("profiles/Normal/modules.nbt")), detection.moduleSources().stream().map(MeteorInstanceScanner.ModuleSource::path).toList());
        assertEquals(Set.of(DataKind.FRIENDS, DataKind.WAYPOINTS), detection.data().keySet());
        var skipped = detection.skipped();
        assertTrue(skipped.contains("HUD layout")); assertTrue(skipped.contains("Meteor settings")); assertTrue(skipped.contains("GUI and themes"));
        assertTrue(skipped.contains("Accounts")); assertTrue(skipped.contains("Proxies"));
        assertTrue(skipped.stream().anyMatch(s -> s.startsWith("Addon data")));
    }

    @Test void backupsGoToANewFolderAndNeverOverwrite() throws Exception {
        var stores = new LinkedHashMap<String, com.google.gson.JsonElement>();
        stores.put("socials", com.google.gson.JsonParser.parseString("{\"socials\":[{\"name\":\"A\"}]}")); stores.put("macros", new com.google.gson.JsonObject());
        Path first = ArgusDataStore.writeBackup(temp.resolve("import-backups"), stores), second = ArgusDataStore.writeBackup(temp.resolve("import-backups"), stores);
        assertNotEquals(first, second);
        assertTrue(Files.readString(first.resolve("socials.json")).contains("\"A\""));
        assertTrue(Files.exists(second.resolve("macros.json")));
    }
    @Test void summaryCountsOnlyWhatIsAdded() {
        var plan = new ArgusDataPlan.Plan(List.of("A", "B"), List.of("C"), List.of(), List.of(new SourceMacro("M", List.of("x"), Optional.empty())), List.of());
        assertEquals("2 friends, 1 enemy, 1 macro", ArgusDataStore.summary(plan));
    }
    @Test void plansOnlyAdditionsAndExplainsEverySkip() {
        var source = new DataSource(List.of("Alice", "alice", "Bad Name!", "Eve"), List.of("Eve", "Mallory"),
                List.of(new SourceWaypoint("Base", 1, 64, 2, "nether", "6b6t.org"), new SourceWaypoint("Home", 0, 0, 0, "overworld", "6b6t.org"),
                        new SourceWaypoint("Moon", 0, 0, 0, null, "6b6t.org"), new SourceWaypoint("Far", 40_000_000, 0, 0, "end", "x")),
                List.of(new SourceMacro("Greet", List.of("hi"), Optional.of(new SourceBind(false, 71, 0, "toggle"))),
                        new SourceMacro("Chord", List.of("gg"), Optional.of(new SourceBind(false, 72, 2, "toggle"))),
                        new SourceMacro("Old", List.of("x"), Optional.empty()), new SourceMacro("Empty", List.of(), Optional.empty())));
        var existing = new ArgusDataPlan.Existing(Map.of("bob", "FRIEND", "mallory", "FRIEND"), Set.of("home|6b6t.org"), Set.of("old"));
        var plan = ArgusDataPlan.plan(source, existing, EnumSet.allOf(DataKind.class));
        assertEquals(List.of("Alice"), plan.friends());
        assertEquals(List.of(), plan.enemies(), "Eve is listed as both; Mallory is already an Argus friend");
        assertEquals(List.of(new SourceWaypoint("Base", 1, 64, 2, "nether", "6b6t.org")), plan.waypoints());
        assertEquals(List.of("Greet", "Chord"), plan.macros().stream().map(SourceMacro::name).toList());
        assertTrue(plan.macros().get(1).bind().isEmpty(), "Modified chords import unbound, never as a plain key");
        assertEquals(1, plan.macroJson().getAsJsonArray("macros").get(0).getAsJsonObject().get("commands").getAsJsonArray().size());
        assertEquals(71, plan.macroJson().getAsJsonArray("macros").get(0).getAsJsonObject().get("key").getAsInt());
        assertEquals(-1, plan.macroJson().getAsJsonArray("macros").get(1).getAsJsonObject().get("key").getAsInt());
        for (String needle : List.of("Bad Name!", "Eve", "Mallory", "Home", "Moon", "Far", "Chord", "Old", "Empty"))
            assertTrue(plan.issues().stream().anyMatch(i -> i.sourceId().equals(needle)), "missing explanation for " + needle);
        var onlyFriends = ArgusDataPlan.plan(source, existing, EnumSet.of(DataKind.FRIENDS));
        assertTrue(onlyFriends.waypoints().isEmpty() && onlyFriends.macros().isEmpty() && onlyFriends.enemies().isEmpty());
    }
}

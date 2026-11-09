package dev.argus.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.config.PresetData;
import dev.argus.client.managers.PresetRegistry;
import dev.argus.client.settings.options.PresetCategory;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * GUI-facing view of the core preset store. Presets are the same files the /preset command
 * uses; bindings are stored in the core's auto-preset registry (as /autopreset does), keyed by
 * server address, or by {@code world:<save folder>} for singleplayer worlds. The core already
 * applies server bindings on connect; this class adds the same behaviour for worlds.
 */
final class Presets {
    private Presets() {}

    static final String WORLD_PREFIX = "world:";

    /** "All" first: it captures every module, which is what most people mean by a config. */
    static List<PresetCategory> categories() {
        List<PresetCategory> result = new ArrayList<>(List.of(PresetCategory.values()));
        result.remove(PresetCategory.ALL); result.addFirst(PresetCategory.ALL);
        return result;
    }

    private static PresetRegistry registry(PresetCategory category) { return ClientContext.configurationManager.getPresetRegistry(category); }

    static List<String> names(PresetCategory category) {
        List<String> names = new ArrayList<>();
        for (Object entry : registry(category).getRegistry()) if (entry instanceof PresetData preset) names.add(preset.getName());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static boolean exists(PresetCategory category, String name) { return registry(category).getPreset(name) != null; }

    /** Characters that are unsafe in the preset's file name are rejected rather than rewritten. */
    static boolean validName(String name) { return name.matches("[A-Za-z0-9 _.\\-]{1,32}") && !name.isBlank() && !name.startsWith("."); }

    /** Key under which a preset file carries the module menu's window layout. */
    static final String LAYOUT_KEY = "argusclientLayout";

    /** Window layout travels with config-wide presets (Everything and Modules). */
    static boolean carriesLayout(PresetCategory category) { return category == PresetCategory.ALL || category == PresetCategory.MODULES; }

    static void save(PresetCategory category, String name) throws IOException {
        String trimmed = name.trim();
        registry(category).savePreset(trimmed);
        if (!carriesLayout(category)) return;
        PresetData preset = registry(category).getPreset(trimmed);
        if (preset == null) return;
        preset.contents().add(LAYOUT_KEY, layoutJson());
        registry(category).savePresetByExactName(preset.getName());
    }

    static boolean load(PresetCategory category, String name) {
        if (category == PresetCategory.MODULES && dev.argus.client.config.importing.ImportedPresetStore.isImported(name))
            return dev.argus.client.config.importing.ImportedPresetStore.load(name).success();
        if (!registry(category).applyPreset(name)) return false;
        applyLayout(category, name);
        return true;
    }

    static boolean hasLayout(PresetCategory category, String name) {
        PresetData preset = registry(category).getPreset(name);
        return preset != null && preset.contents().has(LAYOUT_KEY);
    }

    private static JsonObject layoutJson() {
        JsonObject layout = new JsonObject(), windows = new JsonObject();
        GuiState.preferences().windows.forEach((window, place) -> {
            JsonObject json = new JsonObject();
            json.addProperty("x", place.x()); json.addProperty("y", place.y());
            json.addProperty("collapsed", place.collapsed()); json.addProperty("scroll", place.scroll());
            windows.add(window, json);
        });
        layout.addProperty("version", 1);
        layout.add("windows", windows);
        return layout;
    }

    /** Restores the window layout saved in a preset, if it has one. */
    static void applyLayout(PresetCategory category, String name) {
        if (!carriesLayout(category)) return;
        PresetData preset = registry(category).getPreset(name);
        if (preset == null || !preset.contents().has(LAYOUT_KEY) || !preset.contents().get(LAYOUT_KEY).isJsonObject()) return;
        JsonObject layout = preset.contents().getAsJsonObject(LAYOUT_KEY);
        if (!layout.has("windows") || !layout.get("windows").isJsonObject()) return;
        var windows = GuiState.preferences().windows;
        windows.clear();
        for (var entry : layout.getAsJsonObject("windows").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            try {
                windows.put(entry.getKey(), new GuiPreferences.Window(json.get("x").getAsInt(), json.get("y").getAsInt(),
                        json.has("collapsed") && json.get("collapsed").getAsBoolean(), json.has("scroll") ? Math.max(0, json.get("scroll").getAsInt()) : 0));
            } catch (RuntimeException ignored) { }
        }
        GuiState.save();
    }

    static void delete(PresetCategory category, String name) {
        registry(category).deletePreset(name);
        for (String target : targets(category, name)) ClientContext.autoPresetRegistry.removePreset(target, category);
        persist();
    }

    /** target → preset name, for one category. */
    static Map<String, String> bindings(PresetCategory category) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        JsonElement json = ClientContext.autoPresetRegistry.toJson();
        if (!json.isJsonObject()) return result;
        String key = category.getName().toLowerCase(Locale.ROOT);
        for (var server : json.getAsJsonObject().entrySet()) {
            if (!server.getValue().isJsonObject()) continue;
            JsonObject settings = server.getValue().getAsJsonObject();
            if (settings.has(key) && settings.get(key).isJsonPrimitive()) result.put(server.getKey(), settings.get(key).getAsString());
        }
        return result;
    }

    static List<String> targets(PresetCategory category, String preset) {
        List<String> result = new ArrayList<>();
        bindings(category).forEach((target, name) -> { if (name.equalsIgnoreCase(preset)) result.add(target); });
        return result;
    }

    /** A target holds one preset per category, so binding replaces any earlier binding there. */
    static void bind(String target, PresetCategory category, String preset) { ClientContext.autoPresetRegistry.setPreset(target, category, preset); persist(); }
    static void unbind(String target, PresetCategory category) { ClientContext.autoPresetRegistry.removePreset(target, category); persist(); }

    private static void persist() { ClientContext.configurationManager.save(); }

    /** The server address or {@code world:<folder>} you are currently in, or null on the menus. */
    static String currentTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            var folder = integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return folder == null ? null : WORLD_PREFIX + folder;
        }
        var server = mc.getCurrentServer();
        return server == null || server.ip == null || server.ip.isBlank() ? null : server.ip;
    }

    static boolean isWorld(String target) { return target.startsWith(WORLD_PREFIX); }
    static String label(String target) { return isWorld(target) ? target.substring(WORLD_PREFIX.length()) : target; }

    /** Singleplayer equivalent of the core's on-connect auto-preset. Called once per world join. */
    static void onJoin(Minecraft mc) {
        String target = currentTarget();
        if (target == null) return;
        if (!isWorld(target)) {
            // The core already applied the server's presets on connect; restore their window layout.
            for (PresetCategory category : PresetCategory.values()) {
                String preset = bindings(category).get(target);
                if (preset != null) applyLayout(category, preset);
            }
            return;
        }
        for (PresetCategory category : PresetCategory.values()) {
            String preset = bindings(category).get(target);
            if (preset != null && load(category, preset))
                mc.gui.getChat().addMessage(Component.literal("Preset " + preset + " has been loaded for this world."));
        }
    }
}

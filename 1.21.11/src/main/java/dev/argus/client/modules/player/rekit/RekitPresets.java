package dev.argus.client.modules.player.rekit;

import com.google.gson.*;
import java.util.*;

/** Registry-independent snapshots can be saved/loaded even at the title screen. */
public final class RekitPresets {
    private final Map<String, JsonObject> presets = new TreeMap<>();
    public List<String> names() { return List.copyOf(presets.keySet()); }
    public JsonObject get(String name) {
        var preset = presets.get(name);
        return preset == null ? null : preset.deepCopy();
    }
    public void put(String name, JsonObject slots) {
        if (name == null || name.isBlank() || name.length() > 32) throw new IllegalArgumentException("Use a name with 1–32 characters.");
        presets.put(name, slots.deepCopy());
    }
    public void delete(String name) { presets.remove(name); }
    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        presets.forEach((name, slots) -> result.add(name, slots.deepCopy()));
        return result;
    }
    public void load(JsonElement json) {
        presets.clear();
        if (json == null || !json.isJsonObject()) return;
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getKey().isBlank() && entry.getKey().length() <= 32 && entry.getValue().isJsonObject())
                put(entry.getKey(), entry.getValue().getAsJsonObject());
        });
    }
}

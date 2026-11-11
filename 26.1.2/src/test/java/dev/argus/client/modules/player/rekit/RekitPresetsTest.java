package dev.argus.client.modules.player.rekit;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RekitPresetsTest {
    @Test void storesIndependentSnapshotsAndSurvivesConfigurationReload() {
        var presets = new RekitPresets();
        JsonObject slots = JsonParser.parseString("{\"0\":{\"id\":\"minecraft:stone\",\"count\":20}}").getAsJsonObject();
        presets.put("Building", slots); slots.remove("0");
        var loaded = new RekitPresets(); loaded.load(presets.toJson());
        assertEquals(20, loaded.get("Building").getAsJsonObject("0").get("count").getAsInt());
        loaded.get("Building").remove("0");
        assertTrue(loaded.get("Building").has("0"));
        loaded.delete("Building"); assertTrue(loaded.names().isEmpty());
    }
    @Test void rejectsBlankOrOversizedNamesAndLoadsOnlyObjects() {
        var presets = new RekitPresets();
        assertThrows(IllegalArgumentException.class, () -> presets.put(" ", new JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> presets.put("x".repeat(33), new JsonObject()));
        presets.load(JsonParser.parseString("{\"valid\":{},\"broken\":4}"));
        assertEquals(java.util.List.of("valid"), presets.names());
    }
}

package dev.argus.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ClientDataDirectoryTest {
    @TempDir Path game;

    private static final String EGG_MODULES = """
            {
              "client": "eggclient",
              "category": "all",
              "AutoSign": {
                "enabled": false,
                "settings": {
                  "Delay": 0.0,
                  "Line1": "get EggClient",
                  "Line2": "at",
                  "Line3": "github.com/",
                  "Line4": "spectrumdevs/zio"
                }
              },
              "Nametags": {
                "settings": {
                  "IgnoreEggClient": true,
                  "DragonEgg": false
                }
              }
            }
            """;

    private static final String EGG_HUD = """
            {
              "Watermark": { "settings": { "Text": "EggClient" } },
              "Welcomer": { "settings": { "Text": "Welcome to EggClient." } }
            }
            """;

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void importsEggClientSettingsAndUpdatesTheirNames() throws Exception {
        Path egg = Files.createDirectory(game.resolve("eggclient"));
        Files.createDirectories(egg.resolve("presets/modules"));
        Files.writeString(egg.resolve("modules.json"), EGG_MODULES);
        Files.writeString(egg.resolve("hud.json"), EGG_HUD);
        Files.writeString(egg.resolve("presets/modules/PvP.json"), EGG_MODULES);
        Files.writeString(egg.resolve("gui.properties"), "#EggClient GUI\nbarOpen=true\n");
        Path target = game.resolve("argusclient");

        assertEquals(target, ClientDataDirectory.migrateLegacy(target));

        JsonObject modules = read(target.resolve("modules.json"));
        assertEquals("argusclient", modules.get("client").getAsString());
        JsonObject sign = modules.getAsJsonObject("AutoSign").getAsJsonObject("settings");
        assertEquals("get Argus Client", sign.get("Line1").getAsString());
        assertEquals("at github.com/", sign.get("Line2").getAsString());
        assertEquals("Boyonbike/", sign.get("Line3").getAsString());
        assertEquals("Argus-Client", sign.get("Line4").getAsString());
        assertEquals("0.0", sign.get("Delay").toString(), "Numbers keep their saved form");
        JsonObject tags = modules.getAsJsonObject("Nametags").getAsJsonObject("settings");
        assertTrue(tags.get("IgnoreArgus Client").getAsBoolean());
        assertFalse(tags.has("IgnoreEggClient"));
        assertTrue(tags.has("DragonEgg"), "Unrelated names containing Egg stay as they are");

        JsonObject hud = read(target.resolve("hud.json"));
        assertEquals("Argus Client", hud.getAsJsonObject("Watermark").getAsJsonObject("settings").get("Text").getAsString());
        assertEquals("Welcome to Argus Client.",
                hud.getAsJsonObject("Welcomer").getAsJsonObject("settings").get("Text").getAsString());
        assertEquals("argusclient", read(target.resolve("presets/modules/PvP.json")).get("client").getAsString());
        assertEquals("#Argus Client GUI\nbarOpen=true\n", Files.readString(target.resolve("gui.properties")));

        assertEquals(EGG_MODULES, Files.readString(egg.resolve("modules.json")), "Egg Client folder must stay untouched");
    }

    @Test
    void updatesOldNamesInExistingArgusSettings() throws Exception {
        Path target = Files.createDirectory(game.resolve("argusclient"));
        Files.writeString(target.resolve("hud.json"), "{\n  \"Watermark\": {\n    \"Text\": \"Random Stuff Client\"\n  }\n}\n");
        Files.writeString(game.resolve("eggclient"), "ignored: Argus settings already exist");
        ClientDataDirectory.migrateLegacy(target);
        assertEquals("{\n  \"Watermark\": {\n    \"Text\": \"Argus Client\"\n  }\n}\n", Files.readString(target.resolve("hud.json")));
    }

    @Test
    void keepsExistingArgusSettingsOverEggClient() throws Exception {
        Files.createDirectory(game.resolve("eggclient"));
        Files.writeString(game.resolve("eggclient/config.json"), "{\"value\": \"old\"}");
        Path target = Files.createDirectory(game.resolve("argusclient"));
        Files.writeString(target.resolve("config.json"), "{\"value\": \"current\"}");
        ClientDataDirectory.migrateLegacy(target);
        assertEquals("{\"value\": \"current\"}", Files.readString(target.resolve("config.json")), "Unchanged files are not rewritten");
    }

    @Test
    void prefersArgusSettingWhenBothNamesExist() {
        String json = "{\"IgnoreEggClient\": true, \"IgnoreArgus Client\": false}";
        JsonObject renamed = JsonParser.parseString(LegacyConfigRenamer.renameJson(json)).getAsJsonObject();
        assertFalse(renamed.get("IgnoreArgus Client").getAsBoolean());
        assertEquals(1, renamed.size());
    }

    @Test
    void leavesFilesThatAreNotJsonAlone() throws Exception {
        Path target = Files.createDirectory(game.resolve("argusclient"));
        Files.writeString(target.resolve("broken.json"), "EggClient {");
        ClientDataDirectory.migrateLegacy(target);
        assertEquals("EggClient {", Files.readString(target.resolve("broken.json")));
    }

    @Test
    void returnsMissingDirectoryUnchanged() {
        Path target = game.resolve("argusclient");
        assertEquals(target, ClientDataDirectory.migrateLegacy(target));
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsFileInPlaceOfDirectory() throws Exception {
        Path target = Files.writeString(game.resolve("argusclient"), "not a folder");
        assertThrows(UncheckedIOException.class, () -> ClientDataDirectory.migrateLegacy(target));
    }
}

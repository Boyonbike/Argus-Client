package dev.argus.client.config;

import dev.argus.client.config.importing.ExternalConfigReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** A 1.21.11 Argus JSON config must still load with the same modules, toggles and settings. */
class PortConfigCompatibilityTest {
   @TempDir
   Path temp;

   @Test void loadsTwentySixOneTwoConfigFixture() throws Exception {
      Path file = temp.resolve("config-26.1.2.json");
      Files.writeString(file, """
         {"client":"argusclient","category":"all",
          "AutoCrystal":{"enabled":true,"settings":{"Place":"NCP","Rotate":true}},
          "HUD":{"enabled":true,"settings":{"Watermark":true}}}
         """);

      var source = ExternalConfigReader.read(file);

      assertEquals(2, source.modules().size());
      assertTrue(source.modules().get(0).enabled());
      assertEquals("NCP", source.modules().get(0).settings().get("Place").getAsString());
      assertTrue(source.modules().get(0).settings().get("Rotate").getAsBoolean());
   }

   @Test void loadsArgusOneTwentyOneElevenConfigFixture() throws Exception {
      Path file = temp.resolve("config.json");
      Files.writeString(file, """
         {"client":"argusclient","category":"all",
          "Sprint":{"enabled":true,"settings":{"Mode":"Rage"}},
          "ESP":{"enabled":false,"settings":{"Mode":"Meteor"}}}
         """);

      var source = ExternalConfigReader.read(file);

      assertEquals(2, source.modules().size());
      assertTrue(source.modules().get(0).enabled());
      assertEquals("Rage", source.modules().get(0).settings().get("Mode").getAsString());
      assertFalse(source.modules().get(1).enabled());
   }

   @Test void loadsTwentySixTwoArgusConfigWithGlfwBinds() throws Exception {
      Path file = temp.resolve("config-26.2.json");
      Files.writeString(file, """
         {"client":"argusclient","category":"all",
          "HUD":{"enabled":true,"settings":{"Watermark":true,"Layout":{"AnchorX":0.5,"AnchorY":0.5,"Scale":1.0}}},
          "Sprint":{"enabled":true,"settings":{"Mode":"Rage","Keybind":{"code":80,"mouse":false},"MouseBind":{"code":1,"mouse":true}}}}
         """);

      var source = ExternalConfigReader.read(file);

      assertEquals(2, source.modules().size());
      assertTrue(source.modules().get(0).enabled());
      assertEquals(0.5, source.modules().get(0).settings().get("Layout").getAsJsonObject().get("AnchorX").getAsDouble());
      var bind = source.modules().get(1).settings().get("Keybind").getAsJsonObject();
      assertEquals(80, bind.get("code").getAsInt());
      assertFalse(bind.get("mouse").getAsBoolean());
      assertTrue(source.modules().get(1).settings().get("MouseBind").getAsJsonObject().get("mouse").getAsBoolean());
   }

   @Test void keybindSettingKeepsTwentySixTwoGlfwCode() {
      dev.argus.client.settings.KeyBindSetting bind =
         new dev.argus.client.settings.KeyBindSetting("Bind", "Sprint", value -> true);
      com.google.gson.JsonObject json = new com.google.gson.JsonObject();
      json.addProperty("code", 80);
      json.addProperty("mouse", false);
      bind.fromJson(json);
      assertEquals(80, bind.getValue().code());
      assertFalse(bind.getValue().mouse());
   }
}

package dev.argus.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class KeyBindSettingTest {
   private static KeyBindSetting setting() {
      return new KeyBindSetting("Boost Key", "Sprint", value -> true);
   }

   @Test
   void defaultsToFollowingSprint() {
      KeyBindSetting key = setting();
      assertTrue(key.followsDefault());
      assertEquals("Sprint", key.displayName());
      assertEquals(JsonNull.INSTANCE, key.toJson());
   }

   @Test
   void keyboardAndMouseBindsSurviveSaveAndLoad() {
      KeyBindSetting key = setting();
      key.bindKey(70);
      KeyBindSetting loaded = setting();
      loaded.fromJson(key.toJson());
      assertFalse(loaded.followsDefault());
      assertEquals(70, loaded.getValue().code());
      assertFalse(loaded.getValue().mouse());

      key.bindMouse(4);
      loaded.fromJson(key.toJson());
      assertEquals(4, loaded.getValue().code());
      assertTrue(loaded.getValue().mouse());
   }

   @Test
   void missingOrBrokenSavedValueFallsBackToSprint() {
      KeyBindSetting key = setting();
      key.bindKey(70);
      key.fromJson(null);
      assertTrue(key.followsDefault());
      key.bindKey(70);
      key.fromJson(new JsonObject());
      assertTrue(key.followsDefault());
   }

   @Test
   void resetAndDeleteReturnToSprint() {
      KeyBindSetting key = setting();
      key.bindKey(70);
      key.reset();
      assertTrue(key.followsDefault());
      key.bindKey(70);
      key.followDefault();
      assertTrue(key.followsDefault());
   }
}

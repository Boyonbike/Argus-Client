package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class RenamedTweakKeysTest {
   @Test
   void oldTweakKeysMoveToTheArgusKeys() {
      JsonObject saved = new JsonObject();
      saved.addProperty("volytraCometMotion", true);
      saved.addProperty("volytraCustomSpeeds", true);
      saved.addProperty("volytraBaseHorizontalSpeed", 60.0);
      saved.addProperty("volytraCometAcceleration", 0.3);
      saved.addProperty("volytraLandingSwapHeight", 1.25);
      saved.addProperty("volytraStartSpeed", 2.999);
      JsonObject migrated = VolytraElytraFlyHandler.migrateRenamedKeys(saved);
      assertTrue(migrated.get("argusMotion").getAsBoolean());
      assertTrue(migrated.get("argusBaseBoostSpeeds").getAsBoolean());
      assertEquals(60.0, migrated.get("argusBaseHorizontalSpeed").getAsDouble());
      assertEquals(0.3, migrated.get("argusMotionAcceleration").getAsDouble());
      assertEquals(1.25, migrated.get("argusLandingSwapHeight").getAsDouble());
      assertFalse(migrated.has("volytraCometMotion"));
      // Volytra Fly's own settings keep their keys.
      assertEquals(2.999, migrated.get("volytraStartSpeed").getAsDouble());
      // The saved settings object itself is left untouched.
      assertTrue(saved.has("volytraCometMotion"));
   }

   @Test
   void newKeysWinOverStaleOldOnes() {
      JsonObject saved = new JsonObject();
      saved.addProperty("volytraBoostJump", 10);
      saved.addProperty("argusBoostJump", 0);
      assertEquals(0, VolytraElytraFlyHandler.migrateRenamedKeys(saved).get("argusBoostJump").getAsInt());
   }

   @Test
   void everyRenamedKeyPointsAtAnArgusKey() {
      VolytraElytraFlyHandler.RENAMED_CONFIG_KEYS.forEach((oldKey, newKey) -> {
         assertTrue(oldKey.startsWith("volytra"), oldKey);
         assertTrue(newKey.startsWith("argus"), newKey);
      });
      assertEquals(17, VolytraElytraFlyHandler.RENAMED_CONFIG_KEYS.size());
   }

   @Test
   void defaultBoostKeyNullSurvivesTheMove() {
      JsonObject saved = new JsonObject();
      saved.add("volytraBoostKey", JsonNull.INSTANCE);
      assertTrue(VolytraElytraFlyHandler.migrateRenamedKeys(saved).has("argusBoostKey"));
   }
}

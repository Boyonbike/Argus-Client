package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DefaultTargetsTest {
   @Test
   void espStartsWithMeteorsStorageGroups() {
      SpecificRules rules = new SpecificRules();
      rules.load(DefaultTargets.esp());
      assertEquals(6, rules.all().size());
      assertEquals(9, rules.find("default-chests").members().size());
      assertEquals(17, rules.find("default-shulker-boxes").members().size());
      assertEquals(10, rules.find("default-other-storage").members().size());
      assertNotNull(rules.owner(SpecificRules.Type.BLOCK, "minecraft:ender_chest"));
      // Loading and saving again changes nothing, so the list still counts as the defaults.
      assertEquals(DefaultTargets.esp(), rules.toJson());
   }

   @Test
   void tracersStartsWithFriendsAndPlayers() {
      SpecificRules rules = new SpecificRules();
      rules.load(DefaultTargets.tracers());
      assertEquals(2, rules.all().size());
      assertNotNull(rules.owner(SpecificRules.Type.GROUP, SpecificRules.FRIENDS));
      assertNotNull(rules.owner(SpecificRules.Type.GROUP, SpecificRules.ALL_PLAYERS));
      assertEquals(DefaultTargets.tracers(), rules.toJson());
   }
}

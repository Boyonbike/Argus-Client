package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecificRulesTest {
   private static final SpecificRules.Type BLOCK = SpecificRules.Type.BLOCK;
   private static final SpecificRules.Type PLAYER = SpecificRules.Type.PLAYER;

   @Test
   void addReturnsExistingRuleForSameTargetIgnoringCase() {
      SpecificRules rules = new SpecificRules();
      SpecificRules.Rule first = rules.add(PLAYER, "Notch");
      assertSame(first, rules.add(PLAYER, "notch"));
      assertEquals(1, rules.all().size());
      assertNotNull(rules.add(BLOCK, "minecraft:diamond_ore"));
      assertEquals(2, rules.all().size());
   }

   @Test
   void jsonRoundTripKeepsEveryValueAndSkipsBadEntries() {
      SpecificRules rules = new SpecificRules();
      SpecificRules.Rule rule = rules.add(BLOCK, "minecraft:spawner");
      rule.color = 0xFF112233;
      rule.range = 64.0F;
      rule.enabled = false;
      rule.mirror = true;

      SpecificRules loaded = new SpecificRules();
      loaded.load(rules.toJson().replace("[", "[{\"type\":\"NOPE\"},"));
      assertEquals(1, loaded.all().size());
      SpecificRules.Rule back = loaded.find(BLOCK, "minecraft:spawner");
      assertEquals(0xFF112233, back.color);
      assertEquals(64.0F, back.range);
      assertFalse(back.enabled);
      assertTrue(back.mirror);

      loaded.load("not json");
      assertTrue(loaded.isEmpty());
   }

   @Test
   void rangeCyclesThroughPresetsAndWraps() {
      SpecificRules.Rule rule = new SpecificRules.Rule(BLOCK, "minecraft:stone", 0);
      assertTrue(rule.unlimited());
      assertTrue(rule.inRange(1.0E12));
      assertEquals(16.0F, rule.nextRange());
      rule.range = 16.0F;
      assertFalse(rule.inRange(17.0 * 17.0));
      assertEquals(32.0F, rule.nextRange());
      rule.range = 256.0F;
      assertEquals(SpecificRules.UNLIMITED, rule.nextRange());
   }

   @Test
   void mirrorCopiesWhicheverSideChangedWithoutPingPong() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(BLOCK, "minecraft:diamond_ore");
      SpecificRules.Rule right = tracers.add(BLOCK, "minecraft:diamond_ore");
      left.mirror = right.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      left.color = 1;
      right.color = 1;
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      right.color = 2;
      assertEquals(SpecificRules.LEFT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertEquals(2, left.color);
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      left.range = 32.0F;
      assertEquals(SpecificRules.RIGHT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertEquals(32.0F, right.range);
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));
   }

   @Test
   void bothSidesChangedLeftWins() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(PLAYER, "Steve");
      SpecificRules.Rule right = tracers.add(PLAYER, "Steve");
      left.mirror = right.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      SpecificRules.sync(esp, tracers, agreed);
      left.color = 5;
      right.color = 9;
      SpecificRules.sync(esp, tracers, agreed);
      assertEquals(5, right.color);
   }

   @Test
   void halfLinkIsBrokenNotRestored() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(BLOCK, "minecraft:chest");
      left.mirror = true;
      left.color = 7;
      Map<String, String> agreed = new HashMap<>();
      assertEquals(SpecificRules.LEFT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertFalse(left.mirror);
      assertNull(tracers.find(BLOCK, "minecraft:chest"));

      SpecificRules.Rule right = tracers.add(BLOCK, "minecraft:chest");
      right.mirror = true;
      assertEquals(SpecificRules.RIGHT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertFalse(right.mirror);
      assertTrue(agreed.isEmpty());
   }
}

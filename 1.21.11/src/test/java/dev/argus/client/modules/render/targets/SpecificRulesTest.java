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
      assertTrue(rules.remove(first));
      assertNull(rules.find(PLAYER, "NOTCH"));
   }

   @Test
   void jsonRoundTripKeepsFlagsAndSettingsAndSkipsBadEntries() {
      SpecificRules rules = new SpecificRules();
      SpecificRules.Rule rule = rules.add(BLOCK, "minecraft:spawner");
      rule.body.write("{\"TargetColor\":123,\"TargetRange\":64.0}");
      rule.enabled = false;
      rule.mirror = true;

      SpecificRules loaded = new SpecificRules();
      loaded.load(rules.toJson().replace("[", "[{\"type\":\"NOPE\"},"));
      assertEquals(1, loaded.all().size());
      SpecificRules.Rule back = loaded.find(BLOCK, "minecraft:spawner");
      assertEquals("{\"TargetColor\":123,\"TargetRange\":64.0}", back.body.read());
      assertFalse(back.enabled);
      assertTrue(back.mirror);

      loaded.load("not json");
      assertTrue(loaded.isEmpty());
   }

   @Test
   void copyValuesCopiesSettingsAndEnabledButNotMirror() {
      SpecificRules.Rule from = new SpecificRules.Rule(BLOCK, "minecraft:chest");
      from.body.write("{\"TargetColor\":1}");
      from.enabled = false;
      from.mirror = true;
      SpecificRules.Rule to = new SpecificRules.Rule(BLOCK, "minecraft:chest");
      to.copyValues(from);
      assertEquals("{\"TargetColor\":1}", to.body.read());
      assertFalse(to.enabled);
      assertFalse(to.mirror);
   }

   @Test
   void mirrorCopiesWhicheverSideChangedWithoutPingPong() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(BLOCK, "minecraft:diamond_ore");
      SpecificRules.Rule right = tracers.add(BLOCK, "minecraft:diamond_ore");
      left.mirror = right.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      right.body.write("{\"TargetColor\":2}");
      assertEquals(SpecificRules.LEFT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertEquals("{\"TargetColor\":2}", left.body.read());
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      left.enabled = false;
      assertEquals(SpecificRules.RIGHT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertFalse(right.enabled);
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
      left.body.write("{\"TargetColor\":5}");
      right.body.write("{\"TargetColor\":9}");
      SpecificRules.sync(esp, tracers, agreed);
      assertEquals("{\"TargetColor\":5}", right.body.read());
   }

   @Test
   void halfLinkIsBrokenNotRestored() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(BLOCK, "minecraft:chest");
      left.mirror = true;
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

package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecificRulesTest {
   private static final SpecificRules.Member DIAMOND = block("minecraft:diamond_ore");
   private static final SpecificRules.Member DEBRIS = block("minecraft:ancient_debris");
   private static final SpecificRules.Member ZOMBIE = new SpecificRules.Member(SpecificRules.Type.ENTITY, "minecraft:zombie", SpecificRules.Category.MOBS);
   private static final SpecificRules.Member NOTCH = new SpecificRules.Member(SpecificRules.Type.PLAYER, "Notch", SpecificRules.Category.PLAYERS);

   private static SpecificRules.Member block(String id) {
      return new SpecificRules.Member(SpecificRules.Type.BLOCK, id, SpecificRules.Category.BLOCKS);
   }

   @Test
   void eachThingCanBeAddedOnce() {
      SpecificRules rules = new SpecificRules();
      SpecificRules.Rule ores = rules.add(List.of(DIAMOND, DEBRIS, DIAMOND));
      assertEquals(2, ores.members().size());
      assertEquals(SpecificRules.Category.BLOCKS, ores.category());

      SpecificRules.Rule second = rules.add(List.of(DIAMOND, ZOMBIE));
      assertEquals(List.of(ZOMBIE), second.members());
      assertNull(rules.add(List.of(DEBRIS)));
      assertSame(ores, rules.owner(SpecificRules.Type.BLOCK, "minecraft:diamond_ore"));
      assertSame(second, rules.owner(SpecificRules.Type.ENTITY, "MINECRAFT:ZOMBIE"));
   }

   @Test
   void mixedGroupIsCategoryAll() {
      SpecificRules rules = new SpecificRules();
      assertEquals(SpecificRules.Category.ALL, rules.add(List.of(DIAMOND, NOTCH)).category());
      assertEquals(SpecificRules.Category.MOBS, rules.add(List.of(ZOMBIE)).category());
   }

   @Test
   void putCopiesGroupAndTakesItsThingsFromOtherGroups() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule ores = esp.add(List.of(DIAMOND, DEBRIS));
      ores.body.write("{\"TargetColor\":1}");
      SpecificRules.Rule lone = tracers.add(List.of(DIAMOND));
      SpecificRules.Rule mobs = tracers.add(List.of(ZOMBIE, DEBRIS));

      SpecificRules.Rule copy = tracers.put(ores);
      assertEquals(ores.id, copy.id);
      assertEquals(List.of(DIAMOND, DEBRIS), copy.members());
      assertEquals("{\"TargetColor\":1}", copy.body.read());
      assertNull(tracers.find(lone.id));
      assertEquals(List.of(ZOMBIE), tracers.find(mobs.id).members());
   }

   @Test
   void jsonRoundTripKeepsGroupsAndSkipsDuplicatesAndBadEntries() {
      SpecificRules rules = new SpecificRules();
      SpecificRules.Rule rule = rules.add(List.of(DIAMOND, NOTCH));
      rule.body.write("{\"TargetRange\":64.0}");
      rule.enabled = false;
      rule.mirror = true;
      String json = rules.toJson();

      SpecificRules loaded = new SpecificRules();
      loaded.load("[{\"id\":\"bad\"}," + json.substring(1));
      assertEquals(1, loaded.all().size());
      SpecificRules.Rule back = loaded.find(rule.id);
      assertNotNull(back);
      assertEquals(List.of(DIAMOND, NOTCH), back.members());
      assertEquals("{\"TargetRange\":64.0}", back.body.read());
      assertFalse(back.enabled);
      assertTrue(back.mirror);

      loaded.load("not json");
      assertTrue(loaded.isEmpty());
   }

   @Test
   void linkCopiesWhicheverSideChangedWithoutPingPong() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(List.of(DIAMOND));
      SpecificRules.Rule right = tracers.put(left);
      left.mirror = right.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      right.body.write("{\"TargetColor\":2}");
      assertEquals(SpecificRules.LEFT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertEquals("{\"TargetColor\":2}", esp.find(left.id).body.read());
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));

      esp.find(left.id).enabled = false;
      assertEquals(SpecificRules.RIGHT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertFalse(tracers.find(left.id).enabled);
      assertTrue(tracers.find(left.id).mirror);
      assertEquals(0, SpecificRules.sync(esp, tracers, agreed));
   }

   @Test
   void bothSidesChangedLeftWins() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(List.of(NOTCH));
      SpecificRules.Rule right = tracers.put(left);
      left.mirror = right.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      SpecificRules.sync(esp, tracers, agreed);
      left.body.write("{\"TargetColor\":5}");
      right.body.write("{\"TargetColor\":9}");
      SpecificRules.sync(esp, tracers, agreed);
      assertEquals("{\"TargetColor\":5}", tracers.find(left.id).body.read());
   }

   @Test
   void halfLinkIsBrokenNotRestored() {
      SpecificRules esp = new SpecificRules();
      SpecificRules tracers = new SpecificRules();
      SpecificRules.Rule left = esp.add(List.of(DIAMOND));
      left.mirror = true;
      Map<String, String> agreed = new HashMap<>();
      assertEquals(SpecificRules.LEFT_CHANGED, SpecificRules.sync(esp, tracers, agreed));
      assertFalse(left.mirror);
      assertNull(tracers.find(left.id));
      assertTrue(agreed.isEmpty());
   }
}

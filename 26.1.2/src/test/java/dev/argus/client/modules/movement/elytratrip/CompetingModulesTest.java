package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompetingModulesTest {
   private final Map<String, Boolean> on = new HashMap<>(Map.of("AutoEat", true, "Offhand", true, "AutoArmor", false));
   private int switches;

   private CompetingModules modules() {
      return new CompetingModules(name -> on.getOrDefault(name, false), (name, value) -> {
         switches++;
         on.put(name, value);
      });
   }

   @Test
   void onlyOnModulesAreTurnedOff() {
      CompetingModules modules = modules();
      assertEquals(List.of("AutoEat", "Offhand"), modules.switchOff(List.of("AutoEat", "Offhand", "AutoArmor", "Missing")));
      assertFalse(on.get("AutoEat"));
      assertFalse(on.get("Offhand"));
      assertEquals(List.of("AutoEat", "Offhand"), modules.turnedOff());
   }

   @Test
   void restoreTurnsBackExactlyThose() {
      CompetingModules modules = modules();
      modules.switchOff(List.of("AutoEat", "Offhand", "AutoArmor"));
      modules.restore();
      assertTrue(on.get("AutoEat"));
      assertTrue(on.get("Offhand"));
      assertFalse(on.get("AutoArmor"));
      assertTrue(modules.turnedOff().isEmpty());
   }

   @Test
   void restoreIsIdempotent() {
      CompetingModules modules = modules();
      modules.switchOff(List.of("AutoEat"));
      modules.restore();
      int after = switches;
      modules.restore();
      assertEquals(after, switches);
   }

   @Test
   void restoreListSurvivesSaveAndLoad() {
      CompetingModules first = modules();
      List<String> saved = first.switchOff(List.of("AutoEat", "Offhand"));
      CompetingModules second = modules();
      second.load(saved);
      second.restore();
      assertTrue(on.get("AutoEat"));
      assertTrue(on.get("Offhand"));
   }

   @Test
   void moduleTheUserTurnedOnDuringTripIsLeftOn() {
      CompetingModules modules = modules();
      modules.switchOff(List.of("AutoEat", "AutoArmor"));
      on.put("AutoArmor", true);
      int before = switches;
      modules.restore();
      assertTrue(on.get("AutoArmor"));
      assertTrue(on.get("AutoEat"));
      assertEquals(before + 1, switches);
   }

   @Test
   void alreadyOnAtRestoreIsNotToggled() {
      CompetingModules modules = modules();
      modules.switchOff(List.of("AutoEat"));
      on.put("AutoEat", true);
      int before = switches;
      modules.restore();
      assertEquals(before, switches);
   }

   @Test
   void alreadyListedButOnIsTurnedOffAgain() {
      CompetingModules modules = modules();
      modules.load(List.of("AutoEat"));
      modules.switchOff(List.of("AutoEat"));
      assertFalse(on.get("AutoEat"));
      assertEquals(List.of("AutoEat"), modules.turnedOff());
   }

   @Test
   void restoreExceptKeepsThoseForLater() {
      CompetingModules modules = modules();
      on.put("AutoReconnect", true);
      modules.switchOff(List.of("AutoEat", "AutoReconnect"));
      modules.restoreExcept(java.util.Set.of("AutoReconnect"));
      assertTrue(on.get("AutoEat"));
      assertFalse(on.get("AutoReconnect"));
      assertEquals(List.of("AutoReconnect"), modules.turnedOff());
      modules.restore();
      assertTrue(on.get("AutoReconnect"));
   }
}

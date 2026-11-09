package dev.argus.client.modules.combat;

import java.util.function.Predicate;

public class TriggerHostilesVisibilityPredicate implements Predicate<Object> {
   private TriggerModule trigger;

   public TriggerHostilesVisibilityPredicate(TriggerModule trigger) {
      this.trigger = trigger;
   }

   @Override
   public boolean test(Object ignoredValue) {
      return this.trigger.targetsSetting.isExpanded();
   }
}

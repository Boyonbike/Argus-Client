package dev.argus.client.modules.combat;

import java.util.function.Predicate;

public class TriggerAnimalsVisibilityPredicate implements Predicate<Object> {
   private TriggerModule trigger;

   public TriggerAnimalsVisibilityPredicate(TriggerModule trigger) {
      this.trigger = trigger;
   }

   @Override
   public boolean test(Object ignoredValue) {
      return this.trigger.targetsSetting.isExpanded();
   }
}

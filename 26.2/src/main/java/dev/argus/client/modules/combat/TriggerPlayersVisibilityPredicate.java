package dev.argus.client.modules.combat;

import java.util.function.Predicate;

public class TriggerPlayersVisibilityPredicate implements Predicate<Object> {
   private TriggerModule trigger;

   public TriggerPlayersVisibilityPredicate(TriggerModule trigger) {
      this.trigger = trigger;
   }

   @Override
   public boolean test(Object ignoredValue) {
      return this.trigger.targetsSetting.isExpanded();
   }
}

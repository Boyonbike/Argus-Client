package dev.argus.client.modules.combat;

import java.util.function.Predicate;

public class TriggerIgnoreNakedsVisibilityPredicate implements Predicate<Object> {
   private TriggerModule trigger;

   public TriggerIgnoreNakedsVisibilityPredicate(TriggerModule trigger) {
      this.trigger = trigger;
   }

   @Override
   public boolean test(Object ignoredValue) {
      return !this.trigger.targetsSetting.isExpanded() ? false : this.trigger.playersSetting.isExpanded();
   }
}

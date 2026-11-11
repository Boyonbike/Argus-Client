package dev.argus.client.utils.entity;

import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.Setting;

public enum SwingAction {
   INTERACT,
   MINING,
   HIT;

   public boolean isEnabled(AntiCheatModule antiCheat) {
      Setting<Boolean> swingSetting = antiCheat.swingSetting;
      Boolean swingEnabled = swingSetting.getValue();
      if (!swingEnabled) {
         return false;
      } else if (this == MINING) {
         Setting<Boolean> miningSetting = antiCheat.miningSwingSetting;
         Boolean miningEnabled = miningSetting.getValue();
         return miningEnabled;
      } else if (this == HIT) {
         Setting<Boolean> hitSetting = antiCheat.hitSetting;
         Boolean hitEnabled = hitSetting.getValue();
         return hitEnabled;
      } else {
         Setting<Boolean> placeSetting = antiCheat.placeSetting;
         Boolean placeEnabled = placeSetting.getValue();
         return placeEnabled && this == INTERACT;
      }
   }
}

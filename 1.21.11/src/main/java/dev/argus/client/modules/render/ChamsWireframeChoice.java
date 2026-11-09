package dev.argus.client.modules.render;

import dev.argus.client.render.ChamsColorProvider;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import java.awt.Color;

public enum ChamsWireframeChoice implements ChamsColorProvider, NamedEntry {
   BOTH("Both") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         Color[] colors = new Color[2];
         Setting<Color> lineSettingSnapshot = chams.popLineSetting;
         colors[0] = lineSettingSnapshot.getValue();
         Setting<Color> fillSettingSnapshot = chams.popFillSetting;
         colors[1] = fillSettingSnapshot.getValue();
         return colors;
      }
   },
   FILL("Fill") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         Color[] colors = new Color[]{TRANSPARENT_COLOR, null};
         Setting<Color> fillSettingSnapshot = chams.popFillSetting;
         colors[1] = fillSettingSnapshot.getValue();
         return colors;
      }
   },
   LINE("Line") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         Color[] colors = new Color[2];
         Setting<Color> lineSettingSnapshot = chams.popLineSetting;
         colors[0] = lineSettingSnapshot.getValue();
         colors[1] = TRANSPARENT_COLOR;
         return colors;
      }
   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChamsWireframeChoice(String displayName) {
      this.displayName = displayName;
   }
}

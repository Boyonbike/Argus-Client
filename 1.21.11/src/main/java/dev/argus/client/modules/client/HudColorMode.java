package dev.argus.client.modules.client;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import java.awt.Color;

public enum HudColorMode implements NamedEntry {
   NONE("None") {
      @Override
      public Color getColor(HUDModule hud, float position) {
         ColorSetting colorSetting = (ColorSetting)hud.colorSetting;
         Color color = colorSetting.getValue();
         if (!colorSetting.isRainbow()) {
            return color;
         }

         int offset = (int)(position * hud.fadeOffsetSetting.getValue().intValue() * HudColorMode.getPositionScale());
         return colorSetting.applyRainbow(color, offset);
      }
   },
   PULSE("Pulse") {
      @Override
      public Color getColor(HUDModule hud, float position) {
         Setting<Color> firstColorSetting = hud.colorSetting;
         Color firstColor = firstColorSetting.getValue();
         Setting<Color> secondColorSetting = hud.secondColorSetting;
         Color secondColor = secondColorSetting.getValue();
         Setting<Integer> fadeOffsetSetting = hud.fadeOffsetSetting;
         Integer fadeOffset = fadeOffsetSetting.getValue();
         float positionOffset = position * fadeOffset.intValue();
         return ColorUtils.pulse(firstColor, secondColor, 2000.0, positionOffset * HudColorMode.getPositionScale());
      }
   },
   RAINBOW("Rainbow") {
      private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);

      @Override
      public Color getColor(HUDModule hud, float position) {
         Setting<Color> baseColorSetting = hud.colorSetting;
         Color baseColor = baseColorSetting.getValue();
         float[] hsb = Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
         position *= 5.0F;
         float rainbowTimeScale = 360.0F;
         int rainbowPeriod = (int)(rainbowTimeScale * colorsModule.getRainbowPeriod());
         Setting<Integer> fadeOffsetSetting = hud.fadeOffsetSetting;
         Integer fadeOffset = fadeOffsetSetting.getValue();
         float positionOffset = position * fadeOffset.intValue();
         int rainbowOffset = (int)(positionOffset * HudColorMode.getPositionScale());
         float saturation = hsb[1];
         float brightness = hsb[2];
         return ColorUtils.rainbow(rainbowPeriod, rainbowOffset, saturation, brightness, 255);
      }

      @Override
      public void bindColorsModule(ModuleRegistry registry) {
         colorsModule = (ColorsModule)registry.getModule(ColorsModule.class);
      }
   },
   RAW("Raw") {
      @Override
      public Color getColor(HUDModule hud, float position) {
         Setting<Integer> colorAmountSetting = hud.colorAmountSetting;
         Integer colorAmountValue = colorAmountSetting.getValue();
         int colorAmount = Math.max(1, Math.min(colorAmountValue, hud.rawColorSettings.size()));
         Setting<Integer> fadeOffsetSetting = hud.fadeOffsetSetting;
         Integer fadeOffsetValue = fadeOffsetSetting.getValue();
         int fadeOffset = Math.max(1, fadeOffsetValue);
         float inverseFadeOffset = 1.0F / fadeOffset;
         long currentTime = System.currentTimeMillis();
         Setting<Integer> fadeSpeedSetting = hud.fadeSpeedSetting;
         int fadeSpeed = Math.max(1, fadeSpeedSetting.getValue());
         float timePhase = (float)(currentTime / fadeSpeed % (colorAmount * fadeOffset));
         int phase = (int)(timePhase + position / HudColorMode.getPositionScale());
         double palettePosition = phase * inverseFadeOffset;
         int colorIndex = (int)Math.floor(palettePosition);
         if (colorIndex < 0) {
            colorIndex = 0;
         }

         Setting<Boolean> mixColorsSetting = hud.mixColorsSetting;
         Boolean mixColors = mixColorsSetting.getValue();
         if (mixColors) {
            Setting<Color> firstColorSetting = hud.rawColorSettings.get(colorIndex % colorAmount);
            Color firstColor = firstColorSetting.getValue();
            Setting<Color> secondColorSetting = hud.rawColorSettings.get((colorIndex + 1) % colorAmount);
            return ColorUtils.blend(firstColor, secondColorSetting.getValue(), 1.0F - (phase * inverseFadeOffset - colorIndex));
         } else {
            Setting<Color> selectedColorSetting = hud.rawColorSettings.get(colorIndex % colorAmount);
            return selectedColorSetting.getValue();
         }
      }
   };

   private final String displayName;

   private static float getPositionScale() {
      return 10.0F / (FontRenderer.INSTANCE.getHeight() + 2);
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   HudColorMode(String displayName) {
      this.displayName = displayName;
   }

   public abstract Color getColor(HUDModule var1, float var2);

   public void bindColorsModule(ModuleRegistry registry) {
      throw new IllegalStateException("This enum constant has no captured module binding.");
   }
}

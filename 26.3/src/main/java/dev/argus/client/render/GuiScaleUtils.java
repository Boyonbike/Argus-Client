package dev.argus.client.render;

import com.mojang.blaze3d.platform.Window;
import dev.argus.client.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class GuiScaleUtils implements ClientAccess {
   private static final boolean ADVANCED_UI_SCALE_LOADED = FabricLoader.getInstance().isModLoaded("advanced-ui-scale");
   private static double previousGuiScale = -1.0;
   private static double scaleMultiplier = 1.0;

   public static int getBaseScaledHeight() {
      Window window = argusclientMinecraft.getWindow();
      return ADVANCED_UI_SCALE_LOADED ? window.getGuiScaledHeight() : (int)Math.ceil((double)window.getHeight() / calculateGuiScale());
   }

   public static int getScaledHeight() {
      return (int)(argusclientMinecraft.getWindow().getGuiScaledHeight() / scaleMultiplier + 1.0);
   }

   public static int getScaledWidth() {
      return (int)(argusclientMinecraft.getWindow().getGuiScaledWidth() / scaleMultiplier + 1.0);
   }

   public static void restoreGuiScale() {
      if (!(previousGuiScale < 0.0)) {
         argusclientMinecraft.getWindow().setGuiScale((int)previousGuiScale);
         previousGuiScale = -1.0;
         scaleMultiplier = 1.0;
      }
   }

   public static boolean isAdvancedUiScaleLoaded() {
      return ADVANCED_UI_SCALE_LOADED;
   }

   public static double getScaleMultiplier() {
      return scaleMultiplier;
   }

   public static void applyDefaultGuiScale() {
      applyGuiScale(1.0F);
   }

   public static int calculateGuiScale() {
      Minecraft minecraft = argusclientMinecraft;
      return minecraft.getWindow().calculateScale(2, minecraft.isEnforceUnicode());
   }

   public static void applyGuiScale(float scale) {
      if (!ADVANCED_UI_SCALE_LOADED) {
         Window window = argusclientMinecraft.getWindow();
         if (previousGuiScale < 0.0) {
            previousGuiScale = window.getGuiScale();
         }

         scaleMultiplier = scale;
         window.setGuiScale(calculateGuiScale());
      }
   }

   public static double calculateScaleMultiplier(float scale) {
      return ADVANCED_UI_SCALE_LOADED ? 1.0 : (double)calculateGuiScale() / argusclientMinecraft.getWindow().getGuiScale() * scale;
   }

   private GuiScaleUtils() {
   }
}

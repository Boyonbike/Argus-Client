package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DirectionModule extends HudWidgetModule {
   private final Setting<Boolean> preciseSetting;
   private final Setting<Boolean> prefixSetting;

   private String formatYawPitch() {
      Camera cameraForYaw = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
      float cameraYaw = cameraForYaw.yRot();
      float wrappedYaw = Mth.wrapDegrees(cameraYaw);
      Camera cameraForPitch = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
      float pitch = cameraForPitch.xRot();
      Setting<Boolean> preciseOption = this.preciseSetting;
      Boolean precise = preciseOption.getValue();
      if (precise) {
         return "%.1f, %.1f".formatted(wrappedYaw, pitch);
      }

      int integerYaw = (int)wrappedYaw;
      return String.valueOf(integerYaw);
   }

   private String formatDirection() {
      GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = gameRenderer.mainCamera();
      float cameraYawCapture = camera.yRot();
      int sector = RotationManager.getYawSector(cameraYawCapture);
      String prefix = this.getCardinalPrefix(sector);
      ChatFormatting detailsColor = ChatFormatting.WHITE;
      String detailsColorCode = String.valueOf(detailsColor);
      String detailsText = "%s, %s".formatted(this.formatYawPitch(), this.getAxisLabel(sector));
      String details = detailsColorCode + detailsText;
      if (!prefix.isEmpty()) {
         ChatFormatting parenthesesColor = ChatFormatting.WHITE;
         String parenthesesColorCode = String.valueOf(parenthesesColor);
         String parenthesizedDetails = " (" + details + ")";
         details = parenthesesColorCode + parenthesizedDetails;
      }

      return prefix + details;
   }

   private String getCardinalPrefix(int sector) {
      Setting<Boolean> prefixOption = this.prefixSetting;
      Boolean showPrefix = prefixOption.getValue();
      if (!showPrefix) {
         return "";
      }

      return switch (sector) {
         case 0 -> "South";
         case 1 -> "SouthWest";
         case 2 -> "West";
         case 3 -> "NorthWest";
         case 4 -> "North";
         case 5 -> "NorthEast";
         case 6 -> "East";
         case 7 -> "SouthEast";
         default -> throw new IllegalStateException("Invalid index.");
      };
   }

   private String getAxisLabel(int sector) {
      return switch (sector) {
         case 0 -> "+Z";
         case 1 -> "-X +Z";
         case 2 -> "-X";
         case 3 -> "-X -Z";
         case 4 -> "-Z";
         case 5 -> "+X -Z";
         case 6 -> "+X";
         case 7 -> "+X +Z";
         default -> "Waiting...";
      };
   }

   public DirectionModule() {
      super("Direction", "facing");
      boolean defaultPrecise = false;
      this.preciseSetting = this.add(new BooleanSetting("Precise", defaultPrecise));
      boolean defaultPrefix = true;
      this.prefixSetting = this.add(new BooleanSetting("Prefix", defaultPrefix));
      this.setRenderer(new HudTextListRenderer(this, new HudTextRow(() -> {
         String directionText = this.formatDirection();
         return Component.literal(directionText);
      }, () -> true)));
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_LEFT);
   }
}

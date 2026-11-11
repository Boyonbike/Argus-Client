package dev.argus.client.modules.hud;

import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.settings.options.HudAnchor;
import java.time.LocalDate;
import java.time.Month;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class WatermarkModule extends HudWidgetModule {
   private static boolean aprilFoolsDay = false;
   public final Setting<Boolean> versionColorSetting = this.add(new BooleanSetting("VersionColor", true));
   private final Setting<String> textSetting = this.add(new StringSetting("Text", ArgusClient.NAME));

   private String getVersionSuffix() {
      return " v" + ClientAccess.getVersion();
   }

   public String getVersionText() {
      String version = ClientAccess.getVersion();
      return "v" + version;
   }

   private Component buildWatermarkText() {
      if (aprilFoolsDay) {
         String aprilFoolsText = ArgusClient.NAME + " v" + ArgusClient.VERSION;
         return Component.literal(aprilFoolsText);
      }

      String versionSuffix = this.getVersionSuffix();
      MutableComponent versionComponent = Component.literal(versionSuffix);
      Setting<Boolean> versionColorOption = this.versionColorSetting;
      Boolean useWhiteVersion = versionColorOption.getValue();
      if (useWhiteVersion) {
         ChatFormatting versionColor = ChatFormatting.WHITE;
         versionComponent.withStyle(versionColor);
      }

      String configuredText = this.textSetting.getValue();
      String trimmedText = configuredText.trim();
      MutableComponent watermark = Component.literal(trimmedText);
      return watermark.append(versionComponent);
   }

   public WatermarkModule() {
      super("Watermark");
      HudTextRow textRow = new HudTextRow(this::buildWatermarkText, () -> true);
      this.setRenderer(new HudTextListRenderer(this, textRow));
      this.getRenderer().setAnchor(HudAnchor.TOP_LEFT);
      this.setToggled(true);
   }

   static {
      LocalDate today = LocalDate.now();
      aprilFoolsDay = today.getMonth() == Month.APRIL && today.getDayOfMonth() == 1;
   }
}

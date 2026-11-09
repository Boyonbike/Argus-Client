package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.regex.Matcher;

public class MusicModule extends HudWidgetModule {
   private final Setting<Boolean> prefixSetting = this.add(new BooleanSetting("Prefix", false).markVisibilityDependency());
   private final Setting<String> prefixTextSetting = this.add(new StringSetting("Value", "♪").dependsOn(this.prefixSetting));

   private boolean shouldSkipMediaRendering() {
      if (!this.isInHudEditor()) {
         int playbackStatus = MediaSessionBridge.playbackStatus;
         return playbackStatus != 4;
      } else {
         return false;
      }
   }

   @Override
   public void onEnable() {
   }

   @Override
   public float[] calculateDimensions() {
      if (!this.shouldSkipMediaRendering()) {
         Class<WelcomerModule> welcomerType = WelcomerModule.class;
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
         WelcomerModule welcomer = (WelcomerModule)moduleRegistry.getModule(welcomerType);
         if (!welcomer.shouldHideWidget(this)) {
            float[] dimensions = new float[2];
            FontRenderer textRenderer = FontRenderer.INSTANCE;
            dimensions[0] = textRenderer.getWidth(this.getMusicText());
            dimensions[1] = FontRenderer.INSTANCE.getHeight();
            return dimensions;
         }
      }

      return new float[]{0.0F, 0.0F};
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (!this.shouldSkipMediaRendering()) {
         Class<WelcomerModule> welcomerType = WelcomerModule.class;
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
         WelcomerModule welcomer = (WelcomerModule)moduleRegistry.getModule(welcomerType);
         if (!welcomer.shouldHideWidget(this)) {
            FontRenderer.INSTANCE.drawWithShadow(poses, this.getMusicText(), 0.0F, 0.0F, this.getHudColor(this.renderer.getY()));
         }
      }
   }

   private String getMusicText() {
      String text = MediaSessionBridge.getDisplayText();
      if (this.isInHudEditor()) {
         int playbackStatus = MediaSessionBridge.playbackStatus;
         if (playbackStatus != 4) {
            text = "HydrachFM - f3dot";
         }
      }

      Setting<Boolean> prefixOption = this.prefixSetting;
      Boolean prefixEnabled = prefixOption.getValue();
      if (prefixEnabled) {
         Setting<String> prefixTextOption = this.prefixTextSetting;
         String musicText = String.valueOf(text);
         String prefixText = String.valueOf(prefixTextOption.getValue());
         text = "\u0001 \u0001".replaceFirst("\u0001", Matcher.quoteReplacement(prefixText)).replaceFirst("\u0001", Matcher.quoteReplacement(musicText));
      }

      return text.trim();
   }

   public MusicModule() {
      super("Music");
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.enableHudScale();
      this.setRenderer(widget);
   }
}

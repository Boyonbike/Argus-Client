package dev.argus.client.modules.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FloatAnimation;
import dev.argus.client.utils.InterpolatedFloat;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

public final class ChatInputOverlay implements ClientAccess {
   private final FloatAnimation commandHighlightAnimation = new FloatAnimation();
   private final HUDModule hudModule;

   private static boolean isCommandInput() {
      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ChatScreen chatScreen) {
         EditBox input = chatScreen.input;
         String inputText = input.getValue();
         String prefix = CommandRegistry.getPrefix();
         return inputText.startsWith(prefix);
      } else {
         return false;
      }
   }

   ChatInputOverlay(HUDModule hudModule) {
      this.hudModule = hudModule;
   }

   @EventHandler
   private void onHudRender(HudRenderEvent.Unconditional event) {
      FloatAnimation animation = this.commandHighlightAnimation;
      boolean commandInput = isCommandInput();
      animation.setTarget(commandInput, 250L);
      animation = this.commandHighlightAnimation;
      float animationValue = animation.getValue();
      float highlightAlpha = animationValue * 1.5F;
      InterpolatedFloat heightAnimation = this.hudModule.chatInputHeight;
      float chatInputHeight = heightAnimation.getValue();
      Minecraft widthMinecraft = ClientAccess.argusclientMinecraft;
      Window widthWindow = widthMinecraft.getWindow();
      float screenWidth = widthWindow.getGuiScaledWidth();
      Minecraft heightMinecraft = ClientAccess.argusclientMinecraft;
      Window heightWindow = heightMinecraft.getWindow();
      float screenHeight = heightWindow.getGuiScaledHeight();
      Options options = ClientAccess.argusclientMinecraft.options;
      int backgroundArgb = options.getBackgroundColor(Integer.MIN_VALUE);
      Color backgroundColor = new Color(backgroundArgb, true);
      backgroundColor = ColorUtils.multiplyAlpha(backgroundColor, chatInputHeight / 12.0F);
      PoseStack backgroundPose = event.getPoseStack();
      float backgroundTop = screenHeight - 2.0F - chatInputHeight;
      float backgroundRight = screenWidth - 2.0F;
      GuiDrawUtils.fillRect(backgroundPose, 2.0F, backgroundTop, backgroundRight, screenHeight - 2.0F, backgroundColor);
      if (highlightAlpha > 0.0F) {
         Setting<Color> outlineColorSetting = UIModule.INSTANCE.colorSetting;
         Color outlineColor = ColorUtils.multiplyAlpha(outlineColorSetting.getValue(), highlightAlpha);
         PoseStack outlinePose = event.getPoseStack();
         float outlineTop = screenHeight - 3.0F - chatInputHeight;
         float outlineRight = screenWidth - 2.0F;
         GuiDrawUtils.drawRectOutline(outlinePose, 1.0F, outlineTop, outlineRight, screenHeight - 2.0F, outlineColor);
      }
   }
}

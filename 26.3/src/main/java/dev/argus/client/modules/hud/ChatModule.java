package dev.argus.client.modules.hud;

import com.mojang.blaze3d.platform.Window;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.ChatComponent;

public class ChatModule extends HudWidgetModule {
   public float getChatOffsetY() {
      if (this.renderer.getDimensions() == null) {
         return 0.0F;
      }

      Window window = ClientAccess.argusclientMinecraft.getWindow();
      float screenHeight = window.getGuiScaledHeight();
      float scale = this.getChatScale();
      float baselineY = screenHeight - 40.0F;
      float unscaledOffsetY = (this.renderer.getY() - baselineY) / scale;
      return unscaledOffsetY + this.getUnfocusedChatHeight();
   }

   private float getChatScale() {
      Options options = ClientAccess.argusclientMinecraft.options;
      OptionInstance chatScale = options.chatScale();
      Double scale = (Double)chatScale.get();
      return scale.floatValue();
   }

   public float getChatOffsetX() {
      if (this.renderer.getDimensions() == null) {
         return 0.0F;
      }

      float scale = this.getChatScale();
      return this.renderer.getX() / scale;
   }

   public ChatModule() {
      super("Chat");
      ResizableHudWidgetRenderer renderer = new ResizableHudWidgetRenderer(this);
      this.setRenderer(renderer);
   }

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      Options options = ClientAccess.argusclientMinecraft.options;
      OptionInstance chatWidth = options.chatWidth();
      Double widthOption = (Double)chatWidth.get();
      float sizeValue = widthOption.floatValue();
      sizeValue *= 280.0F;
      sizeValue += 40.0F;
      float scalePadding = 12.0F;
      dimensions[0] = sizeValue + scalePadding * this.getChatScale();
      sizeValue = this.getUnfocusedChatHeight();
      dimensions[1] = sizeValue * this.getChatScale();
      return dimensions;
   }

   private float getUnfocusedChatHeight() {
      Options options = ClientAccess.argusclientMinecraft.options;
      OptionInstance unfocusedHeight = options.chatHeightUnfocused();
      Double heightOption = (Double)unfocusedHeight.get();
      double roundedHeight = heightOption.floatValue();
      return ChatComponent.getHeight(roundedHeight);
   }
}

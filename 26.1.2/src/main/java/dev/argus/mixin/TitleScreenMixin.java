package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.commands.PanicCommand;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.modules.hud.WatermarkModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
   private static WatermarkModule watermarkModule = ClientContext.getModule(WatermarkModule.class);
   private static final HUDModule hudModule = ClientContext.getModule(HUDModule.class);
   private static final UIModule uiModule = ClientContext.getModule(UIModule.class);

   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void renderTitleWatermark(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback, @Local(ordinal = 1) float opacity) {
      if (!PanicCommand.unloaded) {
         Minecraft client = ClientAccess.argusclientMinecraft;
         int hudColor = hudModule.fadeSetting.getValue().getColor(hudModule, 0.0F).getRGB();
         String versionText = ChatFormatting.WHITE + watermarkModule.getVersionText();
         graphics.text(client.font, ArgusClient.NAME + " " + versionText, 2, 2, ARGB.color(opacity, hudColor));
      }
   }

   @Override
   public boolean keyPressed(KeyEvent keyEvent) {
      if (!uiModule.getKeybind().mouse() && uiModule.getKeybind().code() == keyEvent.key()) {
         uiModule.enable();
         ClientContext.getClickGui().open();
      }

      return super.keyPressed(keyEvent);
   }
}

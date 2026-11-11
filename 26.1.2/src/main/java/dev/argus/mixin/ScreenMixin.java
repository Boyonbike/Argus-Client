package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.render.BlurModule;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final BlurModule blurModule = ClientContext.getModule(BlurModule.class);
   @Shadow
   @Nullable
   protected Minecraft minecraft;

   @Shadow
   protected abstract void renderMenuBackground(GuiGraphicsExtractor var1);

   @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
   private static void dispatchClientCommandAction(LocalPlayer localPlayer, String command, Screen screen, CallbackInfo callback) {
      if (command.startsWith(CommandRegistry.getPrefix())) {
         localPlayer.connection.sendChat(command);
         callback.cancel();
      }
   }

   @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
   private void suppressScreenTint(GuiGraphicsExtractor graphics, CallbackInfo callback) {
      if (noRenderModule.shouldHideScreenTint()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "renderBackground",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;renderBlurredBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
         shift = Shift.BEFORE
      ),
      cancellable = true
   )
   private void replaceBlurredScreenBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
      if (blurModule.isToggled() && this.minecraft.level != null && this.minecraft.player != null) {
         this.renderMenuBackground(graphics);
         callback.cancel();
      }
   }
}

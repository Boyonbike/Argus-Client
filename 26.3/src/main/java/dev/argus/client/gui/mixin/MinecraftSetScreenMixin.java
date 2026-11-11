package dev.argus.client.gui.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.gui.GuiNavigation;
import dev.argus.client.gui.screens.ClickGuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen-change events and the legacy click-GUI safety net, now that Gui owns screens. */
@Mixin(Gui.class)
public abstract class MinecraftSetScreenMixin {
   @Shadow
   private Screen screen;
   @Unique
   private Screen previousScreen;

   @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
   private Screen argusclient$replaceLegacyGui(Screen next) {
      return next instanceof ClickGuiScreen ? GuiNavigation.replacementFor(this.screen) : next;
   }

   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void postBeforeScreenChange(Screen newScreen, CallbackInfo callback) {
      this.previousScreen = this.screen;
      ScreenChangeEvent.Before event = new ScreenChangeEvent.Before(this.previousScreen, newScreen);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "setScreen",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", shift = Shift.AFTER),
      cancellable = true
   )
   private void postAfterScreenInit(Screen newScreen, CallbackInfo callback) {
      ScreenChangeEvent.AfterInit event = new ScreenChangeEvent.AfterInit(this.previousScreen, newScreen);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }
}

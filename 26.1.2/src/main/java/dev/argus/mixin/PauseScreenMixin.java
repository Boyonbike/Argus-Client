package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.gui.screens.DisconnectConfirmationScreen;
import dev.argus.client.modules.exploit.IllegalDisconnectModule;
import dev.argus.client.modules.misc.AntiQuitModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
   private static final AntiQuitModule antiQuitModule = ClientContext.getModule(AntiQuitModule.class);
   private static final IllegalDisconnectModule illegalDisconnectModule = ClientContext.getModule(IllegalDisconnectModule.class);
   @Unique
   private static boolean disconnectConfirmed;

   protected PauseScreenMixin(Component title) {
      super(title);
   }

}

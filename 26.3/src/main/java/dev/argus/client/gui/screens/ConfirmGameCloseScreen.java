package dev.argus.client.gui.screens;

import dev.argus.client.ClientAccess;
import dev.argus.mixin.GuiAccessor;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class ConfirmGameCloseScreen extends ConfirmScreen {
   public ConfirmGameCloseScreen() {
      super(
         new ConfirmGameCloseScreen.CloseConfirmationHandler(),
         Component.literal("Confirm Game Close"),
         Component.literal("Are you sure that you want to close minecraft?")
      );
   }

   private static final class CloseConfirmationHandler implements BooleanConsumer, ClientAccess {
      public void accept(boolean confirmed) {
         if (!((GuiAccessor)ClientAccess.argusclientMinecraft.gui).argusclient$isDisconnecting()) {
            Minecraft client = ClientAccess.argusclientMinecraft;
            client.gui.setScreen(null);
         }

         if (confirmed) {
            Minecraft client = ClientAccess.argusclientMinecraft;
            client.close();
         } else {
            Minecraft client = ClientAccess.argusclientMinecraft;
            ((dev.argus.mixin.WindowMixin)(Object)client.getWindow()).argusclient$setShouldClose(false);
         }
      }
   }
}

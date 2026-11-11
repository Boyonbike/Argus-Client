package dev.argus.client.gui.screens;

import dev.argus.client.ClientAccess;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class DisconnectConfirmationScreen extends ConfirmScreen {
   public DisconnectConfirmationScreen(Runnable confirmationAction) {
      super(
         new DisconnectConfirmationScreen.ConfirmationCallback(confirmationAction),
         Component.literal("Confirm Disconnect"),
         Component.literal("Are you sure that you want to disconnect?")
      );
   }

   private record ConfirmationCallback(Runnable confirmationAction) implements BooleanConsumer, ClientAccess {
      public void accept(boolean confirmed) {
         ClientAccess.argusclientMinecraft.gui.setScreen(null);
         if (confirmed) {
            this.confirmationAction.run();
         }
      }
   }
}

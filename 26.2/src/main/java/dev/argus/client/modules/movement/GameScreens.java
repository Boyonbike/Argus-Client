package dev.argus.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Which open screens BaritoneElytra and ElytraTrip carry on under. On a server the game keeps running behind the Esc menu,
 * its option screens and chat, so a trip shouldn't stop eating, swapping elytras or checkpointing just because one is up.
 * Minecraft skips its key handling while any screen is open, though, so anything done under one has to act directly
 * rather than by pressing a key.
 */
public final class GameScreens {
   private GameScreens() {
   }

   /** No screen, or one the game keeps playing under: anything but an inventory, a container, death or a disconnect. */
   public static boolean playing(Minecraft mc) {
      Screen screen = mc.gui.screen();
      return screen == null || !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof DeathScreen)
         && !(screen instanceof DisconnectedScreen);
   }

   /** Whether the player's own inventory can be clicked: {@link #playing}, its menu is the one open, and nothing is on the cursor. */
   public static boolean inventoryFree(Minecraft mc, LocalPlayer player) {
      return playing(mc) && player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
   }
}

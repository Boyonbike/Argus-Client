package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.GameScreens;
import dev.argus.client.modules.movement.elytratrip.EatDecision.Choice;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ElytraTrip's own eating and offhand totem, with its own state; nothing is shared with AutoEat or Offhand. */
public final class TripSurvival {
   private static volatile boolean eating;

   private final Minecraft mc = Minecraft.getInstance();
   private final IntSupplier eatAtHealth;
   private final IntSupplier eatAtHunger;
   /** Inventory-menu slot swapped into the hotbar to eat from, swapped back when done; -1 for none. */
   private int swappedFrom = -1;
   private Choice eatingChoice = Choice.NONE;

   public TripSurvival(IntSupplier eatAtHealth, IntSupplier eatAtHunger) {
      this.eatAtHealth = eatAtHealth;
      this.eatAtHunger = eatAtHunger;
   }

   /** Read by the use-key mixin so the client doesn't cancel the eat for want of a held key. */
   public static boolean isEating() {
      return eating;
   }

   public boolean eating() {
      return eating;
   }

   public void tick(LocalPlayer player) {
      // The Esc menu and chat don't stop eating; an open inventory or container does.
      if (!GameScreens.inventoryFree(mc, player)) {
         if (eating) stop(player);
         return;
      }
      if (eating) {
         if (player.isUsingItem()) return;
         // The bite finished (or was cut short): eat again if still needed, else tidy up.
         if (choice(player) == eatingChoice && startEating(player, eatingChoice)) return;
         stop(player);
         return;
      }
      Choice choice = choice(player);
      if (choice != Choice.NONE) startEating(player, choice);
   }

   private Choice choice(LocalPlayer player) {
      boolean hasGapple = find(player, TripInventory::isGapple) >= 0;
      boolean hasFood = find(player, TripInventory::isFood) >= 0;
      int hunger = player.getFoodData().getFoodLevel();
      Choice choice = EatDecision.choose(player.getHealth(), hunger, eatAtHealth.getAsInt(), eatAtHunger.getAsInt(), hasGapple, hasFood);
      // Normal food can't be eaten on a full bar.
      if (choice == Choice.FOOD && hunger >= 20) choice = hasGapple && player.getHealth() <= eatAtHealth.getAsInt() ? Choice.GAPPLE : Choice.NONE;
      return choice;
   }

   private boolean startEating(LocalPlayer player, Choice choice) {
      Predicate<ItemStack> wanted = choice == Choice.GAPPLE ? TripInventory::isGapple : TripInventory::isFood;
      int selected = player.getInventory().getSelectedSlot();
      if (!wanted.test(player.getMainHandItem())) {
         int slot = find(player, wanted);
         if (slot < 0) return false;
         int menuSlot = InventoryUtils.toMenuSlot(slot);
         // Swap it into the held hotbar slot; swapped back once eating ends.
         InventoryUtils.clickSlot(menuSlot, selected, ContainerInput.SWAP);
         if (swappedFrom < 0) swappedFrom = menuSlot;
      }
      mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
      eating = true;
      eatingChoice = choice;
      return true;
   }

   private void stop(LocalPlayer player) {
      if (player.isUsingItem()) mc.gameMode.releaseUsingItem(player);
      if (swappedFrom >= 0 && player.containerMenu == player.inventoryMenu) {
         InventoryUtils.clickSlot(swappedFrom, player.getInventory().getSelectedSlot(), ContainerInput.SWAP);
      }
      swappedFrom = -1;
      eating = false;
      eatingChoice = Choice.NONE;
   }

   /** Stops eating at once, for a pause, refill or the trip ending. */
   public void cancel() {
      LocalPlayer player = mc.player;
      if (player != null && eating) {
         stop(player);
      } else {
         eating = false;
      }
   }

   /** Keeps a totem in the offhand; one click, not while an inventory or container is open. */
   public void ensureTotem(LocalPlayer player) {
      if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || !GameScreens.inventoryFree(mc, player)) return;
      int totem = TripInventory.totemMenuSlot(player);
      if (totem >= 0) InventoryUtils.swapSlotsWithPickup(totem, TripInventory.OFFHAND_SLOT);
   }

   /** Inventory index (0..35) of the first stack matching, hotbar first, or -1. */
   private static int find(LocalPlayer player, Predicate<ItemStack> wanted) {
      for (int i = 0; i < 36; i++) {
         if (wanted.test(player.getInventory().getItem(i))) return i;
      }
      return -1;
   }
}

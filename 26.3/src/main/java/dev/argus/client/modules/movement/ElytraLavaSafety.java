package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Lava on a Baritone elytra flight, for BaritoneElytra and ElytraTrip: drinks Fire Resistance before the route enters lava
 * or once in it, and with none, says when health is too low to risk lava so the route goes round it. Ported from Comet.
 */
public final class ElytraLavaSafety {
   private static final int LAVA_CHECK_TICKS = 10;
   /** A potion takes 32 ticks to drink; held a little longer in case the first tick is lost. */
   private static final int DRINK_TICKS = 45;
   private static final int DRINK_RETRY_TICKS = 200;

   private int drinkTicks;
   private int nextDrink;
   private int previousSlot = -1;
   private InventoryUtils.SwapSlot drinkSlot;
   private boolean avoidLava;

   /** A new trip: lava is allowed again until health says otherwise. */
   public void reset() {
      this.stopDrinking();
      this.nextDrink = 0;
      this.avoidLava = false;
   }

   /** True once health was too low to risk lava without Fire Resistance; the route then goes round lava for the trip. */
   public boolean avoidLava() {
      return this.avoidLava;
   }

   /** Once per tick of a flight. Returns true while drinking, when nothing else should use the hands. */
   public boolean tick(LocalPlayer player, List<? extends BlockPos> route, boolean flyThroughLava, boolean drink, double minHealth,
      Consumer<String> say) {
      if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
         this.stopDrinking();
         return false;
      }
      if (this.drinkTicks > 0) {
         if (--this.drinkTicks == 0) {
            this.stopDrinking();
         } else {
            ClientAccess.argusclientMinecraft.options.keyUse.setDown(true);
            this.keepDrinking(player);
         }
         return this.drinkTicks > 0;
      }
      boolean inLava = player.isInLava();
      if (!inLava && (player.tickCount % LAVA_CHECK_TICKS != 0 || !ElytraRouteCheck.lavaAhead(player, route))) {
         return false;
      }
      if (drink && player.tickCount >= this.nextDrink && this.startDrinking(player, say)) {
         return true;
      }
      if (!inLava && !this.avoidLava && flyThroughLava && minHealth > 0.0 && player.getHealth() <= minHealth) {
         this.avoidLava = true;
         say.accept("Health is too low to risk lava without Fire Resistance, so this trip now routes around it.");
      }
      return false;
   }

   private boolean startDrinking(LocalPlayer player, Consumer<String> say) {
      if (!GameScreens.inventoryFree(ClientAccess.argusclientMinecraft, player)) {
         return false;
      }
      this.nextDrink = player.tickCount + DRINK_RETRY_TICKS;
      InventoryUtils.SwapSlot slot = InventoryUtils.findSwapSlot(ElytraLavaSafety::isFireResistance, true);
      if (!slot.isFound()) {
         return false;
      }
      this.previousSlot = InventoryUtils.getSelectedSlot();
      this.drinkSlot = slot;
      InventoryUtils.swapTo(slot);
      this.drinkTicks = DRINK_TICKS;
      ClientAccess.argusclientMinecraft.options.keyUse.setDown(true);
      this.keepDrinking(player);
      say.accept("Drinking Fire Resistance before the lava.");
      return true;
   }

   /** Lets go of the use key and puts the hotbar back. Safe to call at any time. */
   public void stopDrinking() {
      if (this.drinkTicks == 0 && this.drinkSlot == null) {
         return;
      }
      ClientAccess.argusclientMinecraft.options.keyUse.setDown(false);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      // Under a screen Minecraft doesn't see the key let go, so the drink is stopped here.
      if (player != null && player.isUsingItem() && ClientAccess.argusclientMinecraft.gui.screen() != null
         && ClientAccess.argusclientMinecraft.gameMode != null) {
         ClientAccess.argusclientMinecraft.gameMode.releaseUsingItem(player);
      }
      this.drinkTicks = 0;
      if (this.drinkSlot != null && ClientAccess.argusclientMinecraft.player != null) {
         InventoryUtils.swapBack(this.previousSlot, this.drinkSlot);
      }
      this.drinkSlot = null;
      this.previousSlot = -1;
   }

   /**
    * Starts the drink by using the potion directly. Minecraft only acts on the held use key with no screen open, so under the
    * Esc menu the key alone would never start it; once started it carries on either way.
    */
   private void keepDrinking(LocalPlayer player) {
      var mc = ClientAccess.argusclientMinecraft;
      if (player.isUsingItem() || mc.gameMode == null || mc.gui.screen() == null || !isFireResistance(player.getMainHandItem())) {
         return;
      }
      mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
   }

   private static boolean isFireResistance(ItemStack stack) {
      if (!stack.is(Items.POTION)) {
         return false;
      }
      PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
      return contents != null && (contents.is(Potions.FIRE_RESISTANCE) || contents.is(Potions.LONG_FIRE_RESISTANCE));
   }
}

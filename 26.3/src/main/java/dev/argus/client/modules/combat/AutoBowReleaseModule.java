package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoBowReleaseModule extends Module {
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 5, 2, 20));
   private final Setting<Boolean> crossbowSetting = this.add(new BooleanSetting("Crossbow", false));
   private boolean releasedDuringUse;

   public AutoBowReleaseModule() {
      super("AutoBowRelease", "Shoots your bow automatically.", ModuleCategory.COMBAT, "bowspam");
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.isWorldUnavailable()) {
         ArrowsModule arrows = ClientContext.getModule(ArrowsModule.class);
         if (arrows == null || !arrows.isAutoShooting()) {
            if (this.hasArrowAccess() && ClientAccess.argusclientMinecraft.gui.screen() == null) {
               if (!this.crossbowSetting.getValue() || !this.fireFirstChargedCrossbow()) {
                  this.releaseBowIfReady();
               }
            }
         }
      }
   }

   private void releaseBowIfReady() {
      if (!ClientAccess.argusclientMinecraft.player.isUsingItem()) {
         KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
         if (!useKey.isDown()) {
            this.releasedDuringUse = false;
         }
      }

      if (this.hasArrowAccess()) {
         if (this.isBowReadyToRelease()) {
            ClientAccess.argusclientMinecraft.gameMode.releaseUsingItem(ClientAccess.argusclientMinecraft.player);
            KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
            if (useKey.isDown()) {
               InteractionHand usedHand = ClientAccess.argusclientMinecraft.player.getUsedItemHand();
               PacketSender.sendUseItem(usedHand);
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               PacketSender.sendSwing(swingHand);
            }

            this.releasedDuringUse = true;
         }
      }
   }

   private boolean fireFirstChargedCrossbow() {
      for (InteractionHand hand : InteractionHand.values()) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getItemInHand(hand);
         Item crossbowItem = Items.CROSSBOW;
         if (stack.is(crossbowItem) && CrossbowItem.isCharged(stack)) {
            PacketSender.sendUseItem(hand);
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            PacketSender.sendSwing(swingHand);
            return true;
         }
      }

      return false;
   }

   private boolean hasArrowAccess() {
      if (ClientAccess.argusclientMinecraft.player.isCreative()) {
         return true;
      }

      Predicate<ItemStack> arrowPredicate = candidate -> candidate.getItem() instanceof ArrowItem;
      return InventoryUtils.countItems(arrowPredicate) > 0;
   }

   private boolean isBowReadyToRelease() {
      int releaseTicks = this.getReleaseTicks();
      if (!ClientAccess.argusclientMinecraft.player.isUsingItem()) {
         return false;
      }

      int useTicks = ClientAccess.argusclientMinecraft.player.getTicksUsingItem();
      if (useTicks < releaseTicks) {
         return false;
      }

      ItemStack useStack = ClientAccess.argusclientMinecraft.player.getUseItem();
      Item bowItem = Items.BOW;
      return useStack.is(bowItem);
   }

   public int getReleaseTicks() {
      return this.delaySetting.getValue() + (this.releasedDuringUse ? 0 : 1);
   }

   public int getInitialReleaseTicks() {
      return this.delaySetting.getValue() + 1;
   }
}

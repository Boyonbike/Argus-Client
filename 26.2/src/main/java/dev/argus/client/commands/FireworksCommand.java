package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.InputOverrideManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class FireworksCommand extends Command {
   private static final FireworksModule fireworksModule = ClientContext.getModule(FireworksModule.class);
   private int launchTicksRemaining;

   public FireworksCommand() {
      super("fireworks");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private void useRocket() {
      int selectedSlot;
      int rocketSlot;
      boolean useInventorySwap;
      label16: {
         selectedSlot = InventoryUtils.getSelectedSlot();
         Item rocketItem = Items.FIREWORK_ROCKET;
         rocketSlot = InventoryUtils.findHotbarSlot(rocketItem);
         Item inventoryRocketItem = Items.FIREWORK_ROCKET;
         int inventoryRocketSlot = InventoryUtils.findMenuSlot(inventoryRocketItem);
         useInventorySwap = false;
         if (rocketSlot != -1) {
            LocalPlayer usingItemPlayer = ClientAccess.argusclientMinecraft.player;
            if (!usingItemPlayer.isUsingItem()) {
               break label16;
            }

            LocalPlayer usedHandPlayer = ClientAccess.argusclientMinecraft.player;
            if (usedHandPlayer.getUsedItemHand() != InteractionHand.MAIN_HAND) {
               break label16;
            }
         }

         rocketSlot = inventoryRocketSlot;
         useInventorySwap = true;
      }

      this.withTemporaryRocketSlot(selectedSlot, rocketSlot, useInventorySwap, () -> {
         ElapsedTimer rocketUseTimer = fireworksModule.rocketUseTimer;
         rocketUseTimer.reset();
         MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         InteractionHand useHand = InteractionHand.MAIN_HAND;
         gameMode.useItem(player, useHand);
         SwingInteractionManager swingManager = ClientContext.swingManager;
         InteractionHand swingHand = InteractionHand.MAIN_HAND;
         SwingAction swingAction = SwingAction.INTERACT;
         swingManager.swing(swingHand, swingAction);
      });
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      builder.then(Command.literal("launch").executes(context -> {
         this.launchTicksRemaining = 3;
         return 1;
      }));
   }

   private void withTemporaryRocketSlot(int selectedSlot, int rocketSlot, boolean useInventorySwap, Runnable useRocket) {
      if (rocketSlot != -1) {
         if (useInventorySwap) {
            int menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
            InventoryUtils.swapIntoSelectedSlot(menuSlot);
            useRocket.run();
            menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
            InventoryUtils.swapIntoSelectedSlot(menuSlot);
         } else {
            InventoryUtils.selectHotbarSlot(rocketSlot);
            useRocket.run();
            InventoryUtils.selectHotbarSlot(selectedSlot);
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
      if (this.launchTicksRemaining > 0) {
         this.launchTicksRemaining--;
         InputOverrideManager inputOverrides = ClientContext.inputOverrides;
         inputOverrides.requestJump(true);
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (!player.onGround()) {
            this.useRocket();
            this.launchTicksRemaining = 0;
         }
      }
   }
}

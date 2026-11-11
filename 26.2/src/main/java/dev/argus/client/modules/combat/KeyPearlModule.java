package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class KeyPearlModule extends Module {
   private static ElytraFlyModule elytrafly = ClientContext.getModule(ElytraFlyModule.class);
   private final Setting<Boolean> noEntityTraceSetting = this.add(new BooleanSetting("NoEntityTrace", true));
   private final Setting<Boolean> solidSetting = this.add(new BooleanSetting("Solid", true));
   private final Setting<Boolean> disableOnElytraSetting = this.add(new BooleanSetting("DisableOnElytra", false));
   private final Setting<Boolean> disableOnRidingSetting = this.add(new BooleanSetting("DisableOnRiding", false));
   private final Setting<Boolean> awaitSetting = this.add(new BooleanSetting("Await", false));

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         if (!this.awaitSetting.getValue() && PlayerStateUtils.isEnderPearlOnCooldown()) {
            this.disable();
            return;
         }

         boolean shouldDisable = false;
         if (elytrafly.isToggled() && this.disableOnElytraSetting.getValue() && !ClientAccess.argusclientMinecraft.player.onGround()) {
            shouldDisable = PlayerStateUtils.isWearingElytra(ClientAccess.argusclientMinecraft.player);
         }

         if (ClientAccess.argusclientMinecraft.player.isPassenger() && this.disableOnRidingSetting.getValue()) {
            shouldDisable = true;
         }

         if (this.disableOnElytraSetting.getValue()) {
            ElapsedTimer timerSnapshot = ClientContext.playerStateTracker.getFallFlyingTimer();
            if (!timerSnapshot.hasElapsed(150L)) {
               shouldDisable = true;
            }
         }

         if (shouldDisable) {
            this.disable();
         }
      }
   }

   private boolean isObstructingBlock(BlockPos position) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (!state.canBeReplaced()) {
         Block cobweb = Blocks.COBWEB;
         if (!state.is(cobweb)) {
            return true;
         }
      }

      return false;
   }

   private void swapPearlSlot(int slot, boolean inventorySwap) {
      if (inventorySwap) {
         InventoryUtils.swapIntoSelectedSlot(slot);
      } else {
         InventoryUtils.selectHotbarSlotForPlacement(slot);
      }
   }

   @EventHandler(priority = 100)
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         if (this.awaitSetting.getValue() && PlayerStateUtils.isEnderPearlOnCooldown()) {
            return;
         }

         HitResult hitResult = RaycastUtils.raycastWithEntityTrace(ClientAccess.argusclientMinecraft.player);
         Item pearlItemSnapshot = Items.ENDER_PEARL;
         int hotbarPearlSlot = InventoryUtils.findHotbarSlot(pearlItemSnapshot);
         Item inventoryPearlItem = Items.ENDER_PEARL;
         int inventoryPearlSlot = InventoryUtils.findMenuSlot(inventoryPearlItem);
         int previousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
         boolean entityTraceBlocked = false;
         if (this.noEntityTraceSetting.getValue() && hitResult instanceof EntityHitResult entityHit) {
            Entity hitEntity = entityHit.getEntity();
            if (!hitEntity.getBoundingBox().contains(ClientAccess.argusclientMinecraft.player.getEyePosition())) {
               entityTraceBlocked = true;
            }
         }

         if (inventoryPearlSlot != -1 && !entityTraceBlocked) {
            if (hitResult instanceof BlockHitResult blockHit) {
               BlockPos blockPositionSnapshot = blockHit.getBlockPos();
               if (this.isObstructingBlock(blockPositionSnapshot) && blockHit.getType() != Type.MISS && !this.solidSetting.getValue()) {
                  this.disable();
                  return;
               }
            }

            if (!OffhandModule.isRecentSwapWindowActive()) {
               boolean useInventorySwap = hotbarPearlSlot == -1;
               int selectedPearlSlot = useInventorySwap ? inventoryPearlSlot : hotbarPearlSlot;
               this.swapPearlSlot(selectedPearlSlot, useInventorySwap);
               MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer usePlayer = ClientAccess.argusclientMinecraft.player;
               InteractionHand useHand = InteractionHand.MAIN_HAND;
               gameMode.useItem(usePlayer, useHand);
               SwingInteractionManager swingManager = ClientContext.swingManager;
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               SwingAction swingMode = SwingAction.INTERACT;
               swingManager.swing(swingHand, swingMode);
               int previousPearlSlot = useInventorySwap ? inventoryPearlSlot : previousSlot;
               this.swapPearlSlot(previousPearlSlot, useInventorySwap);
               ClientContext.moduleRegistry
                  .getPlacementModules()
                  .stream()
                  .filter(placementModule -> placementModule.placementPearlSetting.getValue())
                  .forEach(Module::disable);
               this.disable();
            }
         } else {
            this.disable();
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      elytrafly = (ElytraFlyModule)registry.getModule(ElytraFlyModule.class);
   }

   public KeyPearlModule() {
      super("KeyPearl", "Throws an ender pearl.", ModuleCategory.COMBAT);
      this.setDrawn(false);
   }
}

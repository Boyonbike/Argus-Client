package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockBreakingState;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AutoToolModule extends Module {
   private static AntiCheatModule antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   private static NukerModule nukerModule = ClientContext.getModule(NukerModule.class);
   public final Setting<Float> blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 1.0F, 0.7F, 1.0F));
   private final Setting<Boolean> inventorySetting = this.add(new BooleanSetting("Inventory", false));
   private final Setting<Boolean> swapBackSetting = this.add(new BooleanSetting("SwapBack", false));
   public final Setting<Boolean> noBreakDelaySetting = this.add(new BooleanSetting("NoBreakDelay", false));
   private int swapBackSlot;
   private boolean swapBackPending;
   private boolean inventorySwapActive;

   public boolean isBlockDamageOverrideActive() {
      if (!this.isToggled()) {
         return false;
      }

      Setting<Float> blockDamage = this.blockDamageSetting;
      return !blockDamage.isAtMaximum();
   }

   @EventHandler
   private void onMultiPlayerGameModeStartDestroyBlockEvent(MultiPlayerGameModeStartDestroyBlockEvent event) {
      if (nukerModule.isToggled()) {
         NukerModule activeNuker = nukerModule;
         BlockPos nukerPos = event.getPos();
         if (activeNuker.canMineBlock(nukerPos)) {
            return;
         }
      }

      if (this.isBlockDamageOverrideActive()) {
         this.onLocalPlayerPreTickEvent(null);
         BlockPos blockPos = event.getPos();
         Direction direction = event.getDirection();
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockState blockState = level.getBlockState(blockPos);
         ItemStack heldItem = ClientAccess.argusclientMinecraft.player.getMainHandItem();
         boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
         double blockDamage = WorldUtils.getBreakProgress(heldItem, blockState, onGround);
         Setting<Float> blockDamageSetting = this.blockDamageSetting;
         Float threshold = blockDamageSetting.getValue();
         if (!(blockDamage < threshold.floatValue())) {
            if (!event.isCancelled()) {
               if (WorldUtils.canMineBlock(blockPos)) {
                  Action startAction = Action.START_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(startAction, blockPos, direction);
                  Action stopAction = Action.STOP_DESTROY_BLOCK;
                  PacketSender.sendPlayerAction(stopAction, blockPos, direction);
                  if (antiCheatModule.is2b2tEnabled()) {
                     Action abortAction = Action.ABORT_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(abortAction, blockPos, direction);
                  }

                  ClientContext.worldStateManager.destroyBlockPredicted(blockPos);
                  event.cancel();
               }
            }
         }
      }
   }

   @EventHandler
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent tickEvent) {
      BlockPos breakingPos = ((BlockBreakingState)ClientAccess.argusclientMinecraft.gameMode).getCurrentBreakingBlock();
      if (tickEvent == null && ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult blockHit) {
         breakingPos = blockHit.getBlockPos();
      }

      if (breakingPos != null && ClientAccess.argusclientMinecraft.options.keyAttack.isDown()) {
         this.swapBackPending = true;
         Boolean inventorySearch = this.inventorySetting.getValue();
         boolean hotbarOnly = !inventorySearch;
         int bestToolSlot = InventoryUtils.findBestToolSlot(breakingPos, hotbarOnly);
         if (this.inventorySetting.getValue() && bestToolSlot > 9 && !this.inventorySwapActive) {
            this.swapBackSlot = bestToolSlot;
            InventoryUtils.swapIntoSelectedSlot(bestToolSlot);
            this.inventorySwapActive = true;
         } else {
            if (!this.inventorySwapActive) {
               InventoryUtils.selectHotbarSlotForPlacement(bestToolSlot);
            }
         }
      } else {
         Setting<Boolean> swapBackSetting = this.swapBackSetting;
         Boolean swapBack = swapBackSetting.getValue();
         if (swapBack) {
            if (this.swapBackPending) {
               this.swapBackPending = false;
               if (this.inventorySwapActive) {
                  int restoreSlot = this.swapBackSlot;
                  InventoryUtils.swapIntoSelectedSlot(restoreSlot);
                  this.inventorySwapActive = false;
                  return;
               }

               Inventory restoreInventory = ClientAccess.argusclientMinecraft.player.getInventory();
               int restoreSlot = this.swapBackSlot;
               restoreInventory.setSelectedSlot(restoreSlot);
            } else if (!this.inventorySwapActive) {
               this.swapBackSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
            }
         }
      }
   }

   public static void bindModules(ModuleRegistry moduleRegistry) {
      antiCheatModule = (AntiCheatModule)moduleRegistry.getModule(AntiCheatModule.class);
      nukerModule = (NukerModule)moduleRegistry.getModule(NukerModule.class);
   }

   public AutoToolModule() {
      super("AutoTool", "Equips the best tool to use on a block for you.", ModuleCategory.PLAYER);
      Setting<Float> vanillaBlockDamageSetting = this.blockDamageSetting;
      SettingLabelCondition maximumValueMarker = SettingLabelCondition.MAX;
      vanillaBlockDamageSetting.setBoundaryLabel("Vanilla", maximumValueMarker);
   }
}

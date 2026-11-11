package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.KitRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.combat.MainhandModule;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.InventorySorterModeChoice;
import dev.argus.client.settings.options.InventorySorterScopeChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventorySortStrategy;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.stream.IntStream;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

public class InventorySorterModule extends Module {
   private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
   private static MainhandModule mainhandModule = ClientContext.getModule(MainhandModule.class);
   private final Setting<InventorySorterScopeChoice> scopeSetting;
   private final Setting<InventorySorterModeChoice> modeSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> silentSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final ElapsedTimer sortTimer;

   private IntStream getTargetSlots(InventorySorterScopeChoice scope, InventorySorterModeChoice mode) {
      if (scope == InventorySorterScopeChoice.BOTH && mode != InventorySorterModeChoice.KIT) {
         int firstStart = scope.getStartInclusive();
         int firstEnd = scope.getEndExclusive();
         IntStream inventoryFirst = IntStream.range(firstStart, firstEnd).skip(9L);
         int secondStart = scope.getStartInclusive();
         int secondEnd = scope.getEndExclusive();
         IntStream hotbarLast = IntStream.range(secondStart, secondEnd).limit(9L);
         return IntStream.concat(inventoryFirst, hotbarLast);
      } else {
         int startInclusive = scope.getStartInclusive();
         int endExclusive = scope.getEndExclusive();
         return IntStream.range(startInclusive, endExclusive);
      }
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.shouldSkipSorting()) {
         int protectedSlot = mainhandModule.getConfiguredHotbarSlot();
         InventorySorterModeChoice mode = this.modeSetting.getValue();
         InventorySorterScopeChoice scope = this.scopeSetting.getValue();
         Integer[] sourceSlots = mode.getStrategy().getSourceSlots(scope);
         int[] targetSlots = this.getTargetSlots(scope, mode).toArray();
         int attemptedSwaps = 0;
         int pairIndex = 0;

         while (true) {
            int sourceCount = sourceSlots.length;
            int targetCount = targetSlots.length;
            if (pairIndex >= Math.min(sourceCount, targetCount)) {
               break;
            }

            label49: {
               if (mainhandModule.isToggled()) {
                  Integer protectedSource = sourceSlots[pairIndex];
                  if (protectedSlot == protectedSource || protectedSlot == targetSlots[pairIndex]) {
                     break label49;
                  }
               }

               int sourceSlot = sourceSlots[pairIndex];
               ItemStack sourceStack = InventorySortStrategy.getInventoryStack(sourceSlot);
               int targetSlot = targetSlots[pairIndex];
               ItemStack targetStack = InventorySortStrategy.getInventoryStack(targetSlot);
               if (sourceSlots[pairIndex] != targetSlots[pairIndex] && !ItemStack.isSameItem(sourceStack, targetStack)) {
                  int targetInventorySlot = targetSlots[pairIndex];
                  int targetMenuSlot = InventoryUtils.toMenuSlot(targetInventorySlot);
                  int sourceInventorySlot = sourceSlots[pairIndex];
                  int sourceMenuSlot = InventoryUtils.toMenuSlot(sourceInventorySlot);
                  this.executeSlotSwap(targetMenuSlot, sourceMenuSlot);
                  attemptedSwaps++;
               }

               Integer frequency = this.frequencySetting.getValue();
               if (attemptedSwaps >= frequency) {
                  break;
               }

               sourceSlots = mode.getStrategy().getSourceSlots(scope);
            }

            pairIndex++;
         }

         if (attemptedSwaps == 0 && this.autoDisableSetting.getValue()) {
            this.disable();
         }
      }
   }

   private void executeSlotSwap(int firstSlot, int secondSlot) {
      InventoryStateManager initialManager = ClientContext.inventoryStateManager;
      if (!InventoryStateManager.wouldReachClickLimit(initialManager, 1)) {
         this.sortTimer.reset();
         if (firstSlot >= 36 && secondSlot >= 36) {
            int hotbarButton = secondSlot - 36;
            InventoryUtils.swapWithHotbarSlot(firstSlot, hotbarButton);
         } else {
            if (firstSlot >= 36) {
               int firstButton = firstSlot - 36;
               InventoryUtils.swapWithHotbarSlot(secondSlot, firstButton);
            } else if (secondSlot >= 36) {
               int secondButton = secondSlot - 36;
               InventoryUtils.swapWithHotbarSlot(firstSlot, secondButton);
            } else {
               InventoryStateManager fallbackManager = ClientContext.inventoryStateManager;
               if (!InventoryStateManager.wouldReachClickLimit(fallbackManager, 3)) {
                  InventoryUtils.swapSlotsWithPickup(firstSlot, secondSlot);
               }
            }
         }
      }
   }

   private boolean shouldSkipSorting() {
      ItemStack carriedStack = InventoryUtils.getCarriedStack();
      if (!carriedStack.isEmpty()) {
         return true;
      }

      if (multiTaskModule.shouldPauseModule(this)) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.inventoryMenu != ClientAccess.argusclientMinecraft.player.containerMenu) {
         return true;
      }

      if (this.modeSetting.getValue() == InventorySorterModeChoice.KIT) {
         KitRegistry kitOwner = ClientContext.kitRegistry;
         if (KitRegistry.getCurrentKit(kitOwner) == null) {
            return true;
         }
      }

      Boolean silent = this.silentSetting.getValue();
      if (!silent && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof AbstractContainerScreen)) {
         return true;
      }

      ElapsedTimer timer = this.sortTimer;
      long delayMillis = this.delaySetting.getValue().intValue();
      return !timer.hasElapsed(delayMillis);
   }

   public static void bindModules(ModuleRegistry moduleRegistry) {
      multiTaskModule = (MultiTaskModule)moduleRegistry.getModule(MultiTaskModule.class);
      mainhandModule = (MainhandModule)moduleRegistry.getModule(MainhandModule.class);
   }

   public InventorySorterModule() {
      super("InventorySorter", "Sorts items in your inventory.", ModuleCategory.PLAYER);
      InventorySorterScopeChoice defaultScope = InventorySorterScopeChoice.INVENTORY;
      this.scopeSetting = this.add(new EnumSetting<>("Scope", defaultScope));
      InventorySorterModeChoice defaultMode = InventorySorterModeChoice.TYPE;
      this.modeSetting = this.add(new EnumSetting<>("Mode", defaultMode));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 50, 50, 500).withUnit("ms"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 3));
      this.silentSetting = this.add(new BooleanSetting("Silent", false));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
      this.sortTimer = new ElapsedTimer();
   }
}

package dev.argus.client.modules.player;

import com.mojang.blaze3d.platform.InputConstants.Key;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.InputPressEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.mixin.AbstractContainerScreenAccessor;
import dev.argus.mixin.KeyMappingAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;

public class InventoryTweaksModule extends Module {
   private final Setting<Boolean> lockSlotSetting;
   private final ElapsedTimer dropTimer = new ElapsedTimer();
   public final Setting<Boolean> fastMoveSetting;
   private final Setting<Boolean> onlyContainerSetting;
   private final Setting<Boolean> spamSetting;
   public final Setting<Boolean> fastArmorSetting;
   public final Setting<Boolean> bundleTweaksSetting;
   public final Setting<Boolean> xCarryTweaksSetting;
   private int lastQuickMovedSlotId;

   @EventHandler
   private void onDropKey(InputPressEvent event) {
      if (this.lockSlotSetting.getValue() && !this.isWorldUnavailable() && !event.isMouse()) {
         Key dropKey = ((KeyMappingAccessor)ClientAccess.argusclientMinecraft.options.keyDrop).getKey();
         if (dropKey.getValue() == event.getCode() && ClientAccess.argusclientMinecraft.screen == null) {
            if (ClientAccess.argusclientMinecraft.player.getMainHandItem().getMaxStackSize() == 1 && this.dropTimer.hasElapsed(300L)) {
               this.dropTimer.reset();
               event.cancel();
            }
         }
      }
   }

   private int getClickSlotId(Slot slot) {
      CreativeModeTab selectedTab = CreativeModeInventoryScreen.selectedTab;
      boolean inventoryTab = Type.INVENTORY == selectedTab.getType();
      return ClientAccess.argusclientMinecraft.screen instanceof CreativeModeInventoryScreen && inventoryTab ? slot.getContainerSlot() : slot.index;
   }

   public boolean shouldSwapBundleClickButton(Slot slot, int mouseButton) {
      if (!this.isToggled()) {
         return false;
      }

      Boolean bundleTweaksEnabled = this.bundleTweaksSetting.getValue();
      if (bundleTweaksEnabled && (mouseButton == 1 || mouseButton == 0) && slot != null) {
         ItemStack carriedStack = ClientAccess.argusclientMinecraft.player.containerMenu.getCarried();
         TagKey bundleTag = ItemTags.BUNDLES;
         if (carriedStack.is(bundleTag)) {
            ItemStack targetStack = slot.getItem();
            if (!targetStack.isEmpty()) {
               return true;
            }
         }

         if (!carriedStack.isEmpty()) {
            ItemStack targetBundleStack = slot.getItem();
            TagKey targetBundleTag = ItemTags.BUNDLES;
            return targetBundleStack.is(targetBundleTag);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent unusedEvent) {
      Boolean fastMoveEnabled = this.fastMoveSetting.getValue();
      if (fastMoveEnabled && ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen) {
         if (!this.onlyContainerSetting.getValue()
            || ClientAccess.argusclientMinecraft.screen instanceof ContainerScreen
            || ClientAccess.argusclientMinecraft.screen instanceof ShulkerBoxScreen) {
            ItemStack carriedStack = ClientAccess.argusclientMinecraft.player.containerMenu.getCarried();
            if (carriedStack.isEmpty()) {
               Slot focusedSlot = ((AbstractContainerScreenAccessor)ClientAccess.argusclientMinecraft.screen).argusclient$getFocusedSlot();
               if (focusedSlot != null) {
                  if (!focusedSlot.getItem().isEmpty()) {
                     Boolean spamEnabled = this.spamSetting.getValue();
                     if (spamEnabled || this.getClickSlotId(focusedSlot) != this.lastQuickMovedSlotId) {
                        byte mouseButton = -1;
                        byte queriedMouseButton = 0;
                        if (KeyUtils.isPhysicalMouseButtonDown(queriedMouseButton)) {
                           mouseButton = 0;
                        }

                        if (KeyUtils.isPhysicalKeyDown(340) && mouseButton != -1) {
                           MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                           int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
                           int clickedSlotId = this.getClickSlotId(focusedSlot);
                           ClickType clickType = ClickType.QUICK_MOVE;
                           LocalPlayer clickPlayer = ClientAccess.argusclientMinecraft.player;
                           gameMode.handleInventoryMouseClick(containerId, clickedSlotId, mouseButton, clickType, clickPlayer);
                           this.lastQuickMovedSlotId = this.getClickSlotId(focusedSlot);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public InventoryTweaksModule() {
      super("InventoryTweaks", "Modifies your inventory actions.", ModuleCategory.PLAYER);
      this.lockSlotSetting = this.add(new BooleanSetting("LockSlot", false));
      BooleanSetting fastMoveOption = new BooleanSetting("FastMove", true);
      fastMoveOption.markVisibilityDependency();
      this.fastMoveSetting = this.add(fastMoveOption);
      this.onlyContainerSetting = this.add(new BooleanSetting("OnlyContainer", false).dependsOn(this.fastMoveSetting));
      this.spamSetting = this.add(new BooleanSetting("Spam", false).dependsOn(this.fastMoveSetting));
      this.fastArmorSetting = this.add(new BooleanSetting("FastArmor", false));
      this.bundleTweaksSetting = this.add(new BooleanSetting("BundleTweaks", false));
      this.xCarryTweaksSetting = this.add(new BooleanSetting("XCarryTweaks", true));
   }
}

package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.player.AutoEatModule;
import dev.argus.client.modules.player.NoInteractModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class MainhandModule extends Module implements LethalDamageTracker.LethalCheckPolicy {
   private static NoInteractModule noInteractModule = ClientContext.getModule(NoInteractModule.class);
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static AutoEatModule autoEatModule = ClientContext.getModule(AutoEatModule.class);
   private static AutoExpModule autoExpModule = ClientContext.getModule(AutoExpModule.class);
   private static OffhandModule offhandModule = ClientContext.getModule(OffhandModule.class);
   final Setting<Integer> slotSetting = this.add(new NumberSetting<>("Slot", 1, 1, 9));
   final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms"));
   private final Setting<Float> safeSetting = this.add(new NumberSetting<>("Safe", 8.0F, 0.0F, 36.0F).withUnit("hp"));
   private final Setting<Boolean> autoSelectSetting;
   private final Setting<Integer> autoSelectDelaySetting;
   private final Setting<Boolean> lethalSetting;
   private final Setting<Float> healthSetting;
   private final Setting<Boolean> gappleBindSetting;
   private final Setting<Boolean> fallbackSetting;
   private final Setting<Boolean> crappleSetting;
   private final Setting<Boolean> hotbarSetting;
   private final Setting<Boolean> weaponSetting;
   private final Setting<Boolean> totemSetting;
   private final ElapsedTimer replacementTimer;
   private final ElapsedTimer autoSelectTimer;
   private final ElapsedTimer interactionCooldownTimer;
   private boolean tickReady;
   private int previousReplacementSlot;
   private boolean temporaryHotbarSelection;
   private int previousSelectedHotbarSlot;
   private final LethalDamageTracker lethalDamageTracker;

   @Override
   public String getInfo() {
      int itemCount = ClientAccess.argusclientMinecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> {
         Item stackItem = stack.getItem();
         return stackItem == this.getDesiredItem();
      }).mapToInt(ItemStack::getCount).sum();
      return String.valueOf(itemCount);
   }

   @Override
   public void onEnable() {
      this.previousSelectedHotbarSlot = this.previousReplacementSlot = -1;
   }

   private boolean shouldTemporarilySelectGapple() {
      ItemStack mainhandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      if (offhandModule.isToggled() && offhandModule.isGappleBindSafeInBlocks()) {
         OffhandModule bindOwner = offhandModule;
         Item heldItem = mainhandStack.getItem();
         if (bindOwner.canUseGappleBind(heldItem)) {
            return false;
         }
      }

      if (!this.canUseGappleBind()) {
         return false;
      }

      TagKey swords = ItemTags.SWORDS;
      if ((mainhandStack.is(swords) || mainhandStack.is(Items.MACE)) && this.weaponSetting.getValue()) {
         return true;
      }

      Item totem = Items.TOTEM_OF_UNDYING;
      return mainhandStack.is(totem) && this.totemSetting.getValue() ? true : this.temporaryHotbarSelection;
   }

   public int getConfiguredHotbarSlot() {
      return this.slotSetting.getValue() - 1;
   }

   private boolean canUseGappleBind() {
      HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
      if (WorldUtils.isInteractive(hitResult) && !ClientAccess.argusclientMinecraft.player.isShiftKeyDown() && !noInteractModule.isToggled()) {
         if (!ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            return false;
         }

         if (ClientAccess.argusclientMinecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return false;
         }

         if (!ItemUtils.isFood(ClientAccess.argusclientMinecraft.player.getMainHandItem())) {
            return false;
         }
      }

      KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
      if (!useKey.isDown()) {
         return false;
      }

      float health = EntityUtils.getPlayerHealthWithAbsorption();
      Float safeHealth = this.safeSetting.getValue();
      return health >= safeHealth;
   }

   private void autoSelectTotem() {
      if (this.autoSelectSetting.getValue()) {
         ItemStack configuredStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(this.getConfiguredHotbarSlot());
         Item configuredItem = configuredStack.getItem();
         if (configuredItem == Items.TOTEM_OF_UNDYING) {
            if (ClientAccess.argusclientMinecraft.player.isUsingItem()
               && ClientAccess.argusclientMinecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
               return;
            }

            if (!this.lethalDamageTracker.shouldHoldTotem()) {
               float health = EntityUtils.getPlayerHealthWithAbsorption();
               Float requiredHealth = this.healthSetting.getValue();
               if (!(health <= requiredHealth)) {
                  return;
               }
            }

            ElapsedTimer selectTimer = this.autoSelectTimer;
            long delayMillis = this.autoSelectDelaySetting.getValue().intValue();
            if (selectTimer.hasElapsedAndReset(delayMillis)) {
               InventoryUtils.selectHotbarSlotForPlacement(this.getConfiguredHotbarSlot());
            }
         }
      }
   }

   private Item getGappleItem() {
      return this.crappleSetting.getValue() ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE;
   }

   private Item getDesiredItem() {
      int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      if (selectedSlot == this.getConfiguredHotbarSlot() && this.gappleBindSetting.getValue() && this.canStartGappleBind() && this.canUseGappleBind()) {
         return this.getGappleItem();
      } else {
         return this.gappleBindSetting.getValue() && this.fallbackSetting.getValue() && !this.hasTotemAvailable()
            ? this.getGappleItem()
            : Items.TOTEM_OF_UNDYING;
      }
   }

   private boolean hasTotemAvailable() {
      ItemStack configuredStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(this.getConfiguredHotbarSlot());
      Item totem = Items.TOTEM_OF_UNDYING;
      if (configuredStack.is(totem)) {
         return true;
      }

      int totemSlot = InventoryUtils.findInventorySlot(stack -> {
         Item searchedTotem = Items.TOTEM_OF_UNDYING;
         return stack.is(searchedTotem);
      }, true);
      return totemSlot != -1;
   }

   private void selectGappleTemporarily() {
      if (!ClientAccess.argusclientMinecraft.player.isUsingItem() && !ClientAccess.argusclientMinecraft.player.getMainHandItem().is(this.getGappleItem())) {
         int gappleSlot = InventoryUtils.findHotbarSlot(this.getGappleItem());
         this.previousSelectedHotbarSlot = InventoryUtils.getSelectedSlot();
         InventoryUtils.selectHotbarSlotForPlacement(gappleSlot);
         this.temporaryHotbarSelection = true;
      }
   }

   private boolean canStartGappleBind() {
      ItemStack mainhandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      if (ItemUtils.isFood(ClientAccess.argusclientMinecraft.player.getOffhandItem())) {
         return false;
      }

      Item totem = Items.TOTEM_OF_UNDYING;
      return mainhandStack.is(totem) || mainhandStack.is(this.getGappleItem());
   }

   @EventHandler(priority = 100)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         this.tickReady = event.hasPendingTicks();
         this.lethalDamageTracker.update();
         if (ClientAccess.argusclientMinecraft.player.containerMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu) {
            if (!autoCrystalModule.isToggled() || !autoCrystalModule.forceSuicideSetting.getValue()) {
               if (!autoEatModule.isToggled() || !autoEatModule.isEating()) {
                  if (!autoExpModule.isToggled() || !autoExpModule.isActive()) {
                     if (this.hotbarSetting.getValue() && this.tickReady) {
                        if (this.shouldTemporarilySelectGapple()) {
                           this.selectGappleTemporarily();
                           return;
                        }

                        if (this.temporaryHotbarSelection) {
                           InventoryUtils.selectHotbarSlotForPlacement(this.previousSelectedHotbarSlot);
                           this.temporaryHotbarSelection = false;
                           return;
                        }
                     }

                     if (this.tickReady) {
                        this.autoSelectTotem();
                     }

                     Item desiredItem = this.getDesiredItem();
                     ItemStack configuredStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(this.getConfiguredHotbarSlot());
                     if (desiredItem != configuredStack.getItem()) {
                        this.replaceConfiguredSlot(desiredItem);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      BlockHitResult hitResult = event.getHitResult();
      if (WorldUtils.isInteractive(hitResult)) {
         if (!ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
            if (!noInteractModule.isToggled() && !event.isCancelled()) {
               this.interactionCooldownTimer.reset();
            }
         }
      }
   }

   private void replaceConfiguredSlot(Item desiredItem) {
      int replacementSlot = InventoryUtils.findInventorySlot(stack -> stack.is(desiredItem), true);
      if (replacementSlot < 9) {
         replacementSlot = InventoryUtils.toMenuSlot(replacementSlot);
      }

      if (this.previousReplacementSlot != -1) {
         ItemStack previousStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(this.previousReplacementSlot);
         if (previousStack.is(desiredItem) && replacementSlot != -1) {
            replacementSlot = this.previousReplacementSlot;
         }
      }

      if (replacementSlot != -1) {
         NonNullList menuSlots = ClientAccess.argusclientMinecraft.player.containerMenu.slots;
         if (replacementSlot < menuSlots.size()) {
            AbstractContainerMenu menu = ClientAccess.argusclientMinecraft.player.containerMenu;
            Slot slot = menu.getSlot(replacementSlot);
            ItemStack replacementStack = slot.getItem();
            if (!replacementStack.has(DataComponents.FOOD) || this.tickReady) {
               ElapsedTimer swapTimer = this.replacementTimer;
               long delayMillis = this.delaySetting.getValue().intValue();
               if (swapTimer.hasElapsedAndReset(delayMillis) && this.interactionCooldownTimer.hasElapsed(200L)) {
                  int configuredSlot = this.getConfiguredHotbarSlot();
                  InventoryUtils.swapWithHotbarSlot(replacementSlot, configuredSlot);
                  ClientContext.inventoryStateManager.close();
                  this.previousReplacementSlot = replacementSlot;
               }

               KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
               if (KeyUtils.isPhysicallyDown(useKey) && replacementStack.has(DataComponents.FOOD)) {
                  if (ClientAccess.argusclientMinecraft.player.isUsingItem()
                     && ClientAccess.argusclientMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                     ClientAccess.argusclientMinecraft.player.releaseUsingItem();
                  }

                  MultiPlayerGameMode gameModeForUse = ClientAccess.argusclientMinecraft.gameMode;
                  LocalPlayer playerForUse = ClientAccess.argusclientMinecraft.player;
                  InteractionHand offhand = InteractionHand.OFF_HAND;
                  gameModeForUse.useItem(playerForUse, offhand);
               }
            }
         }
      }
   }

   @Override
   public boolean isLethalCheckEnabled() {
      return !this.lethalSetting.getValue() ? false : this.autoSelectSetting.getValue();
   }

   public static void bindModuleConnections(ModuleRegistry registry) {
      noInteractModule = (NoInteractModule)registry.getModule(NoInteractModule.class);
      autoCrystalModule = (AutoCrystalModule)registry.getModule(AutoCrystalModule.class);
      autoEatModule = (AutoEatModule)registry.getModule(AutoEatModule.class);
      autoExpModule = (AutoExpModule)registry.getModule(AutoExpModule.class);
      offhandModule = (OffhandModule)registry.getModule(OffhandModule.class);
   }

   public MainhandModule() {
      super("Mainhand", "Auto totem for main hand.", ModuleCategory.COMBAT);
      Setting<Boolean> autoSelectOption = this.add(new BooleanSetting("AutoSelect", false).markVisibilityDependency());
      this.autoSelectSetting = autoSelectOption;
      this.autoSelectDelaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms").dependsOn(autoSelectOption));
      this.lethalSetting = this.add(new BooleanSetting("Lethal", true).dependsOn(autoSelectOption));
      this.healthSetting = this.add(new NumberSetting<>("Health", 8.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(autoSelectOption));
      Setting<Boolean> gappleBindOption = this.add(new BooleanSetting("GappleBind", true).markVisibilityDependency());
      this.gappleBindSetting = gappleBindOption;
      this.fallbackSetting = this.add(new BooleanSetting("Fallback", true).dependsOn(gappleBindOption));
      this.crappleSetting = this.add(new BooleanSetting("Crapple", false).dependsOn(gappleBindOption));
      Setting<Boolean> hotbarOption = this.add(new BooleanSetting("Hotbar", false).markVisibilityDependency());
      this.hotbarSetting = hotbarOption;
      this.weaponSetting = this.add(new BooleanSetting("Weapon", false).dependsOn(hotbarOption));
      this.totemSetting = this.add(new BooleanSetting("Totem", false).dependsOn(hotbarOption));
      this.replacementTimer = new ElapsedTimer();
      this.autoSelectTimer = new ElapsedTimer();
      this.interactionCooldownTimer = new ElapsedTimer();
      this.lethalDamageTracker = new LethalDamageTracker(this);
      this.autoSelectDelaySetting.setConfigName("SwapDelay");
   }
}

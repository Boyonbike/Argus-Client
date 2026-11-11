package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.movement.NoFallModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.modules.player.NoInteractModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.NoSlowStrictInventoryChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.world.BlockStateUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class OffhandModule extends Module implements LethalDamageTracker.LethalCheckPolicy {
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static OffhandModule offhandModule = ClientContext.getModule(OffhandModule.class);
   private static NoInteractModule noInteractModule = ClientContext.getModule(NoInteractModule.class);
   private static AntiCheatModule antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   private static MainhandModule mainhandModule = ClientContext.getModule(MainhandModule.class);
   private static NoFallModule noFallModule = ClientContext.getModule(NoFallModule.class);
   public final Setting<OffhandItemChoice> itemSetting = this.add(new EnumSetting<>("Item", OffhandItemChoice.Totem));
   final Setting<Set<Item>> customSetting = this.add(new RegistrySetSetting<>("Custom", BuiltInRegistries.ITEM));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms"));
   final Setting<Float> healthSetting = this.add(new NumberSetting<>("Health", 16.0F, 0.0F, 36.0F).withUnit("hp"));
   final Setting<Boolean> lethalSetting = this.add(new BooleanSetting("Lethal", true));
   final Setting<Boolean> elytraSetting = this.add(new BooleanSetting("Elytra", true));
   private final Setting<Boolean> directSwapSetting = this.add(new BooleanSetting("1.17+", true));
   private final Setting<Boolean> containersSetting = this.add(new BooleanSetting("Containers", true));
   final Setting<Boolean> gappleBindSetting = this.add(new BooleanSetting("GappleBind", true).markVisibilityDependency());
   private final Setting<Float> safeSetting;
   private final Setting<Float> safeInBlocksSetting;
   private final Setting<Boolean> axeSetting;
   private final Setting<Boolean> pickaxeSetting;
   private final Setting<Boolean> crystalsSetting;
   private final Setting<Boolean> totemsSetting;
   private final Setting<Boolean> crappleSetting;
   private static final ElapsedTimer RECENT_SWAP_TIMER = new ElapsedTimer();
   private final ElapsedTimer swapDelayTimer;
   private final ElapsedTimer totemPopTimer;
   private int previousSwapSlot;
   private Class previousMenuClass;
   private boolean tickAllowsSwap;
   private boolean deathReportSent;
   private boolean hasInventoryTotem;
   private final LethalDamageTracker lethalDamageTracker;

   public boolean isGappleBindSafeInBlocks() {
      return isGappleBindSafeInBlocks(this);
   }

   private static boolean isGappleBindSafeInBlocks(OffhandModule owner) {
      if (!owner.gappleBindSetting.getValue()) {
         return false;
      }

      float threshold = owner.safeInBlocksSetting.getValue();
      if (threshold >= EntityUtils.getPlayerHealthWithAbsorption()) {
         return false;
      }

      Set<BlockPos> surroundingBlockPositions = PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.argusclientMinecraft.player);
      int qualifyingBlockCount = 0;

      for (BlockPos blockPos : surroundingBlockPositions) {
         BlockState blockState = ClientAccess.argusclientMinecraft.level.getBlockState(blockPos);
         if (BlockStateUtils.isBlastResistantBreakable(blockState)) {
            qualifyingBlockCount++;
         }

         Block bedrock = Blocks.BEDROCK;
         if (blockState.is(bedrock)) {
            return true;
         }
      }

      return qualifyingBlockCount > 2;
   }

   @Override
   public String getInfo() {
      return this.itemSetting.getValue().toString();
   }

   @Override
   public void onToggle() {
      this.previousSwapSlot = -1;
   }

   public boolean canUseGappleBind(Item item) {
      return canUseGappleBind(this, item);
   }

   private static boolean canUseGappleBind(OffhandModule owner, Item item) {
      OffhandItemChoice gappleChoice = OffhandItemChoice.Gapple;
      boolean useCrapple = owner.crappleSetting.getValue();
      Item gappleItem = gappleChoice.getItem(useCrapple);
      if (ClientAccess.argusclientMinecraft.player.isUsingItem()
         && ClientAccess.argusclientMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND
         && ClientAccess.argusclientMinecraft.player.getUseItem().is(gappleItem)
         && owner.itemSetting.getValue() != OffhandItemChoice.Crystal
         && !owner.isMainhandManagingTotemOrFood()) {
         return true;
      } else {
         ItemStack defaultItemStack = item.getDefaultInstance();
         if (ItemUtils.isSword(defaultItemStack) && !(item instanceof AxeItem)) {
            return true;
         } else if (item instanceof AxeItem && owner.axeSetting.getValue()) {
            return true;
         } else {
            ItemStack defaultToolStack = item.getDefaultInstance();
            if (defaultToolStack.is(ItemTags.PICKAXES) && owner.pickaxeSetting.getValue()) {
               return true;
            } else {
               return item == Items.TOTEM_OF_UNDYING && owner.totemsSetting.getValue() ? true : item == Items.END_CRYSTAL && owner.crystalsSetting.getValue();
            }
         }
      }
   }

   public static boolean isRecentSwapWindowActive() {
      return RECENT_SWAP_TIMER.hasElapsed(50L) ? false : offhandModule.isToggled();
   }

   public OffhandItemChoice applyGappleBind(OffhandItemChoice incomingChoice) {
      return applyGappleBind(this, incomingChoice);
   }

   private static OffhandItemChoice applyGappleBind(OffhandModule owner, OffhandItemChoice incomingChoice) {
      if (incomingChoice != null) {
         boolean useCrapple = owner.crappleSetting.getValue();
         if (incomingChoice.getItem(useCrapple) instanceof ShieldItem) {
            return incomingChoice;
         }
      }

      HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
      if (WorldUtils.isInteractive(hitResult) && !ClientAccess.argusclientMinecraft.player.isShiftKeyDown() && !noInteractModule.isToggled()) {
         return incomingChoice;
      }

      float currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption();
      Item mainhandItem = ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem();
      boolean gappleHealthGate = owner.safeSetting.getValue() < currentHealthWithAbsorption;
      Float blockHealthThreshold = owner.safeInBlocksSetting.getValue();
      boolean blockSafetyApplied = false;
      if (blockHealthThreshold < currentHealthWithAbsorption && !owner.safeInBlocksSetting.isAtMinimum()) {
         boolean blockSafe = owner.isGappleBindSafeInBlocks();
         if (!blockSafe && ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            blockSafe = ClientAccess.argusclientMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND;
         }

         gappleHealthGate = blockSafe;
         blockSafetyApplied = true;
      }

      if (!blockSafetyApplied && mainhandItem == Items.TOTEM_OF_UNDYING) {
         gappleHealthGate = true;
      }

      if (!owner.hasInventoryTotem) {
         gappleHealthGate = true;
      }

      if (owner.gappleBindSetting.getValue()) {
         KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
         if (useKey.isDown()
            && gappleHealthGate
            && owner.canUseGappleBind(mainhandItem)
            && ClientAccess.argusclientMinecraft.screen == null
            && owner.itemSetting.getValue() != OffhandItemChoice.Gapple) {
            return OffhandItemChoice.Gapple;
         }
      }

      return incomingChoice;
   }

   @EventHandler
   private void onPlayerTickStart(LocalPlayerPreTickEvent event) {
      updateTotemAvailability(this);
      if (shouldUseStrictTick()) {
         this.tickAllowsSwap = true;
         this.updateOffhand();
      }
   }

   void swapToChosenItem() {
      Class menuClass = ClientAccess.argusclientMinecraft.player.containerMenu.getClass();
      OffhandItemChoice chosenItemChoice = this.selectOffhandChoice();
      int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
      boolean allowCrapple = this.crappleSetting.getValue();
      int chosenContainerSlot = chosenItemChoice.findContainerSlot(allowCrapple);
      boolean canReusePreviousSlot = !(ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen)
         || ClientAccess.argusclientMinecraft.screen instanceof InventoryScreen;
      if (this.previousSwapSlot != -1 && chosenContainerSlot != -1 && canReusePreviousSlot && menuClass == this.previousMenuClass) {
         int previousSlot = this.previousSwapSlot;
         AbstractContainerMenu previousMenu = ClientAccess.argusclientMinecraft.player.containerMenu;
         Slot previousMenuSlot = previousMenu.getSlot(previousSlot);
         ItemStack previousSlotStack = previousMenuSlot.getItem();
         AbstractContainerMenu chosenMenu = ClientAccess.argusclientMinecraft.player.containerMenu;
         Slot chosenMenuSlot = chosenMenu.getSlot(chosenContainerSlot);
         ItemStack chosenSlotStack = chosenMenuSlot.getItem();
         Item previousItem = previousSlotStack.getItem();
         if (chosenSlotStack.is(previousItem)) {
            chosenContainerSlot = this.previousSwapSlot;
         }
      }

      if (chosenContainerSlot != -1) {
         if (this.containersSetting.getValue()
            || !(ClientAccess.argusclientMinecraft.screen instanceof ShulkerBoxScreen)
               && !(ClientAccess.argusclientMinecraft.screen instanceof ContainerScreen)) {
            NonNullList menuSlots = ClientAccess.argusclientMinecraft.player.containerMenu.slots;
            if (chosenContainerSlot < menuSlots.size()) {
               ItemStack carriedStackBeforeSwap = InventoryUtils.getCarriedStack();
               AbstractContainerMenu targetMenu = ClientAccess.argusclientMinecraft.player.containerMenu;
               Slot targetSlot = targetMenu.getSlot(chosenContainerSlot);
               ItemStack targetSlotStack = targetSlot.getItem();
               DataComponentType targetFoodComponent = DataComponents.FOOD;
               if (!targetSlotStack.has(targetFoodComponent) || this.tickAllowsSwap) {
                  this.beginInventorySwap();
                  if (!carriedStackBeforeSwap.isEmpty() && !InventoryUtils.isInventoryFull()) {
                     Item emptyItem = Items.AIR;
                     int emptyContainerSlot = InventoryUtils.findMenuSlot(emptyItem);
                     ContainerInput pickupClick = ContainerInput.PICKUP;
                     InventoryUtils.clickSlot(emptyContainerSlot, 0, pickupClick);
                  }

                  Boolean directSwapEnabled = this.directSwapSetting.getValue();
                  boolean useDirectSwap = false;
                  if (directSwapEnabled) {
                     ItemStack carriedStackForDirectSwap = InventoryUtils.getCarriedStack();
                     useDirectSwap = carriedStackForDirectSwap.isEmpty();
                  }

                  if (useDirectSwap) {
                     this.previousSwapSlot = chosenContainerSlot;
                     MultiPlayerGameMode swapGameMode = ClientAccess.argusclientMinecraft.gameMode;
                     ContainerInput swapClick = ContainerInput.SWAP;
                     LocalPlayer swapPlayer = ClientAccess.argusclientMinecraft.player;
                     swapGameMode.handleContainerInput(containerId, chosenContainerSlot, 40, swapClick, swapPlayer);
                     this.swapDelayTimer.reset();
                  } else {
                     this.swapSlotToOffhand(chosenContainerSlot);
                     this.previousSwapSlot = chosenContainerSlot;
                  }

                  this.previousMenuClass = menuClass;
                  RECENT_SWAP_TIMER.reset();
                  this.endInventorySwap();
                  KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
                  if (KeyUtils.isPhysicallyDown(useKey)) {
                     DataComponentType swapFoodComponent = DataComponents.FOOD;
                     if (targetSlotStack.has(swapFoodComponent)) {
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
      }
   }

   private boolean canUseCurrentMenu() {
      return this.directSwapSetting.getValue()
         ? true
         : !(ClientAccess.argusclientMinecraft.screen instanceof CreativeModeInventoryScreen)
            && ClientAccess.argusclientMinecraft.player.inventoryMenu == ClientAccess.argusclientMinecraft.player.containerMenu
            && !(ClientAccess.argusclientMinecraft.screen instanceof ContainerScreen);
   }

   private void swapSlotToOffhand(int containerSlot) {
      ElapsedTimer delayTimer = this.swapDelayTimer;
      long delayMillis = this.delaySetting.getValue().intValue();
      if (delayTimer.hasElapsed(delayMillis)) {
         NonNullList menuSlots = ClientAccess.argusclientMinecraft.player.containerMenu.slots;
         int menuSlotCount = menuSlots.size();
         if (menuSlotCount > 45) {
            InventoryUtils.swapSlotsWithPickup(containerSlot, 45);
            this.swapDelayTimer.reset();
         }
      }
   }

   private static boolean shouldUseStrictTick() {
      return RotationManager.isSilentRotationMode() ? false : noSlowModule.getStrictInventoryMode() == NoSlowStrictInventoryChoice.GRIM_STRICT;
   }

   private MutableComponent withGameLogTooltip(MutableComponent component) {
      return component.withStyle(style -> style.withHoverEvent(new ShowText(Component.literal("More info in the game log."))));
   }

   @EventHandler(priority = 100)
   private void onClientTick(MinecraftRunTickEvent event) {
      if (!shouldUseStrictTick()) {
         this.tickAllowsSwap = event.hasPendingTicks();
         this.updateOffhand();
      }
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (event.getHand() == InteractionHand.MAIN_HAND) {
         Object noIncomingChoice = null;
         if (this.applyGappleBind((OffhandItemChoice)noIncomingChoice) != null) {
            if (!antiCheatModule.is2b2tEnabled()) {
               MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer player = ClientAccess.argusclientMinecraft.player;
               InteractionHand offhand = InteractionHand.OFF_HAND;
               gameMode.useItem(player, offhand);
               event.cancel();
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundEntityEventPacket entityEventPacket
         && !this.shouldDiscardTotem()
         && this.canUseCurrentMenu()
         && entityEventPacket.getEntity(ClientAccess.argusclientMinecraft.level) == ClientAccess.argusclientMinecraft.player) {
         byte entityEventId = entityEventPacket.getEventId();
         if (entityEventId == 35) {
            ItemStack mainhandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
            Item mainhandTotemItem = Items.TOTEM_OF_UNDYING;
            if (mainhandStack.is(mainhandTotemItem)) {
               LocalPlayer shrinkingPlayer = ClientAccess.argusclientMinecraft.player;
               shrinkingPlayer.getMainHandItem().shrink(1);
            } else {
               ItemStack offhandStack = ClientAccess.argusclientMinecraft.player.getOffhandItem();
               Item offhandTotemItem = Items.TOTEM_OF_UNDYING;
               if (offhandStack.is(offhandTotemItem)) {
                  LocalPlayer shrinkingPlayer = ClientAccess.argusclientMinecraft.player;
                  shrinkingPlayer.getOffhandItem().shrink(1);
               }
            }

            this.totemPopTimer.reset();
            if (!shouldUseStrictTick()) {
               this.swapToChosenItem();
            }
         }
      }

      if (event.getPacket() instanceof ClientboundAddEntityPacket addEntityPacket
         && this.lethalDamageTracker.isLethalCheckEnabled()
         && addEntityPacket.getType() == EntityType.END_CRYSTAL) {
         Vec3 crystalPosition = new Vec3(addEntityPacket.getX(), addEntityPacket.getY(), addEntityPacket.getZ());
         if (this.lethalDamageTracker.isCrystalLethal(crystalPosition) && !shouldUseStrictTick()) {
            this.swapToChosenItem();
         }
      }

      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         ItemStack offhandStackAtDeath = ClientAccess.argusclientMinecraft.player.getOffhandItem();
         Item totemItem = Items.TOTEM_OF_UNDYING;
         if (!offhandStackAtDeath.is(totemItem)) {
            return;
         }

         this.reportDeathDiagnostics();
      }
   }

   private boolean shouldDiscardTotem() {
      if (!autoCrystalModule.isToggled()) {
         return false;
      } else {
         return !autoCrystalModule.forceSuicideSetting.getValue() ? false : this.itemSetting.getValue() == OffhandItemChoice.Totem;
      }
   }

   private void reportDeathDiagnostics() {
      String moduleName = this.getName();
      int moduleNameHash = moduleName.hashCode();
      int messageId = Math.abs(moduleNameHash) * -1 - 1;
      MessageSignature messageSignature = ChatUtils.createSignature(messageId);
      long millisSinceTotemPop = this.totemPopTimer.elapsedMillis();
      int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
      if (!this.deathReportSent) {
         String diagnosticText = "Since last pop: %dms. Latency: %dms.".formatted(millisSinceTotemPop, latencyMillis);
         MutableComponent diagnosticComponent = Component.literal(diagnosticText);
         if (latencyMillis > millisSinceTotemPop || millisSinceTotemPop < 25L) {
            MutableComponent damageOverrideComponent = Component.literal(" Possible death reasons: Damage override/Lag spike.*");
            Component damageOverrideReason = this.withGameLogTooltip(damageOverrideComponent);
            diagnosticComponent.append(damageOverrideReason);
            ChatUtils.MessagePriority damageOverridePriority = ChatUtils.MessagePriority.HIGH;
            ChatUtils.printChat(diagnosticComponent, messageSignature, damageOverridePriority);
            Logger damageOverrideLogger = ClientAccess.logger;
            damageOverrideLogger.warn(
               "*Death was most likely caused by you taking damage before the server receives the update slot packet, making you die holding a totem client-side."
            );
            this.deathReportSent = true;
         } else if (this.isServerLagging() && millisSinceTotemPop < 200L) {
            MutableComponent serverLagComponent = Component.literal(" Possible death reasons: Server lag*");
            Component serverLagReason = this.withGameLogTooltip(serverLagComponent);
            diagnosticComponent.append(serverLagReason);
            ChatUtils.MessagePriority serverLagPriority = ChatUtils.MessagePriority.HIGH;
            ChatUtils.printChat(diagnosticComponent, messageSignature, serverLagPriority);
            Logger serverLagLogger = ClientAccess.logger;
            serverLagLogger.warn("*Death was most likely caused by server lagging for too long.");
            this.deathReportSent = true;
         }
      }
   }

   void endInventorySwap() {
      ClientContext.inventoryStateManager.setClickBatching(false);
      if (!(ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen)) {
         ClientContext.inventoryStateManager.close();
      }
   }

   void beginInventorySwap() {
      ClientContext.inventoryStateManager.setClickBatching(true);
      noSlowModule.prepareForInventoryAction();
   }

   private boolean isServerLagging() {
      int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
      ServerStateManager packetTimestampTracker = ClientContext.serverStateManager;
      ServerStateManager.PacketActivity anyPacketType = ServerStateManager.PacketActivity.ANY;
      long lastPacketTimestamp = packetTimestampTracker.getLastPacketTime(anyPacketType);
      long timeSinceAnyPacket = System.currentTimeMillis() - lastPacketTimestamp;
      long latencyForComparison = latencyMillis;
      return timeSinceAnyPacket > latencyForComparison * 2L ? true : ClientContext.serverStateManager.hasStablePacketFlowFor(500L);
   }

   boolean isFallDistanceSafe() {
      if (!this.lethalSetting.getValue()) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.getAbilities().invulnerable) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.hasEffect(MobEffects.SLOW_FALLING)) {
         return true;
      }

      if (noFallModule.isToggled()) {
         return true;
      }

      float trackedDownwardDistance = PlayerStateUtils.getTrackedFallDistance();
      return trackedDownwardDistance < 16.0F;
   }

   @Override
   public boolean isLethalCheckEnabled() {
      ItemStack mainhandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      Item totemItem = Items.TOTEM_OF_UNDYING;
      return mainhandStack.is(totemItem) ? false : this.lethalSetting.getValue();
   }

   private void updateOffhand() {
      if (!this.isWorldUnavailable()) {
         if (ClientAccess.argusclientMinecraft.player.tickCount > 1) {
            if (ClientAccess.argusclientMinecraft.player.isAlive()) {
               this.deathReportSent = false;
            }

            this.lethalDamageTracker.update();
            if (this.shouldDiscardTotem()) {
               ItemStack offhandStack = ClientAccess.argusclientMinecraft.player.getOffhandItem();
               Item totemItem = Items.TOTEM_OF_UNDYING;
               if (offhandStack.is(totemItem) && this.tickAllowsSwap) {
                  int discardContainerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
                  MultiPlayerGameMode discardGameMode = ClientAccess.argusclientMinecraft.gameMode;
                  ContainerInput discardClickType = ContainerInput.THROW;
                  LocalPlayer playerForDiscard = ClientAccess.argusclientMinecraft.player;
                  discardGameMode.handleContainerInput(discardContainerId, 45, 1, discardClickType, playerForDiscard);
               }
            } else if (this.canUseCurrentMenu()) {
               ElapsedTimer delayTimerForCheck = this.swapDelayTimer;
               long delayMillis = this.delaySetting.getValue().intValue();
               if (delayTimerForCheck.hasElapsed(delayMillis)) {
                  this.swapToChosenItem();
               }
            }
         }
      }
   }

   private OffhandItemChoice selectOffhandChoice() {
      if (autoCrystalModule.isToggled() && autoCrystalModule.forceSuicideSetting.getValue() && this.itemSetting.getValue() != OffhandItemChoice.Totem) {
         return OffhandItemChoice.Crystal;
      }

      boolean useCrapple = this.crappleSetting.getValue();
      OffhandItemChoice selectedChoice = OffhandItemChoice.Totem;
      if (this.lethalDamageTracker.shouldHoldTotem() && this.hasInventoryTotem) {
         return OffhandItemChoice.Totem;
      }

      float currentHealthWithAbsorption = EntityUtils.getPlayerHealthWithAbsorption();
      Float healthThreshold = this.healthSetting.getValue();
      if (currentHealthWithAbsorption > healthThreshold && this.isFallDistanceSafe()) {
         Predicate<ItemStack> configuredItemPredicate = stack -> {
            Item configuredItem = this.itemSetting.getValue().getItem(useCrapple);
            return stack.is(configuredItem);
         };
         if (InventoryUtils.countItems(configuredItemPredicate) > 0) {
            selectedChoice = this.itemSetting.getValue();
         }
      }

      if (this.elytraSetting.getValue() && PlayerStateUtils.isLocalWearingElytra()) {
         selectedChoice = OffhandItemChoice.Totem;
      }

      if (!this.hasInventoryTotem) {
         selectedChoice = this.itemSetting.getValue();
      }

      OffhandItemChoice choiceBeforeGappleBind = selectedChoice;
      return this.applyGappleBind(choiceBeforeGappleBind);
   }

   boolean isMainhandManagingTotemOrFood() {
      if (!mainhandModule.isToggled()) {
         return false;
      }

      int selectedHotbarSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      MainhandModule mainhandForSlot = mainhandModule;
      if (selectedHotbarSlot != mainhandForSlot.getConfiguredHotbarSlot()) {
         return false;
      }

      ItemStack mainhandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      Item totemItem = Items.TOTEM_OF_UNDYING;
      if (mainhandStack.is(totemItem)) {
         return true;
      }

      DataComponentType mainhandFoodComponent = DataComponents.FOOD;
      return mainhandStack.has(mainhandFoodComponent);
   }

   private static void updateTotemAvailability(OffhandModule owner) {
      if (!owner.isWorldUnavailable()) {
         boolean totemFound = false;
         NonNullList inventorySlots = ClientAccess.argusclientMinecraft.player.inventoryMenu.slots;
         Iterator slotIterator = inventorySlots.iterator();

         while (slotIterator.hasNext()) {
            ItemStack slotStack = ((Slot)slotIterator.next()).getItem();
            Item totemItem = Items.TOTEM_OF_UNDYING;
            if (slotStack.is(totemItem)) {
               totemFound = true;
               break;
            }
         }

         owner.hasInventoryTotem = totemFound;
      }
   }

   public static void bindModuleConnections(ModuleRegistry modules) {
      autoCrystalModule = (AutoCrystalModule)modules.getModule(AutoCrystalModule.class);
      offhandModule = (OffhandModule)modules.getModule(OffhandModule.class);
      noInteractModule = (NoInteractModule)modules.getModule(NoInteractModule.class);
      antiCheatModule = (AntiCheatModule)modules.getModule(AntiCheatModule.class);
      noSlowModule = (NoSlowModule)modules.getModule(NoSlowModule.class);
      mainhandModule = (MainhandModule)modules.getModule(MainhandModule.class);
      noFallModule = (NoFallModule)modules.getModule(NoFallModule.class);
      OffhandItemChoice.bindModuleRegistry(modules);
   }

   public OffhandModule() {
      super("Offhand", "Manages your offhand automatically.", ModuleCategory.COMBAT, "autototem");
      Predicate<Object> safeVisibility = unused -> {
         Setting<Boolean> gappleBindOptionx = this.gappleBindSetting;
         return gappleBindOptionx.isExpanded();
      };
      this.safeSetting = this.add(new NumberSetting<>("Safe", 8.0F, 0.0F, 36.0F, safeVisibility).withUnit("hp"));
      this.safeInBlocksSetting = this.add(new NumberSetting<>("SafeInBlocks", 0.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(this.gappleBindSetting));
      this.axeSetting = this.add(new BooleanSetting("Axe", true).dependsOn(this.gappleBindSetting));
      Predicate<Object> pickaxeVisibility = unused -> {
         Setting<Boolean> gappleBindOptionx = this.gappleBindSetting;
         return gappleBindOptionx.isExpanded();
      };
      this.pickaxeSetting = this.add(new BooleanSetting("Pickaxe", true, pickaxeVisibility));
      Predicate<Object> crystalsVisibility = unused -> {
         Setting<Boolean> gappleBindOptionx = this.gappleBindSetting;
         return gappleBindOptionx.isExpanded();
      };
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", true, crystalsVisibility));
      this.totemsSetting = this.add(new BooleanSetting("Totems", false).dependsOn(this.gappleBindSetting));
      Predicate<Object> crappleVisibility = unused -> {
         Setting<OffhandItemChoice> itemOption = this.itemSetting;
         return itemOption.getValue() == OffhandItemChoice.Gapple || this.gappleBindSetting.isExpanded();
      };
      this.crappleSetting = this.add(new BooleanSetting("Crapple", false, crappleVisibility));
      this.swapDelayTimer = new ElapsedTimer();
      this.totemPopTimer = new ElapsedTimer();
      this.previousSwapSlot = -1;
      this.lethalDamageTracker = new LethalDamageTracker(this);
      Setting<Boolean> gappleBindOption = this.gappleBindSetting;
      gappleBindOption.setConfigName("GappleBind");
      this.safeSetting.setConfigName("SafeHealth");
      Setting<Set<Item>> customOption = this.customSetting;
      Runnable customItemCallback = () -> {
         RegistrySetSetting<Item> registryOption = (RegistrySetSetting<Item>)customOption;
         if (registryOption.getLastChangedEntry() != null) {
            customOption.getValue().clear();
            customOption.getValue().add(registryOption.getLastChangedEntry());
         }
      };
      customOption.setChangeCallback(customItemCallback);
      this.safeInBlocksSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.customSetting.addVisibilityPredicate(unused -> {
         Setting<OffhandItemChoice> itemOption = this.itemSetting;
         return itemOption.getValue() == OffhandItemChoice.Custom;
      });
   }
}

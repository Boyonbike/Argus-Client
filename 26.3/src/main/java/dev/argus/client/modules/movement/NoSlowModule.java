package dev.argus.client.modules.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.InventoryActionEvent;
import dev.argus.client.events.ItemUseEvent;
import dev.argus.client.events.LocalPlayerAiStepEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickPhaseEvent;
import dev.argus.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.StatusEffectQueryEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.gui.panels.GamePanel;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.NoSlowModeChoice;
import dev.argus.client.settings.options.NoSlowStrictInventoryChoice;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.ChestSearchController;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.NoSlowInventoryMode;
import dev.argus.client.utils.inventory.NoSlowInventoryNcpMode;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class NoSlowModule extends Module {
   private static MultiTaskModule multiTask;
   public final Setting<Boolean> guiMoveSetting = this.add(new BooleanSetting("GuiMove", true).markVisibilityDependency());
   public final Setting<Boolean> containersSetting = this.add(new BooleanSetting("Containers", true).dependsOn(this.guiMoveSetting));
   public final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", true).dependsOn(this.guiMoveSetting));
   private final Setting<Boolean> strictSetting = this.add(new BooleanSetting("Strict", true).markAsGroup().markVisibilityDependency());
   public final Setting<NoSlowModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", NoSlowModeChoice.NONE).dependsOn(this.strictSetting));
   private final Setting<NoSlowStrictInventoryChoice> strictInventorySetting = this.add(
      new EnumSetting<>("StrictInventory", NoSlowStrictInventoryChoice.NONE).dependsOn(this.strictSetting)
   );
   private final Setting<Boolean> playerSetting = this.add(new BooleanSetting("Player", false).markAsGroup().markVisibilityDependency());
   public final Setting<Boolean> itemsSetting = this.add(new BooleanSetting("Items", true).dependsOn(this.playerSetting));
   public final Setting<Boolean> sneakSetting = this.add(new BooleanSetting("Sneak", false).dependsOn(this.playerSetting));
   public final Setting<Boolean> crawlSetting = this.add(new BooleanSetting("Crawl", false).dependsOn(this.playerSetting));
   public final Setting<Boolean> slownessSetting = this.add(new BooleanSetting("Slowness", false).dependsOn(this.playerSetting));
   final Setting<Boolean> blocksSetting = this.add(new BooleanSetting("Blocks", false).markAsGroup().markVisibilityDependency());
   public final Setting<Boolean> iceSetting;
   public final Setting<Boolean> soulSandSetting;
   public final Setting<Boolean> honeySetting;
   public final Setting<Boolean> berryBushSetting;
   public final Setting<Boolean> slimeSetting;
   public final Setting<Boolean> noSlimeBounceSetting;
   public boolean releasedUsingItemThisTick;
   private final InterpolatedFloat pitchInterpolation;
   private final InterpolatedFloat yawInterpolation;
   private final EnumSelectionMap<NoSlowInventoryMode> strictInventoryModes;

   @EventHandler
   private void onInventoryClickAfterStackHash(MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onInventoryClickIntercept(event);
   }

   @EventHandler
   private void onTickBeforePassengerCheck(LocalPlayerTickPhaseEvent.BeforePassengerCheck event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onTickBeforePassengerCheck();
   }

   @EventHandler
   private void onBeforeContainerClick(MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.beforeMenuClick(event);
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onPlayerAiStep();
   }

   @EventHandler
   private void onInventoryAction(InventoryActionEvent event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onInventoryAction(event);
   }

   @EventHandler
   private void onStatusEffectQuery(StatusEffectQueryEvent event) {
      if (this.slownessSetting.getValue() && event.getEffect() == MobEffects.SLOWNESS) {
         event.cancel();
      }
   }

   @EventHandler(priority = 200)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      this.releasedUsingItemThisTick = false;
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onTickStart(event);
   }

   @EventHandler
   private void onItemSlowdown(ItemUseEvent.Slowdown event) {
      if (this.itemsSetting.getValue()) {
         if (ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            if (!ClientAccess.argusclientMinecraft.player.isPassenger()) {
               boolean cancelSlowdown = true;
               if (this.modeSetting.getValue() == NoSlowModeChoice.GRIMV3) {
                  int useTicks = ClientAccess.argusclientMinecraft.player.getTicksUsingItem();
                  cancelSlowdown = useTicks % 2 == 0 && useTicks > 1;
                  if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                     cancelSlowdown = false;
                  }
               }

               if (this.modeSetting.getValue() == NoSlowModeChoice.GRIMOLD) {
                  LocalPlayer handPlayer = ClientAccess.argusclientMinecraft.player;
                  InteractionHand usedHand = handPlayer.getUsedItemHand();
                  this.handleGrimItemUse(usedHand);
               }

               if (this.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM) {
                  cancelSlowdown = false;
               }

               if (this.modeSetting.getValue() == NoSlowModeChoice._2B2T) {
                  cancelSlowdown = this.handle2b2tItemUse();
               }

               if (cancelSlowdown) {
                  event.cancel();
               }
            }
         }
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      if (this.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM) {
         if (this.itemsSetting.getValue()) {
            if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
               LocalPlayer handPlayer = ClientAccess.argusclientMinecraft.player;
               InteractionHand usedHand = event.getHand();
               if (handPlayer.getItemInHand(usedHand).has(DataComponents.FOOD)) {
                  BlockPos hitPos = event.getHitResult().getBlockPos();
                  if (!BlockInteractionUtils.requiresSneakForInteraction(hitPos)) {
                     ClientAccess.argusclientMinecraft.gameMode.useItem(ClientAccess.argusclientMinecraft.player, InteractionHand.MAIN_HAND);
                     event.cancel();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      NoSlowInventoryMode strategy = this.strictInventoryModes.getSelected();
      strategy.onPacketSend(event);
      if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
         InteractionHand usedHand = ClientAccess.argusclientMinecraft.player.getUsedItemHand();
         ItemStack usedStack = ClientAccess.argusclientMinecraft.player.getItemInHand(usedHand);
         if (ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            if (this.itemsSetting.getValue()) {
               if (!ClientAccess.argusclientMinecraft.player.isHandsBusy()) {
                  if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                     if (usedStack.has(DataComponents.FOOD)) {
                        if (this.modeSetting.getValue() == NoSlowModeChoice.NCP) {
                           ServerboundSetCarriedItemPacket heldSlotPacket = new ServerboundSetCarriedItemPacket(
                              ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot()
                           );
                           PacketSender.send(heldSlotPacket);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isFoodBowShieldOrPearl(ItemStack stack) {
      if (stack.has(DataComponents.FOOD)) {
         return true;
      } else if (stack.is(Items.BOW)) {
         return true;
      } else {
         return stack.is(Items.SHIELD) ? true : stack.is(Items.ENDER_PEARL);
      }
   }

   private static boolean isTypingInTextField(net.minecraft.client.gui.screens.Screen screen) {
      net.minecraft.client.gui.components.events.GuiEventListener focused = screen.getFocused();
      while (focused instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container && container.getFocused() != null) {
         focused = container.getFocused();
      }
      return focused instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused() && box.canConsumeInput();
   }

   public boolean isGuiMoveAllowed() {
      if (ClientAccess.argusclientMinecraft.gui.screen() != null && isTypingInTextField(ClientAccess.argusclientMinecraft.gui.screen())) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ClickGuiScreen) {
         for (Panel panel : ClientContext.getClickGui().getPanels()) {
            if (panel instanceof GamePanel && panel.isFullyExpanded()) {
               return false;
            }
         }

         if (ClientContext.getClickGui().searchBox.isFocused()) {
            return false;
         }

         if (!ClientContext.getClickGui().searchBox.getValue().isBlank()) {
            return false;
         }
      }

      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof AbstractContainerScreen containerScreen && !this.containersSetting.getValue()) {
         return containerScreen.getMenu() == ClientAccess.argusclientMinecraft.player.inventoryMenu;
      } else {
         if (ClientAccess.argusclientMinecraft.gui.screen() != null
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof ChatScreen)
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof SignEditScreen)
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof AnvilScreen)
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof AbstractCommandBlockEditScreen)
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof StructureBlockEditScreen)) {
            if (ChestSearchController.searchBox == null) {
               return true;
            }

            if (!ChestSearchController.searchBox.canConsumeInput()) {
               return true;
            }
         }

         return false;
      }
   }

   public void prepareForInventoryAction() {
      if (!this.releasedUsingItemThisTick) {
         if (multiTask.isItemUseRestricted()) {
            if (!multiTask.isInventoryAllowed()) {
               ClientAccess.argusclientMinecraft.gameMode.releaseUsingItem(ClientAccess.argusclientMinecraft.player);
               this.releasedUsingItemThisTick = true;
            }
         }
      }
   }

   public NoSlowStrictInventoryChoice getStrictInventoryMode() {
      return this.strictInventorySetting.getValue();
   }

   public boolean isUsingItemInConstantiamMode() {
      return !ClientAccess.argusclientMinecraft.player.isUsingItem() ? false : this.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM;
   }

   public boolean isSlimeSlowdownDisabled() {
      return !this.isToggled() ? false : this.slimeSetting.getValue();
   }

   private boolean handle2b2tItemUse() {
      int swapSlot = this.find2b2tSwapSlot();
      if (swapSlot != -1 && !ClientAccess.argusclientMinecraft.isLocalServer()) {
         float latencyTicks = ClientContext.serverStateManager.getPeakLatencyMillis() / 50.0F;
         int tickThreshold = (int)(Math.ceil(latencyTicks / ClientContext.serverStateManager.getTickRateScale()) + 2.0);
         int useTicks = ClientAccess.argusclientMinecraft.player.getTicksUsingItem();
         if (useTicks == tickThreshold) {
            InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ContainerInput.SWAP);
            PacketSender.sendPlayerAction(Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN);
            InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ContainerInput.SWAP);
         }

         return useTicks > tickThreshold;
      } else {
         return true;
      }
   }

   private int find2b2tSwapSlot() {
      if (!ClientAccess.argusclientMinecraft.player.getOffhandItem().isEmpty()) {
         return 40;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (slot != InventoryUtils.getSelectedSlot() && !ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private boolean isAnyRotationKeyPressed() {
      return this.isKeyPressed(264) || this.isKeyPressed(265) || this.isKeyPressed(262) || this.isKeyPressed(263);
   }

   private void handleGrimItemUse(InteractionHand usedHand) {
      InteractionHand otherHand = PlayerStateUtils.oppositeHand(usedHand);
      ItemStack otherHandStack = ClientAccess.argusclientMinecraft.player.getItemInHand(otherHand);
      if (this.isFoodBowShieldOrPearl(otherHandStack)) {
         if (otherHand == InteractionHand.MAIN_HAND) {
            int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
            int nextSlot = selectedSlot + 1;
            nextSlot %= 9;
            InventoryUtils.selectHotbarSlot(nextSlot);
            PacketSender.sendUseItem(otherHand);
            InventoryUtils.selectHotbarSlot(selectedSlot);
            return;
         }

         Predicate<ItemStack> inventoryPredicate = stack -> !this.isFoodBowShieldOrPearl(stack);
         int inventorySlot = InventoryUtils.findInventorySlot(inventoryPredicate, true);
         int containerSlot = InventoryUtils.toMenuSlot(inventorySlot);
         ClientAccess.argusclientMinecraft.gameMode.handleContainerInput(0, containerSlot, 40, ContainerInput.SWAP, ClientAccess.argusclientMinecraft.player);
         PacketSender.sendUseItem(otherHand);
         ClientAccess.argusclientMinecraft.gameMode.handleContainerInput(0, containerSlot, 40, ContainerInput.SWAP, ClientAccess.argusclientMinecraft.player);
      } else {
         PacketSender.sendUseItem(otherHand);
      }
   }

   private boolean isKeyPressed(int key) {
      long handleCapture = ClientAccess.argusclientMinecraft.getWindow().handle();
      return GLFW.glfwGetKey(handleCapture, key) == 1;
   }

   public boolean isGrimStrictInventoryEnabled() {
      return !this.isToggled() ? false : this.strictInventorySetting.getValue() == NoSlowStrictInventoryChoice.GRIM_STRICT;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (this.guiMoveSetting.getValue() && this.rotateSetting.getValue()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (this.isGuiMoveAllowed()
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof ClickGuiScreen)
            && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof HudEditorScreen)) {
            if (this.isKeyPressed(264)) {
               float pitch = Mth.lerp(event.getPartialTick(), player.xRotO, player.getXRot());
               this.pitchInterpolation.setTarget(Math.min(pitch + 8.0F, 90.0F));
            } else if (this.isKeyPressed(265)) {
               float pitch = Mth.lerp(event.getPartialTick(), player.xRotO, player.getXRot());
               this.pitchInterpolation.setTarget(Math.max(pitch - 8.0F, -90.0F));
            }

            if (this.isKeyPressed(262)) {
               float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
               this.yawInterpolation.setTarget(yaw + 8.0F);
            } else if (this.isKeyPressed(263)) {
               float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
               this.yawInterpolation.setTarget(yaw - 8.0F);
            }

            if (this.isAnyRotationKeyPressed()) {
               player.setXRot(this.pitchInterpolation.updateAndGet());
               player.setYRot(this.yawInterpolation.updateAndGet());
            }
         } else {
            this.yawInterpolation.setValue(player.getYRot());
            this.pitchInterpolation.setValue(player.getXRot());
         }
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject moduleJson && moduleJson.get("settings") instanceof JsonObject settings) {
         String itemModeKey = this.modeSetting.getConfigName();
         String inventoryModeKey = this.strictInventorySetting.getConfigName();
         boolean retiredItemMode = isLegacyMode(settings.get(itemModeKey), "GrimStrict");
         boolean retiredInventoryMode = isLegacyMode(settings.get(inventoryModeKey), "Constantiam");
         if (retiredItemMode || retiredInventoryMode) {
            JsonObject migrated = moduleJson.deepCopy();
            JsonObject migratedSettings = migrated.getAsJsonObject("settings");
            if (retiredItemMode) {
               migratedSettings.addProperty(itemModeKey, NoSlowModeChoice.GRIMV3.getName());
            }

            if (retiredInventoryMode) {
               migratedSettings.addProperty(inventoryModeKey, NoSlowStrictInventoryChoice.NCP.getName());
            }

            json = migrated;
         }
      }

      super.fromJson(json);
   }

   private static boolean isLegacyMode(JsonElement value, String retiredName) {
      return value instanceof JsonPrimitive primitive && primitive.isString() && retiredName.equalsIgnoreCase(primitive.getAsString());
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      multiTask = (MultiTaskModule)registry.getModule(MultiTaskModule.class);
      NoSlowInventoryNcpMode.bindReferencedModules(registry);
   }

   public NoSlowModule() {
      super("NoSlow", "Cancels several things that may slow you down.", ModuleCategory.MOVEMENT);
      Predicate<Object> iceVisibility = ignored -> this.blocksSetting.isExpanded();
      this.iceSetting = this.add(new BooleanSetting("Ice", false, iceVisibility));
      Predicate<Object> soulSandVisibility = ignored -> this.blocksSetting.isExpanded();
      this.soulSandSetting = this.add(new BooleanSetting("SoulSand", false, soulSandVisibility));
      Predicate<Object> honeyVisibility = ignored -> this.blocksSetting.isExpanded();
      this.honeySetting = this.add(new BooleanSetting("Honey", false, honeyVisibility));
      Predicate<Object> berryBushVisibility = ignored -> this.blocksSetting.isExpanded();
      this.berryBushSetting = this.add(new BooleanSetting("BerryBush", false, berryBushVisibility));
      Predicate<Object> slimeVisibility = ignored -> this.blocksSetting.isExpanded();
      this.slimeSetting = this.add(new BooleanSetting("Slime", false, slimeVisibility).markVisibilityDependency());
      this.noSlimeBounceSetting = this.add(new BooleanSetting("NoSlimeBounce", true).dependsOn(this.blocksSetting, this.slimeSetting));
      this.pitchInterpolation = new InterpolatedFloat(2.0F, true);
      this.yawInterpolation = new InterpolatedFloat(2.0F, true);
      this.strictInventoryModes = new EnumSelectionMap<>(this.strictInventorySetting);
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NONE, new NoSlowInventoryMode(this));
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NCP, new NoSlowInventoryNcpMode(this));
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.GRIM_STRICT, new NoSlowInventoryGrimStrictMode(this));
      this.setDrawn(false);
   }
}

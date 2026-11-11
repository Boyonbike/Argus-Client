package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.misc.AntiAimModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.EquipmentUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class AutoExpModule extends Module {
   public final Setting<AutoExpModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", AutoExpModule.ModeChoice.SILENT));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 2, 0, 5));
   private final Setting<Integer> frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 16));
   private final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", false));
   private final Setting<Boolean> airSetting = this.add(new BooleanSetting("Air", true));
   private final Setting<Boolean> inventorySetting = this.add(new BooleanSetting("Inventory", false));
   final Setting<Boolean> stopSetting = this.add(new BooleanSetting("Stop", true).markVisibilityDependency());
   private final Setting<Integer> stopAtSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> toolsSetting;
   private static AntiAimModule antiAim = ClientContext.getModule(AntiAimModule.class);
   private static ElytraFlyModule elytraFly = ClientContext.getModule(ElytraFlyModule.class);
   private final ElapsedTimer throwTimer;
   private int swappedHotbarSlot;
   private int swappedInventorySlot;
   private int originalSelectedSlot;
   private boolean active;
   public boolean stopped;

   private boolean needsMending(ItemStack stack) {
      ResourceKey mendingEnchantment = Enchantments.MENDING;
      if (!EnchantmentUtils.hasEnchantment(mendingEnchantment, stack)) {
         return false;
      } else if (stack.isDamageableItem() && !stack.isEmpty()) {
         int durabilityPercent = DamageUtils.getDurabilityPercent(stack);
         Integer stopAtValue = this.stopAtSetting.getValue();
         return durabilityPercent < stopAtValue;
      } else {
         return false;
      }
   }

   public boolean isActive() {
      return this.active;
   }

   @Override
   public String getInfo() {
      if (this.stopped) {
         return "Stopped";
      }

      Predicate<ItemStack> bottleFilter = stack -> {
         Item bottleItem = Items.EXPERIENCE_BOTTLE;
         return stack.is(bottleItem);
      };
      int bottleCount = InventoryUtils.countItems(bottleFilter);
      return String.valueOf(bottleCount);
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         if (this.rotateSetting.getValue()) {
            this.throwTimer.reset();
         }

         if (antiAim.isToggled() && !this.stopped) {
            antiAim.pauseRotations();
         }

         Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
         this.originalSelectedSlot = inventory.getSelectedSlot();
      }
   }

   @Override
   public void onDisable() {
      this.active = false;
      if (this.isWorldUnavailable()) {
         this.swappedInventorySlot = -1;
      } else {
         restoreInventorySwap(this);
         if (ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot() != this.originalSelectedSlot
            && this.modeSetting.getValue() == AutoExpModule.ModeChoice.NORMAL) {
            InventoryUtils.selectHotbarSlotForPlacement(this.originalSelectedSlot);
         }
      }
   }

   @EventHandler(priority = 250)
   private void updateMending(RotationRequestEvent event) {
      this.active = false;
      performMending(this);
   }

   private static void performMending(AutoExpModule owner) {
      if (!owner.shouldPauseMending()) {
         float[] targetRotation = owner.calculateThrowRotations();
         Item bottleToFind = Items.EXPERIENCE_BOTTLE;
         InventoryUtils.SwapSlot bottleSlot = InventoryUtils.findSwapSlot(bottleToFind, false);
         LocalPlayer holdingPlayer = ClientAccess.argusclientMinecraft.player;
         Item bottleItem = Items.EXPERIENCE_BOTTLE;
         boolean holdingBottles = holdingPlayer.isHolding(bottleItem);
         if (!holdingBottles && !bottleSlot.isFound() && owner.swapBottlesFromInventory()) {
            holdingBottles = true;
         }

         if (owner.stopSetting.getValue()) {
            boolean allItemsRepaired = true;

            for (ItemStack armorStack : EquipmentUtils.getArmorStacks(ClientAccess.argusclientMinecraft.player)) {
               if (owner.needsMending(armorStack)) {
                  allItemsRepaired = false;
                  break;
               }
            }

            if ((
                  owner.needsMending(ClientAccess.argusclientMinecraft.player.getMainHandItem())
                     || owner.needsMending(ClientAccess.argusclientMinecraft.player.getOffhandItem())
               )
               && owner.toolsSetting.getValue()) {
               allItemsRepaired = false;
            }

            if (allItemsRepaired || !bottleSlot.isFound() && !holdingBottles) {
               owner.stopped = true;
               if (owner.autoDisableSetting.getValue()) {
                  owner.disable();
               }

               return;
            }
         }

         if (bottleSlot.isFound() || holdingBottles) {
            ElapsedTimer delayTimer = owner.throwTimer;
            int delayTicks = owner.delaySetting.getValue();
            long delayMillis = delayTicks * 50;
            if (delayTimer.hasElapsed(delayMillis)) {
               owner.stopped = false;
               int previousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
               InventoryUtils.swapTo(bottleSlot);

               for (int bottlesThrown = 0; bottlesThrown < owner.frequencySetting.getValue(); bottlesThrown++) {
                  if (owner.rotateSetting.getValue()) {
                     InteractionHand throwHand = bottleSlot.hand();
                     float throwYaw = targetRotation[0];
                     float throwPitch = targetRotation[1];
                     PacketSender.sendUseItem(throwHand, throwYaw, throwPitch);
                  } else {
                     InteractionHand throwHandWithoutRotation = bottleSlot.hand();
                     PacketSender.sendUseItem(throwHandWithoutRotation);
                  }
               }

               SwingInteractionManager swingManager = ClientContext.swingManager;
               InteractionHand swingHand = bottleSlot.hand();
               SwingAction interactionType = SwingAction.INTERACT;
               swingManager.swingPacket(swingHand, interactionType);
               if (owner.modeSetting.getValue() == AutoExpModule.ModeChoice.SILENT) {
                  InventoryUtils.swapBack(previousSlot, bottleSlot);
               }

               owner.throwTimer.reset();
            }
         }

         if (owner.rotateSetting.getValue()) {
            ClientContext.rotationManager.requestRotation(targetRotation, 400);
         }

         owner.active = true;
      }
   }

   private boolean swapBottlesFromInventory() {
      if (!this.inventorySetting.getValue()) {
         return false;
      }

      Item bottleItem = Items.EXPERIENCE_BOTTLE;
      int bottleInventorySlot = InventoryUtils.findMenuSlot(bottleItem);
      int selectedHotbarSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      if (bottleInventorySlot == -1) {
         return false;
      }

      ContainerInput clickType = ContainerInput.SWAP;
      InventoryUtils.clickSlot(bottleInventorySlot, selectedHotbarSlot, clickType);
      this.swappedInventorySlot = bottleInventorySlot;
      this.swappedHotbarSlot = selectedHotbarSlot;
      return true;
   }

   private static void restoreInventorySwap(AutoExpModule owner) {
      if (owner.swappedInventorySlot != -1) {
         int inventorySlot = owner.swappedInventorySlot;
         int hotbarSlot = owner.swappedHotbarSlot;
         ContainerInput clickType = ContainerInput.SWAP;
         InventoryUtils.clickSlot(inventorySlot, hotbarSlot, clickType);
         owner.swappedInventorySlot = -1;
         owner.swappedHotbarSlot = -1;
      }
   }

   private boolean shouldPauseMending() {
      if (!this.airSetting.getValue()) {
         HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
         if (hitResult.getType() == Type.MISS) {
            return true;
         }

         if (ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult blockHit) {
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            BlockPos position = blockHit.getBlockPos();
            BlockState state = level.getBlockState(position);
            if (state.canBeReplaced()) {
               return true;
            }
         }
      }

      return OffhandModule.isRecentSwapWindowActive() ? true : ClientAccess.argusclientMinecraft.player.isFallFlying() && !this.canMendDuringFlight();
   }

   private boolean canMendDuringFlight() {
      if (!this.rotateSetting.getValue()) {
         return false;
      }

      ElytraFlyModule.ModeChoice mode = elytraFly.modeSetting.getValue();
      return elytraFly.isToggled()
         && PlayerStateUtils.isFallFlying()
         && !MovementPhysics.isLocalPlayerMoving()
         && (mode == ElytraFlyModule.ModeChoice.CONTROL || mode == ElytraFlyModule.ModeChoice.NEWGRIM);
   }

   private float[] calculateThrowRotations() {
      if (this.canMendDuringFlight()) {
         return new float[]{ClientAccess.argusclientMinecraft.player.getYRot(), -90.0F};
      }

      AABB playerBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.argusclientMinecraft.player, 1);
      return RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(playerBounds));
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      antiAim = (AntiAimModule)modules.getModule(AntiAimModule.class);
      elytraFly = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
   }

   public AutoExpModule() {
      super("AutoExp", "Mends your armor for you.", ModuleCategory.COMBAT);
      Predicate<Object> stopAtVisibility = unused -> this.stopSetting.isExpanded();
      this.stopAtSetting = this.add(new NumberSetting<>("StopAt", 100, 80, 100, stopAtVisibility).withUnit("%"));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).dependsOn(this.stopSetting));
      this.toolsSetting = this.add(new BooleanSetting("Tools", false).dependsOn(this.stopSetting));
      this.throwTimer = new ElapsedTimer();
      this.stopped = false;
   }

   public enum ModeChoice implements NamedEntry {
      NORMAL("Normal"),
      SILENT("Silent");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}

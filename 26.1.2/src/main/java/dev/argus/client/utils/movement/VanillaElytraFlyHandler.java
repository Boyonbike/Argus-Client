package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class VanillaElytraFlyHandler extends ElytraFlyModeHandler {
   private static AntiCheatModule antiCheatModule;
   private static FireworksModule fireworksModule;
   private final VanillaElytraFlyHandler.FireworkAction fireworkAction = new VanillaElytraFlyHandler.FireworkAction();
   private boolean descendingPhase;
   private boolean fireworkAscentPending;
   private float targetPitch;

   private float computeInitialPitch() {
      if (this.isWorldUnavailable()) {
         return 40.0F;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return player.getXRot();
   }

   public VanillaElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.descendingPhase = true;
      this.targetPitch = this.computeInitialPitch();
   }

   public float getTargetPitch() {
      return this.targetPitch;
   }

   private boolean isFlightActive() {
      return this.module.infDurabilitySetting.getValue().isDisabled()
         ? PlayerStateUtils.isFallFlying()
         : this.module.isDurabilityBypassActive() && !PlayerStateUtils.isInWater(ClientAccess.argusclientMinecraft.player);
   }

   private boolean isGrimAutoLaunchActive() {
      return fireworksModule.isToggled()
         && fireworksModule.grimSetting.getValue()
         && fireworksModule.boostSetting.getValue()
         && fireworksModule.autoLaunchSetting.getValue();
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      boolean grimAutoLaunch = this.isGrimAutoLaunchActive();
      if (!this.isFlightActive()) {
         this.targetPitch = this.computeInitialPitch();
      } else {
         if (antiCheatModule.is2b2tEnabled() && ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            return;
         }

         if (!EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player) || grimAutoLaunch) {
            if (this.fireworkAction.isUsePending()) {
               this.fireworkAction.run();
            } else {
               if (!this.descendingPhase) {
                  double fireworkHeight = ClientAccess.argusclientMinecraft.player.getY();
                  Integer fireworkMinimumValue = this.module.minYSetting.getValue();
                  int fireworkMinimumY = fireworkMinimumValue;
                  if (fireworkHeight <= fireworkMinimumY - 10) {
                     Boolean triggerFirework = this.module.useFireworkSetting.getValue();
                     if (triggerFirework && ClientAccess.argusclientMinecraft.player.getDeltaMovement().y < 0.0) {
                        this.fireworkAscentPending = true;
                     }
                  }
               }

               double ascentHeight = ClientAccess.argusclientMinecraft.player.getY();
               if (ascentHeight >= this.getMaximumAltitude()) {
                  this.fireworkAscentPending = false;
               }

               if (this.fireworkAscentPending) {
                  Boolean usePendingFirework = this.module.useFireworkSetting.getValue();
                  if (usePendingFirework && !grimAutoLaunch) {
                     this.fireworkAction.requestUseAfterDelay();
                     return;
                  }
               }

               double descentHeight = ClientAccess.argusclientMinecraft.player.getY();
               if (descentHeight >= this.getMaximumAltitude()) {
                  this.descendingPhase = true;
               } else {
                  double currentMinimumHeight = ClientAccess.argusclientMinecraft.player.getY();
                  Integer descentMinimumY = this.module.minYSetting.getValue();
                  if (currentMinimumHeight <= descentMinimumY.intValue() && this.descendingPhase) {
                     this.descendingPhase = false;
                  }
               }

               if (this.descendingPhase) {
                  float previousDescendingPitch = this.targetPitch;
                  this.targetPitch = previousDescendingPitch + (grimAutoLaunch ? 8.0F : 2.0F);
               } else {
                  float previousAscendingPitch = this.targetPitch;
                  this.targetPitch = previousAscendingPitch - 5.45F;
               }

               float unclampedPitch = this.targetPitch;
               this.targetPitch = Mth.clamp(unclampedPitch, -40.0F, 40.0F);
            }
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable() && this.isFlightActive() && event.hasPendingTicks()) {
         if (EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player)) {
            if (this.isGrimAutoLaunchActive()) {
               ClientAccess.argusclientMinecraft.player.setXRot(this.targetPitch);
            } else {
               this.targetPitch = -75.0F;
            }
         }

         RotationManager rotationManager = ClientContext.rotationManager;
         float pitch = this.targetPitch;
         Float boxedPitch = pitch;
         int rotationPriority = 999;
         boolean forceRotation = true;
         Float yaw = null;
         rotationManager.requestRotation(yaw, boxedPitch, rotationPriority, forceRotation);
         ClientContext.rotationManager.forceRotationOverride();
      }
   }

   private float getMaximumAltitude() {
      Integer minimumValue = this.module.minYSetting.getValue();
      int minimumY = minimumValue;
      Setting<Integer> heightSetting = this.module.heightSetting;
      Integer height = heightSetting.getValue();
      return minimumY + height;
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
      fireworksModule = (FireworksModule)registry.getModule(FireworksModule.class);
   }

   private static class FireworkAction implements Runnable {
      private final ElapsedTimer lastUseTimer = new ElapsedTimer();
      private boolean usePending;

      @Override
      public void run() {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Inventory inventory = player.getInventory();
         int selectedSlot = inventory.getSelectedSlot();
         boolean includeInventory = true;
         Item firework = Items.FIREWORK_ROCKET;
         InventoryUtils.SwapSlot fireworkSlot = InventoryUtils.findSwapSlot(firework, includeInventory);
         if (fireworkSlot.isFound()) {
            InventoryUtils.swapTo(fireworkSlot);
            MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
            LocalPlayer usePlayer = ClientAccess.argusclientMinecraft.player;
            InteractionHand useHand = fireworkSlot.hand();
            gameMode.useItem(usePlayer, useHand);
            SwingAction swingAction = SwingAction.INTERACT;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            SwingInteractionManager swingManager = ClientContext.swingManager;
            swingManager.swingPacket(swingHand, swingAction);
            InventoryUtils.swapBack(selectedSlot, fireworkSlot);
            ElapsedTimer useTimer = this.lastUseTimer;
            useTimer.reset();
            this.usePending = false;
         }
      }

      public boolean isUsePending() {
         return this.usePending;
      }

      public void requestUseAfterDelay() {
         ElapsedTimer useTimer = this.lastUseTimer;
         long delayMillis = 250L;
         if (useTimer.hasElapsed(delayMillis)) {
            this.usePending = true;
         }
      }
   }
}

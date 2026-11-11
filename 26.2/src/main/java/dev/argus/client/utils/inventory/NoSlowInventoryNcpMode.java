package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.InventoryActionEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.settings.options.MultiTaskModeChoice;
import dev.argus.client.settings.options.NoSlowModeChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;

public class NoSlowInventoryNcpMode extends NoSlowInventoryMode {
   private static MultiTaskModule multiTask = ClientContext.getModule(MultiTaskModule.class);
   private final ElapsedTimer airborneTimer = new ElapsedTimer();
   private boolean groundSpoofPending;
   private boolean positionOffsetSent;

   public NoSlowInventoryNcpMode(NoSlowModule noSlow) {
      super(noSlow);
   }

   @Override
   public void onInventoryAction(InventoryActionEvent event) {
      Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
      if (guiMoveEnabled) {
         NoSlowModule guiModule = this.noSlow;
         if (guiModule.isGuiMoveAllowed()) {
            if (event.getStage() == EventStage.Pre) {
               boolean releaseItem = true;
               if (multiTask.getMode() == MultiTaskModeChoice.ALL) {
                  releaseItem = this.noSlow.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM;
               }

               if (releaseItem && !this.noSlow.releasedUsingItemThisTick) {
                  MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                  LocalPlayer releasePlayer = ClientAccess.argusclientMinecraft.player;
                  gameMode.releaseUsingItem(releasePlayer);
                  this.noSlow.releasedUsingItemThisTick = true;
               }

               if (!MovementPhysics.isLocalPlayerMoving()) {
                  return;
               }

               if (ClientContext.playerStateTracker.getGroundedTicks() > 1 && !this.positionOffsetSent) {
                  double x = ClientAccess.argusclientMinecraft.player.getX();
                  double offsetY = ClientAccess.argusclientMinecraft.player.getY() + 0.0626;
                  double z = ClientAccess.argusclientMinecraft.player.getZ();
                  PacketSender.sendPosition(x, offsetY, z, false);
                  this.positionOffsetSent = true;
                  this.groundSpoofPending = true;
               }

               LocalPlayer sprintPlayer = ClientAccess.argusclientMinecraft.player;
               if (sprintPlayer.isSprinting()) {
                  ServerboundPlayerCommandPacket stopSprinting = new ServerboundPlayerCommandPacket(
                     ClientAccess.argusclientMinecraft.player, Action.STOP_SPRINTING
                  );
                  PacketSender.send(stopSprinting);
               }

               LocalPlayer shiftPlayer = ClientAccess.argusclientMinecraft.player;
               if (shiftPlayer.isShiftKeyDown()) {
                  PlayerStateTracker shiftState = ClientContext.playerStateTracker;
                  shiftState.setSneaking(false);
               }
            } else if (event.getStage() == EventStage.Post && MovementPhysics.isLocalPlayerMoving()) {
               LocalPlayer sprintPlayer = ClientAccess.argusclientMinecraft.player;
               if (sprintPlayer.isSprinting()) {
                  ServerboundPlayerCommandPacket startSprinting = new ServerboundPlayerCommandPacket(
                     ClientAccess.argusclientMinecraft.player, Action.START_SPRINTING
                  );
                  PacketSender.send(startSprinting);
               }

               LocalPlayer shiftPlayer = ClientAccess.argusclientMinecraft.player;
               if (shiftPlayer.isShiftKeyDown()) {
                  PlayerStateTracker shiftState = ClientContext.playerStateTracker;
                  shiftState.setSneaking(true);
               }
            }
         }
      }
   }

   @Override
   public void onTickStart(LocalPlayerPreTickEvent event) {
      LocalPlayer groundPlayer = ClientAccess.argusclientMinecraft.player;
      if (!groundPlayer.onGround()) {
         this.airborneTimer.reset();
      }

      this.positionOffsetSent = false;
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
         ServerboundMovePlayerPacketAccessor var7 = (ServerboundMovePlayerPacketAccessor)packet;
         Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
         if (guiMoveEnabled && this.groundSpoofPending) {
            if (!ClientAccess.argusclientMinecraft.player.isHandsBusy()) {
               LocalPlayer groundPlayer = ClientAccess.argusclientMinecraft.player;
               if (groundPlayer.onGround()) {
                  ElapsedTimer timer = this.airborneTimer;
                  if (timer.hasElapsed(200L)) {
                     if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                        var7.setOnGround(false);
                        this.groundSpoofPending = false;
                     }
                  }
               }
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      multiTask = (MultiTaskModule)registry.getModule(MultiTaskModule.class);
   }
}

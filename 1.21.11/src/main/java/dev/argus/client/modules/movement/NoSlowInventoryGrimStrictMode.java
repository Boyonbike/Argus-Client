package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.InventoryActionEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.integrations.ProtocolCompatibility;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.NoSlowInventoryMode;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.LocalPlayerAccessor;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class NoSlowInventoryGrimStrictMode extends NoSlowInventoryMode {
   private final Queue<Runnable> pendingActions = new ArrayDeque<>();
   private boolean flushPending;
   private boolean replayingAction;
   private boolean inputActive;
   private boolean inventoryInputSuppressed;

   private void flushQueuedActions() {
      if (!this.pendingActions.isEmpty()) {
         long attempts = 0L;

         while (!this.pendingActions.isEmpty() && ++attempts <= 50L) {
            Runnable action = this.pendingActions.poll();
            if (action != null) {
               this.runReplayedAction(action);
            }
         }

         this.pendingActions.clear();
      }
   }

   public NoSlowInventoryGrimStrictMode(NoSlowModule noSlow) {
      super(noSlow);
   }

   private boolean isDelayablePacket(Packet packet) {
      return packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundSwingPacket
         || packet instanceof PosRot && RotationManager.isSilentRotationMode();
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (!this.replayingAction) {
         if (!this.usesLegacyClickProtocol()) {
            Packet packet = event.getPacket();
            if (this.isDelayablePacket(packet) && this.inputActive && !this.pendingActions.isEmpty()) {
               this.queueOrRunAction(() -> PacketSender.send(event.getPacket()));
               event.cancel();
            }

            if (event.getPacket() instanceof ServerboundContainerClosePacket closePacket && this.inputActive) {
               this.queueOrRunAction(() -> PacketSender.send(closePacket));
               event.cancel();
            }

            if (event.getPacket() instanceof ServerboundPlayerCommandPacket command && command.getAction() == Action.START_FALL_FLYING) {
               this.inputActive = false;
               this.flushQueuedActions();
            }
         }
      }
   }

   @Override
   public void onInventoryClickIntercept(MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event) {
      if (!this.usesLegacyClickProtocol()) {
         if (!this.replayingAction && this.inputActive) {
            int containerId = event.getContainerId();
            int slotId = event.getSlotId();
            int button = event.getButton();
            ClickType clickType = event.getClickType();
            this.queueOrRunAction(
               () -> ClientAccess.argusclientMinecraft
                  .gameMode
                  .handleInventoryMouseClick(containerId, slotId, button, clickType, ClientAccess.argusclientMinecraft.player)
            );
            event.cancel();
         }
      }
   }

   @Override
   public void beforeMenuClick(MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event) {
      if (!this.usesLegacyClickProtocol()) {
         if (this.replayingAction) {
            event.cancel();
         }
      }
   }

   @Override
   public void onTickStart(LocalPlayerPreTickEvent event) {
      if (this.flushPending) {
         this.flushQueuedActions();
         this.flushPending = false;
      }

      this.inputActive = this.hasPlayerInput();
      boolean keepPendingFlush = false;
      if (this.inputActive && ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen) {
         ItemStack cursorStack = InventoryUtils.getCarriedStack();
         keepPendingFlush = !cursorStack.isEmpty();
      }

      if (!keepPendingFlush) {
         this.flushPending = !this.pendingActions.isEmpty();
      }

      if (this.flushPending) {
         ClientContext.inputOverrides.requestReset();
         ClientAccess.argusclientMinecraft.player.setSprinting(false);
      }
   }

   @Override
   public void onInventoryAction(InventoryActionEvent event) {
      if (this.usesLegacyClickProtocol() && !this.inventoryInputSuppressed) {
         Input emptyInput = new Input(false, false, false, false, false, false, false);
         PacketSender.send(new ServerboundPlayerInputPacket(emptyInput));
         PacketSender.sendPlayerCommand(Action.STOP_SPRINTING);
         ((LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player).argusclient$setLastPlayerInput(emptyInput);
         ((LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player).argusclient$setLastSprinting(false);
         ClientContext.inputOverrides.blockSprint();
         this.inventoryInputSuppressed = true;
      }
   }

   private boolean canQueueInCurrentScreen() {
      return ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen screen && !this.noSlow.containersSetting.getValue()
         ? screen.getMenu() == ClientAccess.argusclientMinecraft.player.inventoryMenu
         : true;
   }

   private boolean hasPlayerInput() {
      Input input = ClientAccess.argusclientMinecraft.player.input.keyPresses;
      if (input.forward()) {
         return true;
      } else if (input.backward()) {
         return true;
      } else if (input.left()) {
         return true;
      } else if (input.right()) {
         return true;
      } else if (input.jump()) {
         return true;
      } else {
         return input.shift() ? true : input.sprint();
      }
   }

   private synchronized void runReplayedAction(Runnable action) {
      this.replayingAction = true;
      action.run();
      this.replayingAction = false;
   }

   @Override
   public void onPlayerAiStep() {
      if (this.flushPending || this.inventoryInputSuppressed) {
         ClientAccess.argusclientMinecraft.player.setSprinting(false);
      }
   }

   public boolean isReplayingAction() {
      return this.replayingAction;
   }

   @Override
   public void onTickBeforePassengerCheck() {
      this.inputActive = this.hasPlayerInput();
      this.inventoryInputSuppressed = false;
   }

   private boolean usesLegacyClickProtocol() {
      return ProtocolCompatibility.getTargetVersion().olderEq_1_21_5();
   }

   public synchronized void queueOrRunAction(Runnable action) {
      if (!this.replayingAction) {
         if (this.noSlow.guiMoveSetting.getValue() && this.canQueueInCurrentScreen()) {
            this.pendingActions.add(action);
         } else {
            this.runReplayedAction(action);
         }
      }
   }
}

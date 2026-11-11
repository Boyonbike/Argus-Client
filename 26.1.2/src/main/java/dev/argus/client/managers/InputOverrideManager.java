package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.utils.InputUtils;
import java.util.function.Consumer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

public final class InputOverrideManager {
   private final InputOverrideManager.PendingValue<Boolean> pendingSneak = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingJump = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingForward = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingBack = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingLeft = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingRight = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Boolean> pendingSprint = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Float> pendingForwardMovement = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingValue<Float> pendingSidewaysMovement = new InputOverrideManager.PendingValue<>();
   private final InputOverrideManager.PendingFlag sprintBlock = new InputOverrideManager.PendingFlag();

   public InputOverrideManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   public void requestSneak(boolean value) {
      this.pendingSneak.set(value);
   }

   public void requestJump(boolean value) {
      this.pendingJump.set(value);
   }

   public void requestForward(boolean value) {
      this.pendingForward.set(value);
   }

   public void requestBack(boolean value) {
      this.pendingBack.set(value);
   }

   public void requestLeft(boolean value) {
      this.pendingLeft.set(value);
   }

   public void requestRight(boolean value) {
      this.pendingRight.set(value);
   }

   public void requestSprint(boolean value) {
      this.pendingSprint.set(value);
   }

   public void requestForwardMovement(float value) {
      this.pendingForwardMovement.set(value);
   }

   public void requestSidewaysMovement(float value) {
      this.pendingSidewaysMovement.set(value);
   }

   public boolean isSprintBlocked() {
      return this.sprintBlock.isPending();
   }

   public void blockSprint() {
      this.sprintBlock.markPending();
   }

   public Input getInput() {
      if (ClientAccess.argusclientMinecraft.player == null) {
         throw new IllegalStateException("Player is null");
      } else {
         return ClientAccess.argusclientMinecraft.player.input.keyPresses;
      }
   }

   public void requestMovement(float forward, float sideways) {
      this.requestForwardMovement(forward);
      this.requestSidewaysMovement(sideways);
   }

   public void requestReset() {
      this.requestSneak(false);
      this.requestSprint(false);
      this.requestJump(false);
      this.requestForward(false);
      this.requestBack(false);
      this.requestLeft(false);
      this.requestRight(false);
      this.requestMovement(0.0F, 0.0F);
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.sprintBlock.reset();
   }

   @EventHandler(priority = -100)
   private void onKeyboardInputTickEvent(KeyboardInputTickEvent event) {
      ClientInput input = event.getInput();
      this.pendingSneak.consume(value -> InputUtils.setSneak(input, value));
      this.pendingJump.consume(value -> InputUtils.setJump(input, value));
      this.pendingForward.consume(value -> InputUtils.setForward(input, value));
      this.pendingBack.consume(value -> InputUtils.setBack(input, value));
      this.pendingLeft.consume(value -> InputUtils.setLeft(input, value));
      this.pendingRight.consume(value -> InputUtils.setRight(input, value));
      this.pendingSprint.consume(value -> InputUtils.setSprint(input, value));
      this.pendingForwardMovement.consume(value -> InputUtils.setForwardMovement(input, value));
      this.pendingSidewaysMovement.consume(value -> InputUtils.setSidewaysMovement(input, value));
   }

   private static class PendingFlag {
      private boolean pending;

      boolean isPending() {
         return this.pending;
      }

      void markPending() {
         this.pending = true;
      }

      void reset() {
         this.pending = false;
      }
   }

   private static final class PendingValue<T> extends InputOverrideManager.PendingFlag {
      private T value;

      public void set(T value) {
         this.value = value;
         this.markPending();
      }

      void consume(Consumer<T> consumer) {
         if (this.isPending()) {
            consumer.accept(this.value);
            this.reset();
         }
      }
   }
}

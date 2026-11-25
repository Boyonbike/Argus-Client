package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import java.util.function.BooleanSupplier;

/**
 * While a trip runs, the movement keys (walk, jump, sneak, sprint) do nothing unless Freecam is on, so a stray key can't
 * knock the player off the route. The mouse still turns the camera: Baritone's freeLook keeps its steering off the
 * camera, and the powered glide follows Baritone's heading, not where the player looks.
 */
public final class TripInputLock {
   private final BaritoneBridge baritone;
   private final BooleanSupplier freecam;
   private boolean locked;

   public TripInputLock(BaritoneBridge baritone, BooleanSupplier freecam) {
      this.baritone = baritone;
      this.freecam = freecam;
   }

   public void lock() {
      if (locked) return;
      locked = true;
      baritone.setFreeLook(true);
   }

   public void unlock() {
      if (!locked) return;
      locked = false;
      baritone.setFreeLook(false);
   }

   public boolean locked() {
      return locked;
   }

   public void onKeyboardInputTick(KeyboardInputTickEvent event) {
      if (locked && !freecam.getAsBoolean()) event.reset();
   }
}

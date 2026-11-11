package dev.argus.client.events;

import net.minecraft.client.resources.sounds.SoundInstance;

public final class SoundEnginePlayEvent extends CancellableEvent {
   private final SoundInstance soundInstance;

   public SoundInstance getSoundInstance() {
      return this.soundInstance;
   }

   public SoundEnginePlayEvent(SoundInstance soundInstance) {
      this.soundInstance = soundInstance;
   }
}

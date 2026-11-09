package dev.argus.client.audio;

import dev.argus.client.ClientAccess;
import dev.argus.mixin.AbstractSoundInstanceAccessor;
import java.io.ByteArrayInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class SoundClip implements ClientAccess {
   public static final SoundClip NOOP = new SoundClip(null);
   private final byte[] soundBytes;

   public SoundClip(byte[] soundBytes) {
      this.soundBytes = soundBytes;
   }

   public void play() {
      this.play(1.0F);
   }

   public void playAt(Vec3 position) {
      this.playAt(position, 1.0F);
   }

   public void play(float volume) {
      this.playAt(null, volume);
   }

   public void playAt(Vec3 position, float volume) {
      if (this.soundBytes != null) {
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         minecraft.execute(() -> {
            if (minecraft.getSoundManager() != null && (position == null || minecraft.level != null)) {
               try {
                  ByteArrayInputStream stream = new ByteArrayInputStream(this.soundBytes);
                  StreamSoundInstance sound = position == null ? new StreamSoundInstance(stream) : new StreamSoundInstance(position, stream);
                  ((AbstractSoundInstanceAccessor)sound).setVolume(volume);
                  minecraft.getSoundManager().play(sound);
               } catch (Exception failure) {
                  ClientAccess.logger.warn("Could not play sound", failure);
               }
            }
         });
      }
   }
}

package dev.argus.client.modules.misc;

import dev.argus.client.events.EventHandler;
import dev.argus.client.events.SoundEnginePlayEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import java.util.Set;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class SoundBlockerModule extends Module {
   private final Setting<Set<SoundEvent>> soundsSetting = this.add(new RegistrySetSetting<>("Sounds", BuiltInRegistries.SOUND_EVENT));

   public SoundBlockerModule() {
      super("SoundBlocker", "Blocks certain sounds from being played.", ModuleCategory.MISC);
   }

   @EventHandler
   private void onSoundEnginePlayEvent(SoundEnginePlayEvent event) {
      for (SoundEvent sound : this.soundsSetting.getValue()) {
         Identifier blockedIdentifier = sound.location();
         SoundInstance playingSound = event.getSoundInstance();
         if (blockedIdentifier.equals(playingSound.getIdentifier())) {
            event.cancel();
            break;
         }
      }
   }
}

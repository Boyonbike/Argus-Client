package dev.argus.client.modules.hud;

import dev.argus.client.modules.Module;
import net.minecraft.network.chat.Component;

public class ModuleListHudRow extends HudTextRow {
   private final Module module;

   public ModuleListHudRow(ModuleListModule owner, Module module) {
      super(() -> {
         String infoText = module.getInfoString();
         return Component.literal(infoText);
      }, () -> module.isToggled() && module.isDrawn() && (!module.getKeybind().isUnbound() || !owner.onlyBoundSetting.getValue()));
      this.module = module;
   }

   public Module getModule() {
      return this.module;
   }
}

package dev.argus.client.settings.options;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.modules.render.ChamsModule;
import dev.argus.client.render.ChamsColorProvider;
import dev.argus.client.settings.NamedEntry;
import java.awt.Color;
import net.minecraft.world.entity.Entity;

public enum ChamsStyle implements ChamsColorProvider, NamedEntry {
   BOTH("Both"),
   FILL("Fill") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         return new Color[]{TRANSPARENT_COLOR, chams.fillSetting.getValue()};
      }
   },
   LINE("Line") {
      @Override
      public Color[] getColors(ChamsModule chams) {
         return new Color[]{chams.outlineSetting.getValue(), TRANSPARENT_COLOR};
      }
   },
   PLAIN("Plain") {
      @Override
      public void renderEntity(ChamsModule chams, Entity entity, PoseStack poseStack) {
      }
   },
   OFF("Off");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChamsStyle(String displayName) {
      this.displayName = displayName;
   }

   @Override
   public boolean isEnabled() {
      return this != OFF;
   }
}

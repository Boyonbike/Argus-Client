package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;

public abstract class TracerRenderer implements ClientAccess {
   protected final TracersModule module;

   public TracerRenderer(TracersModule module) {
      this.module = module;
   }

   public abstract void render(PoseStack var1, float var2);
}

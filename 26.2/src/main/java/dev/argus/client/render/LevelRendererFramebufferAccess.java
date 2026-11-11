package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface LevelRendererFramebufferAccess {
   void argusclient$popFramebuffer();

   void argusclient$pushFramebuffer(RenderTarget var1);
}

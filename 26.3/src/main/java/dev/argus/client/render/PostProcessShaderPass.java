package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RenderTargetRegistry;
import dev.argus.client.managers.ShaderRegistry;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fStack;

public class PostProcessShaderPass extends BaseShaderPass {
   private static final Identifier OVERLAY_TEXTURE;
   public static final RenderTarget POST_TARGET = RenderTargetRegistry.createRenderTarget("post");
   protected static int overlayTextureId = -1;

   @Override
   public void updateUniforms() {
      this.uniforms.set("u_Proj", RenderUtils.WORLD_PROJECTION_MATRIX);
      Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
      this.uniforms.set("u_ModelView", modelViewMatrix);
      float capturedWidth = ClientAccess.argusclientMinecraft.getWindow().getWidth();
      float height = ClientAccess.argusclientMinecraft.getWindow().getHeight();
      this.uniforms.set("u_Size", capturedWidth, height);
      this.uniforms.set("u_Time", (float)(System.nanoTime() / 1.0e9));
      this.uniforms.set("u_Texture", POST_TARGET.getColorTextureView(),
         RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR));
   }

   public PostProcessShaderPass(String vertexResource, String fragmentResource) {
      super(vertexResource, fragmentResource);
   }

   @Override
   public void render(Runnable draw) {
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      RenderUtils.clearColor(POST_TARGET);
      RenderUtils.clearDepth(POST_TARGET);
      this.renderToTarget(draw);
      if (buffers.hasColor()) {
         buffers.reset();
         this.prepareOutput();
         this.updateUniforms();
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         this.program.pipeline().draw(encoder, ClientAccess.argusclientMinecraft.gameRenderer.mainRenderTarget());
         // No submit(): it is the per-frame boundary (Minecraft calls it once); mid-frame it recycles transient uniform memory.
      }
   }

   protected void prepareOutput() {
   }

   protected void renderToTarget(Runnable draw) {
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      RenderTargetUtils.overrideRenderTargets(POST_TARGET);
      ClientContext.shaderRegistry.renderingIntoTarget = true;
      draw.run();
      buffers.endBatchAndClean();
      ClientContext.shaderRegistry.renderingIntoTarget = false;
      RenderTargetUtils.restoreRenderTargets();
   }

   public static GpuTextureView overlayView() {
      return overlayTexture().getTextureView();
   }

   public static GpuSampler overlaySampler() {
      return overlayTexture().getSampler();
   }

   private static AbstractTexture overlayTexture() {
      TextureManager textureManager = ClientAccess.argusclientMinecraft.getTextureManager();
      return textureManager.getTexture(OVERLAY_TEXTURE);
   }

   static {
      String overlayPath = "textures/overlay.png";
      String overlayNamespace = "argusclient-mount";
      OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath(overlayNamespace, overlayPath);
   }
}

package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RenderTargetRegistry;
import java.awt.Color;
import net.minecraft.client.Minecraft;

public final class MaskShaderRenderer implements ClientAccess {
   public static final RenderTarget MASK_TARGET = RenderTargetRegistry.createRenderTarget("argusclient_blur");
   public static final RenderTarget HORIZONTAL_TARGET = RenderTargetRegistry.createRenderTarget("argusclient_blur_horizontal");
   public static final RenderTarget VERTICAL_TARGET = RenderTargetRegistry.createRenderTarget("argusclient_blur_vertical");
   private static float blurWidth;
   private static float bloom;
   private static float opacity;
   private static float modulate;

   public static float getBlurWidth() {
      return blurWidth;
   }

   public static float getBloom() {
      return bloom;
   }

   public static float getModulate() {
      return modulate;
   }

   public static void setParameters(float width, float bloomStrength, float maskOpacity, float modulation) {
      blurWidth = width;
      bloom = bloomStrength;
      opacity = maskOpacity;
      modulate = modulation;
   }

   public static float getOpacity() {
      return opacity;
   }

   public static void renderBlur(Runnable drawMask, float width) {
      blurWidth = width;
      captureMask(drawMask);
      renderEffect("blur_mask");
   }

   public static void captureMask(Runnable drawMask) {
      captureMask(drawMask, Color.WHITE);
   }

   public static void captureMask(Runnable drawMask, Color clearColor) {
      RenderSystem.assertOnRenderThread();
      RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      if (drawMask != null) {
         RenderUtils.clearColor(MASK_TARGET, clearColor);
         RenderUtils.clearDepth(MASK_TARGET);
         RenderTargetUtils.setMainRenderTarget(MASK_TARGET);
         drawMask.run();
         RenderTargetUtils.setMainRenderTarget(mainTarget);
      }
   }

   public static void renderEffect(String effectId) {
      ClientContext.shaderRegistry.render(effectId, () -> {});
   }
}

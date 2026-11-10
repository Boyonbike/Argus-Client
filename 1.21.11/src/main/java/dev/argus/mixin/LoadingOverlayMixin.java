package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.argus.client.render.LoadingOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
   @ModifyArg(method = "registerTextures", at = @At(value = "INVOKE", target =
         "Lnet/minecraft/client/renderer/texture/TextureManager;registerAndLoad(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/ReloadableTexture;)V"), index = 1)
   private static ReloadableTexture useArgusLogo(ReloadableTexture original) {
      return new LoadingOverlayRenderer();
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target =
         "Ljava/util/function/IntSupplier;getAsInt()I"), require = 3, allow = 3)
   private int useArgusBackground(int original) {
      return 0xFF252D31;
   }

   @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
         "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
         require = 2, allow = 2)
   private void drawArgusLogo(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
         float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth,
         int textureHeight, int color, Operation<Void> original) {
      // Vanilla draws two slices of its logo. Draw the entire Argus emblem in the first pass.
      if (v == 0.0F) {
         LoadingOverlayRenderer.render(graphics, color);
      }
   }
}

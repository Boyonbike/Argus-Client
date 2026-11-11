package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.argus.client.render.LoadingOverlayRenderer;
import dev.argus.client.render.loading.GuiLoadingCanvas;
import dev.argus.client.render.loading.LoadingAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
   @Shadow
   private float currentProgress;
   @Shadow
   private long fadeOutStart;

   @Unique
   private final LoadingAnimation argus$animation = new LoadingAnimation();

   @ModifyArg(method = "registerTextures", at = @At(value = "INVOKE", target =
         "Lnet/minecraft/client/renderer/texture/TextureManager;registerAndLoad(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/ReloadableTexture;)V"), index = 1)
   private static ReloadableTexture useArgusLogo(ReloadableTexture original) {
      return new LoadingOverlayRenderer();
   }

   @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target =
         "Ljava/util/function/IntSupplier;getAsInt()I"), require = 3, allow = 3)
   private int useArgusBackground(int original) {
      return LoadingAnimation.BACKGROUND;
   }

   @Inject(method = "extractRenderState", at = @At("HEAD"))
   private void trackMouse(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      this.argus$animation.mouse(mouseX, mouseY);
   }

   @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target =
         "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
         require = 2, allow = 2)
   private void drawArgusLoadingScreen(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
         float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth,
         int textureHeight, int color, Operation<Void> original) {
      // Vanilla draws two halves of its logo; render the whole animated screen on the first one.
      if (v == 0.0F) {
         float fadeOut = this.fadeOutStart > -1L ? (Util.getMillis() - this.fadeOutStart) / 1000.0F : -1.0F;
         this.argus$animation.render(new GuiLoadingCanvas(graphics), (color >>> 24) / 255.0F, this.currentProgress, fadeOut,
               Util.getNanos());
      }
   }

   @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
   private void hideVanillaProgressBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
      // The animated screen draws its own progress bar.
      ci.cancel();
   }
}

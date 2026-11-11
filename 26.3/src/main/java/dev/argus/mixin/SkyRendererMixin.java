package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.SkyboxModule;
import dev.argus.client.render.MaskShaderRenderer;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   @Unique
   private static boolean hook = false;

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/dimension/DimensionType;skybox()Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"
      )
   )
   private Skybox overrideDimensionSkybox(Skybox originalSkybox) {
      if (skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         return switch ((SkyboxModule.SkyType)skyboxModule.typeSetting.getValue()) {
            case NONE -> originalSkybox;
            case OVERWORLD -> Skybox.OVERWORLD;
            case FLAT -> Skybox.NONE;
            case END -> Skybox.END;
         };
      } else {
         return originalSkybox;
      }
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void overrideSkyRenderState(ClientLevel level, float partialTick, Camera camera, SkyRenderState renderState, CallbackInfo callback) {
      if (skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         renderState.skyColor = skyboxModule.skySetting.getValue().hashCode();
         if (skyboxModule.typeSetting.getValue() == SkyboxModule.SkyType.FLAT) {
            renderState.shouldRenderDarkDisc = false;
         }
      }
   }

   @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
   private void hideSun(float brightness, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule.shouldHideCelestialBodies()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
   private void hideMoon(MoonPhase phase, float brightness, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule.shouldHideCelestialBodies()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
   private void hideStars(float brightness, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule.shouldHideStars()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderStars", at = @At("TAIL"))
   private void renderFancyStarBloom(float brightness, PoseStack poses, CallbackInfo callback) {
      if (skyboxModule.isToggled() && skyboxModule.fancyStarsSetting.getValue() && !hook) {
         this.renderStarsToBloomMask(brightness, poses);
      }
   }

   @ModifyArgs(
      method = "renderStars",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )
   )
   public void overrideStarTint(Args arguments) {
      if (skyboxModule.isToggled() && skyboxModule.fancyStarsSetting.getValue()) {
         Color starColor = skyboxModule.starsColorSetting.getValue();
         float originalAlpha = ((Vector4f)arguments.get(1)).w();
         arguments.set(1, new Vector4f(starColor.getRed() / 255.0F, starColor.getGreen() / 255.0F, starColor.getBlue() / 255.0F, originalAlpha));
      }
   }

   @Unique
   private void renderStarsToBloomMask(float brightness, PoseStack poses) {
      RenderTarget previousTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      RenderUtils.clearColor(MaskShaderRenderer.MASK_TARGET, skyboxModule.starsColorSetting.getValue());
      RenderUtils.clearDepth(MaskShaderRenderer.MASK_TARGET);
      RenderTargetUtils.setMainRenderTarget(MaskShaderRenderer.MASK_TARGET);
      hook = true;
      this.renderStars(brightness, poses);
      hook = false;
      RenderTargetUtils.setMainRenderTarget(previousTarget);
      MaskShaderRenderer.setParameters(4.0F, 1.5F, 1.0F, 2.0F);
      MaskShaderRenderer.renderEffect("bloom_mask");
   }

   @Shadow
   private void renderStars(float brightness, PoseStack poses) {
   }
}

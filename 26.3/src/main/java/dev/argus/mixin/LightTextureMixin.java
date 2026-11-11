package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.AmbienceModule;
import dev.argus.client.render.ClientRenderPipelines;
import java.awt.Color;
import net.minecraft.client.renderer.Lightmap;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Lightmap.class)
public class LightTextureMixin {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);
   @Unique
   private static final Vector3f lightmapTint = new Vector3f();

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderPipelines;LIGHTMAP:Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;")
   )
   private RenderPipeline overrideLightmapPipeline(RenderPipeline originalPipeline) {
      if (ambienceModule.isToggled() && ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN) {
         return ClientRenderPipelines.AMBIENCE;
      } else {
         return ambienceModule.isToggled() && ambienceModule.occlusionSetting.getValue() && ambienceModule.occlusionSetting.isVisible()
            ? ClientRenderPipelines.OCCLUSION
            : originalPipeline;
      }
   }

   @ModifyArgs(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
         ordinal = 1
      )
   )
   private void overrideLightmapTint(Args arguments) {
      boolean tintEnabled = ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN
         || ambienceModule.occlusionSetting.getValue() && ambienceModule.occlusionSetting.isVisible();
      Color tintColor = ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN
         ? ambienceModule.colorSetting.getValue()
         : ambienceModule.occlusionColorSetting.getValue();
      if (ambienceModule.isToggled() && tintEnabled) {
         arguments.set(0, lightmapTint.set(tintColor.getRed(), tintColor.getGreen(), tintColor.getBlue()).div(255.0F));
      }
   }
}

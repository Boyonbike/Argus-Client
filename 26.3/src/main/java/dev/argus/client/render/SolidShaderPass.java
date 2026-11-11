package dev.argus.client.render;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.ShaderModule;
import dev.argus.client.settings.Setting;
import java.awt.Color;

public class SolidShaderPass extends PreprocessedShaderPass {
   private static final ShaderModule shaderModule = ClientContext.getModule(ShaderModule.class);

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      ShaderUniforms capturedProgramForImageEnabled = this.uniforms;
      boolean imageEnabled = shaderModule.imageSetting.getValue();
      capturedProgramForImageEnabled.set("u_Image", imageEnabled);
      ShaderUniforms capturedProgramForOverlaySampler = this.uniforms;
      capturedProgramForOverlaySampler.set("u_Overlay", PostProcessShaderPass.overlayView(), PostProcessShaderPass.overlaySampler());
      ShaderUniforms capturedProgramForOverlayAlpha = this.uniforms;
      ShaderModule capturedShaderForOverlayAlpha = shaderModule;
      float overlayAlpha = capturedShaderForOverlayAlpha.getOverlayAlpha();
      capturedProgramForOverlayAlpha.set("u_OverlayAlpha", overlayAlpha);
      ShaderUniforms capturedProgramForLineWidth = this.uniforms;
      ShaderModule capturedShaderForHandOutline = shaderModule;
      int lineWidth;
      if (capturedShaderForHandOutline.shouldSkipHandOutline()) {
         lineWidth = 0;
      } else {
         lineWidth = shaderModule.lineWidthSetting.getValue();
      }

      capturedProgramForLineWidth.set("u_Width", lineWidth);
      ShaderUniforms capturedProgramForFastLinesEnabled = this.uniforms;
      boolean fastLinesEnabled = shaderModule.fastLinesSetting.getValue() || ClientContext.shaderRegistry.renderingHands;
      capturedProgramForFastLinesEnabled.set("u_FastLines", fastLinesEnabled);
      ShaderUniforms capturedProgramForShapeMode = this.uniforms;
      capturedProgramForShapeMode.set("u_ShapeMode", 2);
      ShaderUniforms capturedProgramForGlowWeight = this.uniforms;
      float glowWeight = shaderModule.weightSetting.getValue();
      capturedProgramForGlowWeight.set("u_Glow", glowWeight);
      ShaderUniforms capturedProgramForFillColor = this.uniforms;
      ShaderModule capturedShaderForHandFill = shaderModule;
      Color selectedFillColor;
      if (capturedShaderForHandFill.shouldSkipHandFill()) {
         selectedFillColor = ColorUtils.TRANSPARENT;
      } else {
         ShaderModule capturedShaderForClusterAlpha = shaderModule;
         Color fillColorValue = shaderModule.fillSetting.getValue();
         selectedFillColor = capturedShaderForClusterAlpha.applyClusterAlpha(fillColorValue);
      }

      Color fillColor = selectedFillColor;
      capturedProgramForFillColor.set("u_FillColor", fillColor);
      ShaderUniforms capturedProgramForOutlineColor = this.uniforms;
      Color outlineColor = shaderModule.outlineColorSetting.getValue();
      capturedProgramForOutlineColor.set("u_OutlineColor", outlineColor);
      ShaderUniforms capturedProgramForDecoratorKind = this.uniforms;
      ShaderModule.TypeChoice decoratorTypeValue = shaderModule.typeSetting.getValue();
      int decoratorKind = decoratorTypeValue.ordinal();
      capturedProgramForDecoratorKind.set("u_Dots", decoratorKind);
      ShaderUniforms capturedProgramForDecoratorRadius = this.uniforms;
      Integer decoratorRadiusValue = shaderModule.radiusSetting.getValue();
      int decoratorRadius = decoratorRadiusValue;
      capturedProgramForDecoratorRadius.set("u_DotsRadius", decoratorRadius);
      ShaderUniforms capturedProgramForDecoratorAlpha = this.uniforms;
      Setting<Float> decoratorAlphaOption = shaderModule.alphaSetting;
      Float decoratorAlphaValue = decoratorAlphaOption.getValue();
      float decoratorAlpha = decoratorAlphaValue;
      capturedProgramForDecoratorAlpha.set("u_DotsAlpha", decoratorAlpha);
   }

   public SolidShaderPass() {
      super("base.vert", "solid.frag", DISTANCE_PASS);
      this.initializeProgram();
   }
}

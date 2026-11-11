package dev.argus.client.render;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.render.ShaderModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.UncheckedExceptions;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class CustomShaderPass extends PreprocessedShaderPass implements ReloadableShaderPass {
   private static final ShaderModule shaderModule = ClientContext.getModule(ShaderModule.class);

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      GL13.glActiveTexture(33991);
      int overlayTextureId = PostProcessShaderPass.overlayTextureId;
      GL11.glBindTexture(3553, overlayTextureId);
      ShaderUniforms capturedProgramForImageEnabled = this.uniforms;
      boolean imageEnabled = shaderModule.imageSetting.getValue();
      capturedProgramForImageEnabled.set("u_Image", imageEnabled);
      ShaderUniforms capturedProgramForOverlaySampler = this.uniforms;
      capturedProgramForOverlaySampler.set("u_Overlay", 7);
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
      ShaderUniforms capturedProgramForInnerGlowEnabled = this.uniforms;
      boolean innerGlowEnabled = shaderModule.innerSetting.getValue();
      capturedProgramForInnerGlowEnabled.set("u_GlowInner", innerGlowEnabled);
      ShaderUniforms capturedProgramForGlowQuality = this.uniforms;
      int glowQuality = shaderModule.qualitySetting.getValue();
      capturedProgramForGlowQuality.set("u_GlowQuality", glowQuality);
      ShaderUniforms capturedProgramForFillColor = this.uniforms;
      ShaderModule capturedShaderForHandFill = shaderModule;
      Color selectedFillColor;
      if (capturedShaderForHandFill.shouldSkipHandFill()) {
         Setting<Color> outlineColorOption = shaderModule.outlineColorSetting;
         selectedFillColor = ColorUtils.transparent(outlineColorOption.getValue());
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
      Setting<ShaderModule.TypeChoice> decoratorTypeOption = shaderModule.typeSetting;
      ShaderModule.TypeChoice decoratorTypeValue = decoratorTypeOption.getValue();
      int decoratorKind = decoratorTypeValue.ordinal();
      capturedProgramForDecoratorKind.set("u_Dots", decoratorKind);
      ShaderUniforms capturedProgramForDecoratorRadius = this.uniforms;
      Setting<Integer> decoratorRadiusOption = shaderModule.radiusSetting;
      Integer decoratorRadiusValue = decoratorRadiusOption.getValue();
      int decoratorRadius = decoratorRadiusValue;
      capturedProgramForDecoratorRadius.set("u_DotsRadius", decoratorRadius);
      ShaderUniforms capturedProgramForDecoratorAlpha = this.uniforms;
      Setting<Float> decoratorAlphaOption = shaderModule.alphaSetting;
      Float decoratorAlphaValue = decoratorAlphaOption.getValue();
      float decoratorAlpha = decoratorAlphaValue;
      capturedProgramForDecoratorAlpha.set("u_DotsAlpha", decoratorAlpha);
      ShaderUniforms capturedProgramForGlowWeight = this.uniforms;
      Setting<Float> glowWeightOption = shaderModule.weightSetting;
      Float glowWeightValue = glowWeightOption.getValue();
      float glowWeight = glowWeightValue;
      capturedProgramForGlowWeight.set("u_Glow", glowWeight);
      ShaderUniforms capturedProgramForAnimationTime = this.uniforms;
      float elapsedMillis = (float)(System.currentTimeMillis() - START_TIME_MILLIS);
      float animationTime = elapsedMillis / 1000.0F;
      capturedProgramForAnimationTime.set("u_Time", animationTime);
      ShaderUniforms capturedProgramForFadeEnabled = this.uniforms;
      Setting<Float> fadeOption = shaderModule.fadeSetting;
      boolean fadeEnabled = !fadeOption.isAtMaximum();
      capturedProgramForFadeEnabled.set("u_Fade", fadeEnabled);
   }

   @Override
   public CompiledShaderProgram compileProgram() {
      Path shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY;
      Path customFragmentPath = shaderDirectory.resolve("custom.frag");
      String vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource);
      String fragmentSource = RenderTargetUtils.readShaderSource(this.fragmentResource);

      String customMainImage;
      try {
         customMainImage = Files.readString(customFragmentPath);
      } catch (Throwable failure) {
         throw UncheckedExceptions.rethrow(failure);
      }

      String templateFragmentSource = fragmentSource;
      fragmentSource = templateFragmentSource.replaceFirst("vec4\\s+mainImage\\s*\\(\\s*bool\\s+outline\\s*\\)\\s*\\{[\\s\\S]*?\\}", customMainImage);
      return CompiledShaderProgram.compile(vertexSource, fragmentSource);
   }

   public CustomShaderPass() {
      super("base.vert", "custom.frag", COVERAGE_PASS);
      this.reload(this);
   }
}

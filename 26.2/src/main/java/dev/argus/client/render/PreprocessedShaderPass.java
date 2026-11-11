package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RenderTargetRegistry;
import dev.argus.client.modules.render.ShaderModule;

public abstract class PreprocessedShaderPass extends PostProcessShaderPass {
   private static final ShaderModule shaderModule = ClientContext.getModule(ShaderModule.class);
   private static final RenderTarget PRE_TARGET = RenderTargetRegistry.createRenderTarget("argusclient_pre_pass");
   protected static final PreprocessedShaderPass.PrePass COVERAGE_PASS = new PreprocessedShaderPass.PrePass("multi/coverage.frag", "u_Coverage", true);
   protected static final PreprocessedShaderPass.PrePass DISTANCE_PASS = new PreprocessedShaderPass.PrePass("multi/distance.frag", "u_Distance", false);
   private final PreprocessedShaderPass.PrePass prePass;

   protected PreprocessedShaderPass(String vertexResource, String fragmentResource, PreprocessedShaderPass.PrePass prePass) {
      super(vertexResource, fragmentResource);
      this.prePass = prePass;
   }

   @Override
   protected void prepareOutput() {
      if (!shaderModule.shouldSkipHandOutline() && shaderModule.lineWidthSetting.getValue() > 0 && this.prePass != null) {
         this.prePass.apply(POST_TARGET);
      }
   }

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      if (this.prePass != null) {
         this.uniforms.set(this.prePass.samplerName, 16, PRE_TARGET);
      }
   }

   protected static final class PrePass extends RenderTargetShaderStage {
      private final String samplerName;
      private final boolean coverage;

      private PrePass(String fragmentResource, String samplerName, boolean coverage) {
         super("base.vert", fragmentResource, PreprocessedShaderPass.PRE_TARGET);
         this.samplerName = samplerName;
         this.coverage = coverage;
      }

      @Override
      public void updateUniforms() {
         super.updateUniforms();
         this.uniforms.set("u_Width", PreprocessedShaderPass.shaderModule.lineWidthSetting.getValue());
         if (this.coverage) {
            this.uniforms.set("u_Step", PreprocessedShaderPass.shaderModule.qualitySetting.getValue());
         }
      }
   }
}

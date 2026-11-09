package dev.argus.client.render;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.ESPModule;

public class ESPOutlineShaderPass extends PostProcessShaderPass {
   private static final ESPModule espModule = ClientContext.getModule(ESPModule.class);

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      ShaderUniforms widthUniforms = this.uniforms;
      widthUniforms.set("u_Width", 2);
      ShaderUniforms glowUniforms = this.uniforms;
      glowUniforms.set("u_GlowMultiplier", 1.0F);
      ShaderUniforms fillOpacityUniforms = this.uniforms;
      float fillOpacity = espModule.blockFillOpacitySetting.getValue();
      fillOpacityUniforms.set("u_FillOpacity", fillOpacity);
   }

   public ESPOutlineShaderPass() {
      super("base.vert", "outline.frag");
      this.initializeProgram();
   }
}

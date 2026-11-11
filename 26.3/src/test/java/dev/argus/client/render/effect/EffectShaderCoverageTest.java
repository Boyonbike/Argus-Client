package dev.argus.client.render.effect;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EffectShaderCoverageTest {
   private static final List<String> SHADERS = List.of(
      "base.vsh", "bloom.fsh", "gradient.fsh", "rainbow.fsh", "solid.fsh",
      "custom.fsh", "outline.fsh", "blur_mask.fsh",
      "multi/bloom_mask.fsh", "multi/blur_mask.fsh", "multi/coverage.fsh",
      "multi/distance.fsh", "multi/dof.fsh", "multi/glowy.fsh",
      "sky/base.vsh", "sky/sky.fsh", "shulker_icon.vert", "shulker_icon.frag",
      "custom/example.frag", "util/util.frag", "util/blur_util.frag");

   @Test
   void everyEffectShaderExists() {
      for (String path : SHADERS) {
         assertNotNull(EffectShaderCoverageTest.class.getResourceAsStream(
            "/assets/argusclient/shaders/" + path), path);
      }
   }

   @Test
   void shadersDoNotUseLegacyUniformLocations() throws Exception {
      for (String path : SHADERS) {
         try (InputStream in = EffectShaderCoverageTest.class.getResourceAsStream(
               "/assets/argusclient/shaders/" + path)) {
            String text = new String(in.readAllBytes());
            assertFalse(text.contains("glGetUniformLocation"), path);
         }
      }
   }
}

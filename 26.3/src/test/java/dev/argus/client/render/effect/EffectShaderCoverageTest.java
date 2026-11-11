package dev.argus.client.render.effect;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EffectShaderCoverageTest {
   private static final List<String> SHADERS = List.of(
      "base.vert", "bloom.frag", "gradient.frag", "rainbow.frag", "solid.frag",
      "custom.frag", "outline.frag", "blur_mask.frag",
      "multi/bloom_mask.frag", "multi/blur_mask.frag", "multi/coverage.frag",
      "multi/distance.frag", "multi/dof.frag", "multi/glowy.frag",
      "sky/base.vert", "sky/sky.frag", "shulker_icon.vert", "shulker_icon.frag",
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

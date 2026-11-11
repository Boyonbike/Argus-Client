package dev.argus.client.render;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlintTintTest {
   @Test
   void defaultIsWhite() {
      Vector4f colour = GlintTint.colour(false, null, false, null, 255);
      assertEquals(1.0F, colour.x);
      assertEquals(1.0F, colour.y);
      assertEquals(1.0F, colour.z);
      assertEquals(1.0F, colour.w);
   }

   @Test
   void glintModuleColourIsApplied() {
      Vector4f colour = GlintTint.colour(true, new Vector4f(0.5F, 0.25F, 1.0F, 0.8F), false, null, 255);
      assertEquals(0.5F, colour.x);
      assertEquals(0.25F, colour.y);
      assertEquals(1.0F, colour.z);
      assertEquals(0.8F, colour.w);
   }

   @Test
   void chamsBatchOverridesColour() {
      Vector4f colour = GlintTint.colour(true, new Vector4f(0.5F, 0.5F, 0.5F, 0.5F), true, new Vector3f(0.1F, 0.2F, 0.3F), 255);
      assertEquals(0.1F, colour.x);
      assertEquals(0.2F, colour.y);
      assertEquals(0.3F, colour.z);
      assertEquals(1.0F, colour.w);
   }

   @Test
   void renderAlphaMultiplies() {
      Vector4f colour = GlintTint.colour(false, null, false, null, 128);
      assertEquals(128.0F / 255.0F, colour.w, 0.0001F);
   }
}

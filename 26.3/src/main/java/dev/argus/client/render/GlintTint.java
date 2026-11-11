package dev.argus.client.render;

import org.joml.Vector3f;
import org.joml.Vector4f;

/** Pure colour math for the glint tint restored on 26.2's RenderType.prepare path. */
public final class GlintTint {
   private GlintTint() {
   }

   public static Vector4f colour(boolean glintToggled, Vector4f glintColour, boolean chamsBatch, Vector3f chamsColour, int renderAlpha) {
      Vector4f result = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
      if (glintToggled && glintColour != null) {
         result.mul(glintColour);
      }
      if (chamsBatch && chamsColour != null) {
         result.set(chamsColour.x, chamsColour.y, chamsColour.z, 1.0F);
      }
      if (renderAlpha != 255) {
         result.w *= Math.max(0, Math.min(255, renderAlpha)) / 255.0F;
      }
      return result;
   }
}

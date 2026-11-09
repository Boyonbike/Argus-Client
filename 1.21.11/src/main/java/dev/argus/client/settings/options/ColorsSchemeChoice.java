package dev.argus.client.settings.options;

import dev.argus.client.render.ColorUtils;
import dev.argus.client.settings.NamedEntry;
import java.awt.Color;
import net.minecraft.util.ARGB;

public enum ColorsSchemeChoice implements NamedEntry {
   NORMAL("Normal", null),
   PROTANOPIA("Protanopia", ColorsSchemeChoice.TransformMatrices.PROTANOPIA),
   DEUTERANOPIA("Deuteranopia", ColorsSchemeChoice.TransformMatrices.DEUTERANOPIA),
   TRITANOPIA("Tritanopia", ColorsSchemeChoice.TransformMatrices.TRITANOPIA);

   private final String name;
   private final float[][] matrix;

   ColorsSchemeChoice(String name, float[][] matrix) {
      this.name = name;
      this.matrix = matrix;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public int transform(int argb) {
      return this.matrix == null ? argb : ARGB.color(ARGB.alphaFloat(argb), this.applyMatrix(argb, this.matrix));
   }

   public Color transform(Color color) {
      if (this.matrix == null) {
         return color;
      }

      Color transformed = new Color(this.applyMatrix(color.hashCode(), this.matrix));
      return ColorUtils.withAlpha(transformed, color.getAlpha());
   }

   private int applyMatrix(int argb, float[][] matrix) {
      int alpha = argb >>> 24 & 0xFF;
      int red = argb >>> 16 & 0xFF;
      int green = argb >>> 8 & 0xFF;
      int blue = argb & 0xFF;
      int transformedRed = Math.clamp((int)(red * matrix[0][0] + green * matrix[0][1] + blue * matrix[0][2]), 0, 255);
      int transformedGreen = Math.clamp((int)(red * matrix[1][0] + green * matrix[1][1] + blue * matrix[1][2]), 0, 255);
      int transformedBlue = Math.clamp((int)(red * matrix[2][0] + green * matrix[2][1] + blue * matrix[2][2]), 0, 255);
      return alpha << 24 | transformedRed << 16 | transformedGreen << 8 | transformedBlue;
   }

   private static class TransformMatrices {
      public static final float[][] PROTANOPIA = new float[][]{{0.567F, 0.433F, 0.0F}, {0.558F, 0.442F, 0.0F}, {0.0F, 0.242F, 0.758F}};
      public static final float[][] DEUTERANOPIA = new float[][]{{0.625F, 0.375F, 0.0F}, {0.7F, 0.3F, 0.0F}, {0.0F, 0.3F, 0.7F}};
      public static final float[][] TRITANOPIA = new float[][]{{0.95F, 0.05F, 0.0F}, {0.0F, 0.433F, 0.567F}, {0.0F, 0.475F, 0.525F}};
   }
}

package dev.argus.client.render.loading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.render.loading.LoadingAtlas.Region;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoadingAtlasTest {
   private static LoadingAtlas atlas;
   private static List<Region> regions;
   private static List<String> names;

   @BeforeAll
   static void build() throws Exception {
      atlas = LoadingAtlas.build(pixels("/assets/argusclient/textures/argusclient.png", 256),
            pixels("/assets/argusclient/textures/loading_glyphs.png", 512));
      regions = new ArrayList<>();
      names = new ArrayList<>();
      for (Field field : LoadingAtlas.class.getFields()) {
         if (field.getType() == Region.class && Modifier.isStatic(field.getModifiers())) {
            regions.add((Region) field.get(null));
            names.add(field.getName());
         }
      }
   }

   @Test
   void regionsStayInsideTheAtlasAndNeverOverlap() {
      for (int i = 0; i < regions.size(); i++) {
         Region a = regions.get(i);
         assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.w() <= LoadingAtlas.WIDTH && a.y() + a.h() <= LoadingAtlas.HEIGHT,
               names.get(i) + " is outside the atlas");
         for (int j = i + 1; j < regions.size(); j++) {
            Region b = regions.get(j);
            // Regions with opaque edges need a one-texel gutter so neighbours' filtering never reads them.
            int gap = opaqueEdges(a) || opaqueEdges(b) ? 1 : 0;
            boolean overlap = a.x() - gap < b.x() + b.w() && b.x() < a.x() + a.w() + gap
                  && a.y() - gap < b.y() + b.h() && b.y() < a.y() + a.h() + gap;
            assertFalse(overlap, names.get(i) + " overlaps or touches " + names.get(j));
         }
      }
   }

   @Test
   void softSpritesHaveTransparentBordersSoFilteringNeverBleeds() {
      // Bilinear sampling at a quad's edge reads half a texel outside its region.
      for (int i = 0; i < regions.size(); i++) {
         Region r = regions.get(i);
         if (opaqueEdges(r)) {
            continue;
         }
         for (int x = r.x(); x < r.x() + r.w(); x++) {
            assertEquals(0, alpha(x, r.y()), names.get(i) + " top edge");
            assertEquals(0, alpha(x, r.y() + r.h() - 1), names.get(i) + " bottom edge");
         }
         for (int y = r.y(); y < r.y() + r.h(); y++) {
            assertEquals(0, alpha(r.x(), y), names.get(i) + " left edge");
            assertEquals(0, alpha(r.x() + r.w() - 1, y), names.get(i) + " right edge");
         }
      }
   }

   @Test
   void eyeIsDetectedInTheLogo() {
      assertTrue(atlas.eyeValid, "eye socket flood fill did not match the logo art");
      assertTrue(atlas.eyeTop < LoadingAtlas.IRIS_CY && atlas.eyeBottom > LoadingAtlas.IRIS_CY);
      int row = Math.round(LoadingAtlas.IRIS_CY);
      assertTrue(atlas.rowMinX[row] < LoadingAtlas.IRIS_CX && atlas.rowMaxX[row] > LoadingAtlas.IRIS_CX);
   }

   @Test
   void wordmarkGlyphsAreBaked() {
      assertTrue(atlas.glyphsValid);
      for (char c : "ARGUSLOADINGREADY0123456789%".toCharArray()) {
         int index = LoadingAtlas.glyphIndex(c);
         assertTrue(index >= 0 && atlas.glyphInkRight[index] > atlas.glyphInkLeft[index], "missing glyph " + c);
      }
   }

   /** Blocks sampled from their interior only, or deliberately opaque at the edges. */
   private static boolean opaqueEdges(Region r) {
      return r == LoadingAtlas.WHITE || r == LoadingAtlas.GRADIENT || r == LoadingAtlas.VIGNETTE;
   }

   private static int alpha(int x, int y) {
      return atlas.pixels[y * LoadingAtlas.WIDTH + x] >>> 24;
   }

   private static int[] pixels(String path, int size) throws Exception {
      try (InputStream stream = LoadingAtlasTest.class.getResourceAsStream(path)) {
         BufferedImage image = ImageIO.read(stream);
         assertEquals(size, image.getWidth());
         return image.getRGB(0, 0, size, size, null, 0, size);
      }
   }
}

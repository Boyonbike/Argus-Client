package dev.argus.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.argus.client.ArgusClient;
import dev.argus.client.render.loading.LoadingAnimation;
import dev.argus.client.render.loading.LoadingAtlas;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Replaces the Mojang Studios logo texture with the generated loading-screen atlas.
 * The atlas is built once per session and re-uploaded on every resource reload.
 */
public final class LoadingOverlayRenderer extends ReloadableTexture {
   private static final String LOGO_PATH = "/assets/argusclient/textures/argusclient.png";
   private static final String GLYPHS_PATH = "/assets/argusclient/textures/loading_glyphs.png";

   private static LoadingAtlas cached;
   private static boolean failed;

   public LoadingOverlayRenderer() {
      super(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);
   }

   @Override
   public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
      // The startup overlay needs its texture before mod resource packs finish loading.
      int[] logo = readPixels(LOGO_PATH, LoadingAtlas.LOGO);
      if (logo == null) {
         throw new IOException("Missing loading logo " + LOGO_PATH);
      }
      LoadingAtlas atlas = atlas(logo);
      NativeImage image = new NativeImage(LoadingAtlas.WIDTH, LoadingAtlas.HEIGHT, false);
      if (atlas != null) {
         int[] pixels = atlas.pixels;
         for (int y = 0; y < LoadingAtlas.HEIGHT; y++) {
            int row = y * LoadingAtlas.WIDTH;
            for (int x = 0; x < LoadingAtlas.WIDTH; x++) {
               image.setPixel(x, y, pixels[row + x]);
            }
         }
      } else {
         // Fallback: the plain logo in the top-left cell.
         for (int y = 0; y < LoadingAtlas.LOGO; y++) {
            for (int x = 0; x < LoadingAtlas.LOGO; x++) {
               image.setPixel(x, y, logo[y * LoadingAtlas.LOGO + x]);
            }
         }
      }
      LoadingAnimation.setAtlas(atlas);
      return new TextureContents(image, new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F));
   }

   private static synchronized LoadingAtlas atlas(int[] logo) {
      if (cached == null && !failed) {
         try {
            cached = LoadingAtlas.build(logo, readPixels(GLYPHS_PATH, LoadingAtlas.GLYPHS.w()));
         } catch (Throwable error) {
            failed = true;
            ArgusClient.LOGGER.error("Failed to build the animated loading screen; using the static logo", error);
         }
      }
      return cached;
   }

   private static int[] readPixels(String path, int size) throws IOException {
      try (InputStream stream = LoadingOverlayRenderer.class.getResourceAsStream(path)) {
         if (stream == null) {
            return null;
         }
         try (NativeImage image = NativeImage.read(stream)) {
            if (image.getWidth() != size || image.getHeight() != size) {
               return null;
            }
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
               for (int x = 0; x < size; x++) {
                  pixels[y * size + x] = image.getPixel(x, y);
               }
            }
            return pixels;
         }
      }
   }
}

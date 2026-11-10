package dev.argus.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;

public final class LoadingOverlayRenderer extends ReloadableTexture {
   private static final String LOGO_PATH = "/assets/argusclient/textures/argusclient.png";
   private static final int TEXTURE_SIZE = 256;

   public LoadingOverlayRenderer() {
      super(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);
   }

   @Override
   public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
      // The startup overlay needs its texture before mod resource packs finish loading.
      try (InputStream stream = LoadingOverlayRenderer.class.getResourceAsStream(LOGO_PATH)) {
         if (stream == null) {
            throw new IOException("Missing loading logo " + LOGO_PATH);
         }
         return new TextureContents(NativeImage.read(stream),
               new TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0.0F));
      }
   }

   public static void render(GuiGraphics graphics, int color) {
      int size = Math.min(graphics.guiWidth(), graphics.guiHeight()) / 2;
      int x = (graphics.guiWidth() - size) / 2;
      int y = (graphics.guiHeight() - size) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION,
            x, y, 0.0F, 0.0F, size, size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
   }
}

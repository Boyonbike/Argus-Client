package dev.argus.client.render.loading;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Draws the loading animation through vanilla GUI pipelines that exist before resources load. */
public final class GuiLoadingCanvas implements LoadingCanvas {
   private static final Identifier TEXTURE = LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION;
   private static final RenderPipeline NORMAL = RenderPipelines.GUI_TEXTURED;
   /** Vanilla's loading-logo pipeline: SRC_ALPHA, ONE. */
   private static final RenderPipeline ADDITIVE = RenderPipelines.MOJANG_LOGO;

   private final GuiGraphicsExtractor graphics;

   public GuiLoadingCanvas(GuiGraphicsExtractor graphics) {
      this.graphics = graphics;
   }

   @Override
   public float width() {
      return this.graphics.guiWidth();
   }

   @Override
   public float height() {
      return this.graphics.guiHeight();
   }

   @Override
   public Matrix3x2fStack pose() {
      return this.graphics.pose();
   }

   @Override
   public void blit(boolean additive, int x, int y, int u, int v, int w, int h, int argb) {
      this.graphics.blit(additive ? ADDITIVE : NORMAL, TEXTURE, x, y, u, v, w, h, w, h,
            LoadingAtlas.WIDTH, LoadingAtlas.HEIGHT, argb);
   }
}

package dev.argus.client.render;

import dev.argus.client.utils.inventory.PixelIcon;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.argus.client.ArgusClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ShulkerIconRenderer {
   private static final long RETRY_MILLIS = 10_000L;
   // GUI extraction runs on the render thread.
   private static Integer extractingColour;
   private static RenderPipeline pipeline;
   private static long failedAt;

   private ShulkerIconRenderer() {
   }

   public static Integer extractingColour() {
      return extractingColour;
   }

   public static void draw(GuiGraphicsExtractor graphics, ItemStack item, Integer colour) {
      Integer previous = extractingColour;
      extractingColour = colour;
      try {
         graphics.item(item, 0, 0);
      } finally {
         extractingColour = previous;
      }
   }

   /** Draws pixel art in the same 16x16 space as a GUI item. */
   public static void drawPixels(GuiGraphicsExtractor graphics, PixelIcon icon, float x, float y, float scale) {
      var pose = graphics.pose();
      pose.pushMatrix();
      try {
         pose.translate(x, y);
         pose.scale(scale, scale);
         for (int row = 0; row < PixelIcon.SIZE; row++) {
            int start = 0;
            while (start < PixelIcon.SIZE) {
               int colour = icon.pixel(start, row);
               int end = start + 1;
               while (end < PixelIcon.SIZE && icon.pixel(end, row) == colour) end++;
               if ((colour >>> 24) != 0) graphics.fill(start, row, end, row + 1, colour);
               start = end;
            }
         }
      } finally {
         pose.popMatrix();
      }
   }

   /** Draws an item (optionally as a flat silhouette) at a GUI position. */
   public static void drawAt(GuiGraphicsExtractor graphics, ItemStack item, Integer colour, float x, float y, float scale) {
      var pose = graphics.pose();
      pose.pushMatrix();
      try {
         pose.translate(x, y);
         pose.scale(scale, scale);
         draw(graphics, item, colour);
      } finally {
         pose.popMatrix();
      }
   }

   public static BlitRenderState colourBlit(BlitRenderState original, GuiItemRenderState item) {
      Integer colour = ((ShulkerIconRenderState)(Object)item).argus$iconColour();
      if (colour == null) {
         return original;
      }

      RenderPipeline solid = pipeline();
      // Without the silhouette shader keep the item's normal icon rather than failing the GUI frame.
      if (solid == null) {
         return original;
      }

      return new BlitRenderState(
         solid, original.textureSetup(), original.pose(), original.x0(), original.y0(), original.x1(), original.y1(),
         original.u0(), original.u1(), original.v0(), original.v1(), colour, original.scissorArea(), original.bounds()
      );
   }

   /** Compiles the silhouette pipeline once; after a failure it is retried at most every ten seconds. */
   private static RenderPipeline pipeline() {
      if (pipeline != null) {
         return pipeline;
      }

      if (failedAt != 0L && System.currentTimeMillis() - failedAt < RETRY_MILLIS) {
         return null;
      }

      try {
         RenderPipeline base = RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
         RenderPipeline created = RenderPipeline.builder()
            .withLocation(id("pipeline/shulker_icon"))
            .withVertexShader(id("shaders/shulker_icon.vert"))
            .withFragmentShader(id("shaders/shulker_icon.frag"))
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION)
            .withVertexBinding(0, base.getVertexFormatBinding(0)).withPrimitiveTopology(base.getPrimitiveTopology())
            .withDepthStencilState(base.getDepthStencilState())
            .withColorTargetState(base.getColorTargetState())
            .withCull(base.isCull())
            .build();
         RenderSystem.getDevice().precompilePipeline(created, (identifier, type) -> {
            try (var input = Minecraft.getInstance().getResourceManager().open(identifier)) {
               return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
               throw new UncheckedIOException(exception);
            }
         });
         pipeline = created;
         failedAt = 0L;
         return created;
      } catch (RuntimeException | LinkageError error) {
         ArgusClient.LOGGER.error("ShulkerIcons could not compile its solid-colour shader; showing normal icons", error);
         failedAt = System.currentTimeMillis();
         return null;
      }
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("argusclient", path);
   }
}

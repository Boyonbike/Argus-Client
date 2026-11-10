package dev.argus.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argus.client.utils.inventory.NoOpVertexConsumer;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

public class ColorOutlineBufferSource implements MultiBufferSource {
   private final BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
   private int colorArgb;
   private boolean colorSet;

   public int getColorArgb() {
      return this.colorArgb;
   }

   public void endBatchAndClean() {
      this.buffers.endBatch();
      BufferSourceUtils.cleanup(this.buffers);
   }

   public void reset() {
      this.colorSet = false;
   }

   public boolean hasColor() {
      return this.colorSet;
   }

   /** While set, outline geometry is recorded into this sink instead of drawn (see ESP's cached block meshes). */
   private QuadSink capture;

   public void beginCapture(QuadSink sink) {
      this.capture = sink;
   }

   public void endCapture() {
      if (this.capture != null) {
         this.capture.alignToQuads();
      }

      this.capture = null;
   }

   @Override
   public VertexConsumer getBuffer(RenderType renderType) {
      if (this.capture != null) {
         boolean outlined = renderType.isOutline() || renderType.outline().isPresent();
         return outlined && renderType.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
            ? new ColorOutlineBufferSource.CaptureVertexConsumer(this, this.capture)
            : NoOpVertexConsumer.INSTANCE;
      }

      if (renderType.isOutline()) {
         return new ColorOutlineBufferSource.OutlineVertexConsumer(this, this.buffers.getBuffer(renderType));
      }

      Optional<RenderType> outlineType = renderType.outline();
      return outlineType.isPresent()
         ? new ColorOutlineBufferSource.OutlineVertexConsumer(this, this.buffers.getBuffer(outlineType.get()))
         : NoOpVertexConsumer.INSTANCE;
   }

   public void setColorArgb(int argb) {
      this.colorArgb = argb;
      this.colorSet = true;
   }

   private record CaptureVertexConsumer(ColorOutlineBufferSource source, QuadSink sink) implements VertexConsumer {
      @Override
      public VertexConsumer addVertex(float x, float y, float z) {
         this.sink.vertex(x, y, z, this.source.getColorArgb());
         return this;
      }

      @Override
      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer setColor(int argb) {
         return this;
      }

      @Override
      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      @Override
      public VertexConsumer setLineWidth(float width) {
         return this;
      }
   }

   private record OutlineVertexConsumer(ColorOutlineBufferSource source, VertexConsumer delegate) implements VertexConsumer {
      @Override
      public VertexConsumer addVertex(float x, float y, float z) {
         this.delegate.addVertex(x, y, z).setColor(this.source.getColorArgb());
         return this;
      }

      @Override
      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer setColor(int argb) {
         return this;
      }

      @Override
      public VertexConsumer setUv(float u, float v) {
         this.delegate.setUv(u, v);
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      @Override
      public VertexConsumer setLineWidth(float width) {
         return this;
      }
   }

   public static class SuppliedBufferSource extends BufferSource {
      private final Supplier<? extends MultiBufferSource> sourceSupplier;

      public SuppliedBufferSource(Supplier<? extends MultiBufferSource> sourceSupplier) {
         super(null, null);
         this.sourceSupplier = sourceSupplier;
      }

      @Override
      public VertexConsumer getBuffer(RenderType renderType) {
         return this.sourceSupplier.get().getBuffer(renderType);
      }

      @Override
      public void endBatch() {
      }

      @Override
      public void endBatch(RenderType renderType) {
      }
   }
}

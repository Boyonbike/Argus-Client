package dev.argus.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argus.client.utils.inventory.NoOpVertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.rendertype.RenderType;

public class ColorOutlineBufferSource {
   private final Map<RenderType, MeshBuffer> buffers = new LinkedHashMap<>();
   private int colorArgb;
   private boolean colorSet;
   private QuadSink capture;

   public int getColorArgb() {
      return this.colorArgb;
   }

   public void endBatchAndClean() {
      for (Map.Entry<RenderType, MeshBuffer> entry : this.buffers.entrySet()) {
         entry.getValue().draw(entry.getKey());
      }
      this.buffers.clear();
   }

   public void reset() {
      this.colorSet = false;
   }

   public boolean hasColor() {
      return this.colorSet;
   }

   /** While set, outline geometry is recorded into this sink instead of drawn (see ESP's cached block meshes). */
   public void beginCapture(QuadSink sink) {
      this.capture = sink;
   }

   public void endCapture() {
      if (this.capture != null) {
         this.capture.alignToQuads();
      }

      this.capture = null;
   }

   public VertexConsumer getBuffer(RenderType renderType) {
      if (this.capture != null) {
         boolean outlined = renderType.isOutline() || renderType.outline().isPresent();
         return outlined && renderType.primitiveTopology() == com.mojang.blaze3d.PrimitiveTopology.QUADS
            ? new ColorOutlineBufferSource.CaptureVertexConsumer(this, this.capture)
            : NoOpVertexConsumer.INSTANCE;
      }

      if (renderType.isOutline()) {
         return new ColorOutlineBufferSource.OutlineVertexConsumer(this, this.buffer(renderType));
      }

      Optional<RenderType> outlineType = renderType.outline();
      return outlineType.isPresent()
         ? new ColorOutlineBufferSource.OutlineVertexConsumer(this, this.buffer(outlineType.get()))
         : NoOpVertexConsumer.INSTANCE;
   }

   private MeshBuffer buffer(RenderType renderType) {
      return this.buffers.computeIfAbsent(renderType, type -> MeshBuffer.create().setFormat(type.primitiveTopology(), type.format()));
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
}

package dev.argus.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Minimal replacement for the removed Tesselator, sharing one staging buffer per begin/end pair. */
public final class ArgusTesselator {
   private static final ArgusTesselator INSTANCE = new ArgusTesselator();
   private ByteBufferBuilder buffer;

   private ArgusTesselator() {
   }

   public static ArgusTesselator getInstance() {
      return INSTANCE;
   }

   public BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
      if (this.buffer == null) {
         this.buffer = new ByteBufferBuilder(786432);
      }
      return new BufferBuilder(this.buffer, topology, format);
   }

   public void end() {
      if (this.buffer != null) {
         this.buffer.close();
         this.buffer = null;
      }
   }
}

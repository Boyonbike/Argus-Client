package dev.argus.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

/**
 * Collects quads as position + colour vertices (the {@code POSITION_COLOR} layout: three floats, then R, G, B, A bytes)
 * in native memory, ready to upload once into a GPU buffer. Reused between builds; call {@link #close()} to free it.
 */
public final class QuadSink implements AutoCloseable {
   public static final int VERTEX_BYTES = 16;
   private ByteBuffer buffer = MemoryUtil.memAlloc(64 * 1024).order(ByteOrder.nativeOrder());
   private int vertices;

   public void reset() {
      this.buffer.clear();
      this.vertices = 0;
   }

   public void vertex(float x, float y, float z, int argb) {
      if (this.buffer.remaining() < VERTEX_BYTES) {
         this.buffer = MemoryUtil.memRealloc(this.buffer, this.buffer.capacity() * 2).order(ByteOrder.nativeOrder());
      }

      this.buffer.putFloat(x).putFloat(y).putFloat(z);
      this.buffer.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >>> 24));
      this.vertices++;
   }

   /** Adds the six faces of a box. */
   public void box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int argb) {
      this.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, argb);
      this.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, argb);
      this.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, argb);
      this.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, argb);
      this.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, argb);
      this.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, argb);
   }

   private void quad(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
      this.vertex(x1, y1, z1, argb);
      this.vertex(x2, y2, z2, argb);
      this.vertex(x3, y3, z3, argb);
      this.vertex(x4, y4, z4, argb);
   }

   /** Drops a trailing partial quad (a model that stopped mid-face) so the quad index buffer stays aligned. */
   public void alignToQuads() {
      int extra = this.vertices % 4;
      if (extra != 0) {
         this.buffer.position(this.buffer.position() - extra * VERTEX_BYTES);
         this.vertices -= extra;
      }
   }

   public int vertexCount() {
      return this.vertices;
   }

   public int quadCount() {
      return this.vertices / 4;
   }

   /** A read-only view of the written bytes, valid until the next write. */
   public ByteBuffer data() {
      ByteBuffer view = this.buffer.duplicate().order(ByteOrder.nativeOrder());
      view.flip();
      return view;
   }

   @Override
   public void close() {
      if (this.buffer != null) {
         MemoryUtil.memFree(this.buffer);
         this.buffer = null;
      }
   }
}

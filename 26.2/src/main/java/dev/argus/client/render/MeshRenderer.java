package dev.argus.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 26.2 removed RenderType.draw(MeshData). This replays a built mesh through the render type's
 * prepared pipeline, keeping Argus's immediate-mode world rendering intact.
 */
public final class MeshRenderer {
   private static final List<GpuBuffer> PENDING = new ArrayList<>();

   private MeshRenderer() {
   }

   /** Frees the buffers of the frame that has just finished executing. */
   public static void beginFrame() {
      for (GpuBuffer buffer : PENDING) {
         buffer.close();
      }
      PENDING.clear();
   }

   public static void draw(RenderType renderType, MeshData mesh) {
      if (renderType == null || mesh == null) {
         return;
      }

      MeshData.DrawState state = mesh.drawState();
      ByteBuffer vertices = copy(mesh.vertexBuffer());
      applyColorModifiers(vertices, state, renderType);
      ByteBuffer indices = buildIndices(state);
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer vertexBuffer = device.createBuffer(() -> "argus-vertices", GpuBuffer.USAGE_VERTEX, vertices);
      GpuBuffer indexBuffer = device.createBuffer(() -> "argus-indices", GpuBuffer.USAGE_INDEX, indices);
      PENDING.add(vertexBuffer);
      PENDING.add(indexBuffer);
      PreparedRenderType prepared = renderType.prepare();
      prepared.drawFromBuffer(vertexBuffer, indexBuffer, state.indexType(), 0, 0, state.indexCount());
   }

   private static void applyColorModifiers(ByteBuffer vertices, MeshData.DrawState state, RenderType renderType) {
      if (ClientRenderPipelines.isGlint(renderType.pipeline())) {
         return; // The RenderType uniform hook applies glint colour and render alpha.
      }
      float alpha = 1.0F;
      int renderAlpha = RenderUtils.getRenderColor()[3];
      if (renderAlpha != 255) {
         alpha = renderAlpha / 255.0F;
      }
      if (alpha == 1.0F) {
         return;
      }
      VertexFormat format = state.format();
      VertexFormatElement colour = format.getElement(DefaultVertexFormat.COLOR_SEMANTIC_NAME);
      if (colour == null || colour.format() != GpuFormat.RGBA8_UNORM) {
         return;
      }
      int stride = format.getVertexSize();
      int offset = colour.offset();
      for (int index = 0; index < state.vertexCount(); index++) {
         int base = index * stride + offset;
         int r = vertices.get(base) & 0xFF;
         int g = vertices.get(base + 1) & 0xFF;
         int b = vertices.get(base + 2) & 0xFF;
         int a = vertices.get(base + 3) & 0xFF;
         vertices.put(base, (byte)r);
         vertices.put(base + 1, (byte)g);
         vertices.put(base + 2, (byte)b);
         vertices.put(base + 3, (byte)((int)(a * alpha)));
      }
   }

   private static ByteBuffer copy(ByteBuffer source) {
      ByteBuffer view = source.duplicate();
      ByteBuffer copy = ByteBuffer.allocateDirect(view.remaining()).order(ByteOrder.nativeOrder());
      copy.put(view);
      copy.flip();
      return copy;
   }

   static ByteBuffer buildIndices(MeshData.DrawState state) {
      int indexCount = state.indexCount();
      int vertexCount = state.vertexCount();
      IndexType type = state.indexType();
      ByteBuffer buffer = ByteBuffer.allocateDirect(indexCount * type.bytes).order(ByteOrder.nativeOrder());
      if (state.primitiveTopology() == PrimitiveTopology.QUADS) {
         int quads = Math.min(vertexCount / 4, indexCount / 6);
         for (int quad = 0; quad < quads; quad++) {
            int base = quad * 4;
            put(buffer, type, base);
            put(buffer, type, base + 1);
            put(buffer, type, base + 2);
            put(buffer, type, base);
            put(buffer, type, base + 2);
            put(buffer, type, base + 3);
         }
      } else {
         for (int index = 0; index < indexCount; index++) {
            put(buffer, type, vertexCount == 0 ? 0 : index % vertexCount);
         }
      }
      buffer.flip();
      return buffer;
   }

   private static void put(ByteBuffer buffer, IndexType type, int index) {
      if (type == IndexType.INT) {
         buffer.putInt(index);
      } else {
         buffer.putShort((short)index);
      }
   }
}

package dev.argus.client.render;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 26.2 drawFromBuffer always consumes an index buffer; these pin the generated order. */
class MeshRendererTest {
   @Test
   void quadsBecomeTwoTrianglesEach() {
      MeshData.DrawState state = new MeshData.DrawState(
         DefaultVertexFormat.POSITION_COLOR, 8, 12, PrimitiveTopology.QUADS, IndexType.SHORT
      );
      ByteBuffer indices = MeshRenderer.buildIndices(state);
      short[] expected = {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7};
      assertEquals(expected.length * 2, indices.remaining());
      for (short value : expected) {
         assertEquals(value, indices.getShort());
      }
   }

   @Test
   void linesStaySequential() {
      MeshData.DrawState state = new MeshData.DrawState(
         DefaultVertexFormat.POSITION_COLOR, 4, 4, PrimitiveTopology.LINES, IndexType.SHORT
      );
      ByteBuffer indices = MeshRenderer.buildIndices(state);
      for (short value = 0; value < 4; value++) {
         assertEquals(value, indices.getShort());
      }
   }
}

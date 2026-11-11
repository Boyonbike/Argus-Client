package dev.argus.client.render;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

public class SodiumMeshBuffer extends MeshBuffer implements VertexBufferWriter {
   public SodiumMeshBuffer(int capacity) {
      super(capacity);
   }

   @Override
   public void sodium$push(MemoryStack stack, long vertices, int vertexCount, VertexFormat format) {
   }

   public boolean canUseIntrinsics() {
      return false;
   }
}

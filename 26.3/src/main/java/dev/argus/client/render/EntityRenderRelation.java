package dev.argus.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import java.util.function.BiConsumer;

public enum EntityRenderRelation {
   DEFAULT,
   FRIEND,
   ENEMY;

   private final MeshBuffer meshBuffer = MeshBuffer.create().setFormat(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);

   public MeshBuffer getMeshBuffer() {
      return this.meshBuffer;
   }

   public static void forEachMesh(BiConsumer<EntityRenderRelation, MeshBuffer> consumer) {
      for (EntityRenderRelation relation : values()) {
         consumer.accept(relation, relation.getMeshBuffer());
      }
   }
}

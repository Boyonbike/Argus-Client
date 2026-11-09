package dev.argus.client.utils.inventory;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class NoOpVertexConsumer implements VertexConsumer {
   public static final NoOpVertexConsumer INSTANCE = new NoOpVertexConsumer();

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
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

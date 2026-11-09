package dev.argus.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argus.client.utils.inventory.NoOpVertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class ChamsBufferSource extends BufferSource {
   public static final ChamsBufferSource INSTANCE = new ChamsBufferSource();
   private final Object2ObjectOpenHashMap<RenderType, VertexConsumer> consumersByRenderType = new Object2ObjectOpenHashMap();

   ChamsBufferSource() {
      super(null, null);
   }

   @Override
   public VertexConsumer getBuffer(RenderType renderType) {
      return renderType.state.outputTarget == OutputTarget.ITEM_ENTITY_TARGET
         ? NoOpVertexConsumer.INSTANCE
         : (VertexConsumer)this.consumersByRenderType.computeIfAbsent(renderType, ignored -> new ChamsVertexConsumer());
   }
}

package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockOutlineRenderEvent extends CancellableEvent {
   private PoseStack poseStack;
   private VertexConsumer vertexConsumer;
   private BlockPos blockPos;
   private BlockState blockState;

   public BlockPos getBlockPos() {
      return this.blockPos;
   }

   public void setBlockPos(BlockPos blockPos) {
      this.blockPos = blockPos;
   }

   public BlockState getBlockState() {
      return this.blockState;
   }

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public BlockOutlineRenderEvent(PoseStack poseStack, VertexConsumer vertexConsumer, BlockPos blockPos, BlockState blockState) {
      this.poseStack = poseStack;
      this.vertexConsumer = vertexConsumer;
      this.blockPos = blockPos;
      this.blockState = blockState;
   }

   public void setVertexConsumer(VertexConsumer vertexConsumer) {
      this.vertexConsumer = vertexConsumer;
   }

   public void setPoseStack(PoseStack poseStack) {
      this.poseStack = poseStack;
   }

   public void setBlockState(BlockState blockState) {
      this.blockState = blockState;
   }

   public VertexConsumer getVertexConsumer() {
      return this.vertexConsumer;
   }
}

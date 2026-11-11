package dev.argus.client.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockRaycastContext {
   private final Vec3 start;
   private final Vec3 end;
   private final Block blockMode;
   private final Fluid fluidMode;
   private final CollisionContext collisionContext;
   private final BlockRaycastClassifier classifier;

   public BlockRaycastContext(
      Vec3 rayStart, Vec3 rayEnd, Block blockSelectionMode, Fluid fluidSelectionMode, Entity collisionEntity, BlockRaycastClassifier overrideClassifier
   ) {
      this.start = rayStart;
      this.end = rayEnd;
      this.blockMode = blockSelectionMode;
      this.fluidMode = fluidSelectionMode;
      this.collisionContext = CollisionContext.of(collisionEntity);
      this.classifier = overrideClassifier;
   }

   public Vec3 getStart() {
      return this.start;
   }

   public Vec3 getEnd() {
      return this.end;
   }

   public Block getBlockMode() {
      return this.blockMode;
   }

   public Fluid getFluidMode() {
      return this.fluidMode;
   }

   public CollisionContext getCollisionContext() {
      return this.collisionContext;
   }

   public BlockRaycastClassifier getClassifier() {
      return this.classifier;
   }

   public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos blockPos) {
      Block selectionMode = this.blockMode;
      CollisionContext collision = this.collisionContext;
      return selectionMode.get(state, level, blockPos, collision);
   }

   public VoxelShape getFluidShape(FluidState state, BlockGetter level, BlockPos blockPos) {
      Fluid fluidSelectionMode = this.fluidMode;
      VoxelShape selectedShape;
      if (fluidSelectionMode.canPick(state)) {
         selectedShape = state.getShape(level, blockPos);
      } else {
         selectedShape = Shapes.empty();
      }

      return selectedShape;
   }

   public static class Builder {
      private final Vec3 start;
      private final Vec3 end;
      private Block blockMode = Block.COLLIDER;
      private Fluid fluidMode = Fluid.NONE;
      private Entity entity = Minecraft.getInstance().player;
      private BlockRaycastClassifier classifier = BlockRaycastClassifier.NO_OVERRIDE;

      public Builder(Vec3 rayStart, Vec3 rayEnd) {
         this.start = rayStart;
         this.end = rayEnd;
      }

      public BlockRaycastContext.Builder withBlockMode(Block blockSelectionMode) {
         this.blockMode = blockSelectionMode;
         return this;
      }

      public BlockRaycastContext.Builder withFluidMode(Fluid fluidSelectionMode) {
         this.fluidMode = fluidSelectionMode;
         return this;
      }

      public BlockRaycastContext.Builder withEntity(Entity collisionEntity) {
         this.entity = collisionEntity;
         return this;
      }

      public BlockRaycastContext.Builder withClassifier(BlockRaycastClassifier overrideClassifier) {
         this.classifier = overrideClassifier;
         return this;
      }

      public BlockRaycastContext.Builder withClassifiers(BlockRaycastClassifier... overrideClassifiers) {
         this.classifier = BlockRaycastClassifier.firstNonNone(overrideClassifiers);
         return this;
      }

      public BlockRaycastContext build() {
         return new BlockRaycastContext(this.start, this.end, this.blockMode, this.fluidMode, this.entity, this.classifier);
      }
   }
}

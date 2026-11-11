package dev.argus.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockCollisionShapeEvent extends CancellableEvent {
   public static final BlockCollisionShapeEvent INSTANCE = new BlockCollisionShapeEvent();
   private VoxelShape shape;
   private BlockPos pos;
   private BlockState state;

   public BlockPos getPos() {
      return this.pos;
   }

   public VoxelShape getShape() {
      return this.shape;
   }

   public BlockState getState() {
      return this.state;
   }

   private BlockCollisionShapeEvent() {
   }

   public static BlockCollisionShapeEvent prepare(VoxelShape shape, BlockPos pos, BlockState state) {
      INSTANCE.shape = shape;
      INSTANCE.pos = pos;
      INSTANCE.state = state;
      return INSTANCE;
   }

   /**
    * Whether a collision query should go through the event bus. Queries for other entities (mobs, items,
    * other players) are skipped: listeners only change what the local player and its vehicle collide with,
    * and posting for every entity in the world was a large constant cost. Queries without an entity still post.
    */
   public static boolean shouldPost(CollisionContext context) {
      if (!(context instanceof EntityCollisionContext entityContext)) {
         return true;
      }

      Entity entity = entityContext.getEntity();
      if (entity == null) {
         return true;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && (entity == player || player.getVehicle() == entity || entity.hasPassenger(player));
   }

   public void setShape(VoxelShape shape) {
      this.shape = shape;
   }
}

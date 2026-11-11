package dev.argus.client.render;

import net.minecraft.world.level.block.state.BlockState;

/** The block currently being meshed, exposed to the MaterialInfo layer override. */
public final class MeshBlockContext {
   private static final ThreadLocal<BlockState> CURRENT = new ThreadLocal<>();

   private MeshBlockContext() {
   }

   public static void set(BlockState state) {
      CURRENT.set(state);
   }

   public static BlockState get() {
      return CURRENT.get();
   }

   public static void clear() {
      CURRENT.remove();
   }
}

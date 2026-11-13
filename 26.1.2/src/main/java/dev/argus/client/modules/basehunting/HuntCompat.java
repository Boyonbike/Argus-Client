package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.ServerAddressUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Minecraft calls that differ between the versions Argus supports, kept together so each version's copy of the
 * Base Hunting modules only differs in this file.
 */
public final class HuntCompat {
   private HuntCompat() {
   }

   public static boolean signHasText(SignBlockEntity sign) {
      return hasText(sign.getFrontText()) || hasText(sign.getBackText());
   }

   private static boolean hasText(SignText text) {
      for (Component line : text.getMessages(false)) {
         if (!line.getString().isBlank()) {
            return true;
         }
      }

      return false;
   }

   public static boolean isThrownPearl(Entity entity) {
      return entity instanceof ThrownEnderpearl;
   }

   /** The block an enderman is carrying, or null for anything else. */
   public static BlockState carriedBlock(Entity entity) {
      return entity instanceof EnderMan enderman ? enderman.getCarriedBlock() : null;
   }

   /** The key Argus files waypoints under for the current server. */
   public static String serverKey() {
      return ServerAddressUtils.getCurrentServerAddress();
   }

   public static boolean isScreenOpen() {
      return ClientAccess.argusclientMinecraft.screen != null;
   }

   public static long chunkKey(ChunkPos chunk) {
      return chunk.pack();
   }

   public static int chunkX(ChunkPos chunk) {
      return chunk.x();
   }

   public static int chunkZ(ChunkPos chunk) {
      return chunk.z();
   }
}

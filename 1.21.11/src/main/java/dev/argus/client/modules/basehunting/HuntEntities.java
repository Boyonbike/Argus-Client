package dev.argus.client.modules.basehunting;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Entity helpers for the Base Hunting ESPs. */
final class HuntEntities {
   private HuntEntities() {
   }

   /** The entity's box where it is drawn this frame, between last tick's position and this one. */
   static AABB interpolatedBox(Entity entity, float partialTick) {
      double dx = Mth.lerp(partialTick, entity.xOld, entity.getX()) - entity.getX();
      double dy = Mth.lerp(partialTick, entity.yOld, entity.getY()) - entity.getY();
      double dz = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - entity.getZ();
      return entity.getBoundingBox().move(dx, dy, dz);
   }
}

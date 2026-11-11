package dev.argus.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public interface EntityRenderStateAccess {
   Entity argusclient$getEntity();

   void argusclient$setEntity(Entity var1);

   static Entity getEntity(EntityRenderState renderState) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      return access.argusclient$getEntity();
   }

   static void setEntity(EntityRenderState renderState, Entity entity) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      access.argusclient$setEntity(entity);
   }
}

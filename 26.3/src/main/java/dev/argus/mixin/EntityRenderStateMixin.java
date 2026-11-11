package dev.argus.mixin;

import dev.argus.client.render.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
   @Unique
   private Entity renderedEntity;

   @Override
   public Entity argusclient$getEntity() {
      return this.renderedEntity;
   }

   @Override
   public void argusclient$setEntity(Entity entity) {
      this.renderedEntity = entity;
   }
}

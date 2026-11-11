package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;

public final class NameTagRenderEvent extends CancellableEvent {
   private final PoseStack poseStack;
   private final Entity entity;

   public Entity getEntity() {
      return this.entity;
   }

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public NameTagRenderEvent(PoseStack poseStack, Entity entity) {
      this.poseStack = poseStack;
      this.entity = entity;
   }
}

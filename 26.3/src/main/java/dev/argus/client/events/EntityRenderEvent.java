package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;

public class EntityRenderEvent extends CancellableEvent {
   protected Entity entity;
   protected PoseStack poseStack;
   protected SubmitNodeCollector collector;

   public Entity getEntity() {
      return this.entity;
   }

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public SubmitNodeCollector getCollector() {
      return this.collector;
   }

   public static class Post extends EntityRenderEvent {
      private static final EntityRenderEvent.Post INSTANCE = new EntityRenderEvent.Post();

      public static EntityRenderEvent.Post create(Entity entity, PoseStack poseStack, SubmitNodeCollector collector) {
         INSTANCE.entity = entity;
         INSTANCE.poseStack = poseStack;
         INSTANCE.collector = collector;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }

   public static class Pre extends EntityRenderEvent {
      private static final EntityRenderEvent.Pre INSTANCE = new EntityRenderEvent.Pre();

      public static EntityRenderEvent.Pre create(Entity entity, PoseStack poseStack, SubmitNodeCollector collector) {
         INSTANCE.entity = entity;
         INSTANCE.poseStack = poseStack;
         INSTANCE.collector = collector;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }
}

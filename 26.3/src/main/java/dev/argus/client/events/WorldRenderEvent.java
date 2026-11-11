package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;

public class WorldRenderEvent extends CancellableEvent {
   protected PoseStack poseStack;
   protected float partialTick;

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public float getPartialTick() {
      return this.partialTick;
   }

   public static class BeforeBufferSource extends WorldRenderEvent {
      private static final WorldRenderEvent.BeforeBufferSource INSTANCE = new WorldRenderEvent.BeforeBufferSource();

      public static WorldRenderEvent.BeforeBufferSource create(PoseStack poses, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }

   public static class BeforeDepthClear extends WorldRenderEvent {
      private static final WorldRenderEvent.BeforeDepthClear INSTANCE = new WorldRenderEvent.BeforeDepthClear();

      public static WorldRenderEvent.BeforeDepthClear create(PoseStack poses, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }

   public static class Main extends WorldRenderEvent {
      private static final WorldRenderEvent.Main INSTANCE = new WorldRenderEvent.Main();

      public static WorldRenderEvent.Main create(PoseStack poses, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }

   public static class Overlay extends WorldRenderEvent {
      private static final WorldRenderEvent.Overlay INSTANCE = new WorldRenderEvent.Overlay();

      public static WorldRenderEvent.Overlay create(PoseStack poses, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }
}

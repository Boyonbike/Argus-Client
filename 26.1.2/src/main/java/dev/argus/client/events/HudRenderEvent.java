package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudRenderEvent extends CancellableEvent {
   private static final HudRenderEvent INSTANCE = new HudRenderEvent();
   protected PoseStack poseStack;
   protected GuiGraphicsExtractor guiGraphics;
   protected float partialTick;

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public float getPartialTick() {
      return this.partialTick;
   }

   public GuiGraphicsExtractor getGuiGraphics() {
      return this.guiGraphics;
   }

   public static HudRenderEvent create(PoseStack poses, GuiGraphicsExtractor graphics, float partialTick) {
      INSTANCE.poseStack = poses;
      INSTANCE.partialTick = partialTick;
      INSTANCE.guiGraphics = graphics;
      INSTANCE.resetCancellation();
      return INSTANCE;
   }

   public static class Before extends HudRenderEvent {
      private static final HudRenderEvent.Before INSTANCE = new HudRenderEvent.Before();

      public static HudRenderEvent.Before create(PoseStack poses, GuiGraphicsExtractor graphics, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.guiGraphics = graphics;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }

   public static class Unconditional extends HudRenderEvent {
      private static final HudRenderEvent.Unconditional INSTANCE = new HudRenderEvent.Unconditional();

      public static HudRenderEvent.Unconditional create(PoseStack poses, GuiGraphicsExtractor graphics, float partialTick) {
         INSTANCE.poseStack = poses;
         INSTANCE.partialTick = partialTick;
         INSTANCE.guiGraphics = graphics;
         INSTANCE.resetCancellation();
         return INSTANCE;
      }
   }
}

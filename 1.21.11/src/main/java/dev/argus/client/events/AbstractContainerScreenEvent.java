package dev.argus.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.render.GuiProjectionUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class AbstractContainerScreenEvent extends CancellableEvent {
   private final Screen screen;
   private GuiGraphics graphics;
   private PoseStack poseStack;
   private float mouseX;
   private float mouseY;
   private Runnable tickCallback = null;

   public AbstractContainerScreenEvent(Screen screen) {
      this.screen = screen;
   }

   public Screen getScreen() {
      return this.screen;
   }

   public PoseStack getPoseStack() {
      return this.poseStack;
   }

   public float getMouseX() {
      return this.mouseX;
   }

   public float getMouseY() {
      return this.mouseY;
   }

   public GuiGraphics getGraphics() {
      return this.graphics;
   }

   public void setGraphics(GuiGraphics graphics) {
      this.graphics = graphics;
      this.poseStack = GuiProjectionUtils.toPoseStack(graphics);
   }

   public void setMouseX(float mouseX) {
      this.mouseX = mouseX;
   }

   public void setMouseY(float mouseY) {
      this.mouseY = mouseY;
   }

   public void setTickCallback(Runnable tickCallback) {
      this.tickCallback = tickCallback;
   }

   public void runTickCallback() {
      if (this.tickCallback != null) {
         this.tickCallback.run();
      }
   }
}

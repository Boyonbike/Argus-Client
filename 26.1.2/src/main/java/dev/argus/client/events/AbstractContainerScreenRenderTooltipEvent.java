package dev.argus.client.events;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;

public final class AbstractContainerScreenRenderTooltipEvent extends CancellableEvent {
   public static int mouseX;
   public static int mouseY;
   private final Screen screen;
   private List tooltipComponents;
   private int x;
   private int y;

   public AbstractContainerScreenRenderTooltipEvent(Screen screen, List tooltipComponents, int x, int y, int currentMouseX, int currentMouseY) {
      this.screen = screen;
      this.tooltipComponents = tooltipComponents;
      this.x = x;
      this.y = y;
      mouseX = currentMouseX;
      mouseY = currentMouseY;
   }

   public Screen getScreen() {
      return this.screen;
   }

   public List getTooltipComponents() {
      return this.tooltipComponents;
   }

   public void setTooltipComponents(List tooltipComponents) {
      this.tooltipComponents = tooltipComponents;
   }

   public int getX() {
      return this.x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return this.y;
   }

   public void setY(int y) {
      this.y = y;
   }
}

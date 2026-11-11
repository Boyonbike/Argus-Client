package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.collections.IntPair;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryTransferAnimation implements ClientAccess {
   private final ClampedInterpolatedFloat progress = new ClampedInterpolatedFloat(2.0F);
   private final ItemStack stack;
   private final IntPair destination;
   private final IntPair source;

   public InventoryTransferAnimation(ItemStack stack, IntPair source, IntPair destination) {
      this.stack = stack.copy();
      this.destination = destination;
      this.source = source;
      this.progress.setValue(0.0F);
   }

   public void render(GuiGraphics graphics) {
      this.progress.setTarget(true);
      float fraction = this.progress.updateAndGet();
      float startX = this.source.first();
      int endX = this.destination.first();
      int drawX = (int)(startX + (endX - this.source.first()) * fraction);
      float startY = this.source.second();
      int endY = this.destination.second();
      int drawY = (int)(startY + (endY - this.source.second()) * fraction);
      if (fraction > 0.8) {
         float red = 1.0F;
         float green = 1.0F;
         float blue = 1.0F;
         float alpha = 1.0F;
         float fade = fraction - 0.8F;
         RenderUtils.setRenderColor(red, green, blue, alpha - fade / 0.2F);
      }

      ItemStack renderedStack = this.stack;
      graphics.renderItem(renderedStack, drawX, drawY);
      ItemStack decoratedStack = this.stack;
      Font font = ClientAccess.argusclientMinecraft.font;
      graphics.renderItemDecorations(font, decoratedStack, drawX, drawY);
      if (fraction > 0.8) {
         float red = 1.0F;
         float green = 1.0F;
         float blue = 1.0F;
         RenderUtils.setRenderColor(red, green, blue, 1.0F);
      }
   }

   public boolean isFinished() {
      return this.progress.getValue() == 1.0F;
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      } else if (other != null && this.getClass() == other.getClass()) {
         InventoryTransferAnimation animation = (InventoryTransferAnimation)other;
         Item item = this.stack.getItem();
         ItemStack otherStack = animation.stack;
         return Objects.equals(item, otherStack.getItem()) && Objects.equals(this.destination, animation.destination);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.stack.getItem(), this.destination);
   }
}

package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.access.ChatGraphicsXOffset;
import net.minecraft.client.gui.components.ChatComponent.DrawingBackgroundGraphicsAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DrawingBackgroundGraphicsAccess.class)
public class ChatComponentDrawingBackgroundGraphicsAccessMixin implements ChatGraphicsXOffset {
   @Unique
   private int messageXOffset;

   @Override
   public void argusclient$updateX(int offsetX) {
      this.messageXOffset = offsetX;
   }

   @ModifyExpressionValue(method = "handleMessage", at = @At(value = "CONSTANT", args = "intValue=0"))
   private int applyMessageXOffset(int originalX) {
      return this.messageXOffset != 0 ? this.messageXOffset : originalX;
   }
}

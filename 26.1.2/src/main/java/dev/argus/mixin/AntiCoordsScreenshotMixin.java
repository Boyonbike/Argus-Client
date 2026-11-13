package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.argus.client.modules.misc.AntiCoordsModule;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public abstract class AntiCoordsScreenshotMixin {
   /** The screenshot key's frame already has the HUD in it, so AntiCoords takes the capture on the next frame instead. */
   @WrapOperation(
      method = "keyPress",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"
      )
   )
   private void argus$antiCoordsDeferScreenshot(File gameDirectory, RenderTarget target, Consumer<Component> callback, Operation<Void> original) {
      if (!AntiCoordsModule.deferScreenshot(callback)) {
         original.call(gameDirectory, target, callback);
      }
   }
}

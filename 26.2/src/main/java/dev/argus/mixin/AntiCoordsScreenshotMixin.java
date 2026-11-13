package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.argus.client.modules.misc.AntiCoordsModule;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class AntiCoordsScreenshotMixin {
   /** The screenshot key's frame already has the HUD in it, so AntiCoords takes the capture on the next frame instead. */
   @WrapOperation(
      method = "handleGlobalKeyPress",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V")
   )
   private void argus$antiCoordsDeferScreenshot(Minecraft minecraft, boolean controlDown, Operation<Void> original) {
      Consumer<Component> callback = message -> minecraft.execute(() -> minecraft.showDebugChat(message));
      if (!AntiCoordsModule.deferScreenshot(callback)) {
         original.call(minecraft, controlDown);
      }
   }
}

package dev.argus.mixin;

import dev.argus.client.ClientContext;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
   @Inject(
      method = "submit(FLnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Z)V",
      at = @At("HEAD"), cancellable = true
   )
   private void suppressScreenEffectsInTarget(
      float partialTick,
      SubmitNodeCollector collector,
      PlayerRenderState playerState,
      CameraRenderState cameraState,
      boolean ignore,
      CallbackInfo callback
   ) {
      if (ClientContext.shaderRegistry.renderingIntoTarget) {
         callback.cancel();
      }
   }
}

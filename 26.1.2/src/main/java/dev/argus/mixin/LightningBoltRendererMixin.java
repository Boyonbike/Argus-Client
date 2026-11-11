package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.KillEffectsModule;
import dev.argus.client.render.ClientLightningState;
import java.awt.Color;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LightningBoltRenderer.class)
public final class LightningBoltRendererMixin {
   private static final KillEffectsModule killEffectsModule = ClientContext.getModule(KillEffectsModule.class);
   @Unique
   private static LightningBolt renderedLightningBolt;

   @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/LightningBolt;Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;F)V",
      at = @At("HEAD")
   )
   private void captureRenderedLightningBolt(LightningBolt lightning, LightningBoltRenderState renderState, float partialTick, CallbackInfo callback) {
      renderedLightningBolt = lightning;
   }

   @ModifyArgs(
      method = "quad",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
   )
   private static void overrideClientLightningTint(Args arguments) {
      if (renderedLightningBolt != null
         && ((ClientLightningState)renderedLightningBolt).argusclient$isClient()
         && killEffectsModule.isToggled()
         && killEffectsModule.thunderSetting.getValue()) {
         Color lightningColor = killEffectsModule.colorSetting.getValue();
         arguments.set(0, lightningColor.getRed() / 255.0F);
         arguments.set(1, lightningColor.getGreen() / 255.0F);
         arguments.set(2, lightningColor.getBlue() / 255.0F);
      }
   }
}

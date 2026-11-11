package dev.argus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);

   @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
   private static void suppressFireOverlay(PoseStack poses, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.fireSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
   private static void suppressWaterOverlay(Minecraft client, PoseStack poses, SubmitNodeCollector collector, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.blindnessSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
   private static void suppressTextureOverlay(TextureAtlasSprite sprite, PoseStack poses, SubmitNodeCollector collector, int light, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.blindnessSetting.getValue() || freecamModule.isToggled()) {
         callback.cancel();
      }
   }
}

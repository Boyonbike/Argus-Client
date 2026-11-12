package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.modules.render.SkyboxModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

   @Inject(method = "extract", at = @At("HEAD"))
   private void captureCullFrustum(DeltaTracker deltaTracker, Camera camera, float partialTick, CallbackInfo callback) {
      RotationManager.setFrustum(camera.getCullFrustum());
   }

   @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return freecam.isToggled() || originalDetached;
   }

   @ModifyExpressionValue(
      method = "extract",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
         ordinal = 1
      )
   )
   private Object applyCloudHeight(Object originalHeight) {
      return skyboxModule.getCloudHeight((Float)originalHeight);
   }

   @ModifyExpressionValue(
      method = "extract",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
         ordinal = 0
      )
   )
   private Object applyCloudColor(Object originalColor) {
      // 26.3: CLOUD_COLOR is a Vector4fc (it was an ARGB int in 26.2).
      return skyboxModule.isToggled() && skyboxModule.cloudsSetting.getValue()
         ? ARGB.vector4fFromARGB32(skyboxModule.cloudsColorSetting.getValue().getRGB())
         : originalColor;
   }
}

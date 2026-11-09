package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.ViewClipModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {
   private static final ViewClipModule viewClip = ClientContext.getModule(ViewClipModule.class);

   @Shadow
   public abstract void setCameraType(CameraType var1);

   @ModifyExpressionValue(method = "<init>", at = @At(value = "CONSTANT", args = "intValue=110"))
   private int replaceFovUpperBound(int originalUpperBound) {
      return 150;
   }

   @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
   public void skipConfiguredCameraType(CameraType cameraType, CallbackInfo callback) {
      if (viewClip.shouldSkipCameraType(cameraType)) {
         CameraType[] cameraTypes = CameraType.values();
         this.setCameraType(cameraTypes[(cameraType.ordinal() + 1) % cameraTypes.length]);
         callback.cancel();
      }
   }
}

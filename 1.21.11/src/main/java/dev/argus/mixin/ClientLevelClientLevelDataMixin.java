package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.SkyboxModule;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class ClientLevelClientLevelDataMixin {
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

   @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
   private void overrideHorizonHeightForSkybox(CallbackInfoReturnable<Double> callback) {
      if (ClientAccess.argusclientMinecraft.level != null
         && skyboxModule.isToggled()
         && skyboxModule.isCurrentDimensionEnabled()
         && !ClientAccess.argusclientMinecraft.level.dimension().identifier().getPath().contains("over")) {
         callback.setReturnValue(1.0);
         callback.cancel();
      }
   }
}

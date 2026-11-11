package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.FreeLookModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.utils.LockUtils;
import dev.argus.client.utils.math.Vec3Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPickMixin {
   private static FreecamModule freecam() {
      return ClientContext.getModule(FreecamModule.class);
   }

   private static FreeLookModule freelook() {
      return ClientContext.getModule(FreeLookModule.class);
   }
   @Unique
   private boolean argusclient$pickingFromFreecam;

   @Shadow
   public abstract void pick(float partialTick);

   @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
   private void applyCameraPicking(float partialTick, CallbackInfo callback) {
      Minecraft client = (Minecraft)(Object)this;
      if (freecam().isToggled() && client.getCameraEntity() != null && !this.argusclient$pickingFromFreecam) {
         callback.cancel();
         Entity cameraEntity = client.getCameraEntity();
         double savedX = cameraEntity.getX();
         double savedY = cameraEntity.getY();
         double savedZ = cameraEntity.getZ();
         double savedPreviousX = cameraEntity.xo;
         double savedPreviousY = cameraEntity.yo;
         double savedPreviousZ = cameraEntity.zo;
         float savedYaw = cameraEntity.getYRot();
         float savedPitch = cameraEntity.getXRot();
         float savedPreviousYaw = cameraEntity.yRotO;
         float savedPreviousPitch = cameraEntity.xRotO;
         float savedHeadYaw = cameraEntity.getYHeadRot();
         cameraEntity.xo = freecam().previousCameraPosition.x;
         cameraEntity.yo = freecam().previousCameraPosition.y - cameraEntity.getEyeHeight(cameraEntity.getPose());
         cameraEntity.zo = freecam().previousCameraPosition.z;
         cameraEntity.setYRot(freecam().cameraYaw);
         cameraEntity.setYHeadRot(freecam().cameraYaw);
         cameraEntity.setXRot(freecam().cameraPitch);
         cameraEntity.yRotO = freecam().previousCameraYaw;
         cameraEntity.xRotO = freecam().previousCameraPitch;
         this.argusclient$pickingFromFreecam = true;
         LockUtils.runWithSharedLock(() -> {
            Vec3Utils.setComponents(cameraEntity.position(), freecam().cameraPosition.x, freecam().cameraPosition.y - cameraEntity.getEyeHeight(cameraEntity.getPose()), freecam().cameraPosition.z);
            this.pick(partialTick);
            Vec3Utils.setComponents(cameraEntity.position(), savedX, savedY, savedZ);
         });
         this.argusclient$pickingFromFreecam = false;
         cameraEntity.xo = savedPreviousX;
         cameraEntity.yo = savedPreviousY;
         cameraEntity.zo = savedPreviousZ;
         cameraEntity.setYRot(savedYaw);
         cameraEntity.setYHeadRot(savedHeadYaw);
         cameraEntity.setXRot(savedPitch);
         cameraEntity.yRotO = savedPreviousYaw;
         cameraEntity.xRotO = savedPreviousPitch;
      }

      if (freelook().isToggled() && !freecam().isToggled()) {
         callback.cancel();
      }
   }
}

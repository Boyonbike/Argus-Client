package dev.argus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.ESPModule;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
   private static final ESPModule espModule = ClientContext.getModule(ESPModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
   private void suppressBlockEntitySubmission(
      BlockEntityRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      if (!espModule.isRenderingBlockEntities()) {
         if (noRenderModule.shouldHideBlockEntityType(renderState.blockEntityType)) {
            callback.cancel();
         } else if (noRenderModule.isToggled()
            && noRenderModule.tileEntitiesSetting.getValue()
            && renderState.blockPos.distSqr(Minecraft.getInstance().gameRenderer.mainCamera().blockPosition())
               > noRenderModule.tileDistanceSetting.getValue() * noRenderModule.tileDistanceSetting.getValue()) {
            callback.cancel();
         }
      }
   }
}

package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EntityRenderEvent;
import dev.argus.client.modules.render.ChamsModule;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.render.EntityRenderStateAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final ChamsModule chamsModule = ClientContext.getModule(ChamsModule.class);

   @ModifyExpressionValue(method = "submit", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
   private static boolean applyEntityGlintAndShadow(boolean originalShadowFlag, @Local(argsOnly = true) EntityRenderState renderState) {
      return chamsModule.shouldRenderGlintForEntity(EntityRenderStateAccess.getEntity(renderState))
         ? true
         : ClientContext.shaderRegistry.renderingIntoTarget || noRenderModule.shouldHideEntityShadow() || originalShadowFlag;
   }

   @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
   public void onEntityRenderPre(
      EntityRenderState renderState,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      PoseStack poses,
      SubmitNodeCollector collector,
      CallbackInfo callback
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      if (entity != null) {
         EntityRenderEvent.Pre event = EntityRenderEvent.Pre.create(entity, poses, collector);
         ClientAccess.eventBus.post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "submit", at = @At("RETURN"))
   public void onEntityRenderPost(
      EntityRenderState renderState,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      PoseStack poses,
      SubmitNodeCollector collector,
      CallbackInfo callback
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      EntityRenderEvent.Post event = EntityRenderEvent.Post.create(entity, poses, collector);
      ClientAccess.eventBus.post(event);
   }

   @Inject(method = "submit", at = @At("HEAD"))
   public void applyEntityGlintAndFire(
      EntityRenderState renderState,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      PoseStack poses,
      SubmitNodeCollector collector,
      CallbackInfo callback
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      if (chamsModule.shouldRenderGlintForEntity(entity)) {
         renderState.displayFireAnimation = false;
      }

      if (noRenderModule.isToggled()
         && noRenderModule.fireSetting.getValue()
         && (noRenderModule.othersSetting.getValue() || entity instanceof LocalPlayer || entity instanceof EndCrystal)) {
         renderState.displayFireAnimation = false;
      }
   }
}

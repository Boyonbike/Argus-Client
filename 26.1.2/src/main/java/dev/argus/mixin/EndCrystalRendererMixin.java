package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.modules.render.AnimationsModule;
import dev.argus.client.modules.render.ChamsModule;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.EntityRenderStateAccess;
import dev.argus.client.utils.combat.EndCrystalState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EndCrystalRenderer.class)
public class EndCrystalRendererMixin {
   private static final AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static final ChamsModule chamsModule = ClientContext.getModule(ChamsModule.class);
   private static final AnimationsModule animationsModule = ClientContext.getModule(AnimationsModule.class);
   @Shadow
   @Final
   private static Identifier END_CRYSTAL_LOCATION;
   @Unique
   private static EndCrystal renderedCrystal;

   @ModifyArgs(
      method = {
            "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
      },
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0)
   )
   private void applyCrystalScale(Args arguments) {
      if (animationsModule.isToggled() && animationsModule.crystalsSetting.getValue()) {
         arguments.set(0, (Float)arguments.get(0) * animationsModule.crystalScaleSetting.getValue());
         arguments.set(1, (Float)arguments.get(1) * animationsModule.crystalScaleSetting.getValue());
         arguments.set(2, (Float)arguments.get(2) * animationsModule.crystalScaleSetting.getValue());
      }
   }

   @ModifyExpressionValue(
      method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;RENDER_TYPE:Lnet/minecraft/client/renderer/rendertype/RenderType;"
      )
   )
   private RenderType selectCrystalRenderType(RenderType originalType) {
      return chamsModule.isToggled()
            && chamsModule.shouldRenderEntityInRange(renderedCrystal)
            && chamsModule.xQZSetting.getValue()
            && !ClientContext.shaderRegistry.renderingIntoTarget
         ? ClientRenderTypes.armorCutoutNoCull(END_CRYSTAL_LOCATION)
         : originalType;
   }

   @Inject(
      method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void onCrystalRenderSubmit(
      EndCrystalRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      renderedCrystal = (EndCrystal)EntityRenderStateAccess.getEntity(renderState);
      if (renderedCrystal != null
         && System.currentTimeMillis() - ((EndCrystalState)renderedCrystal).getSpawnTime() > Math.max(ClientContext.serverStateManager.getLatencyMillis(), 50)
         && renderedCrystal.tickCount < 10
         && autoCrystalModule.isToggled()
         && autoCrystalModule.instantSetting.getValue().isEnabled()
         && ((EndCrystalState)renderedCrystal).isClientAttacked()) {
         callback.cancel();
      }
   }

   @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
   private static void applyCrystalBobOffset(float age, CallbackInfoReturnable<Float> callback) {
      if (animationsModule.isToggled() && animationsModule.crystalsSetting.getValue()) {
         float bobOffset = Mth.sin(age * 0.2F) / 2.0F + 0.5F;
         float floatFactor = 1.0F;
         if (animationsModule.isToggled() && animationsModule.crystalsSetting.getValue()) {
            floatFactor = animationsModule.floatFactorSetting.getValue();
         }

         if (age < 10.0F
            && autoCrystalModule.isToggled()
            && animationsModule.floatFactorSetting.getValue() != 0.0F
            && autoCrystalModule.instantSetting.getValue().isEnabled()
            && ((EndCrystalState)renderedCrystal).isClientAttacked()) {
            floatFactor *= ((EndCrystalState)renderedCrystal).getSpawnTime() % 2L == 0L ? 0.0F : 1.0F;
         }

         bobOffset = (bobOffset * bobOffset + bobOffset) * floatFactor;
         callback.cancel();
         callback.setReturnValue(bobOffset - 1.4F);
      }
   }
}

package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.modules.render.AnimationsModule;
import dev.argus.client.render.EntityRenderStateAccess;
import dev.argus.client.utils.combat.EndCrystalState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalModel.class)
public class EndCrystalModelMixin {
   private static final AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static final AnimationsModule animationsModule = ClientContext.getModule(AnimationsModule.class);
   @Shadow
   @Final
   public ModelPart base;
   @Shadow
   @Final
   public ModelPart innerGlass;
   @Shadow
   @Final
   public ModelPart outerGlass;
   @Shadow
   @Final
   public ModelPart cube;

   @ModifyExpressionValue(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;ageInTicks:F", ordinal = 0),
      require = 0
   )
   private float applyCrystalRotationSpeed(float originalRotation, @Local(argsOnly = true) EndCrystalRenderState renderState) {
      return originalRotation * this.updateCrystalAnimationAndGetSpeed(renderState);
   }

   @Unique
   private float updateCrystalAnimationAndGetSpeed(EndCrystalRenderState renderState) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      float speedMultiplier = 1.0F;
      if (entity.tickCount < 10
         && autoCrystalModule.isToggled()
         && autoCrystalModule.instantSetting.getValue().isEnabled()
         && ((EndCrystalState)entity).isClientAttacked()) {
         speedMultiplier *= 1.5F;
      }

      if (animationsModule.isToggled() && animationsModule.crystalsSetting.getValue()) {
         speedMultiplier *= animationsModule.rotationSpeedSetting.getValue();
         this.base.skipDraw = animationsModule.shouldHideCrystalPart(0);
         this.outerGlass.skipDraw = animationsModule.shouldHideCrystalPart(1);
         this.innerGlass.skipDraw = animationsModule.shouldHideCrystalPart(2);
         this.cube.skipDraw = animationsModule.shouldHideCrystalPart(3);
      }

      return speedMultiplier;
   }
}

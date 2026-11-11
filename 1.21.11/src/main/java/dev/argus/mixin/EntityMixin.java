package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.StepHeightEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.modules.movement.VelocityModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.modules.render.FreeLookModule;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.movement.EntityMotionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityMotionAccess {
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   private static ElytraFlyModule elytraFlyModule = ClientContext.getModule(ElytraFlyModule.class);
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private static VelocityModule velocityModule = ClientContext.getModule(VelocityModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   @Unique
   private float cameraPitch;
   @Unique
   private float cameraYaw;
   @Unique
   private Vec3 previousVelocity;

   @Shadow
   private Vec3 collide(Vec3 movement) {
      return null;
   }

   @Shadow
   @Override
   public abstract boolean equals(Object var1);

   @Shadow
   public abstract Vec3 getDeltaMovement();

   @Shadow
   public abstract boolean hasPose(Pose var1);

   @Shadow
   public abstract Vec3 calculateViewVector(float var1, float var2);

   @ModifyReceiver(
      method = "getBlockSpeedFactor",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getSpeedFactor()F"),
      require = 0
   )
   private Block overrideBlockSpeedFactor(Block originalBlock) {
      if ((Object)this != Minecraft.getInstance().player) {
         return originalBlock;
      } else {
         return noSlowModule.isToggled() && noSlowModule.soulSandSetting.getValue() ? Blocks.STONE : originalBlock;
      }
   }

   @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void cancelEntityPush(Entity otherEntity, CallbackInfo callback) {
      if (this.equals(Minecraft.getInstance().player) && velocityModule.isToggled() && velocityModule.pushSetting.getValue()) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(
      method = "updateFluidHeightAndDoFluidPushing",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
      )
   )
   private Vec3 modifyFluidPush(Vec3 originalPush) {
      return velocityModule.isToggled() && velocityModule.liquidsSetting.getValue() ? Vec3.ZERO : originalPush;
   }

   @ModifyExpressionValue(
      method = "pick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;")
   )
   private Vec3 overridePickViewVector(Vec3 originalView) {
      if (!freelook.isToggled()) {
         return originalView;
      }

      Vector2f cameraRotation = freelook.getCameraRotation();
      return this.calculateViewVector(cameraRotation.y(), cameraRotation.x());
   }

   @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
   public void updateFreelookRotation(double horizontalInput, double verticalInput, CallbackInfo callback) {
      if (!freecamModule.isToggled() && freelook.isToggled() && ClientAccess.isLocalPlayer(this)) {
         double pitchDelta = verticalInput * 0.15;
         double yawDelta = horizontalInput * 0.15;
         this.cameraPitch = Mth.clamp(this.cameraPitch + (float)pitchDelta, -90.0F, 90.0F);
         this.cameraYaw += (float)yawDelta;
         callback.cancel();
      }
   }

   @ModifyExpressionValue(
      method = "move",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
   )
   private Vec3 modifyStepCollision(Vec3 originalCollision, @Local(argsOnly = true) Vec3 movement) {
      if (!ClientAccess.isLocalPlayer(this)) {
         return this.collide(movement);
      }

      LivingEntity entity = (LivingEntity)(Object)this;
      float previousHeight = EntityUtils.getStepHeight(entity);
      StepHeightEvent beforeStep = new StepHeightEvent(EventStage.Pre, previousHeight);
      ClientAccess.eventBus.post(beforeStep);
      boolean modified = beforeStep.isModified();
      float requestedHeight = beforeStep.getHeight();
      if (modified) {
         EntityUtils.setStepHeight(entity, requestedHeight);
      }

      Vec3 collision = this.collide(movement);
      if (modified) {
         EntityUtils.setStepHeight(entity, previousHeight);
      }

      if (collision != null) {
         ClientAccess.eventBus.post(new StepHeightEvent(EventStage.Post, (float)collision.y));
      }

      return collision;
   }

   // getPose() runs many times per entity per tick and frame; avoid a callback allocation on every call.
   @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
   private Pose overrideElytraPose(Pose original) {
      return (Object)this == Minecraft.getInstance().player && (elytraFlyModule.isPacketFlightActive() || elytraFlyModule.isDurabilityBypassActive())
         ? Pose.STANDING
         : original;
   }

   @ModifyExpressionValue(
      method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F")
   )
   private float applyLegacySneakingEyePosition(float originalEyeHeight) {
      return noRenderModule.isToggled() && noRenderModule.newSneakingSetting.getValue() && this.hasPose(Pose.CROUCHING) ? 1.54F : originalEyeHeight;
   }

   @WrapOperation(
      method = "saveWithoutId",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V")
   )
   private void saveAdditionalDataForSidePlayer(Entity entity, ValueOutput output, Operation original) {
      if (output instanceof TagValueOutput tagOutput && tagOutput.buildResult().contains("argusclient_side_player")) {
         try {
            original.call(new Object[]{entity, output});
         } catch (Throwable var6) {
         }
      } else {
         original.call(new Object[]{entity, output});
      }
   }

   @Inject(
      method = "baseTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", ordinal = 0, shift = Shift.AFTER)
   )
   private void capturePreviousVelocity(CallbackInfo callback) {
      this.previousVelocity = this.getDeltaMovement();
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializePreviousVelocity(EntityType entityType, Level level, CallbackInfo callback) {
      this.previousVelocity = Vec3.ZERO;
   }

   @Unique
   @Override
   public float getCameraPitch() {
      return this.cameraPitch;
   }

   @Unique
   @Override
   public float getCameraYaw() {
      return this.cameraYaw;
   }

   @Unique
   @Override
   public void setCameraPitch(float pitch) {
      this.cameraPitch = pitch;
   }

   @Unique
   @Override
   public void setCameraYaw(float yaw) {
      this.cameraYaw = yaw;
   }

   @Override
   public Vec3 argusclient$getPrevVelocity() {
      return this.previousVelocity;
   }

   @Unique
   private static void argusclient$bindEntityModules(ModuleRegistry moduleRegistry) {
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
      elytraFlyModule = (ElytraFlyModule)moduleRegistry.getModule(ElytraFlyModule.class);
      freecamModule = (FreecamModule)moduleRegistry.getModule(FreecamModule.class);
      velocityModule = (VelocityModule)moduleRegistry.getModule(VelocityModule.class);
      freelook = (FreeLookModule)moduleRegistry.getModule(FreeLookModule.class);
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(NoRenderModule.class);
   }
}

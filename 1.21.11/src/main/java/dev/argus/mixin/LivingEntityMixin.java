package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.LivingEntityAddEffectEvent;
import dev.argus.client.events.LivingEntityJumpFromGroundEvent;
import dev.argus.client.events.LivingEntityRotationEvent;
import dev.argus.client.events.StatusEffectQueryEvent;
import dev.argus.client.events.VehicleMoveEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.misc.SwingModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.FastClimbModule;
import dev.argus.client.modules.movement.HighJumpModule;
import dev.argus.client.modules.movement.JesusModule;
import dev.argus.client.modules.movement.NoJumpDelayModule;
import dev.argus.client.render.ForcedEntityLayers;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.network.ServerSideDeathState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements ServerSideDeathState, ForcedEntityLayers {
   @Shadow
   private int noJumpDelay;
   @Unique
   private VehicleMoveEvent vehicleMoveEvent;
   @Unique
   private LivingEntityRotationEvent rotationEvent;
   @Unique
   private boolean serverSideDead;
   @Unique
   private boolean forcedLayers;
   @Unique
   private float travelYaw;
   @Unique
   private float travelPitch;
   private static ElytraFlyModule efly = ClientContext.getModule(ElytraFlyModule.class);
   private static NoJumpDelayModule noJumpDelayModule = ClientContext.getModule(NoJumpDelayModule.class);
   private static JesusModule jesusModule = ClientContext.getModule(JesusModule.class);
   private static SwingModule swing = ClientContext.getModule(SwingModule.class);
   private static FastClimbModule fastClimbModule = ClientContext.getModule(FastClimbModule.class);
   private static HighJumpModule highJumpModule = ClientContext.getModule(HighJumpModule.class);

   public LivingEntityMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Shadow
   @Override
   public abstract float getViewYRot(float var1);

   @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
   private void overrideSwingDuration(CallbackInfoReturnable<Integer> callback) {
      if (swing.isToggled()) {
         LivingEntity entity = (LivingEntity)(Object)this;
         callback.cancel();
         boolean legacySwing = swing.typeSetting.getValue() == SwingModule.TypeChoice.ONE_EIGHT;
         int baseDuration = (int)((legacySwing ? 7 : 6) / swing.speedSetting.getValue());
         if (MobEffectUtil.hasDigSpeed(entity)) {
            callback.setReturnValue(baseDuration - (1 + MobEffectUtil.getDigSpeedAmplification(entity)));
         } else {
            callback.setReturnValue(
               entity.hasEffect(MobEffects.MINING_FATIGUE) ? baseDuration + (1 + entity.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2 : baseDuration
            );
         }
      }
   }

   // isFallFlying() is read for every living entity in ticking and rendering. One return-value modifier
   // replaces the former HEAD and TAIL cancellable injects, which allocated two callbacks per call.
   @ModifyReturnValue(method = "isFallFlying", at = @At("RETURN"))
   private boolean overrideFallFlying(boolean original) {
      return original && !(ClientAccess.isLocalPlayer(this) && (efly.isPacketFlightActive() || efly.isDurabilityBypassActive()));
   }

   @ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
   private boolean allowStandingOnWater(boolean original) {
      return original
         || (Object)this == Minecraft.getInstance().player && jesusModule.isToggled() && jesusModule.canWalkOnWater() && !Minecraft.getInstance().player.isInWater();
   }

   @Inject(method = "aiStep", at = @At(value = "CONSTANT", args = "stringValue=jump"))
   private void postAiStepRotation(CallbackInfo callback) {
      if (ClientAccess.isLocalPlayer(this)) {
         LivingEntityRotationEvent.LivingEntityAiStepEvent event = new LivingEntityRotationEvent.LivingEntityAiStepEvent(this.getViewYRot(1.0F), this.getXRot());
         ClientAccess.eventBus.post(event);
      }
   }

   @Inject(method = "travel", at = @At("HEAD"))
   private void applyTravelRotation(Vec3 movement, CallbackInfo callback) {
      if ((Object)this == Minecraft.getInstance().player) {
         ElytraFlyModule.hook = true;
         this.travelYaw = this.getViewYRot(1.0F);
         this.travelPitch = this.getXRot();
         this.rotationEvent = new LivingEntityRotationEvent(this.travelYaw, this.travelPitch, false);
         ClientAccess.eventBus.post(this.rotationEvent);
         if (this.rotationEvent.isCancelled()) {
            this.setYRot(this.rotationEvent.getYaw());
            this.setXRot(this.rotationEvent.getPitch());
         }
      }
   }

   @Inject(method = "travel", at = @At("TAIL"))
   private void restoreTravelRotation(Vec3 movement, CallbackInfo callback) {
      if ((Object)this == Minecraft.getInstance().player) {
         ElytraFlyModule.hook = false;
         if (this.rotationEvent.isCancelled()) {
            this.setYRot(this.travelYaw);
            this.setXRot(this.travelPitch);
         }
      }
   }

   @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
   private float modifyJumpYaw(float originalYaw) {
      this.rotationEvent = new LivingEntityRotationEvent(this.getYRot(), this.getXRot(), true);
      ClientAccess.eventBus.post(this.rotationEvent);
      return this.rotationEvent.isCancelled() ? this.rotationEvent.getYaw() : originalYaw;
   }

   @Override
   public void setServerSideDead(boolean dead) {
      this.serverSideDead = dead;
   }

   @Override
   public boolean isServerSideDead() {
      return this.serverSideDead;
   }

   @Override
   public void argusclient$setForcedLayers(boolean forced) {
      this.forcedLayers = forced;
   }

   @Override
   public boolean argusclient$areLayersForced() {
      return this.forcedLayers;
   }

   @Inject(method = "aiStep", at = @At("HEAD"))
   public void updateJumpDelay(CallbackInfo callback) {
      Minecraft client = Minecraft.getInstance();
      if (efly.isToggled() && efly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE && this.equals(client.player) && this.noJumpDelay > 2) {
         this.noJumpDelay = 2;
      } else if (noJumpDelayModule.isToggled() && this.equals(client.player) && !client.player.isFallFlying()) {
         this.noJumpDelay = 0;
      }
   }

   // Return-value modifiers instead of cancellable injects: these run for every living entity's effect
   // checks, and a cancellable inject allocated a callback object on each call.
   @ModifyReturnValue(method = "hasEffect", at = @At("RETURN"))
   private boolean filterHasEffect(boolean original, Holder effect) {
      if (original && (Object)this == Minecraft.getInstance().player) {
         StatusEffectQueryEvent event = new StatusEffectQueryEvent(effect);
         ClientAccess.eventBus.post(event);
         if (event.isCancelled()) {
            return false;
         }
      }

      return original;
   }

   @ModifyReturnValue(method = "getEffect", at = @At("RETURN"))
   private MobEffectInstance filterGetEffect(MobEffectInstance original, Holder effect) {
      if (original != null && (Object)this == Minecraft.getInstance().player) {
         StatusEffectQueryEvent event = new StatusEffectQueryEvent(effect);
         ClientAccess.eventBus.post(event);
         if (event.isCancelled()) {
            return null;
         }
      }

      return original;
   }

   @Inject(
      method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", shift = Shift.AFTER)
   )
   private void postAddEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callback) {
      Minecraft client = Minecraft.getInstance();
      if (ClientAccess.isLocalPlayer(this) || client.isLocalServer() && (Object)this instanceof ServerPlayer) {
         LivingEntityAddEffectEvent event = new LivingEntityAddEffectEvent(effect);
         ClientAccess.eventBus.post(event);
      }
   }

   @Inject(
      method = "jumpFromGround",
      at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/entity/LivingEntity;getJumpPower()F", shift = Shift.AFTER),
      cancellable = true
   )
   private void handleJumpPower(CallbackInfo callback, @Local LocalFloatRef jumpPower) {
      if (ClientAccess.isLocalPlayer(this)) {
         LivingEntityJumpFromGroundEvent event = new LivingEntityJumpFromGroundEvent(jumpPower.get());
         ClientAccess.eventBus.post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }

         jumpPower.set(event.getJumpPower());
      }
   }

   @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "CONSTANT", args = "floatValue=1.0E-5"))
   private float modifyJumpThreshold(float originalThreshold) {
      return highJumpModule.isToggled() ? -1.0F : originalThreshold;
   }

   @ModifyExpressionValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "CONSTANT", args = "doubleValue=0.2"))
   private double modifyClimbSpeed(double originalSpeed, @Local(ordinal = 1) Vec3 movement) {
      if (fastClimbModule.canFastClimb()) {
         return fastClimbModule.speedSetting.isAtMinimum() ? movement.y : fastClimbModule.speedSetting.getValue().floatValue();
      } else {
         return originalSpeed;
      }
   }

   @Inject(method = "handleOnClimbable", at = @At("HEAD"), cancellable = true)
   private void bypassClimbConstraints(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
      if (fastClimbModule.canFastClimb() && fastClimbModule.speedSetting.isAtMinimum()) {
         callback.setReturnValue(movement);
      }
   }

   @ModifyExpressionValue(
      method = "addAdditionalSaveData",
      at = {
            @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityEquipment;isEmpty()Z")
      }
   )
   private boolean skipSidePlayerDataSave(boolean originalEmpty, @Local(argsOnly = true) ValueOutput output) {
      return output instanceof TagValueOutput tagOutput && tagOutput.buildResult().contains("argusclient_side_player") || originalEmpty;
   }

   @Inject(
      method = "handleRelativeFrictionAndCalculateMovement",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
         shift = Shift.BEFORE
      )
   )
   private void postVehicleMovementOnGround(Vec3 travelInput, float movementFactor, CallbackInfoReturnable<Vec3> callback) {
      if (VehicleMoveEvent.isLocalPlayerVehicle(this)) {
         this.vehicleMoveEvent = new VehicleMoveEvent(this.getDeltaMovement());
         ClientAccess.eventBus.post(this.vehicleMoveEvent);
         Vec3Utils.setComponents(this.getDeltaMovement(), this.vehicleMoveEvent.getX(), this.vehicleMoveEvent.getY(), this.vehicleMoveEvent.getZ());
      }
   }

   @Inject(
      method = {"travelInWater", "travelInLava"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
         shift = Shift.BEFORE
      )
   )
   private void postVehicleMovementInFluid(Vec3 travelInput, double movementFactor, boolean movementFlag, double verticalFactor, CallbackInfo callback) {
      if (VehicleMoveEvent.isLocalPlayerVehicle(this)) {
         this.vehicleMoveEvent = new VehicleMoveEvent(this.getDeltaMovement());
         ClientAccess.eventBus.post(this.vehicleMoveEvent);
         Vec3Utils.setComponents(this.getDeltaMovement(), this.vehicleMoveEvent.getX(), this.vehicleMoveEvent.getY(), this.vehicleMoveEvent.getZ());
      }
   }

   @Unique
   private static void argusclient$bindLivingEntityModules(ModuleRegistry moduleRegistry) {
      efly = (ElytraFlyModule)moduleRegistry.getModule(ElytraFlyModule.class);
      noJumpDelayModule = (NoJumpDelayModule)moduleRegistry.getModule(NoJumpDelayModule.class);
      jesusModule = (JesusModule)moduleRegistry.getModule(JesusModule.class);
      swing = (SwingModule)moduleRegistry.getModule(SwingModule.class);
      fastClimbModule = (FastClimbModule)moduleRegistry.getModule(FastClimbModule.class);
      highJumpModule = (HighJumpModule)moduleRegistry.getModule(HighJumpModule.class);
   }
}

package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.access.LocalPlayerTickAccess;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerAiStepEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerMoveTowardsClosestSpaceEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.LocalPlayerTickPhaseEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.exploit.ReachModule;
import dev.argus.client.modules.movement.EntityControlModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.modules.movement.SprintModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementMixin extends AbstractClientPlayer implements ClientAccess, LocalPlayerTickAccess {
   private static final NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   private static final EntityControlModule entityControlModule = ClientContext.getModule(EntityControlModule.class);
   private static final SprintModule sprintModule = ClientContext.getModule(SprintModule.class);
   private static final ReachModule reachModule = ClientContext.getModule(ReachModule.class);
   @Unique
   private LocalPlayerSendPositionEvent sendPositionEvent;
   @Shadow
   private float yRotLast;
   @Shadow
   private float xRotLast;

   public LocalPlayerMovementMixin(ClientLevel level, GameProfile profile) {
      super(level, profile);
   }

   @Shadow
   protected abstract void updateAutoJump(float var1, float var2);

   @Shadow
   private void sendPosition() {
   }

   @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = Shift.BEFORE))
   public void postTickBeforeSuperclassTick(CallbackInfo callback) {
      if (ClientAccess.argusclientMinecraft.player != null && ClientAccess.argusclientMinecraft.level != null) {
         ClientAccess.eventBus.post(new LocalPlayerPreTickEvent());
      }
   }

   @Inject(method = "tick", at = @At("RETURN"))
   public void postTickReturnEvent(CallbackInfo callback) {
      if (ClientAccess.argusclientMinecraft.player != null && ClientAccess.argusclientMinecraft.level != null) {
         ClientAccess.eventBus.post(new LocalPlayerTickEvent());
      }
   }

   @Inject(method = "move", at = @At("HEAD"), cancellable = true)
   private void applyMoveEvent(MoverType moverType, Vec3 movement, CallbackInfo callback) {
      Vec3 originalMovement = new Vec3(movement.x, movement.y, movement.z);
      LocalPlayerMoveEvent event = new LocalPlayerMoveEvent(movement, moverType);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      } else if (!originalMovement.equals(event.getVelocity())) {
         callback.cancel();
         double previousX = this.getX();
         double previousZ = this.getZ();
         super.move(event.getMoverType(), event.getVelocity());
         float deltaX = (float)(this.getX() - previousX);
         float deltaZ = (float)(this.getZ() - previousZ);
         super.addWalkedDistance(Mth.length(deltaX, deltaZ) * 0.6F);
         this.updateAutoJump(deltaX, deltaZ);
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void postBeforePassengerCheckEvent(CallbackInfo callback) {
      ClientAccess.eventBus.post(new LocalPlayerTickPhaseEvent.BeforePassengerCheck());
   }

   @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
   private void suppressTotemActivation(net.minecraft.world.item.ItemStack stack, CallbackInfo callback) {
      dev.argus.client.modules.render.NoRenderModule noRender =
         ClientContext.getModule(dev.argus.client.modules.render.NoRenderModule.class);
      if (stack.getItem() == net.minecraft.world.item.Items.TOTEM_OF_UNDYING
         && noRender.isToggled() && noRender.totemOverlaySetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = Shift.AFTER),
      cancellable = true
   )
   private void applyPreSendPositionEvent(CallbackInfo callback) {
      this.resetEvent();
      ClientAccess.eventBus.post(this.sendPositionEvent);
      this.setPos(this.sendPositionEvent.getX(), this.sendPositionEvent.getY(), this.sendPositionEvent.getZ());
      this.setYRot(this.sendPositionEvent.getCurrentYaw());
      this.setXRot(this.sendPositionEvent.getCurrentPitch());
      this.setOnGround(this.sendPositionEvent.isOnGround());
      if (this.sendPositionEvent.isCancelled()) {
         callback.cancel();
      }

      ClientAccess.eventBus.post(new LocalPlayerTickPhaseEvent.LocalPlayerTickEvent());
   }

   @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;ambientSoundHandlers:Ljava/util/List;"))
   private void restorePreSendPositionState(CallbackInfo callback) {
      if (this.sendPositionEvent.getX() != this.sendPositionEvent.getOriginalX()
         || this.sendPositionEvent.getY() != this.sendPositionEvent.getOriginalY()
         || this.sendPositionEvent.getZ() != this.sendPositionEvent.getOriginalZ()) {
         this.setPos(new Vec3(this.sendPositionEvent.getOriginalX(), this.sendPositionEvent.getOriginalY(), this.sendPositionEvent.getOriginalZ()));
      }

      this.resetRotations();
      if (this.onGround() == this.sendPositionEvent.isOnGround()) {
         this.setOnGround(this.sendPositionEvent.wasOnGround());
      }
   }

   @Inject(method = "sendPosition", at = @At("RETURN"))
   private void postSendPositionReturnEvent(CallbackInfo callback) {
      LocalPlayerSendPositionEvent event = new LocalPlayerSendPositionEvent(EventStage.Post, this.sendPositionEvent);
      if (this.sendPositionEvent.isCancelled()) {
         event.cancel();
      }

      ClientAccess.eventBus.post(event);
   }

   @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
   private boolean overrideSprintInput(boolean vanillaSprint) {
      return sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting() || vanillaSprint;
   }

   @ModifyArgs(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V"), require = 0)
   private void forceSprintingWhenAllowed(Args arguments) {
      if (sprintModule.isToggled() && sprintModule.canStartSprinting()) {
         arguments.set(0, true);
      }
   }

   @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
   private void postSprintAiStepEvent(CallbackInfo callback) {
      ClientAccess.eventBus.post(new LocalPlayerAiStepEvent());
   }

   @Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
   private void cancelMoveTowardsClosestSpace(double x, double z, CallbackInfo callback) {
      LocalPlayerMoveTowardsClosestSpaceEvent event = new LocalPlayerMoveTowardsClosestSpaceEvent();
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "getJumpRidingScale", at = @At("HEAD"), cancellable = true)
   private void maximizeRidingJumpScale(CallbackInfoReturnable<Float> callback) {
      if (entityControlModule.isToggled() && entityControlModule.horseJumpSetting.getValue()) {
         callback.setReturnValue(1.0F);
      }
   }

   @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"))
   private void suppressPortalTransitionEffect(CallbackInfo callback) {
      if (noSlowModule.isToggled() && this.portalProcess != null) {
         this.portalProcess.setAsInsidePortalThisTick(false);
      }
   }

   @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
   private void overrideMovingSlowly(CallbackInfoReturnable<Boolean> callback) {
      boolean bypassCrawl = ClientAccess.argusclientMinecraft.player.isVisuallyCrawling() && noSlowModule.crawlSetting.getValue();
      boolean bypassSneak = !bypassCrawl && ClientAccess.argusclientMinecraft.player.isShiftKeyDown() && noSlowModule.sneakSetting.getValue();
      if (noSlowModule.isToggled() && (bypassCrawl || bypassSneak)) {
         callback.setReturnValue(false);
      }
   }

   @ModifyExpressionValue(method = "isSlowDueToUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean overrideItemUseForSlowdown(boolean originalUsingItem) {
      return (!noSlowModule.isToggled() || !noSlowModule.itemsSetting.getValue()) && originalUsingItem;
   }

   @Inject(
      method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
      ),
      cancellable = true
   )
   private static void useBlockHitWhenEntityTraceIgnored(
      Entity entity, double blockRange, double entityRange, float partialTick, CallbackInfoReturnable<HitResult> callback, @Local HitResult blockHit
   ) {
      if (reachModule.shouldIgnoreEntityTrace()) {
         callback.setReturnValue(blockHit);
      }
   }

   @Override
   public void sendMovementPacketsWrapper() {
      this.sendPosition();
   }

   @Override
   public void superTick() {
      super.tick();
   }

   @Override
   public void resetEvent() {
      this.sendPositionEvent = new LocalPlayerSendPositionEvent(
         EventStage.Pre, this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.onGround()
      );
   }

   @Override
   public void argusclient$updateServer(float yaw, float pitch) {
      this.yRotLast = yaw;
      this.xRotLast = pitch;
   }

   @Override
   public void resetRotations() {
      RotationManager rotations = ClientContext.rotationManager;
      float renderYaw = this.isPassenger() && !this.sendPositionEvent.isModified() ? this.getVehicle().getYRot() : this.sendPositionEvent.getYaw();
      rotations.updateRenderRotationsIfNeeded(renderYaw, this.sendPositionEvent.getPitch());
      if (this.getYRot() == this.sendPositionEvent.getCurrentYaw()) {
         this.setYRot(this.sendPositionEvent.getOriginalYaw());
      }

      if (this.getXRot() == this.sendPositionEvent.getCurrentPitch()) {
         this.setXRot(this.sendPositionEvent.getOriginalPitch());
      }
   }
}

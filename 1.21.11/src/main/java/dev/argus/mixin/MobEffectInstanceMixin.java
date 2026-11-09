package dev.argus.mixin;

import dev.argus.client.access.MobEffectDurationAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectInstance.class)
public class MobEffectInstanceMixin implements MobEffectDurationAccess {
   @Shadow
   private int duration;
   @Unique
   private int argusclient$initialDuration;

   @Inject(method = "<init>(Lnet/minecraft/core/Holder;IIZZZLnet/minecraft/world/effect/MobEffectInstance;)V", at = @At("TAIL"))
   private void argusclient$captureInitialDuration(
      Holder effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, MobEffectInstance hiddenEffect, CallbackInfo ci
   ) {
      this.argusclient$initialDuration = duration;
   }

   @Inject(method = "setDetailsFrom", at = @At("TAIL"))
   private void argusclient$captureCopiedDuration(MobEffectInstance effect, CallbackInfo ci) {
      this.argusclient$initialDuration = effect.getDuration();
   }

   @Inject(
      method = "update",
      at = @At(value = "FIELD", target = "Lnet/minecraft/world/effect/MobEffectInstance;duration:I", opcode = 181, shift = Shift.AFTER)
   )
   private void argusclient$captureUpdatedDuration(CallbackInfoReturnable<Boolean> ci) {
      this.argusclient$initialDuration = this.duration;
   }

   @Override
   public float argusclient$getDurationRatio() {
      return (float)this.duration / this.argusclient$initialDuration;
   }
}

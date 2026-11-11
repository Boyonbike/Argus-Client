package dev.argus.client.utils;

import dev.argus.client.ClientContext;
import java.util.function.Supplier;
import net.minecraft.util.Mth;

public class ClampedInterpolatedFloat extends InterpolatedFloat {
   @Override
   public float getValue() {
      float rawValue = this.currentValue;
      float upperBound = 1.0F;
      return Mth.clamp(rawValue, 0.0F, upperBound);
   }

   public void setValue(boolean value) {
      this.setValue(value ? 1.0F : 0.0F);
   }

   public void setTarget(boolean value) {
      this.setTarget(value ? 1.0F : 0.0F);
   }

   public ClampedInterpolatedFloat(float speed, boolean easingEnabled) {
      super(speed, easingEnabled);
   }

   public ClampedInterpolatedFloat(Supplier speedSupplier, boolean easingEnabled) {
      super(speedSupplier, easingEnabled);
   }

   public ClampedInterpolatedFloat(float speed) {
      super(speed);
   }

   public void reset() {
      this.setTarget(0.0F);
   }

   @Override
   public float updateAndGet() {
      FrameTiming frameTiming = ClientContext.frameTiming;
      float interpolationInput = 0.5F;
      float timingFactor = frameTiming.getFrameFactor(interpolationInput);
      Float suppliedSpeed = (Float)this.speedSupplier.get();
      float interpolationFactor = timingFactor * suppliedSpeed;
      if (this.easingEnabled) {
         float shiftedFactor = interpolationFactor - 1.0F;
         interpolationFactor = (float)(1.0 - shiftedFactor * Math.pow(shiftedFactor, 3.0));
      }

      this.currentValue = this.previousValue + (this.currentValue - this.previousValue) * interpolationFactor;
      boolean shouldSnap = this.currentValue == this.targetValue;
      if (!shouldSnap) {
         float targetDifference = this.targetValue - this.currentValue;
         double absoluteTargetDifference = Math.abs(targetDifference);
         shouldSnap = absoluteTargetDifference < 0.01;
      }

      if (shouldSnap) {
         float snapValue = this.targetValue;
         this.setValue(snapValue);
      }

      int timingSample = ClientContext.frameTiming.getFramesPerSecond();
      if (timingSample <= 15) {
         this.currentValue = this.targetValue;
      }

      return this.getValue();
   }
}

package dev.argus.client.utils;

import java.util.function.Function;
import net.minecraft.util.Mth;

public class FloatAnimation {
   private float startValue;
   private float targetValue;
   private long endTimeMillis;
   private long durationMillis;

   public float getValue() {
      if (System.currentTimeMillis() > this.endTimeMillis) {
         return this.targetValue;
      }

      float remainingMillis = (float)(this.endTimeMillis - System.currentTimeMillis());
      float remainingFraction = remainingMillis / (float)Math.max(this.durationMillis, 1L);
      float progress = 1.0F - Mth.clamp(remainingFraction, 0.0F, 1.0F);
      return progress * (this.targetValue - this.startValue) + this.startValue;
   }

   public void setValue(float value) {
      this.setTarget(value, 0L);
   }

   public void setTarget(boolean targetState, long newDurationMillis) {
      float decodedTarget = targetState ? 1.0F : 0.0F;
      this.setTarget(decodedTarget, newDurationMillis);
   }

   public float getValue(Function easing) {
      if (System.currentTimeMillis() > this.endTimeMillis) {
         return this.targetValue;
      }

      float interpolationScale = 1.0F;
      float remainingMillis = (float)(this.endTimeMillis - System.currentTimeMillis());
      float remainingFraction = remainingMillis / (float)Math.max(this.durationMillis, 1L);
      float clampUpperBound = 1.0F;
      float easingInput = interpolationScale - Mth.clamp(remainingFraction, 0.0F, clampUpperBound);
      Float easingResult = (Float)easing.apply(easingInput);
      return easingResult * (this.targetValue - this.startValue) + this.startValue;
   }

   public void setTarget(float newTargetValue, long newDurationMillis) {
      if (this.targetValue != newTargetValue) {
         this.startValue = this.getValue();
         this.targetValue = newTargetValue;
         this.endTimeMillis = System.currentTimeMillis() + newDurationMillis;
         this.durationMillis = newDurationMillis;
      }
   }
}

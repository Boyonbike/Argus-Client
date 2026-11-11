package dev.argus.client.utils;

import dev.argus.client.ClientAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Mth;

public final class FrameTiming implements ClientAccess {
   private static final long NANOS_PER_SECOND = 1000000000L;
   private final Deque<Long> frameExpirationsNanos = new ArrayDeque<>();
   private long lastFrameTimeNanos = System.nanoTime();
   private long lastFrameRate;
   private int framesPerSecond;

   public int getFramesPerSecond() {
      return this.framesPerSecond;
   }

   public float getFrameFactor(float rateScale) {
      return Mth.clamp(1.0F / ((float)this.lastFrameRate * rateScale), 0.0F, 1.0F);
   }

   public void recordFrame() {
      long sampledTimeNanos = System.nanoTime();
      Deque<Long> expirationQueue = this.frameExpirationsNanos;
      long expirationNanos = sampledTimeNanos + 1000000000L;
      expirationQueue.add(expirationNanos);

      while (!this.frameExpirationsNanos.isEmpty()) {
         Long headExpirationNanos = this.frameExpirationsNanos.peekFirst();
         if (sampledTimeNanos <= headExpirationNanos) {
            break;
         }

         this.frameExpirationsNanos.remove();
      }

      this.framesPerSecond = this.frameExpirationsNanos.size();
      this.lastFrameRate = 1000000000L / (sampledTimeNanos - this.lastFrameTimeNanos);
      this.lastFrameTimeNanos = sampledTimeNanos;
   }
}

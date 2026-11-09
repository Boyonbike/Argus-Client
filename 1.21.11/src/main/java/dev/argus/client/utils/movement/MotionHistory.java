package dev.argus.client.utils.movement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.ToDoubleFunction;

public final class MotionHistory {
   private final int capacity;
   private final Deque<MotionSample> samples;

   public MotionSample getLatest() {
      return this.samples.peekLast();
   }

   public MotionHistory(int capacity) {
      this.capacity = capacity;
      this.samples = new ArrayDeque<>(capacity);
   }

   public boolean isEmpty() {
      return this.samples.isEmpty();
   }

   public void append(MotionSample sample) {
      if (this.samples.size() >= this.capacity) {
         this.samples.pollFirst();
      }

      this.samples.addLast(sample);
   }

   public double weightedAverage(ToDoubleFunction<? super MotionSample> extractor) {
      if (this.samples.isEmpty()) {
         return 0.0;
      }

      double weightedTotal = 0.0;
      double totalWeight = 0.0;
      int weight = 1;

      for (MotionSample sample : this.samples) {
         weightedTotal += extractor.applyAsDouble(sample) * weight;
         totalWeight += weight++;
      }

      return weightedTotal / totalWeight;
   }

   public double average(ToDoubleFunction<? super MotionSample> extractor) {
      return this.samples.stream().mapToDouble(extractor).average().orElse(0.0);
   }

   public void clear() {
      this.samples.clear();
   }
}

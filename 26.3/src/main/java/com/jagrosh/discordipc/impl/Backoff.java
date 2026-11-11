package com.jagrosh.discordipc.impl;

import java.util.Random;

public class Backoff {
   private final long minAmount;
   private final long maxAmount;
   private final Random randGenerator;
   private long current;
   private int fails;

   public Backoff(long minAmount, long maxAmount) {
      this.minAmount = minAmount;
      this.maxAmount = maxAmount;
      this.current = minAmount;
      this.fails = 0;
      this.randGenerator = new Random();
   }

   public void reset() {
      this.fails = 0;
      this.current = this.minAmount;
   }

   public long nextDelay() {
      this.fails++;
      double increment = this.current * 2.0 * this.rand01();
      this.current = Math.min(this.current + (long)increment, this.maxAmount);
      return this.current;
   }

   private double rand01() {
      return this.randGenerator.nextDouble();
   }
}

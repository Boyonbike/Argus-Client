package dev.argus.client.modules.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SpeedMineTimingTest {
   @Test
   void progressScaleFollowsTpsOnlyWhenSynced() {
      assertEquals(0.5F, SpeedMineTiming.progressScale(true, 0.5F, 1.0F), 1e-6);
      assertEquals(1.0F, SpeedMineTiming.progressScale(false, 0.5F, 1.0F), 1e-6);
   }

   @Test
   void progressScaleCompensatesForTimerSpeed() {
      assertEquals(0.5F, SpeedMineTiming.progressScale(false, 1.0F, 2.0F), 1e-6);
      assertEquals(0.25F, SpeedMineTiming.progressScale(true, 0.5F, 2.0F), 1e-6);
   }

   @Test
   void breakDelaySkippedByNoBreakDelayOrElapsed() {
      assertTrue(SpeedMineTiming.isBreakDelayActive(false, false));
      assertFalse(SpeedMineTiming.isBreakDelayActive(true, false));
      assertFalse(SpeedMineTiming.isBreakDelayActive(false, true));
   }

   @Test
   void instantDelayHasA2b2tFloorOnlyWithAlternative() {
      assertEquals(0.0F, SpeedMineTiming.instantDelaySeconds(0.0F, false, true), 1e-6);
      assertEquals(0.0F, SpeedMineTiming.instantDelaySeconds(0.0F, true, false), 1e-6);
      assertEquals(0.1F, SpeedMineTiming.instantDelaySeconds(0.0F, true, true), 1e-6);
      assertEquals(0.5F, SpeedMineTiming.instantDelaySeconds(0.5F, true, true), 1e-6);
   }

   @Test
   void serverTicksFollowTpsAndClamp() {
      assertEquals(20.0, SpeedMineTiming.serverTicks(1000L, 20.0F), 1e-9);
      assertEquals(10.0, SpeedMineTiming.serverTicks(1000L, 10.0F), 1e-9);
      assertEquals(20.0, SpeedMineTiming.serverTicks(1000L, 30.0F), 1e-9);
      assertEquals(0.0, SpeedMineTiming.serverTicks(-50L, 20.0F), 1e-9);
      assertEquals(1.0, SpeedMineTiming.serverTicks(50L, Float.NaN), 1e-9);
   }

   @Test
   void creditKeepsFractionsAndCapsAtTwo() {
      double credit = 0.0;
      // 10 TPS: one server tick every 100 ms; three 50 ms client ticks earn 1.5 starts.
      for (int i = 0; i < 3; i++) {
         credit = SpeedMineTiming.replenishStartCredit(credit, 50L, 10.0F);
      }
      assertEquals(1.5, credit, 1e-9);
      assertEquals(SpeedMineTiming.MAX_START_CREDIT, SpeedMineTiming.replenishStartCredit(credit, 10_000L, 10.0F), 1e-9);
   }

   @Test
   void startsWaitForCreditOnlyWhenSyncedBelowFullTps() {
      assertFalse(SpeedMineTiming.canStart(true, 10.0F, 0.5));
      assertTrue(SpeedMineTiming.canStart(true, 10.0F, 1.0));
      assertTrue(SpeedMineTiming.canStart(true, 20.0F, 0.0));
      assertTrue(SpeedMineTiming.canStart(false, 5.0F, 0.0));
      assertTrue(SpeedMineTiming.canStart(true, Float.NaN, 0.0));
   }

   @Test
   void onlySyncSpendsCreditAndNeverBelowZero() {
      assertEquals(0.5, SpeedMineTiming.spendStartCredit(true, 1.5), 1e-9);
      assertEquals(0.0, SpeedMineTiming.spendStartCredit(true, 0.4), 1e-9);
      assertEquals(1.5, SpeedMineTiming.spendStartCredit(false, 1.5), 1e-9);
   }

   @Test
   void renderProgressIsClampedFractionOfDamage() {
      assertEquals(0.5F, SpeedMineTiming.renderProgress(0.35F, 0.7F), 1e-6);
      assertEquals(1.0F, SpeedMineTiming.renderProgress(2.0F, 0.7F), 1e-6);
      assertEquals(0.0F, SpeedMineTiming.renderProgress(-1.0F, 0.7F), 1e-6);
   }
}

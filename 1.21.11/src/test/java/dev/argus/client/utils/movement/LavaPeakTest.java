package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LavaPeakTest {
   @Test
   void stillRisingIsNotThePeak() {
      assertFalse(LiquidTakeoff.atPeak(10.0, 10.2, 0.05));
   }

   @Test
   void roseLastTickAndWouldStopRisingIsThePeak() {
      assertTrue(LiquidTakeoff.atPeak(10.0, 10.2, 0.0));
      assertTrue(LiquidTakeoff.atPeak(10.0, 10.2, -0.08));
   }

   @Test
   void alreadyFallingIsNotThePeak() {
      assertFalse(LiquidTakeoff.atPeak(10.2, 10.0, -0.1));
   }

   @Test
   void noHistoryIsNotThePeak() {
      assertFalse(LiquidTakeoff.atPeak(Double.NaN, 10.2, -0.1));
   }

   @Test
   void bobbingInPlaceIsNotThePeak() {
      assertFalse(LiquidTakeoff.atPeak(10.0, 10.0, -0.01));
   }
}

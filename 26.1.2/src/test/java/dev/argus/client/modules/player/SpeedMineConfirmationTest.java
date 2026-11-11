package dev.argus.client.modules.player;

import dev.argus.client.settings.options.SpeedMineConfirmChoice;
import org.junit.jupiter.api.Test;

import static dev.argus.client.settings.options.SpeedMineConfirmChoice.BYPASS;
import static dev.argus.client.settings.options.SpeedMineConfirmChoice.STRICT;
import static dev.argus.client.settings.options.SpeedMineConfirmChoice.STRONG;
import static org.junit.jupiter.api.Assertions.*;

final class SpeedMineConfirmationTest {
   private static final long POS = 42L;
   private static final long OTHER = 7L;

   /** A chain whose first break the server has confirmed. */
   private static SpeedMineConfirmation primed() {
      SpeedMineConfirmation c = new SpeedMineConfirmation();
      c.onBreakSent(POS, 0L);
      c.onServerAir(POS);
      return c;
   }

   @Test
   void breaksBeforeFirstConfirmationAreRetriesNotRebreaks() {
      SpeedMineConfirmation c = new SpeedMineConfirmation();
      c.onBreakSent(POS, 0L);
      c.onBreakSent(POS, 50L);
      assertFalse(c.isRebreak(POS));
      assertTrue(c.canRebreak(STRICT, 50L));
   }

   @Test
   void airOnAnotherBlockDoesNotConfirm() {
      SpeedMineConfirmation c = new SpeedMineConfirmation();
      c.onBreakSent(POS, 0L);
      c.onServerAir(OTHER);
      assertFalse(c.isRebreak(POS));
   }

   @Test
   void strictWaitsForEachRebreakToBeConfirmed() {
      SpeedMineConfirmation c = primed();
      assertTrue(c.isRebreak(POS));
      assertTrue(c.canRebreak(STRICT, 100L));
      c.onBreakSent(POS, 100L);
      assertFalse(c.canRebreak(STRICT, 150L));
      c.onServerAir(POS);
      assertTrue(c.canRebreak(STRICT, 200L));
   }

   @Test
   void unansweredRebreakTimesOut() {
      SpeedMineConfirmation c = primed();
      c.onBreakSent(POS, 100L);
      assertFalse(c.canRebreak(STRICT, 100L + SpeedMineConfirmation.PENDING_TIMEOUT_MILLIS - 1));
      assertTrue(c.canRebreak(STRICT, 100L + SpeedMineConfirmation.PENDING_TIMEOUT_MILLIS));
   }

   @Test
   void strongNeverWaits() {
      SpeedMineConfirmation c = primed();
      c.onBreakSent(POS, 100L);
      assertTrue(c.isStrong(STRONG));
      assertTrue(c.canRebreak(STRONG, 101L));
   }

   @Test
   void bypassIsStrictUntilARebreakIsConfirmedThenStrong() {
      SpeedMineConfirmation c = primed();
      assertFalse(c.isStrong(BYPASS));
      c.onBreakSent(POS, 100L);
      assertFalse(c.canRebreak(BYPASS, 150L));
      c.onServerAir(POS);
      assertTrue(c.isStrong(BYPASS));
      c.onBreakSent(POS, 200L);
      assertTrue(c.canRebreak(BYPASS, 201L));
   }

   @Test
   void firstBreakConfirmationAloneDoesNotUnlockBypass() {
      assertFalse(primed().isStrong(BYPASS));
   }

   @Test
   void newBlockOrClearStartsOver() {
      SpeedMineConfirmation c = primed();
      c.onBreakSent(POS, 100L);
      c.onServerAir(POS);
      assertTrue(c.isStrong(BYPASS));
      c.onBreakSent(OTHER, 200L);
      assertFalse(c.isStrong(BYPASS));
      assertFalse(c.isRebreak(POS));
      SpeedMineConfirmation cleared = primed();
      cleared.clear();
      assertFalse(cleared.isRebreak(POS));
   }

   @Test
   void choiceNamesMatchComet() {
      assertEquals("Strict", SpeedMineConfirmChoice.STRICT.getName());
      assertEquals("Strong", SpeedMineConfirmChoice.STRONG.getName());
      assertEquals("Bypass", SpeedMineConfirmChoice.BYPASS.getName());
   }
}

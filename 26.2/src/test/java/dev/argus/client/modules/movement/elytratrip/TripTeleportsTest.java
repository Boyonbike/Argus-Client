package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.movement.elytratrip.TripTeleports.Outcome;
import org.junit.jupiter.api.Test;

class TripTeleportsTest {
   @Test
   void nothingSentIsIdle() {
      assertEquals(Outcome.IDLE, new TripTeleports(3).poll(0));
   }

   @Test
   void sentIsPending() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      assertEquals(Outcome.PENDING, teleports.poll(10));
   }

   @Test
   void successLineFinishesTeleport() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("Teleporting to: ely", 500);
      assertEquals(Outcome.DONE, teleports.poll(600));
   }

   @Test
   void warmupThenTimeoutCountsAsDone() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("Teleporting in 5 seconds", 100);
      assertEquals(Outcome.WARMUP, teleports.poll(200));
      assertTrue(teleports.warmupLeft(200) > 0);
      assertEquals(Outcome.DONE, teleports.poll(5_100));
   }

   @Test
   void transferAtWarmupEndIsDone() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("Teleporting in 5 seconds", 0);
      teleports.transferred(4_000);
      assertEquals(Outcome.DONE, teleports.poll(4_000));
   }

   @Test
   void longJumpIsDone() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.longJump(800);
      assertEquals(Outcome.DONE, teleports.poll(900));
   }

   @Test
   void invalidHomeFails() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("Invalid home ely", 200);
      assertEquals(Outcome.FAILED, teleports.poll(300));
   }

   @Test
   void cooldownReplyReportsWait() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("You must wait 2 minutes before using /home again", 0);
      assertEquals(Outcome.COOLDOWN, teleports.poll(0));
      assertEquals(120_000, teleports.cooldownLeft(0));
   }

   @Test
   void silentServerFailsAfterTimeoutAndCountsRetries() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      assertEquals(Outcome.FAILED, teleports.poll(120_000));
      teleports.send("ely", 120_000);
      assertEquals(Outcome.FAILED, teleports.poll(240_000));
      teleports.send("ely", 240_000);
      assertEquals(Outcome.GAVE_UP, teleports.poll(360_000));
   }

   @Test
   void successResetsFailures() {
      TripTeleports teleports = new TripTeleports(2);
      teleports.send("ely", 0);
      assertEquals(Outcome.FAILED, teleports.poll(120_000));
      teleports.send("ely", 120_000);
      teleports.message("Teleporting to: ely", 120_100);
      assertEquals(Outcome.DONE, teleports.poll(120_200));
      teleports.send("checkpoint", 200_000);
      assertEquals(Outcome.FAILED, teleports.poll(320_000));
   }

   @Test
   void refillCanWaitUsesCooldownPlusMargin() {
      TripTeleports teleports = new TripTeleports(3);
      teleports.send("ely", 0);
      teleports.message("You must wait 100 seconds before using /home again", 0);
      assertTrue(teleports.refillCanWait(161, 0));
      assertFalse(teleports.refillCanWait(160, 0));
   }

   @Test
   void noCooldownMeansNoWait() {
      assertFalse(new TripTeleports(3).refillCanWait(1000, 0) && new TripTeleports(3).cooldownLeft(0) > 0);
      assertEquals(0, new TripTeleports(3).cooldownLeft(0));
   }
}

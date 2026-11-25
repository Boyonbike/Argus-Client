package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.argus.client.modules.movement.elytratrip.TripEmbeds.TripReport;
import dev.argus.client.modules.movement.elytratrip.TripPhases.EndReason;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.Supply;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripEmbedsTest {
   private static final TripReport REPORT = new TripReport("6b6t.org", "minecraft:the_nether", 1234, -5678, 9000, -12000, 15000.4, 39.5,
      5, 3, 14, 60, 18.0F, 125_000, 1, 2, 0);
   private static final String USER = "876543210987654321";

   private static List<JsonObject> all(boolean hide, String ping) {
      return List.of(
         TripEmbeds.started(REPORT, hide),
         TripEmbeds.progress(REPORT, hide),
         TripEmbeds.refillStarted(REPORT, EnumSet.of(Supply.TOTEMS), hide),
         TripEmbeds.refillDone(REPORT, "Took 3 totems.", 42_000, hide),
         TripEmbeds.refillWarning(REPORT, "No Food chest tagged.", hide),
         TripEmbeds.died(REPORT, "blew up", hide, ping),
         TripEmbeds.disconnected(REPORT, hide),
         TripEmbeds.reconnected(REPORT, hide),
         TripEmbeds.parked(REPORT, "The refill failed.", hide, ping),
         TripEmbeds.ended(REPORT, EndReason.ARRIVED, EnumSet.noneOf(Supply.class), hide, ping),
         TripEmbeds.ended(REPORT, EndReason.STATION_SHORT, EnumSet.of(Supply.TOTEMS), hide, ping));
   }

   @Test
   void hideCoordsRemovesEveryCoordinate() {
      for (JsonObject body : all(true, USER)) {
         String json = body.toString();
         for (String coordinate : List.of("1234", "-5678", "5678", "9000", "-12000", "12000", "1,234", "5,678", "9,000", "12,000")) {
            assertFalse(json.contains(coordinate), coordinate + " leaked in " + json);
         }
         assertTrue(json.contains("Nether"), json);
      }
      assertTrue(TripEmbeds.progress(REPORT, true).toString().contains("15,000"));
   }

   @Test
   void shownCoordsAppearWhenNotHidden() {
      String json = TripEmbeds.progress(REPORT, false).toString();
      assertTrue(json.contains("1234") && json.contains("-5678"), json);
   }

   @Test
   void parkedAndEndedPing() {
      assertEquals("<@" + USER + ">", TripEmbeds.parked(REPORT, "x", false, USER).get("content").getAsString());
      assertEquals("<@" + USER + ">", TripEmbeds.ended(REPORT, EndReason.ARRIVED, EnumSet.noneOf(Supply.class), false, USER).get("content").getAsString());
   }

   @Test
   void progressNeverPings() {
      assertFalse(TripEmbeds.progress(REPORT, false).has("content"));
   }

   @Test
   void rolePingUsesAmpersand() {
      assertEquals("<@&" + USER + ">", TripEmbeds.parked(REPORT, "x", false, "&" + USER).get("content").getAsString());
   }

   @Test
   void invalidPingIsIgnored() {
      assertFalse(TripEmbeds.parked(REPORT, "x", false, "@everyone").has("content"));
   }

   @Test
   void mentionsAreLimitedToThePing() {
      JsonObject body = TripEmbeds.parked(REPORT, "x", false, USER);
      assertEquals(0, body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
   }

   @Test
   void shortStationListsSupplies() {
      String json = TripEmbeds.ended(REPORT, EndReason.STATION_SHORT, EnumSet.of(Supply.TOTEMS, Supply.GAPPLES), false, "").toString();
      assertTrue(json.contains("totems") && json.contains("gapples"), json);
   }

   @Test
   void colours() {
      assertEquals(0xEF4444, colour(TripEmbeds.parked(REPORT, "x", false, "")));
      assertEquals(0x22C55E, colour(TripEmbeds.ended(REPORT, EndReason.ARRIVED, EnumSet.noneOf(Supply.class), false, "")));
      assertEquals(0x6B7280, colour(TripEmbeds.progress(REPORT, false)));
      assertEquals(0x3B82F6, colour(TripEmbeds.started(REPORT, false)));
   }

   private static int colour(JsonObject body) {
      return body.getAsJsonArray("embeds").get(0).getAsJsonObject().get("color").getAsInt();
   }

   @Test
   void webhookValidation() {
      assertTrue(TripEmbeds.validWebhook("https://discord.com/api/webhooks/1/x"));
      assertTrue(TripEmbeds.validWebhook("https://discordapp.com/api/webhooks/1/x"));
      assertFalse(TripEmbeds.validWebhook("https://evil.com/api/webhooks/1/x"));
      assertFalse(TripEmbeds.validWebhook("http://discord.com/api/webhooks/1/x"));
      assertFalse(TripEmbeds.validWebhook("https://discord.com.evil.com/api/webhooks/1/x"));
      assertFalse(TripEmbeds.validWebhook(""));
   }
}

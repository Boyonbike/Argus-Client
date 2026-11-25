package dev.argus.client.modules.movement.elytratrip;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.modules.movement.elytratrip.TripPhases.EndReason;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.Supply;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds ElytraTrip's Discord webhook messages. Pure: every coordinate goes through one place so hiding is total. */
public final class TripEmbeds {
   public static final int BLUE = 0x3B82F6;
   public static final int GREY = 0x6B7280;
   public static final int YELLOW = 0xEAB308;
   public static final int GREEN = 0x22C55E;
   public static final int ORANGE = 0xF97316;
   public static final int RED = 0xEF4444;

   /** A snapshot of the trip for one message. */
   public record TripReport(String server, String dimension, int x, int z, int destX, int destZ, double blocksLeft, double avgSpeed,
                            int elytras, int totems, int gapples, int food, float health, long checkpointAgeMs, int deaths, int refills,
                            int disconnects) {
   }

   private TripEmbeds() {
   }

   public static boolean validWebhook(String url) {
      if (url == null) return false;
      try {
         URI uri = URI.create(url.strip());
         String host = uri.getHost();
         return "https".equals(uri.getScheme()) && host != null
            && (host.equals("discord.com") || host.equals("discordapp.com") || host.equals("ptb.discord.com") || host.equals("canary.discord.com"))
            && uri.getPath() != null && uri.getPath().startsWith("/api/webhooks/");
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   public static JsonObject started(TripReport report, boolean hide) {
      JsonObject embed = embed("Trip started", BLUE, "Flying to the destination, " + blocks(report.blocksLeft()) + " blocks away.", report);
      if (!hide) field(embed, "Destination", report.destX() + ", " + report.destZ(), true);
      position(embed, report, hide);
      return message(embed, null);
   }

   public static JsonObject progress(TripReport report, boolean hide) {
      JsonObject embed = embed("Trip progress", GREY, null, report);
      field(embed, "Blocks left", blocks(report.blocksLeft()), true);
      field(embed, "Average speed", String.format(Locale.ROOT, "%.1f b/s", report.avgSpeed()), true);
      field(embed, "Time left", report.avgSpeed() > 0.5 ? ChatTeleportState.remaining((long)(report.blocksLeft() / report.avgSpeed() * 1000)) : "Unknown", true);
      supplies(embed, report);
      field(embed, "Health", String.format(Locale.ROOT, "%.0f", report.health()), true);
      field(embed, "Checkpoint", report.checkpointAgeMs() < 0 ? "Not set yet" : ChatTeleportState.remaining(report.checkpointAgeMs()) + " ago", true);
      field(embed, "So far", report.deaths() + " deaths, " + report.refills() + " refills, " + report.disconnects() + " disconnects", false);
      position(embed, report, hide);
      return message(embed, null);
   }

   public static JsonObject refillStarted(TripReport report, Set<Supply> reason, boolean hide) {
      String why = reason.isEmpty() ? "after a death" : "low on " + list(reason);
      JsonObject embed = embed("Refill started", YELLOW, "Going to the station, " + why + ".", report);
      position(embed, report, hide);
      return message(embed, null);
   }

   public static JsonObject refillDone(TripReport report, String detail, long tookMs, boolean hide) {
      JsonObject embed = embed("Refill done", GREEN, detail + " It took " + ChatTeleportState.remaining(tookMs) + ".", report);
      supplies(embed, report);
      return message(embed, null);
   }

   public static JsonObject refillWarning(TripReport report, String text, boolean hide) {
      return message(embed("Station warning", ORANGE, text, report), null);
   }

   public static JsonObject died(TripReport report, String cause, boolean hide, String ping) {
      JsonObject embed = embed("Died", RED, cause == null || cause.isBlank() ? "The player died. Refilling, then back to the checkpoint." : cause, report);
      position(embed, report, hide);
      return message(embed, ping);
   }

   public static JsonObject disconnected(TripReport report, boolean hide) {
      return message(embed("Disconnected", GREY, "Reconnecting.", report), null);
   }

   public static JsonObject reconnected(TripReport report, boolean hide) {
      return message(embed("Reconnected", GREY, "Back on the server; carrying on.", report), null);
   }

   public static JsonObject parked(TripReport report, String reason, boolean hide, String ping) {
      JsonObject embed = embed("Parked: needs you", RED, reason + " The trip is waiting; fix it, then press Resume.", report);
      position(embed, report, hide);
      return message(embed, ping);
   }

   public static JsonObject ended(TripReport report, EndReason reason, Set<Supply> shortSupplies, boolean hide, String ping) {
      JsonObject embed = switch (reason) {
         case ARRIVED -> embed("Arrived", GREEN, "Reached the destination and disconnected.", report);
         case STATION_SHORT -> embed("Station is short", RED, "The station couldn't restock " + list(shortSupplies)
            + ". Stopped at the station and disconnected; restock it, then start the trip again.", report);
         case TELEPORT_FAILED -> embed("Teleport failed", RED, "/home kept failing. Stopped and disconnected.", report);
         case STOPPED, NONE -> embed("Trip stopped", GREY, "Stopped from the client.", report);
      };
      supplies(embed, report);
      position(embed, report, hide);
      return message(embed, reason == EndReason.STOPPED || reason == EndReason.NONE ? null : ping);
   }

   private static String list(Set<Supply> supplies) {
      return supplies.stream().map(Supply::label).collect(Collectors.joining(", "));
   }

   private static String blocks(double blocks) {
      return String.format(Locale.ROOT, "%,d", Math.round(Math.max(0, blocks)));
   }

   private static String dimension(String id) {
      return switch (id) {
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_end" -> "End";
         default -> id;
      };
   }

   private static JsonObject embed(String title, int colour, String description, TripReport report) {
      JsonObject embed = new JsonObject();
      embed.addProperty("title", title);
      embed.addProperty("color", colour);
      if (description != null) embed.addProperty("description", description);
      embed.add("fields", new JsonArray());
      JsonObject footer = new JsonObject();
      footer.addProperty("text", "ElytraTrip on " + report.server() + ", " + dimension(report.dimension()));
      embed.add("footer", footer);
      return embed;
   }

   private static void supplies(JsonObject embed, TripReport report) {
      field(embed, "Supplies", report.elytras() + " spare elytras, " + report.totems() + " totems, " + report.gapples() + " gapples, "
         + report.food() + " food", false);
   }

   private static void position(JsonObject embed, TripReport report, boolean hide) {
      if (!hide) field(embed, "Position", report.x() + ", " + report.z(), true);
   }

   private static void field(JsonObject embed, String name, String value, boolean inline) {
      JsonObject field = new JsonObject();
      field.addProperty("name", name);
      field.addProperty("value", value.length() > 1000 ? value.substring(0, 1000) : value);
      field.addProperty("inline", inline);
      embed.getAsJsonArray("fields").add(field);
   }

   /** Wraps an embed; a ping is a user id, or a role id starting with '&', and only that id may be mentioned. */
   private static JsonObject message(JsonObject embed, String ping) {
      JsonObject message = new JsonObject();
      message.addProperty("username", "ElytraTrip");
      JsonObject mentions = new JsonObject();
      String id = ping == null ? "" : ping.strip();
      boolean role = id.startsWith("&");
      if (role) id = id.substring(1);
      if (id.matches("\\d{5,25}")) {
         message.addProperty("content", role ? "<@&" + id + ">" : "<@" + id + ">");
         JsonArray ids = new JsonArray();
         ids.add(id);
         mentions.add(role ? "roles" : "users", ids);
      }
      mentions.add("parse", new JsonArray());
      message.add("allowed_mentions", mentions);
      JsonArray embeds = new JsonArray();
      embeds.add(embed);
      message.add("embeds", embeds);
      return message;
   }
}

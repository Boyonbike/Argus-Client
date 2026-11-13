package dev.argus.client.modules.basehunting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Posts BaseFinder's finds to a Discord channel as rich embeds: colour-coded by kind, with the coordinates (or hidden),
 * dimension, server, distance and direction from you, a Nether/Overworld conversion, a map-ready /highlight command,
 * and an optional ping. Messages queue up and respect Discord's rate limits, and a test button checks the link. Only
 * Discord webhook links are accepted.
 */
final class HuntDiscordAlerts {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
   private final Module module;
   private final Setting<Boolean> enabledSetting;
   private final Setting<String> urlSetting;
   private final Setting<Boolean> basesSetting;
   private final Setting<Boolean> stashesSetting;
   private final Setting<Boolean> coordsSetting;
   private final Setting<Boolean> serverSetting;
   private final Setting<String> pingSetting;
   private final Setting<Boolean> pingRoleSetting;
   private final Setting<String> nameSetting;
   private final Setting<Integer> cooldownSetting;
   private final Setting<Boolean> testSetting;
   private final Deque<JsonObject> queue = new ArrayDeque<>();
   private boolean sending;
   private long lastSentMillis;

   HuntDiscordAlerts(Module module) {
      this.module = module;
      this.enabledSetting = module.add(new BooleanSetting("Discord", false).markAsGroup());
      this.urlSetting = module.add(new StringSetting("DiscordLink", "").dependsOn(this.enabledSetting));
      this.basesSetting = module.add(new BooleanSetting("PostBases", true).dependsOn(this.enabledSetting));
      this.stashesSetting = module.add(new BooleanSetting("PostStashes", true).dependsOn(this.enabledSetting));
      this.coordsSetting = module.add(new BooleanSetting("PostCoords", true).dependsOn(this.enabledSetting));
      this.serverSetting = module.add(new BooleanSetting("PostServer", true).dependsOn(this.enabledSetting));
      this.pingSetting = module.add(new StringSetting("PingId", "").dependsOn(this.enabledSetting));
      this.pingRoleSetting = module.add(new BooleanSetting("PingIsRole", false).dependsOn(this.enabledSetting));
      this.nameSetting = module.add(new StringSetting("PostAs", "Argus Base Hunting").dependsOn(this.enabledSetting));
      this.cooldownSetting = module.add(new NumberSetting<>("PostCooldown", 2, 0, 120).withUnit("s").dependsOn(this.enabledSetting));
      this.testSetting = module.add(new BooleanSetting("SendTest", false).dependsOn(this.enabledSetting));
      this.testSetting.setChangeCallback(() -> {
         if (this.testSetting.getValue()) {
            this.testSetting.setValue(false);
            this.sendTest();
         }
      });
   }

   /** Queues a find for posting; {@code stash} picks the stash or base switch and colour. Safe from any thread. */
   void post(boolean stash, String what, BlockPos position) {
      if (!this.enabledSetting.getValue() || !(stash ? this.stashesSetting.getValue() : this.basesSetting.getValue()) || !this.validUrl()) {
         return;
      }

      ClientAccess.argusclientMinecraft.execute(() -> this.enqueue(this.embedFor(stash, what, position)));
   }

   private boolean validUrl() {
      String url = this.urlSetting.getValue().trim();
      return url.startsWith("https://discord.com/api/webhooks/")
         || url.startsWith("https://discordapp.com/api/webhooks/")
         || url.startsWith("https://ptb.discord.com/api/webhooks/")
         || url.startsWith("https://canary.discord.com/api/webhooks/");
   }

   private void sendTest() {
      if (!this.validUrl()) {
         this.say("Paste a Discord webhook link (https://discord.com/api/webhooks/...) into DiscordLink first.");
         return;
      }

      JsonObject embed = new JsonObject();
      embed.addProperty("title", "Base hunting alerts are connected");
      embed.addProperty("description", "Finds from " + this.module.getName() + " will show up here.");
      embed.addProperty("color", 0x5865F2);
      embed.addProperty("timestamp", Instant.now().toString());
      this.enqueue(this.message(embed, false));
      this.say("Sent a test message.");
   }

   private JsonObject embedFor(boolean stash, String what, BlockPos position) {
      JsonObject embed = new JsonObject();
      embed.addProperty("title", stash ? "📦 Stash found" : "🏠 Possible base found");
      embed.addProperty("description", capitalise(what));
      embed.addProperty("color", stash ? 0xF1C40F : 0xE67E22);
      embed.addProperty("timestamp", Instant.now().toString());
      JsonArray fields = new JsonArray();
      DimensionType dimension = WorldUtils.getDimension();
      if (this.coordsSetting.getValue()) {
         fields.add(field("Coordinates", "`" + position.getX() + " " + position.getY() + " " + position.getZ() + "`", true));
         if (dimension == DimensionType.OVERWORLD) {
            fields.add(field("Nether", "`" + Math.floorDiv(position.getX(), 8) + " " + Math.floorDiv(position.getZ(), 8) + "`", true));
         } else if (dimension == DimensionType.THE_NETHER) {
            fields.add(field("Overworld", "`" + position.getX() * 8 + " " + position.getZ() * 8 + "`", true));
         }
      } else {
         fields.add(field("Coordinates", "Hidden", true));
      }

      fields.add(field("Dimension", dimension.getDisplayName(), true));
      if (this.serverSetting.getValue()) {
         fields.add(field("Server", HuntCompat.serverKey(), true));
      }

      if (ClientAccess.argusclientMinecraft.player != null) {
         Vec3 player = ClientAccess.argusclientMinecraft.player.position();
         double dx = position.getX() + 0.5 - player.x;
         double dz = position.getZ() + 0.5 - player.z;
         fields.add(field("From you", Math.round(Math.sqrt(dx * dx + dz * dz)) + "m " + HuntMarkers.compass(dx, dz), true));
         if (this.coordsSetting.getValue()) {
            fields.add(field("Spotted from", "`" + Math.round(player.x) + " " + Math.round(player.y) + " " + Math.round(player.z) + "`", true));
         }
      }

      embed.add("fields", fields);
      JsonObject footer = new JsonObject();
      footer.addProperty("text", "Argus Client • " + this.module.getName());
      embed.add("footer", footer);
      return this.message(embed, true);
   }

   private JsonObject message(JsonObject embed, boolean ping) {
      JsonObject message = new JsonObject();
      String name = this.nameSetting.getValue().trim();
      if (!name.isEmpty()) {
         message.addProperty("username", name.length() > 80 ? name.substring(0, 80) : name);
      }

      String id = this.pingSetting.getValue().trim();
      JsonObject mentions = new JsonObject();
      JsonArray parse = new JsonArray();
      if (ping && id.matches("\\d{5,25}")) {
         boolean role = this.pingRoleSetting.getValue();
         message.addProperty("content", role ? "<@&" + id + ">" : "<@" + id + ">");
         JsonArray ids = new JsonArray();
         ids.add(id);
         mentions.add(role ? "roles" : "users", ids);
      }

      // Never let a find's text ping @everyone or anyone else.
      mentions.add("parse", parse);
      message.add("allowed_mentions", mentions);
      JsonArray embeds = new JsonArray();
      embeds.add(embed);
      message.add("embeds", embeds);
      return message;
   }

   private static JsonObject field(String name, String value, boolean inline) {
      JsonObject field = new JsonObject();
      field.addProperty("name", name);
      field.addProperty("value", value.length() > 1000 ? value.substring(0, 1000) + "…" : value);
      field.addProperty("inline", inline);
      return field;
   }

   private static String capitalise(String text) {
      return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
   }

   private synchronized void enqueue(JsonObject message) {
      if (this.queue.size() >= 50) {
         // A burst this big is probably a false-positive storm; drop the oldest rather than spam the channel later.
         this.queue.pollFirst();
      }

      this.queue.addLast(message);
      this.pump();
   }

   private synchronized void pump() {
      if (this.sending || this.queue.isEmpty()) {
         return;
      }

      this.sending = true;
      JsonObject message = this.queue.pollFirst();
      String url = this.urlSetting.getValue().trim();
      long wait = Math.max(0L, this.lastSentMillis + this.cooldownSetting.getValue() * 1000L - System.currentTimeMillis());
      ClientAccess.executorService.execute(() -> this.deliver(url, message, wait, 0));
   }

   private void deliver(String url, JsonObject message, long waitMillis, int attempt) {
      long retryAfter = -1L;
      try {
         if (waitMillis > 0L) {
            Thread.sleep(waitMillis);
         }

         HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("User-Agent", "ArgusClient")
            .POST(HttpRequest.BodyPublishers.ofString(message.toString(), StandardCharsets.UTF_8))
            .build();
         HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
         if (response.statusCode() == 429 && attempt < 3) {
            retryAfter = parseRetryAfter(response);
         } else if (response.statusCode() >= 400) {
            ClientAccess.argusclientMinecraft.execute(() -> this.say("Discord refused the message (HTTP " + response.statusCode() + "); check DiscordLink."));
         }
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
      } catch (Exception failure) {
         ClientAccess.logger.warn("Could not post a {} find to Discord", this.module.getName(), failure);
      }

      if (retryAfter >= 0L) {
         this.deliver(url, message, retryAfter, attempt + 1);
         return;
      }

      synchronized (this) {
         this.lastSentMillis = System.currentTimeMillis();
         this.sending = false;
         this.pump();
      }
   }

   private static long parseRetryAfter(HttpResponse<String> response) {
      try {
         JsonObject body = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
         return (long)(body.get("retry_after").getAsDouble() * 1000.0) + 250L;
      } catch (Exception unreadable) {
         return 2000L;
      }
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal(this.module.getName() + ": " + message), ChatUtils.moduleSignature(this.module));
   }
}

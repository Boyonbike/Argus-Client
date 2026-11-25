package dev.argus.client.modules.movement.elytratrip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Sends ElytraTrip's webhook messages one at a time, honouring Discord's rate limits. The pattern is BaseFinder's alerts'. */
public final class TripDiscord {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
   /** Gap between messages so a burst of events never trips Discord's limit. */
   private static final long GAP_MS = 1_000;

   private final Supplier<String> url;
   private final Consumer<String> say;
   private final Deque<JsonObject> queue = new ArrayDeque<>();
   private boolean sending;
   private long lastSentMillis;

   public TripDiscord(Supplier<String> url, Consumer<String> say) {
      this.url = url;
      this.say = say;
   }

   public boolean configured() {
      return TripEmbeds.validWebhook(url.get());
   }

   /** Queues a message; does nothing when no valid webhook is set. */
   public synchronized void post(JsonObject message) {
      if (!configured()) return;
      if (queue.size() >= 50) queue.pollFirst();
      queue.addLast(message);
      pump();
   }

   public void test() {
      if (!configured()) {
         say.accept("Paste a Discord webhook link (https://discord.com/api/webhooks/...) into Webhook first.");
         return;
      }
      JsonObject message = new JsonObject();
      message.addProperty("username", "ElytraTrip");
      message.addProperty("content", "ElytraTrip is connected. Trip updates will appear here.");
      JsonObject mentions = new JsonObject();
      mentions.add("parse", new com.google.gson.JsonArray());
      message.add("allowed_mentions", mentions);
      post(message);
      say.accept("Sent a test message to Discord.");
   }

   private synchronized void pump() {
      if (sending || queue.isEmpty()) return;
      sending = true;
      JsonObject message = queue.pollFirst();
      String target = url.get().strip();
      long wait = Math.max(0L, lastSentMillis + GAP_MS - System.currentTimeMillis());
      ClientAccess.executorService.execute(() -> deliver(target, message, wait, 0));
   }

   private void deliver(String target, JsonObject message, long waitMillis, int attempt) {
      long retryAfter = -1L;
      try {
         if (waitMillis > 0L) Thread.sleep(waitMillis);
         HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("User-Agent", "ArgusClient")
            .POST(HttpRequest.BodyPublishers.ofString(message.toString(), StandardCharsets.UTF_8)).build();
         HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
         if (response.statusCode() == 429 && attempt < 3) {
            retryAfter = retryAfter(response);
         } else if (response.statusCode() >= 400) {
            ClientAccess.argusclientMinecraft.execute(() -> say.accept("Discord refused a trip update (HTTP " + response.statusCode() + "); check Webhook."));
         }
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
      } catch (Exception failure) {
         ClientAccess.logger.warn("Could not post an ElytraTrip update to Discord", failure);
      }
      if (retryAfter >= 0L) {
         deliver(target, message, retryAfter, attempt + 1);
         return;
      }
      synchronized (this) {
         lastSentMillis = System.currentTimeMillis();
         sending = false;
         pump();
      }
   }

   private static long retryAfter(HttpResponse<String> response) {
      try {
         return (long)(JsonParser.parseString(response.body()).getAsJsonObject().get("retry_after").getAsDouble() * 1000.0) + 250L;
      } catch (Exception unreadable) {
         return 2_000L;
      }
   }
}

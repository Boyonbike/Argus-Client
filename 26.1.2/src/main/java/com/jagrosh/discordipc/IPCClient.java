package com.jagrosh.discordipc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jagrosh.discordipc.entities.Callback;
import com.jagrosh.discordipc.entities.DiscordBuild;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.pipe.Pipe;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;
import com.jagrosh.discordipc.impl.Backoff;
import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IPCClient implements Closeable {
   private static final Logger LOGGER = LoggerFactory.getLogger(IPCClient.class);
   private final Backoff RECONNECT_TIME_MS = new Backoff(500L, 60000L);
   private final long clientId;
   private final boolean autoRegister;
   private final HashMap callbacks = new HashMap();
   private final String applicationId;
   private final String optionalSteamId;
   private volatile Pipe pipe;
   private Logger forcedLogger = null;
   private IPCListener listener = null;
   private Thread readThread = null;
   private String encoding = "UTF-8";
   private long nextDelay = 0L;
   private boolean debugMode;
   private boolean verboseLogging;

   public IPCClient(long clientId, boolean debugMode, boolean verboseLogging, boolean autoRegister, String applicationId, String optionalSteamId) {
      this.clientId = clientId;
      this.debugMode = debugMode;
      this.verboseLogging = verboseLogging;
      this.applicationId = applicationId;
      this.autoRegister = autoRegister;
      this.optionalSteamId = optionalSteamId;
   }

   public IPCClient(long clientId, boolean debugMode, boolean verboseLogging, boolean autoRegister, String applicationId) {
      this(clientId, debugMode, verboseLogging, autoRegister, applicationId, null);
   }

   public IPCClient(long clientId, boolean debugMode, boolean verboseLogging) {
      this(clientId, debugMode, verboseLogging, false, null);
   }

   public IPCClient(long clientId, boolean debugMode, boolean autoRegister, String applicationId, String optionalSteamId) {
      this(clientId, debugMode, false, autoRegister, applicationId, optionalSteamId);
   }

   public IPCClient(long clientId, boolean debugMode, boolean autoRegister, String applicationId) {
      this(clientId, debugMode, autoRegister, applicationId, null);
   }

   public IPCClient(long clientId, boolean debugMode) {
      this(clientId, debugMode, false, null);
   }

   public IPCClient(long clientId, boolean autoRegister, String applicationId, String optionalSteamId) {
      this(clientId, false, autoRegister, applicationId, optionalSteamId);
   }

   public IPCClient(long clientId, boolean autoRegister, String applicationId) {
      this(clientId, autoRegister, applicationId, null);
   }

   public IPCClient(long clientId) {
      this(clientId, false, null);
   }

   private static int getPID() {
      String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
      return Integer.parseInt(runtimeName.substring(0, runtimeName.indexOf(64)));
   }

   public Logger getCurrentLogger(Logger logger) {
      return this.forcedLogger != null ? this.forcedLogger : logger;
   }

   public void setForcedLogger(Logger logger) {
      this.forcedLogger = logger;
   }

   public void setListener(IPCListener listener) {
      this.listener = listener;
      if (this.pipe != null) {
         this.pipe.setListener(listener);
      }
   }

   public String getApplicationId() {
      return this.applicationId;
   }

   public String getOptionalSteamId() {
      return this.optionalSteamId;
   }

   public boolean isAutoRegister() {
      return this.autoRegister;
   }

   public String getEncoding() {
      return this.encoding;
   }

   public void setEncoding(String encoding) {
      this.encoding = encoding;
   }

   public long getClientID() {
      return this.clientId;
   }

   public boolean isDebugMode() {
      return this.debugMode;
   }

   public void setDebugMode(boolean debugMode) {
      this.debugMode = debugMode;
   }

   public boolean isVerboseLogging() {
      return this.verboseLogging;
   }

   public void setVerboseLogging(boolean verboseLogging) {
      this.verboseLogging = verboseLogging;
   }

   public void connect(DiscordBuild... preferredBuilds) throws NoDiscordClientException {
      this.checkConnected(false);

      long delay;
      while ((delay = this.nextDelay - System.currentTimeMillis()) > 0L) {
         if (this.debugMode) {
            this.getCurrentLogger(LOGGER).info("[DEBUG] Attempting connection in: " + delay + "ms");
         }

         try {
            Thread.sleep(delay);
         } catch (InterruptedException var6) {
         }
      }

      this.callbacks.clear();
      this.pipe = null;

      try {
         this.pipe = Pipe.openPipe(this, this.clientId, this.callbacks, preferredBuilds);
      } catch (Exception failure) {
         this.updateReconnectTime();
         throw failure;
      }

      if (this.isAutoRegister()) {
         try {
            if (this.optionalSteamId != null && !this.optionalSteamId.isEmpty()) {
               this.registerSteamGame(this.getApplicationId(), this.optionalSteamId);
            } else {
               this.registerApp(this.getApplicationId(), null);
            }
         } catch (Throwable failure) {
            if (this.debugMode) {
               this.getCurrentLogger(LOGGER).error("Unable to register application", failure);
            } else {
               this.getCurrentLogger(LOGGER).error("Unable to register application, enable debug mode for trace...");
            }
         }
      }

      if (this.debugMode) {
         this.getCurrentLogger(LOGGER).info("[DEBUG] Client is now connected and ready!");
      }

      if (this.listener != null) {
         this.listener.onReady(this);
         this.pipe.setListener(this.listener);
      }

      this.startReading();
   }

   public void sendRichPresence(RichPresence presence) {
      this.sendRichPresence(presence, null);
   }

   public void sendRichPresence(RichPresence presence, Callback callback) {
      this.checkConnected(true);
      if (this.debugMode) {
         this.getCurrentLogger(LOGGER).info("[DEBUG] Sending RichPresence to discord: " + (presence == null ? null : presence.toDecodedJson(this.encoding)));
      }

      JsonObject request = new JsonObject();
      JsonObject arguments = new JsonObject();
      request.addProperty("cmd", "SET_ACTIVITY");
      arguments.addProperty("pid", getPID());
      arguments.add("activity", presence == null ? null : presence.toJson());
      request.add("args", arguments);
      this.pipe.send(Packet.OpCode.FRAME, request, callback);
   }

   public void registerSteamGame(String applicationId, String steamId) {
      if (this.pipe != null) {
         this.pipe.registerSteamGame(applicationId, steamId);
      }
   }

   public void registerApp(String applicationId, String command) {
      if (this.pipe != null) {
         this.pipe.registerApp(applicationId, command);
      }
   }

   public void subscribe(IPCClient.Event event) {
      this.subscribe(event, null);
   }

   public void subscribe(IPCClient.Event event, Callback callback) {
      this.checkConnected(true);
      if (!event.isSubscribable()) {
         throw new IllegalStateException("Cannot subscribe to " + event + " event!");
      }

      if (this.debugMode) {
         this.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Subscribing to Event: %s", event.name()));
      }

      JsonObject request = new JsonObject();
      request.addProperty("cmd", "SUBSCRIBE");
      request.addProperty("evt", event.name());
      this.pipe.send(Packet.OpCode.FRAME, request, callback);
   }

   public void respondToJoinRequest(User user, IPCClient.ApprovalMode approval, Callback callback) {
      this.checkConnected(true);
      if (user != null) {
         if (this.debugMode) {
            this.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Sending response to %s as %s", user.getName(), approval.name()));
         }

         JsonObject request = new JsonObject();
         request.addProperty("cmd", approval == IPCClient.ApprovalMode.ACCEPT ? "SEND_ACTIVITY_JOIN_INVITE" : "CLOSE_ACTIVITY_JOIN_REQUEST");
         JsonObject arguments = new JsonObject();
         arguments.addProperty("user_id", user.getId());
         request.add("args", arguments);
         this.pipe.send(Packet.OpCode.FRAME, request, callback);
      }
   }

   public void respondToJoinRequest(User user, IPCClient.ApprovalMode approval) {
      this.respondToJoinRequest(user, approval, null);
   }

   public PipeStatus getStatus() {
      return this.pipe == null ? PipeStatus.UNINITIALIZED : this.pipe.getStatus();
   }

   @Override
   public void close() {
      this.checkConnected(true);

      try {
         this.pipe.close();
      } catch (IOException failure) {
         if (this.debugMode) {
            this.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Failed to close pipe: %s", failure));
         }
      }
   }

   public DiscordBuild getDiscordBuild() {
      return this.pipe == null ? null : this.pipe.getDiscordBuild();
   }

   public User getCurrentUser() {
      return this.pipe == null ? null : this.pipe.getCurrentUser();
   }

   private void checkConnected(boolean connected) {
      if (connected && this.getStatus() != PipeStatus.CONNECTED) {
         throw new IllegalStateException(String.format("IPCClient (ID: %d) is not connected!", this.clientId));
      }

      if (!connected && this.getStatus() == PipeStatus.CONNECTED) {
         throw new IllegalStateException(String.format("IPCClient (ID: %d) is already connected!", this.clientId));
      }
   }

   private void startReading() {
      IPCClient client = this;
      this.readThread = new Thread(() -> this.readPipe(client), "IPCClient-Reader");
      this.readThread.setDaemon(true);
      if (this.debugMode) {
         this.getCurrentLogger(LOGGER).info("[DEBUG] Starting IPCClient reading thread!");
      }

      this.readThread.start();
   }

   private void readPipe(IPCClient client) {
      try {
         while (true) {
            Packet packet;
            if ((packet = this.pipe.read()).getOp() != Packet.OpCode.CLOSE) {
               JsonObject json = packet.getJson();
               if (json != null) {
                  IPCClient.Event event = IPCClient.Event.of(
                     json.has("evt") && !json.get("evt").isJsonNull() ? json.getAsJsonPrimitive("evt").getAsString() : null
                  );
                  String nonce = json.has("nonce") && !json.get("nonce").isJsonNull() ? json.getAsJsonPrimitive("nonce").getAsString() : null;
                  switch (event) {
                     case NULL:
                        if (nonce != null && this.callbacks.containsKey(nonce)) {
                           ((Callback)this.callbacks.remove(nonce)).succeed(packet);
                        }
                     case READY:
                     default:
                        break;
                     case ERROR:
                        if (nonce != null && this.callbacks.containsKey(nonce)) {
                           ((Callback)this.callbacks.remove(nonce))
                              .fail(
                                 json.has("data") && json.getAsJsonObject("data").has("message")
                                    ? json.getAsJsonObject("data").getAsJsonObject("message").getAsString()
                                    : null
                              );
                        }
                        break;
                     case ACTIVITY_JOIN:
                        if (this.debugMode) {
                           this.getCurrentLogger(LOGGER).info("[DEBUG] Reading thread received a 'join' event.");
                        }
                        break;
                     case ACTIVITY_SPECTATE:
                        if (this.debugMode) {
                           this.getCurrentLogger(LOGGER).info("[DEBUG] Reading thread received a 'spectate' event.");
                        }
                        break;
                     case ACTIVITY_JOIN_REQUEST:
                        if (this.debugMode) {
                           this.getCurrentLogger(LOGGER).info("[DEBUG] Reading thread received a 'join request' event.");
                        }
                        break;
                     case UNKNOWN:
                        if (this.debugMode) {
                           this.getCurrentLogger(LOGGER)
                              .info("[DEBUG] Reading thread encountered an event with an unknown type: " + json.getAsJsonPrimitive("evt").getAsString());
                        }
                  }

                  if (this.listener != null && json.has("cmd") && json.getAsJsonPrimitive("cmd").getAsString().equals("DISPATCH")) {
                     try {
                        JsonObject data = json.getAsJsonObject("data");
                        switch (IPCClient.Event.of(json.getAsJsonPrimitive("evt").getAsString())) {
                           case ACTIVITY_JOIN:
                              this.listener.onActivityJoin(client, data.getAsJsonPrimitive("secret").getAsString());
                              break;
                           case ACTIVITY_SPECTATE:
                              this.listener.onActivitySpectate(client, data.getAsJsonPrimitive("secret").getAsString());
                              break;
                           case ACTIVITY_JOIN_REQUEST:
                              JsonObject userData = data.getAsJsonObject("user");
                              User user = new User(
                                 userData.getAsJsonPrimitive("username").getAsString(),
                                 userData.has("global_name") && userData.get("global_name").isJsonPrimitive()
                                    ? userData.getAsJsonPrimitive("global_name").getAsString()
                                    : null,
                                 userData.has("discriminator") && userData.get("discriminator").isJsonPrimitive()
                                    ? userData.getAsJsonPrimitive("discriminator").getAsString()
                                    : "0",
                                 Long.parseLong(userData.getAsJsonPrimitive("id").getAsString()),
                                 userData.has("avatar") && userData.get("avatar").isJsonPrimitive()
                                    ? userData.getAsJsonPrimitive("avatar").getAsString()
                                    : null
                              );
                              this.listener.onActivityJoinRequest(client, data.has("secret") ? data.getAsJsonObject("secret").getAsString() : null, user);
                        }
                     } catch (Exception failure) {
                        this.getCurrentLogger(LOGGER).error(String.format("Exception when handling event: %s", failure));
                     }
                  }
               }
            } else {
               this.pipe.setStatus(PipeStatus.DISCONNECTED);
               if (this.listener != null) {
                  this.listener.onClose(client, packet.getJson());
               }
               break;
            }
         }
      } catch (IOException | JsonParseException failure) {
         this.getCurrentLogger(LOGGER).error(String.format("Reading thread encountered an Exception: %s", failure));
         this.pipe.setStatus(PipeStatus.DISCONNECTED);
         if (this.listener != null) {
            this.RECONNECT_TIME_MS.reset();
            this.updateReconnectTime();
            this.listener.onDisconnect(client, failure);
         }
      }
   }

   private void updateReconnectTime() {
      this.nextDelay = System.currentTimeMillis() + this.RECONNECT_TIME_MS.nextDelay();
   }

   public enum ApprovalMode {
      ACCEPT,
      DENY;
   }

   public enum Event {
      NULL(false),
      READY(false),
      ERROR(false),
      ACTIVITY_JOIN(true),
      ACTIVITY_SPECTATE(true),
      ACTIVITY_JOIN_REQUEST(true),
      UNKNOWN(false);

      private final boolean subscribable;

      Event(boolean subscribable) {
         this.subscribable = subscribable;
      }

      static IPCClient.Event of(String name) {
         if (name == null) {
            return NULL;
         }

         for (IPCClient.Event event : values()) {
            if (event != UNKNOWN && event.name().equalsIgnoreCase(name)) {
               return event;
            }
         }

         return UNKNOWN;
      }

      public boolean isSubscribable() {
         return this.subscribable;
      }
   }
}

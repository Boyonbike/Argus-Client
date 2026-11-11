package com.jagrosh.discordipc.entities.pipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.Callback;
import com.jagrosh.discordipc.entities.DiscordBuild;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Pipe {
   private static final Logger LOGGER = LoggerFactory.getLogger(Pipe.class);
   private static final int VERSION = 1;
   private static final long HANDSHAKE_TIMEOUT_MS = 2_000;
   private static final String[] unixPaths = new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
   private static final String[] unixFolderPaths = new String[]{"/snap.discord", "/app/com.discordapp.Discord"};
   final IPCClient ipcClient;
   private final HashMap callbacks;
   volatile PipeStatus status = PipeStatus.CONNECTING;
   IPCListener listener;
   private DiscordBuild build;
   private User currentUser;

   Pipe(IPCClient client, HashMap callbacks) {
      this.ipcClient = client;
      this.callbacks = callbacks;
   }

   public static Pipe openPipe(IPCClient client, long clientId, HashMap callbacks, DiscordBuild... preferredBuilds) throws NoDiscordClientException {
      if (preferredBuilds == null || preferredBuilds.length == 0) {
         preferredBuilds = new DiscordBuild[]{DiscordBuild.ANY};
      }

      Pipe pipe = null;
      Pipe[] candidates = new Pipe[DiscordBuild.values().length];
      var openedCandidates = new ArrayList<Pipe>();

      for (int index = 0; index < 10; index++) {
         String location = getPipeLocation(index);
         if (client.isDebugMode()) {
            client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Searching for IPC Pipe: \"%s\"", location));
         }

         try {
            File file = new File(location.replace("\\", "\\\\"));
            if (file.exists()) {
               if (client.isDebugMode()) {
                  client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Found valid file, attempting connection to IPC: \"%s\"", location));
               }

               pipe = createPipe(client, callbacks, file);
               JsonObject handshake = new JsonObject();
               handshake.addProperty("v", 1);
               handshake.addProperty("client_id", Long.toString(clientId));
               pipe.send(Packet.OpCode.HANDSHAKE, handshake);
               if (pipe.status == PipeStatus.DISCONNECTED) throw new IOException("Unable to send Discord IPC handshake");
               Packet response = pipe.readHandshake(HANDSHAKE_TIMEOUT_MS);
               JsonObject json = response.getJson();
               JsonObject data = json.getAsJsonObject("data");
               JsonObject userData = data.getAsJsonObject("user");
               pipe.build = DiscordBuild.from(data.getAsJsonObject("config").get("api_endpoint").getAsString());
               pipe.currentUser = new User(
                  userData.getAsJsonPrimitive("username").getAsString(),
                  userData.has("global_name") && userData.get("global_name").isJsonPrimitive()
                     ? userData.getAsJsonPrimitive("global_name").getAsString()
                     : null,
                  userData.has("discriminator") && userData.get("discriminator").isJsonPrimitive()
                     ? userData.getAsJsonPrimitive("discriminator").getAsString()
                     : "0",
                  Long.parseLong(userData.getAsJsonPrimitive("id").getAsString()),
                  userData.has("avatar") && userData.get("avatar").isJsonPrimitive() ? userData.getAsJsonPrimitive("avatar").getAsString() : null
               );
               if (client.isDebugMode()) {
                  client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Found a valid client (%s) with packet: %s", pipe.build.name(), response));
                  client.getCurrentLogger(LOGGER)
                     .info(String.format("[DEBUG] Found a valid user (%s) with id: %s", pipe.currentUser.getName(), pipe.currentUser.getId()));
               }

               if (pipe.build == preferredBuilds[0] || DiscordBuild.ANY == preferredBuilds[0]) {
                  if (client.isDebugMode()) {
                     client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Found preferred client: %s", pipe.build.name()));
                  }
                  break;
               }

               openedCandidates.add(pipe);
               candidates[pipe.build.ordinal()] = pipe;
               candidates[DiscordBuild.ANY.ordinal()] = pipe;
               pipe = null;
            } else if (client.isDebugMode()) {
               client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Unable to locate IPC Pipe: \"%s\"", location));
            }
         } catch (IOException | RuntimeException failure) {
            if (pipe != null) {
               try {
                  pipe.closeTransport();
               } catch (IOException cleanupFailure) {
                  failure.addSuppressed(cleanupFailure);
               }
            }
            if (client.isDebugMode()) client.getCurrentLogger(LOGGER).debug("Discord IPC endpoint failed: " + location, failure);
            pipe = null;
            if (Thread.currentThread().isInterrupted()) break;
         }
      }

      if (pipe == null && !Thread.currentThread().isInterrupted()) {
         for (int preferenceIndex = 1; preferenceIndex < preferredBuilds.length; preferenceIndex++) {
            DiscordBuild preferredBuild = preferredBuilds[preferenceIndex];
            if (client.isDebugMode()) {
               client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Looking for client build: %s", preferredBuild.name()));
            }

            if (candidates[preferredBuild.ordinal()] != null) {
               pipe = candidates[preferredBuild.ordinal()];
               if (client.isDebugMode()) {
                  client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Found preferred client: %s", pipe.build.name()));
               }
               break;
            }
         }

      }

      for (Pipe candidate : openedCandidates) {
         if (candidate != pipe) {
            try {
               candidate.closeTransport();
            } catch (IOException failure) {
               if (client.isDebugMode()) {
                  client.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Failed to close an open IPC pipe: %s", failure));
               }
            }
         }
      }

      if (pipe == null) throw new NoDiscordClientException();
      pipe.status = PipeStatus.CONNECTED;
      return pipe;
   }

   private static Pipe createPipe(IPCClient client, HashMap callbacks, File endpoint) throws IOException {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.startsWith("windows")) {
         return new WindowsPipe(client, callbacks, endpoint);
      }

      if (!os.contains("linux") && !os.contains("mac") && !os.contains("darwin")) {
         throw new RuntimeException("Unsupported OS: " + os);
      }

      return !os.contains("mac") && !os.contains("darwin") ? new UnixPipe(client, callbacks, endpoint) : new MacPipe(client, callbacks, endpoint);
   }

   private static String generateNonce() {
      return UUID.randomUUID().toString();
   }

   private static String getPipeLocation(int index) {
      String directory = null;
      String pipeName = "discord-ipc-" + index;
      if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
         return "\\\\?\\pipe\\" + pipeName;
      }

      for (String environmentVariable : unixPaths) {
         directory = System.getenv(environmentVariable);
         if (directory != null) {
            break;
         }
      }

      if (directory == null) {
         directory = "/tmp";
      }

      for (String folder : unixFolderPaths) {
         String candidate = directory + folder;
         File candidateDirectory = new File(candidate);
         if (candidateDirectory.exists() && candidateDirectory.isDirectory() && candidateDirectory.list().length > 0) {
            directory = candidate;
            break;
         }
      }

      return directory + "/" + pipeName;
   }

   public void send(Packet.OpCode opcode, JsonObject json, Callback callback) {
      try {
         String nonce = generateNonce();
         json.addProperty("nonce", nonce);
         Packet packet = new Packet(opcode, json, this.ipcClient.getEncoding());
         if (callback != null && !callback.isEmpty()) {
            this.callbacks.put(nonce, callback);
         }

         this.write(packet.toBytes());
         if (this.ipcClient.isDebugMode()) {
            this.ipcClient.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Sent packet: %s", packet.toDecodedString()));
         }

         if (this.listener != null) {
            this.listener.onPacketSent(this.ipcClient, packet);
         }
      } catch (IOException failure) {
         this.ipcClient.getCurrentLogger(LOGGER).error("Encountered an IOException while sending a packet and disconnected!");
         this.status = PipeStatus.DISCONNECTED;
      }
   }

   public void send(Packet.OpCode opcode, JsonObject json) {
      this.send(opcode, json, null);
   }

   public Packet receive(Packet.OpCode opcode, byte[] payload) {
      JsonObject json = new JsonParser().parse(new String(payload)).getAsJsonObject();
      Packet packet = new Packet(opcode, json, this.ipcClient.getEncoding());
      if (this.ipcClient.isDebugMode()) {
         this.ipcClient.getCurrentLogger(LOGGER).info(String.format("[DEBUG] Received packet: %s", packet));
      }

      if (this.listener != null) {
         this.listener.onPacketReceived(this.ipcClient, packet);
      }

      return packet;
   }

   Packet readHandshake(long timeoutMillis) throws IOException {
      FutureTask<Packet> reply = new FutureTask<>(this::read);
      Thread.startVirtualThread(reply);
      try {
         return reply.get(timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (TimeoutException failure) {
         throw new SocketTimeoutException("Discord IPC handshake timed out");
      } catch (InterruptedException failure) {
         Thread.currentThread().interrupt();
         throw new IOException("Discord IPC handshake interrupted", failure);
      } catch (ExecutionException failure) {
         throw new IOException("Discord IPC handshake failed", failure.getCause());
      } finally {
         reply.cancel(true);
      }
   }

   /** Failed handshakes should release the transport without trying another protocol write. */
   void closeTransport() throws IOException {
      this.close();
   }

   public abstract Packet read() throws IOException;

   public abstract void write(byte[] var1) throws IOException;

   public abstract void registerApp(String var1, String var2);

   public abstract void registerSteamGame(String var1, String var2);

   public PipeStatus getStatus() {
      return this.status;
   }

   public void setStatus(PipeStatus status) {
      this.status = status;
   }

   public void setListener(IPCListener listener) {
      this.listener = listener;
   }

   public abstract void close() throws IOException;

   public DiscordBuild getDiscordBuild() {
      return this.build;
   }

   public User getCurrentUser() {
      return this.currentUser;
   }
}

package com.jagrosh.discordipc.entities.pipe;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.impl.WinRegistry;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsPipe extends Pipe {
   private static final Logger LOGGER = LoggerFactory.getLogger(WindowsPipe.class);
   private static final Float javaSpec = Float.parseFloat(System.getProperty("java.specification.version"));
   private final int targetKey = -2147483647;
   private final long targetLongKey = -2147483647L;
   public RandomAccessFile file;

   WindowsPipe(IPCClient client, HashMap callbacks, File endpoint) throws IOException {
      super(client, callbacks);
      this.file = new RandomAccessFile(endpoint, "rw");
   }

   @Override
   public void write(byte[] bytes) throws IOException {
      this.file.write(bytes);
   }

   @Override
   public Packet read() throws IOException {
      while ((this.status == PipeStatus.CONNECTED || this.status == PipeStatus.CLOSING) && this.file.length() == 0L) {
         try {
            Thread.sleep(50L);
         } catch (InterruptedException var4) {
         }
      }

      if (this.status == PipeStatus.DISCONNECTED) {
         throw new IOException("Disconnected!");
      }

      if (this.status == PipeStatus.CLOSED) {
         return new Packet(Packet.OpCode.CLOSE, null, this.ipcClient.getEncoding());
      }

      Packet.OpCode opcode = Packet.OpCode.values()[Integer.reverseBytes(this.file.readInt())];
      int length = Integer.reverseBytes(this.file.readInt());
      byte[] payload = new byte[length];
      this.file.readFully(payload);
      return this.receive(opcode, payload);
   }

   @Override
   void closeTransport() throws IOException {
      this.status = PipeStatus.CLOSED;
      this.file.close();
   }

   @Override
   public void close() throws IOException {
      if (this.ipcClient.isDebugMode()) {
         this.ipcClient.getCurrentLogger(LOGGER).info("[DEBUG] Closing IPC pipe...");
      }

      this.status = PipeStatus.CLOSING;
      this.send(Packet.OpCode.CLOSE, new JsonObject());
      this.closeTransport();
   }

   @Override
   public void registerApp(String applicationId, String command) {
      String javaHome = System.getProperty("java.home");
      File javaExecutable = new File(javaHome.split(";")[0] + "/bin/java.exe");
      File javawExecutable = new File(javaHome.split(";")[0] + "/bin/javaw.exe");
      String javaPath = javaExecutable.exists() ? javaExecutable.getAbsolutePath() : (javawExecutable.exists() ? javawExecutable.getAbsolutePath() : null);
      if (javaPath == null) {
         throw new RuntimeException("Unable to find java path");
      }

      String registrationCommand;
      if (command != null) {
         registrationCommand = command;
      } else {
         registrationCommand = javaPath;
      }

      String protocol = "discord-" + applicationId;
      String description = "URL:Run game " + applicationId + " protocol";
      String protocolKey = "Software\\Classes\\" + protocol;
      String iconKey = protocolKey + "\\DefaultIcon";
      String commandKey = protocolKey + "\\shell\\open\\command";

      try {
         if (javaSpec >= 11.0F) {
            WinRegistry.createKey(-2147483647L, protocolKey);
            WinRegistry.writeStringValue(-2147483647L, protocolKey, "", description);
            WinRegistry.writeStringValue(-2147483647L, protocolKey, "URL Protocol", "\u0000");
            WinRegistry.createKey(-2147483647L, iconKey);
            WinRegistry.writeStringValue(-2147483647L, iconKey, "", javaPath);
            WinRegistry.createKey(-2147483647L, commandKey);
            WinRegistry.writeStringValue(-2147483647L, commandKey, "", registrationCommand);
         } else {
            WinRegistry.createKey(-2147483647, protocolKey);
            WinRegistry.writeStringValue(-2147483647, protocolKey, "", description);
            WinRegistry.writeStringValue(-2147483647, protocolKey, "URL Protocol", "\u0000");
            WinRegistry.createKey(-2147483647, iconKey);
            WinRegistry.writeStringValue(-2147483647, iconKey, "", javaPath);
            WinRegistry.createKey(-2147483647, commandKey);
            WinRegistry.writeStringValue(-2147483647, commandKey, "", registrationCommand);
         }
      } catch (Throwable failure) {
         throw new RuntimeException("Unable to modify Discord registry keys", failure);
      }
   }

   @Override
   public void registerSteamGame(String applicationId, String steamId) {
      try {
         String steamPath;
         if (javaSpec >= 11.0F) {
            steamPath = WinRegistry.readString(-2147483647L, "Software\\\\Valve\\\\Steam", "SteamExe");
         } else {
            steamPath = WinRegistry.readString(-2147483647, "Software\\\\Valve\\\\Steam", "SteamExe");
         }

         if (steamPath == null) {
            throw new RuntimeException("Steam exe path not found");
         }

         steamPath = steamPath.replaceAll("/", "\\");
         String command = "\"" + steamPath + "\" steam://rungameid/" + steamId;
         this.registerApp(applicationId, command);
      } catch (Exception failure) {
         throw new RuntimeException("Unable to register Steam game", failure);
      }
   }
}

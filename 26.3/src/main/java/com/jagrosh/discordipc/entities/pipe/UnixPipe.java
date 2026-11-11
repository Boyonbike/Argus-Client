package com.jagrosh.discordipc.entities.pipe;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.Packet;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixPipe extends Pipe {
   private static final Logger LOGGER = LoggerFactory.getLogger(UnixPipe.class);
   private final JavaUnixSocketTransport socket;

   UnixPipe(IPCClient client, HashMap callbacks, File endpoint) throws IOException {
      super(client, callbacks);
      this.socket = JavaUnixSocketTransport.connect(endpoint.toPath());
   }

   @Override
   public Packet read() throws IOException {
      try {
         if (this.status == PipeStatus.DISCONNECTED) {
            throw new IOException("Disconnected!");
         } else if (this.status == PipeStatus.CLOSED) {
            return new Packet(Packet.OpCode.CLOSE, null, this.ipcClient.getEncoding());
         } else {
            IpcFrameCodec.Frame frame = this.socket.read();
            Packet.OpCode[] codes = Packet.OpCode.values();
            if (frame.opcode() >= 0 && frame.opcode() < codes.length) {
               return this.receive(codes[frame.opcode()], frame.body());
            } else {
               throw new IOException("Unknown IPC opcode: " + frame.opcode());
            }
         }
      } catch (IOException failure) {
         if (this.status == PipeStatus.CLOSED) {
            return new Packet(Packet.OpCode.CLOSE, null, this.ipcClient.getEncoding());
         } else {
            throw failure;
         }
      }
   }

   @Override
   public void write(byte[] packet) throws IOException {
      this.socket.write(packet);
   }

   @Override
   void closeTransport() throws IOException {
      this.status = PipeStatus.CLOSED;
      this.socket.close();
   }

   @Override
   public void close() throws IOException {
      if (this.status != PipeStatus.CLOSED) {
         try {
            if (this.ipcClient.isDebugMode()) {
               this.ipcClient.getCurrentLogger(LOGGER).info("[DEBUG] Closing IPC pipe...");
            }

            this.status = PipeStatus.CLOSING;
            this.send(Packet.OpCode.CLOSE, new JsonObject());
         } finally {
            this.closeTransport();
         }
      }
   }

   public boolean mkdir(String path) {
      File directory = new File(path);
      return directory.exists() && directory.isDirectory() || directory.mkdir();
   }

   @Override
   public void registerApp(String applicationId, String command) {
      String home = System.getenv("HOME");
      if (home == null) {
         throw new RuntimeException("Unable to find user HOME directory");
      }

      if (command == null) {
         try {
            command = Files.readSymbolicLink(Paths.get("/proc/self/exe")).toString();
         } catch (Exception failure) {
            throw new RuntimeException("Unable to get current exe path from /proc/self/exe", failure);
         }
      }

      String desktopEntry = "[Desktop Entry]\nName=Game "
         + applicationId
         + "\nExec="
         + command
         + " %%u\nType=Application\nNoDisplay=true\nCategories=Discord;Games;\nMimeType=x-scheme-handler/discord-"
         + applicationId
         + ";\n";
      String desktopFile = "/discord-" + applicationId + ".desktop";
      String path = home + "/.local";
      if (this.mkdir(path)) {
         this.ipcClient.getCurrentLogger(LOGGER).warn("[DEBUG] Failed to create directory '" + path + "', may already exist");
      }

      path = path + "/share";
      if (this.mkdir(path)) {
         this.ipcClient.getCurrentLogger(LOGGER).warn("[DEBUG] Failed to create directory '" + path + "', may already exist");
      }

      path = path + "/applications";
      if (this.mkdir(path)) {
         this.ipcClient.getCurrentLogger(LOGGER).warn("[DEBUG] Failed to create directory '" + path + "', may already exist");
      }

      path = path + desktopFile;

      try (FileWriter writer = new FileWriter(path)) {
         writer.write(desktopEntry);
      } catch (Exception failure) {
         throw new RuntimeException("Failed to write desktop info into '" + path + "'");
      }

      String mimeCommand = "xdg-mime default discord-" + applicationId + ".desktop x-scheme-handler/discord-" + applicationId;

      try {
         ProcessBuilder process = new ProcessBuilder(mimeCommand.split(" "));
         process.environment();
         int exitCode = process.start().waitFor();
         if (exitCode < 0) {
            throw new Exception("xdg-mime returned " + exitCode);
         }
      } catch (Exception failure) {
         throw new RuntimeException("Failed to register mime handler", failure);
      }
   }

   @Override
   public void registerSteamGame(String applicationId, String command) {
      this.registerApp(applicationId, "xdg-open steam://rungameid/" + command);
   }
}

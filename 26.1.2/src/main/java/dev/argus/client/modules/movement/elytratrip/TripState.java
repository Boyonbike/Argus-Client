package dev.argus.client.modules.movement.elytratrip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.chat.SetHomeScheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What a trip needs to survive a game restart, saved as argusclient/elytra-trip-state.json. */
public final class TripState {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public TripPhases.Snapshot phases;
   public int destX;
   public int destZ;
   public String server;
   public int deaths;
   public int refills;
   public int disconnects;
   public long startedAt;
   public boolean afterDeath;
   public List<String> turnedOff = List.of();
   public SetHomeScheduler.State checkpoint;
   public Map<ChatTeleportState.Kind, Long> learned = new EnumMap<>(ChatTeleportState.Kind.class);

   public static TripState load(Path file) {
      try {
         if (!Files.exists(file)) return new TripState();
         TripState state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TripState.class);
         if (state == null) return new TripState();
         if (state.turnedOff == null) state.turnedOff = List.of();
         if (state.learned == null) state.learned = new EnumMap<>(ChatTeleportState.Kind.class);
         return state;
      } catch (IOException | RuntimeException e) {
         return new TripState();
      }
   }

   public void save(Path file) throws IOException {
      Files.createDirectories(file.getParent());
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }
}

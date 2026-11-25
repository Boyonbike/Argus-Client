package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.chat.SetHomeScheduler;
import dev.argus.client.modules.movement.elytratrip.TripPhases.EndReason;
import dev.argus.client.modules.movement.elytratrip.TripPhases.Phase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TripStateTest {
   @TempDir
   Path dir;

   @Test
   void roundTripsPhaseDestinationCountersTurnedOffAndLearnedCooldowns() throws Exception {
      TripState state = new TripState();
      state.phases = new TripPhases.Snapshot(Phase.REFILLING, Phase.FLYING, EndReason.NONE, 1234L, 2, "", 1, false);
      state.destX = 100_000;
      state.destZ = -250_000;
      state.server = "6b6t.org";
      state.deaths = 1;
      state.refills = 3;
      state.disconnects = 4;
      state.startedAt = 99L;
      state.turnedOff = List.of("AutoEat", "Offhand");
      state.checkpoint = new SetHomeScheduler.State(10, 20, 15, 300_000, 0, 0, List.of());
      state.learned = Map.of(ChatTeleportState.Kind.Home, 60_000L);
      state.afterDeath = true;
      Path file = dir.resolve("state.json");
      state.save(file);

      TripState loaded = TripState.load(file);
      assertEquals(state.phases, loaded.phases);
      assertEquals(100_000, loaded.destX);
      assertEquals(-250_000, loaded.destZ);
      assertEquals("6b6t.org", loaded.server);
      assertEquals(3, loaded.refills);
      assertEquals(List.of("AutoEat", "Offhand"), loaded.turnedOff);
      assertEquals(state.checkpoint, loaded.checkpoint);
      assertEquals(60_000L, loaded.learned.get(ChatTeleportState.Kind.Home));
      assertEquals(true, loaded.afterDeath);
   }

   @Test
   void missingFileIsEmptyState() {
      TripState loaded = TripState.load(dir.resolve("none.json"));
      assertNull(loaded.phases);
      assertEquals(List.of(), loaded.turnedOff);
   }

   @Test
   void corruptFileIsEmptyState() throws Exception {
      Path file = dir.resolve("bad.json");
      Files.writeString(file, "{oops");
      assertNull(TripState.load(file).phases);
   }
}

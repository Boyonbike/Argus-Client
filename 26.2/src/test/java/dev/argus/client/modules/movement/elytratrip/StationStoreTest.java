package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StationStoreTest {
   private static final String SERVER = "6b6t.org";
   private static final BlockPos A = new BlockPos(10, 64, 10);
   private static final BlockPos B = new BlockPos(20, 64, 20);

   @TempDir
   Path dir;

   private StationStore store() {
      return new StationStore(dir.resolve("stations.json"));
   }

   @Test
   void toggleAddsAndRemovesRole() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.ELYTRAS);
      assertEquals(Set.of(ChestRole.ELYTRAS), store.roles(SERVER, A));
      store.toggle(SERVER, A, ChestRole.ELYTRAS);
      assertTrue(store.roles(SERVER, A).isEmpty());
      assertTrue(store.all(SERVER).isEmpty());
   }

   @Test
   void oneChestManyRoles() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.TOTEMS);
      store.toggle(SERVER, A, ChestRole.GAPPLES);
      assertEquals(EnumSet.of(ChestRole.TOTEMS, ChestRole.GAPPLES), store.roles(SERVER, A));
   }

   @Test
   void clearRemovesEveryRole() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.TOTEMS);
      store.toggle(SERVER, A, ChestRole.GAPPLES);
      store.clear(SERVER, A);
      assertTrue(store.roles(SERVER, A).isEmpty());
   }

   @Test
   void perServerIsolation() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.DUMP);
      assertTrue(store.roles("other.net", A).isEmpty());
      assertEquals(Set.of(ChestRole.DUMP), store.roles("6B6T.org:25565", A));
   }

   @Test
   void nearestFirst() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.FOOD);
      store.toggle(SERVER, B, ChestRole.FOOD);
      assertEquals(List.of(B, A), store.chests(SERVER, ChestRole.FOOD, new Vec3(21, 64, 21)));
      assertEquals(List.of(A, B), store.chests(SERVER, ChestRole.FOOD, new Vec3(9, 64, 9)));
   }

   @Test
   void missingListsUntaggedRoles() {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.ELYTRAS);
      store.toggle(SERVER, B, ChestRole.DUMP);
      assertEquals(EnumSet.of(ChestRole.ARMOUR, ChestRole.TOTEMS, ChestRole.GAPPLES, ChestRole.FOOD), store.missing(SERVER));
   }

   @Test
   void saveAndReload() throws Exception {
      StationStore store = store();
      store.toggle(SERVER, A, ChestRole.ELYTRAS);
      store.toggle(SERVER, B, ChestRole.DUMP);
      store.save();
      StationStore reloaded = store();
      assertNull(reloaded.loadError());
      assertEquals(Set.of(ChestRole.ELYTRAS), reloaded.roles(SERVER, A));
      assertEquals(Set.of(ChestRole.DUMP), reloaded.roles(SERVER, B));
   }

   @Test
   void corruptFileReportsLoadErrorAndStartsEmpty() throws Exception {
      Files.writeString(dir.resolve("stations.json"), "{not json");
      StationStore store = store();
      assertNotNull(store.loadError());
      assertTrue(store.all(SERVER).isEmpty());
   }

   @Test
   void canonicalHalfIsLowerXZ() {
      assertEquals(A, StationStore.canonical(A, A.east()));
      assertEquals(A, StationStore.canonical(A.east(), A));
      assertEquals(A, StationStore.canonical(A.south(), A));
      assertEquals(A, StationStore.canonical(A, null));
   }
}

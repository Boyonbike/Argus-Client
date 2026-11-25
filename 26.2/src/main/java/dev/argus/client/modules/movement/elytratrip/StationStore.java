package dev.argus.client.modules.movement.elytratrip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.argus.client.chat.SetHomeStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Station chest tags, per server, saved as JSON. Both halves of a double chest are stored as one: the lower (x, z) half. */
public final class StationStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   /** On disk: server → "x,y,z" → role names. */
   private static final class Document {
      Map<String, Map<String, List<String>>> servers = new LinkedHashMap<>();
   }

   private final Path file;
   private final Map<String, Map<BlockPos, EnumSet<ChestRole>>> servers = new LinkedHashMap<>();
   private String loadError;

   public StationStore(Path file) {
      this.file = file;
      load();
   }

   public String loadError() {
      return loadError;
   }

   /** The half of a double chest tags are stored under; {@code other} is the second half, or null for a single chest. */
   public static BlockPos canonical(BlockPos pos, BlockPos other) {
      if (other == null) return pos.immutable();
      if (other.getX() < pos.getX() || other.getX() == pos.getX() && other.getZ() < pos.getZ()) return other.immutable();
      return pos.immutable();
   }

   private Map<BlockPos, EnumSet<ChestRole>> server(String server) {
      return servers.computeIfAbsent(SetHomeStore.normaliseServer(server), key -> new LinkedHashMap<>());
   }

   public Set<ChestRole> roles(String server, BlockPos pos) {
      EnumSet<ChestRole> roles = server(server).get(pos);
      return roles == null ? EnumSet.noneOf(ChestRole.class) : EnumSet.copyOf(roles);
   }

   public void toggle(String server, BlockPos pos, ChestRole role) {
      Map<BlockPos, EnumSet<ChestRole>> chests = server(server);
      EnumSet<ChestRole> roles = chests.computeIfAbsent(pos.immutable(), key -> EnumSet.noneOf(ChestRole.class));
      if (!roles.remove(role)) roles.add(role);
      if (roles.isEmpty()) chests.remove(pos);
   }

   public void clear(String server, BlockPos pos) {
      server(server).remove(pos);
   }

   /** Chests with this role, nearest to {@code from} first. */
   public List<BlockPos> chests(String server, ChestRole role, Vec3 from) {
      List<BlockPos> result = new ArrayList<>();
      server(server).forEach((pos, roles) -> {
         if (roles.contains(role)) result.add(pos);
      });
      result.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(from)));
      return result;
   }

   public Map<BlockPos, Set<ChestRole>> all(String server) {
      Map<BlockPos, Set<ChestRole>> copy = new LinkedHashMap<>();
      server(server).forEach((pos, roles) -> copy.put(pos, EnumSet.copyOf(roles)));
      return copy;
   }

   public EnumSet<ChestRole> missing(String server) {
      EnumSet<ChestRole> missing = EnumSet.allOf(ChestRole.class);
      server(server).values().forEach(missing::removeAll);
      return missing;
   }

   public void save() throws IOException {
      Document doc = new Document();
      servers.forEach((server, chests) -> {
         if (chests.isEmpty()) return;
         Map<String, List<String>> out = new LinkedHashMap<>();
         chests.forEach((pos, roles) -> out.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), roles.stream().map(Enum::name).toList()));
         doc.servers.put(server, out);
      });
      Files.createDirectories(file.getParent());
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temp, GSON.toJson(doc), StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   private void load() {
      if (!Files.exists(file)) return;
      try {
         Document doc = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Document.class);
         if (doc == null || doc.servers == null) return;
         doc.servers.forEach((server, chests) -> chests.forEach((key, roleNames) -> {
            String[] parts = key.split(",");
            if (parts.length != 3 || roleNames == null) return;
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip()));
            EnumSet<ChestRole> roles = EnumSet.noneOf(ChestRole.class);
            for (String name : roleNames) {
               try {
                  roles.add(ChestRole.valueOf(name));
               } catch (IllegalArgumentException ignored) {
                  // A role from a newer version: skip it.
               }
            }
            if (!roles.isEmpty()) servers.computeIfAbsent(server, s -> new LinkedHashMap<>()).put(pos, roles);
         }));
      } catch (IOException | RuntimeException e) {
         servers.clear();
         loadError = String.valueOf(e.getMessage());
      }
   }
}

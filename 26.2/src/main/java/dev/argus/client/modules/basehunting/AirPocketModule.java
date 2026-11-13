package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Scans loaded chunks for pockets of plain air inside cave air and hands each one to the subclass to judge. */
abstract class AirPocketModule extends Module {
   protected final HuntMarkers markers;
   private final ChunkScanner chunkScanner = new ChunkScanner(1, this::scanChunk, null, null);
   private int ticks;

   protected AirPocketModule(String name, String description, HuntMarkers.Factory markers) {
      super(name, description, ModuleCategory.BASE_HUNTING);
      this.markers = markers.create(this);
   }

   /** How many blocks a pocket may hold before it is ignored as a mined-out area. */
   protected abstract int pocketLimit();

   protected abstract void onPocket(ClientLevel level, List<int[]> pocket);

   @Override
   public void onEnable() {
      this.chunkScanner.start();
      if (!this.isWorldUnavailable()) {
         this.chunkScanner.scanLoadedChunks();
      }
   }

   @Override
   public void onDisable() {
      this.chunkScanner.stop();
      this.markers.clear();
   }

   @Override
   public String getInfo() {
      return this.markers.size() == 0 ? null : String.valueOf(this.markers.size());
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.markers.clear();
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable() && ++this.ticks >= 100) {
         this.ticks = 0;
         this.markers.pruneFarFrom(ClientAccess.argusclientMinecraft.player.position());
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }

   protected static AirPockets.Lookup lookup(ClientLevel level) {
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      return (x, y, z) -> {
         Block block = level.getBlockState(position.set(x, y, z)).getBlock();
         return block == Blocks.AIR ? AirPockets.Kind.AIR : block == Blocks.CAVE_AIR ? AirPockets.Kind.CAVE_AIR : AirPockets.Kind.OTHER;
      };
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      DimensionType dimension = WorldUtils.getDimension();
      if (level == null || dimension == DimensionType.THE_END) {
         return;
      }

      // Caves sit below the surface; above these heights plain air is just the sky.
      int topY = dimension == DimensionType.THE_NETHER ? 126 : 180;
      AirPockets.Lookup lookup = lookup(level);
      LongOpenHashSet visited = new LongOpenHashSet();
      LevelChunkSection[] sections = chunk.getSections();
      int minY = chunk.getMinY();

      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         LevelChunkSection section = sections[sectionIndex];
         int sectionY = minY + sectionIndex * 16;
         if (section == null || sectionY > topY || !section.maybeHas(state -> state.is(Blocks.CAVE_AIR)) || !section.maybeHas(state -> state.is(Blocks.AIR))) {
            continue;
         }

         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  if (!section.getBlockState(x, y, z).is(Blocks.AIR)) {
                     continue;
                  }

                  int worldX = chunkPosition.getMinBlockX() + x;
                  int worldY = sectionY + y;
                  int worldZ = chunkPosition.getMinBlockZ() + z;
                  if (worldY > topY || visited.contains(AirPockets.pack(worldX, worldY, worldZ)) || !AirPockets.touchesCaveAir(lookup, worldX, worldY, worldZ)) {
                     continue;
                  }

                  // Pockets too big to matter come back null; their blocks are still marked visited so they are walked once.
                  List<int[]> pocket = AirPockets.pocket(lookup, worldX, worldY, worldZ, this.pocketLimit(), visited);
                  if (pocket != null) {
                     this.onPocket(level, pocket);
                  }
               }
            }
         }
      }
   }
}

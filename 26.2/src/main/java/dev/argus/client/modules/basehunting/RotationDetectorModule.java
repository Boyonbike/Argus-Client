package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.ChunkScanner;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Finds blocks lying on their side that only ever generate standing up: logs underground, basalt, purpur and quartz pillars,
 * hay bales and (optionally) deepslate. A sideways one was placed by a player. Surface blocks with open sky above are skipped by default, since
 * fallen trees and big tree branches lie sideways naturally. Ported from bephax's RotationDetector.
 */
public class RotationDetectorModule extends Module {
   private final Setting<Boolean> logsSetting = this.add(new BooleanSetting("Logs", true));
   private final Setting<Boolean> basaltSetting = this.add(new BooleanSetting("Basalt", true));
   private final Setting<Boolean> purpurSetting = this.add(new BooleanSetting("PurpurPillars", true));
   private final Setting<Boolean> haySetting = this.add(new BooleanSetting("HayBales", true));
   private final Setting<Boolean> quartzSetting = this.add(new BooleanSetting("QuartzPillars", true));
   private final Setting<Boolean> deepslateSetting = this.add(new BooleanSetting("Deepslate", false));
   private final Setting<Integer> minYSetting = this.add(new NumberSetting<>("MinY", -64, -64, 320));
   private final Setting<Integer> maxYSetting = this.add(new NumberSetting<>("MaxY", 320, -64, 320));
   private final Setting<Integer> perChunkSetting = this.add(new NumberSetting<>("MinPerChunk", 1, 1, 32));
   private final Setting<Boolean> ignoreSurfaceSetting = this.add(new BooleanSetting("IgnoreSurface", true).markVisibilityDependency());
   private final Setting<Integer> airCheckSetting = this.add(new NumberSetting<>("AirCheck", 5, 1, 20).withUnit("m").dependsOn(this.ignoreSurfaceSetting));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 0, 0, 40), new Color(255, 0, 0, 200), false, false);
   private final ChunkScanner chunkScanner = new ChunkScanner(1, this::scanChunk, null, null);
   private int ticks;

   public RotationDetectorModule() {
      super("RotationDetector", "Finds sideways logs, basalt, pillars, hay bales and deepslate, which only generate standing up.", ModuleCategory.BASE_HUNTING);
   }

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

   private boolean isWatched(BlockState state) {
      Block block = state.getBlock();
      return this.logsSetting.getValue() && state.is(BlockTags.LOGS)
         || this.basaltSetting.getValue() && (block == Blocks.BASALT || block == Blocks.POLISHED_BASALT)
         || this.purpurSetting.getValue() && block == Blocks.PURPUR_PILLAR
         || this.haySetting.getValue() && block == Blocks.HAY_BLOCK
         || this.quartzSetting.getValue() && block == Blocks.QUARTZ_PILLAR
         || this.deepslateSetting.getValue() && block == Blocks.DEEPSLATE;
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null) {
         return;
      }

      LevelChunkSection[] sections = chunk.getSections();
      int minY = chunk.getMinY();
      int lowest = this.minYSetting.getValue();
      int highest = this.maxYSetting.getValue();
      java.util.List<BlockPos> found = new java.util.ArrayList<>();
      java.util.List<String> names = new java.util.ArrayList<>();
      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         LevelChunkSection section = sections[sectionIndex];
         if (section == null || section.hasOnlyAir() || !section.maybeHas(this::isWatched)) {
            continue;
         }

         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (!this.isWatched(state) || !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
                     continue;
                  }

                  int blockY = minY + sectionIndex * 16 + y;
                  if (blockY < lowest || blockY > highest) {
                     continue;
                  }

                  BlockPos position = new BlockPos(chunkPosition.getMinBlockX() + x, blockY, chunkPosition.getMinBlockZ() + z);
                  if (this.ignoreSurfaceSetting.getValue() && this.hasAirAbove(level, position)) {
                     continue;
                  }

                  found.add(position);
                  names.add(state.getBlock().getName().getString());
               }
            }
         }
      }

      // A lone sideways block can be a fluke; several in one chunk are a build.
      if (found.size() >= this.perChunkSetting.getValue()) {
         for (int index = 0; index < found.size(); index++) {
            this.markers.report(found.get(index), new AABB(found.get(index)), "a sideways " + names.get(index));
         }
      }
   }

   private boolean hasAirAbove(ClientLevel level, BlockPos position) {
      int distance = this.airCheckSetting.getValue();
      for (int offset = 1; offset <= distance; offset++) {
         if (level.getBlockState(position.above(offset)).is(Blocks.AIR)) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }
}

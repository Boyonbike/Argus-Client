package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.ChunkScanner;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Finds dungeons and mineshaft spider rooms whose spawner has been broken, which only players do. Ported from Trouser
 * Streak's NoSpawnerDetector: a dungeon is a mostly cobblestone floor under cave air, a spider room a clump of cobwebs.
 */
public class NoSpawnerModule extends Module {
   private final Setting<Boolean> dungeonsSetting = this.add(new BooleanSetting("Dungeons", true).markVisibilityDependency());
   private final Setting<Boolean> needChestSetting = this.add(new BooleanSetting("NeedChest", true).dependsOn(this.dungeonsSetting));
   private final Setting<Boolean> mineshaftsSetting = this.add(new BooleanSetting("Mineshafts", true));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(170, 80, 255, 50), new Color(170, 80, 255, 230), true);
   private final List<BlockPos> checked = new CopyOnWriteArrayList<>();
   private final ChunkScanner chunkScanner = new ChunkScanner(1, this::scanChunk, null, null);

   public NoSpawnerModule() {
      super("NoSpawner", "Finds dungeons and mineshaft spider rooms whose spawner has been broken.", ModuleCategory.BASE_HUNTING);
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
      this.checked.clear();
   }

   @Override
   public String getInfo() {
      return this.markers.size() == 0 ? null : String.valueOf(this.markers.size());
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.markers.clear();
      this.checked.clear();
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null) {
         return;
      }

      boolean dungeons = this.dungeonsSetting.getValue();
      boolean mineshafts = this.mineshaftsSetting.getValue();
      LevelChunkSection[] sections = chunk.getSections();
      int minY = chunk.getMinY();

      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         LevelChunkSection section = sections[sectionIndex];
         if (section == null
            || section.hasOnlyAir()
            || !section.maybeHas(state -> dungeons && state.is(Blocks.MOSSY_COBBLESTONE) || mineshafts && state.is(Blocks.COBWEB))) {
            continue;
         }

         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  BlockState state = section.getBlockState(x, y, z);
                  BlockPos position = new BlockPos(chunkPosition.getMinBlockX() + x, minY + sectionIndex * 16 + y, chunkPosition.getMinBlockZ() + z);
                  if (dungeons && state.is(Blocks.MOSSY_COBBLESTONE) && this.isNew(position, 8)) {
                     this.checkDungeon(level, position);
                  } else if (mineshafts && state.is(Blocks.COBWEB) && this.isNew(position, 6)) {
                     this.checkSpiderRoom(level, position);
                  }
               }
            }
         }
      }
   }

   private boolean isNew(BlockPos position, int spacing) {
      for (BlockPos seen : this.checked) {
         if (seen.closerThan(position, spacing)) {
            return false;
         }
      }

      return true;
   }

   private void checkDungeon(ClientLevel level, BlockPos center) {
      int floorBlocks = 0;
      boolean caveAir = false;
      boolean chest = false;
      boolean spawner = false;

      for (int x = -4; x <= 4; x++) {
         for (int z = -4; z <= 4; z++) {
            Block floor = level.getBlockState(center.offset(x, 0, z)).getBlock();
            if (floor == Blocks.MOSSY_COBBLESTONE || floor == Blocks.COBBLESTONE) {
               floorBlocks++;
            }

            Block above = level.getBlockState(center.offset(x, 1, z)).getBlock();
            caveAir |= above == Blocks.CAVE_AIR;
            chest |= above == Blocks.CHEST;
            spawner |= above == Blocks.SPAWNER;
         }
      }

      // 81 floor blocks; a dungeon floor is more than half cobblestone.
      if (floorBlocks > 44 && caveAir && !spawner && (chest || !this.needChestSetting.getValue())) {
         this.checked.add(center);
         this.markers.report(center, new AABB(center.offset(-4, 0, -4)).expandTowards(8, 1, 8), "a dungeon with its spawner broken");
      }
   }

   private void checkSpiderRoom(ClientLevel level, BlockPos center) {
      int webs = 0;
      boolean caveAir = false;
      boolean air = false;

      for (BlockPos nearby : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) {
         Block block = level.getBlockState(nearby).getBlock();
         webs += block == Blocks.COBWEB ? 1 : 0;
         caveAir |= block == Blocks.CAVE_AIR;
         air |= block == Blocks.AIR;
      }

      // 18 blocks checked; spider rooms are packed with webs, and broken ones leave plain air behind.
      if (webs <= 8 || !caveAir || !air) {
         return;
      }

      for (int x = -18; x <= 18; x++) {
         for (int z = -18; z <= 18; z++) {
            Block block = level.getBlockState(center.offset(x, 0, z)).getBlock();
            if (block == Blocks.SPAWNER || block == Blocks.VOID_AIR) {
               return;
            }
         }
      }

      this.checked.add(center);
      this.markers.report(center, new AABB(center.offset(-1, 0, -1)).expandTowards(2, 1, 2), "a mineshaft spider room with its spawner broken");
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }
}

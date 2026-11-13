package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Estimates whether a chunk holds a base from the blocks and entities in it. Ported from Trouser Streak's BaseFinder:
 * six editable block lists, each with how many of its blocks a chunk needs, plus detectors for things that do not
 * generate naturally (written signs, lit portals, soul-sand bubble columns, builds in the sky or on the Nether roof,
 * misplaced bedrock, spawners without their dungeon, item frames, pearls, name tags, boats, traded-with villagers and
 * crowded animal pens), plus a seventh list of your own. It also finds stashes, which was the separate StashFinder:
 * piles of chests or other storage, shulker boxes, stacked chest minecarts and chested mounts.
 */
public class BaseFinderModule extends Module {
   private final Setting<Integer> minYSetting = this.add(new NumberSetting<>("MinYOffset", 0, 0, 128).withUnit("m"));
   private final Setting<Integer> maxYSetting = this.add(new NumberSetting<>("MaxYOffset", 0, 0, 128).withUnit("m"));
   private final BlockList[] lists = new BlockList[]{
      this.list("PlayerOnly", 1, BaseFinderBlocks.PLAYER_ONLY),
      this.list("Uncommon", 6, BaseFinderBlocks.UNCOMMON),
      this.list("Utility", 4, BaseFinderBlocks.UTILITY),
      this.list("Building", 2, BaseFinderBlocks.BUILDING),
      this.list("Common", 12, BaseFinderBlocks.COMMON),
      this.list("Redstone", 12, BaseFinderBlocks.REDSTONE),
      this.list("Custom", 1, new String[0], false)
   };
   private final Setting<Boolean> detectorsSetting = this.add(new BooleanSetting("Detectors", true).markAsGroup());
   private final Setting<Boolean> signsSetting = this.add(new BooleanSetting("Signs", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> portalsSetting = this.add(new BooleanSetting("Portals", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> bubblesSetting = this.add(new BooleanSetting("Bubbles", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> skySetting = this.add(new BooleanSetting("SkyBuilds", true).markVisibilityDependency().dependsOn(this.detectorsSetting));
   private final Setting<Integer> skyHeightSetting = this.add(new NumberSetting<>("SkyY", 260, 64, 319).dependsOn(this.detectorsSetting, this.skySetting));
   private final Setting<Boolean> bedrockSetting = this.add(new BooleanSetting("Bedrock", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> roofSetting = this.add(new BooleanSetting("NetherRoof", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> spawnerSetting = this.add(new BooleanSetting("ModifiedSpawners", true).dependsOn(this.detectorsSetting));
   private final Setting<Boolean> entitiesSetting = this.add(new BooleanSetting("Entities", true).markAsGroup());
   private final Setting<Boolean> framesSetting = this.add(new BooleanSetting("ItemFrames", true).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> pearlsSetting = this.add(new BooleanSetting("Pearls", true).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> nameTagsSetting = this.add(new BooleanSetting("NameTags", true).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> boatsSetting = this.add(new BooleanSetting("Boats", false).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> villagersSetting = this.add(new BooleanSetting("TradedVillagers", true).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> clustersSetting = this.add(new BooleanSetting("Clusters", true).markVisibilityDependency().dependsOn(this.entitiesSetting));
   private final Setting<Integer> clusterSizeSetting = this.add(new NumberSetting<>("ClusterSize", 14, 4, 64).dependsOn(this.entitiesSetting, this.clustersSetting));
   private final Setting<Set<EntityType<?>>> clusterMobsSetting = this.add(
      new RegistrySetSetting<EntityType<?>>("ClusterMobs", BuiltInRegistries.ENTITY_TYPE, creatures()).dependsOn(this.entitiesSetting, this.clustersSetting)
   );
   private final Setting<Integer> entityDelaySetting = this.add(new NumberSetting<>("EntityScanDelay", 20, 1, 200).withUnit(" ticks").dependsOn(this.entitiesSetting));
   private final Setting<Boolean> columnSetting = this.add(new BooleanSetting("FullHeightBox", false));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 120, 0, 45), new Color(255, 120, 0, 220), true);
   private final HuntDiscordAlerts discord = new HuntDiscordAlerts(this);
   private final BaseFinderStashes stashes = new BaseFinderStashes(this, this.markers, this.discord);
   private final Set<Long> reportedChunks = ConcurrentHashMap.newKeySet();
   private final ChunkScanner chunkScanner = new ChunkScanner(1, this::scanChunk, null, null);
   private int entityTicks;

   public BaseFinderModule() {
      super("BaseFinder", "Finds bases and stashes from the blocks, storage and entities in each chunk.", ModuleCategory.BASE_HUNTING);
   }

   private BlockList list(String name, int count, String[] defaults) {
      return this.list(name, count, defaults, true);
   }

   private static EntityType<?>[] creatures() {
      java.util.List<EntityType<?>> creatures = new java.util.ArrayList<>();
      for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
         if (type.getCategory() == MobCategory.CREATURE) {
            creatures.add(type);
         }
      }

      return creatures.toArray(new EntityType<?>[0]);
   }

   private BlockList list(String name, int count, String[] defaults, boolean on) {
      BooleanSetting enabled = new BooleanSetting(name, on);
      enabled.markVisibilityDependency();
      Setting<Boolean> enabledSetting = this.add(enabled);
      Setting<java.util.Set<Block>> blocks = this.add(
         new RegistrySetSetting<>(name + "Blocks", BuiltInRegistries.BLOCK, HuntBlocks.blocks(defaults)).dependsOn(enabledSetting)
      );
      Setting<Integer> needed = this.add(new NumberSetting<>(name + "Count", count, 1, 64).dependsOn(enabledSetting));
      return new BlockList(name, enabledSetting, blocks, needed);
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
      this.reportedChunks.clear();
   }

   @Override
   public String getInfo() {
      return this.markers.size() == 0 ? null : String.valueOf(this.markers.size());
   }

   /** Whether stash finds go to Argus's saved stash list. */
   public boolean saveStashes() {
      return this.stashes.saveStashes();
   }

   @EventHandler
   private void onChunkData(ClientChunkCacheReplaceWithPacketDataEvent event) {
      if (!this.isWorldUnavailable()) {
         this.stashes.onChunk(event.getChunk());
      }
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.markers.clear();
      this.reportedChunks.clear();
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      if (this.isWorldUnavailable() || this.reportedChunks.contains(HuntCompat.chunkKey(chunkPosition))) {
         return;
      }

      int levelMinY = chunk.getMinY();
      int minY = levelMinY + this.minYSetting.getValue();
      int maxY = chunk.getMaxY() - this.maxYSetting.getValue();
      DimensionType dimension = WorldUtils.getDimension();
      int[] counts = new int[this.lists.length];
      BlockPos[] firstMatch = new BlockPos[this.lists.length];
      LevelChunkSection[] sections = chunk.getSections();
      BlockPos spawner = null;
      boolean spawnerCompanions = false;

      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         LevelChunkSection section = sections[sectionIndex];
         if (section == null || section.hasOnlyAir()) {
            continue;
         }

         int sectionY = levelMinY + sectionIndex * 16;
         for (int y = 0; y < 16; y++) {
            int blockY = sectionY + y;
            if (blockY <= minY || blockY >= maxY) {
               continue;
            }

            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (state.isAir()) {
                     continue;
                  }

                  BlockPos position = new BlockPos(chunkPosition.getMinBlockX() + x, blockY, chunkPosition.getMinBlockZ() + z);
                  String special = this.specialReason(state, position, dimension, levelMinY);
                  if (special != null) {
                     this.found(chunkPosition, position, special);
                     return;
                  }

                  Block block = state.getBlock();
                  if (this.spawnerSetting.getValue()) {
                     if (block == Blocks.SPAWNER) {
                        spawner = position;
                     } else if (isSpawnerCompanion(block, dimension)) {
                        spawnerCompanions = true;
                     }
                  }

                  for (int listIndex = 0; listIndex < this.lists.length; listIndex++) {
                     BlockList list = this.lists[listIndex];
                     if (list.enabled.getValue() && list.blocks.getValue().contains(block)) {
                        if (firstMatch[listIndex] == null) {
                           firstMatch[listIndex] = position;
                        }

                        if (++counts[listIndex] >= list.needed.getValue()) {
                           this.found(chunkPosition, firstMatch[listIndex], counts[listIndex] + " " + list.name + " blocks");
                           return;
                        }
                     }
                  }
               }
            }
         }
      }

      if (spawner != null && !spawnerCompanions) {
         this.found(chunkPosition, spawner, "a spawner without its dungeon, mineshaft or fortress");
         return;
      }

      if (this.signsSetting.getValue()) {
         try {
            for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
               if (blockEntity instanceof SignBlockEntity sign && HuntCompat.signHasText(sign)) {
                  this.found(chunkPosition, sign.getBlockPos(), "a written sign");
                  return;
               }
            }
         } catch (ConcurrentModificationException changedWhileReading) {
            // The game thread changed this chunk's block entities mid-copy; the next load of the chunk checks again.
         }
      }
   }

   /** Blocks that generate around natural spawners: dungeons, mineshafts, geodes beside them, and fortresses. */
   private static boolean isSpawnerCompanion(Block block, DimensionType dimension) {
      return switch (dimension) {
         case OVERWORLD -> block == Blocks.MOSSY_COBBLESTONE || block == Blocks.COBWEB || block == Blocks.STONE_BRICK_STAIRS || block == Blocks.BUDDING_AMETHYST
            || block == Blocks.RAIL || block == Blocks.OAK_PLANKS || block == Blocks.DARK_OAK_PLANKS;
         case THE_NETHER -> block == Blocks.NETHER_BRICK_FENCE || block == Blocks.NETHER_BRICKS || block == Blocks.GOLD_BLOCK || block == Blocks.POLISHED_BLACKSTONE_BRICKS;
         case THE_END -> false;
      };
   }

   /** Things that never generate naturally, so one is enough to mark a base. */
   private String specialReason(BlockState state, BlockPos position, DimensionType dimension, int levelMinY) {
      Block block = state.getBlock();
      if (this.portalsSetting.getValue() && (block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL && dimension != DimensionType.THE_END)) {
         return "a lit portal";
      }

      if (this.bubblesSetting.getValue() && block == Blocks.BUBBLE_COLUMN && !state.getValue(BubbleColumnBlock.DRAG_DOWN)) {
         return "a soul sand bubble column";
      }

      int y = position.getY();
      if (this.skySetting.getValue() && dimension == DimensionType.OVERWORLD && y >= this.skyHeightSetting.getValue()) {
         return "a sky build";
      }

      if (this.bedrockSetting.getValue() && block == Blocks.BEDROCK) {
         if (dimension == DimensionType.OVERWORLD && y > levelMinY + 4) {
            return "misplaced bedrock";
         }

         if (dimension == DimensionType.THE_NETHER && y > 4 && y < 123 || dimension == DimensionType.THE_NETHER && y > 127) {
            return "misplaced bedrock";
         }
      }

      if (this.roofSetting.getValue() && dimension == DimensionType.THE_NETHER && y > 127 && block != Blocks.BEDROCK) {
         return "a Nether roof build";
      }

      return null;
   }

   private void found(ChunkPos chunkPosition, BlockPos position, String reason) {
      if (this.reportedChunks.add(HuntCompat.chunkKey(chunkPosition))) {
         double bottom = position.getY();
         double top = position.getY() + 1;
         if (this.columnSetting.getValue() && ClientAccess.argusclientMinecraft.level != null) {
            bottom = ClientAccess.argusclientMinecraft.level.getMinY();
            top = ClientAccess.argusclientMinecraft.level.getMaxY() + 1;
         }

         AABB box = new AABB(
            chunkPosition.getMinBlockX(), bottom, chunkPosition.getMinBlockZ(), chunkPosition.getMaxBlockX() + 1, top, chunkPosition.getMaxBlockZ() + 1
         );
         BlockPos center = new BlockPos(chunkPosition.getMiddleBlockX(), position.getY(), chunkPosition.getMiddleBlockZ());
         if (this.markers.report(center, box, "a possible base (" + reason + ")")) {
            this.discord.post(false, reason, center);
         }
      }
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || ++this.entityTicks < this.entityDelaySetting.getValue()) {
         return;
      }

      this.entityTicks = 0;
      this.markers.pruneFarFrom(ClientAccess.argusclientMinecraft.player.position());
      if (!this.entitiesSetting.getValue()) {
         return;
      }

      Map<Long, Map<EntityType<?>, Integer>> animalsByChunk = new HashMap<>();
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof Player) {
            continue;
         }

         ChunkPos chunkPosition = entity.chunkPosition();
         if (this.reportedChunks.contains(HuntCompat.chunkKey(chunkPosition))) {
            continue;
         }

         String reason = null;
         if (this.framesSetting.getValue() && entity instanceof ItemFrame && WorldUtils.getDimension() != DimensionType.THE_END) {
            // End ships carry an item frame with an elytra, so frames only count outside the End.
            reason = "an item frame";
         } else if (this.pearlsSetting.getValue() && HuntCompat.isThrownPearl(entity)) {
            reason = "an ender pearl";
         } else if (this.nameTagsSetting.getValue() && entity.hasCustomName() && !(entity instanceof ItemFrame)) {
            reason = "a name-tagged " + entity.getType().getDescription().getString();
         } else if (this.boatsSetting.getValue() && entity instanceof AbstractBoat) {
            reason = "a boat";
         } else if (this.villagersSetting.getValue() && entity instanceof Villager villager && villager.getVillagerData().level() > 1) {
            // Villagers only level up by trading, so a levelled one means someone has been here.
            reason = "a traded-with villager";
         } else if (this.clustersSetting.getValue() && this.clusterMobsSetting.getValue().contains(entity.getType())) {
            Map<EntityType<?>, Integer> counts = animalsByChunk.computeIfAbsent(HuntCompat.chunkKey(chunkPosition), unused -> new HashMap<>());
            int count = counts.merge(entity.getType(), 1, Integer::sum);
            if (count >= this.clusterSizeSetting.getValue()) {
               reason = count + " " + entity.getType().getDescription().getString() + " in one chunk";
            }
         }

         if (reason != null) {
            this.found(chunkPosition, entity.blockPosition(), reason);
         }
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }

   private record BlockList(String name, Setting<Boolean> enabled, Setting<java.util.Set<Block>> blocks, Setting<Integer> needed) {
   }
}

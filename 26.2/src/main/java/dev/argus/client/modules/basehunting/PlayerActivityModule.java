package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.inventory.PalettedContainerDataAccess;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.awt.Color;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;

/**
 * Spots chunks where a player is probably still active. A chunk section's palette only shrinks when the server saves
 * and reloads it, so a block type listed in the palette that no longer appears in the section was removed recently,
 * while the chunk stayed loaded by someone nearby. Ported from Trouser Streak's OnlinePlayerActivityDetector; ignores
 * blocks that disappear on their own (grass, leaves, water, fire and similar).
 */
public class PlayerActivityModule extends Module {
   private static final String[] OVERWORLD_NOISE = {
      "bedrock", "grass_block", "dirt", "snow_block", "blue_ice", "sand", "gravel", "suspicious_gravel", "diorite", "granite", "andesite",
      "tuff", "deepslate", "stone", "raw_iron_block", "budding_amethyst", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
      "waxed_weathered_copper_bulb", "waxed_oxidized_copper_bulb", "waxed_copper_block", "cobweb", "oak_fence", "dark_oak_fence", "rail",
      "sculk_vein", "sculk_sensor", "acacia_leaves", "birch_leaves", "oak_leaves", "spruce_leaves", "dark_oak_leaves", "cherry_leaves",
      "jungle_leaves", "cactus", "cave_vines", "sugar_cane", "tall_grass", "short_grass", "seagrass", "tall_seagrass", "vine", "fern",
      "large_fern", "kelp", "moss_block", "big_dripleaf", "big_dripleaf_stem", "glow_lichen", "brown_mushroom", "red_mushroom", "fire",
      "cave_air", "barrier", "air", "water", "lava", "bubble_column"
   };
   private static final String[] NETHER_NOISE = {
      "barrier", "air", "cave_air", "lava", "fire", "netherrack", "magma_block", "soul_sand", "soul_soil", "nether_brick_fence", "crimson_nylium",
      "crimson_roots", "weeping_vines_plant", "weeping_vines", "red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus"
   };
   private static final String[] END_NOISE = {"air", "barrier", "chorus_plant", "purpur_stairs"};
   private static final Set<Block> ORES = Set.of(HuntBlocks.blocks(
      "coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore", "iron_ore", "deepslate_iron_ore", "gold_ore", "deepslate_gold_ore",
      "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "redstone_ore", "deepslate_redstone_ore", "emerald_ore",
      "deepslate_emerald_ore", "nether_gold_ore", "nether_quartz_ore", "gilded_blackstone", "ancient_debris"
   ));
   private final Setting<Boolean> oresSetting = this.add(new BooleanSetting("CountOres", false));
   private final Setting<Boolean> listBlocksSetting = this.add(new BooleanSetting("ListBlocks", true));
   private final Setting<Set<Block>> overworldIgnoreSetting = this.add(new RegistrySetSetting<>("OverworldIgnore", BuiltInRegistries.BLOCK, HuntBlocks.blocks(OVERWORLD_NOISE)));
   private final Setting<Set<Block>> netherIgnoreSetting = this.add(new RegistrySetSetting<>("NetherIgnore", BuiltInRegistries.BLOCK, HuntBlocks.blocks(NETHER_NOISE)));
   private final Setting<Set<Block>> endIgnoreSetting = this.add(new RegistrySetSetting<>("EndIgnore", BuiltInRegistries.BLOCK, HuntBlocks.blocks(END_NOISE)));
   private final Setting<Boolean> sectionBoxSetting = this.add(new BooleanSetting("SectionBox", true));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(0, 220, 255, 45), new Color(0, 220, 255, 230), false);
   private final ChunkScanner chunkScanner = new ChunkScanner(1, this::scanChunk, null, null);
   private int ticks;

   public PlayerActivityModule() {
      super("PlayerActivity", "Spots chunks where a player is probably still active nearby.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onEnable() {
      this.chunkScanner.start();
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

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      DimensionType dimension = WorldUtils.getDimension();
      Set<Block> noise = switch (dimension) {
         case THE_NETHER -> this.netherIgnoreSetting.getValue();
         case THE_END -> this.endIgnoreSetting.getValue();
         default -> this.overworldIgnoreSetting.getValue();
      };
      LevelChunkSection[] sections = chunk.getSections();
      int minY = chunk.getMinY();

      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         PalettedContainer<BlockState> states = sections[sectionIndex].getStates();
         Palette<BlockState> palette = PalettedContainerDataAccess.getPalette(states);
         // Only small linear palettes are sent exactly as the server holds them.
         if (!(palette instanceof LinearPalette)) {
            return;
         }

         // A brand new chunk starts its bottom palette with air; those are generated, not lived in.
         if (sectionIndex == 0 && dimension != DimensionType.THE_END && palette.valueFor(0).isAir()) {
            return;
         }

         BitStorage storage = PalettedContainerDataAccess.getStorage(states);
         IntOpenHashSet used = new IntOpenHashSet();
         storage.getAll(used::add);
         Set<String> missing = new TreeSet<>();

         for (int index = 0; index < palette.getSize(); index++) {
            if (used.contains(index)) {
               continue;
            }

            Block block = palette.valueFor(index).getBlock();
            if (noise.contains(block) || !this.oresSetting.getValue() && ORES.contains(block)) {
               continue;
            }

            missing.add(block.getName().getString());
         }

         if (!missing.isEmpty()) {
            int y = minY + sectionIndex * 16 + 8;
            BlockPos center = new BlockPos(chunkPosition.getMiddleBlockX(), y, chunkPosition.getMiddleBlockZ());
            // The whole 16-block section the change is in, or a thin slab at its middle to keep the view clear.
            double half = this.sectionBoxSetting.getValue() ? 8.0 : 0.5;
            AABB box = new AABB(chunkPosition.getMinBlockX(), y - half, chunkPosition.getMinBlockZ(), chunkPosition.getMaxBlockX() + 1, y + half, chunkPosition.getMaxBlockZ() + 1);
            String what = this.listBlocksSetting.getValue() ? "recent player activity (removed " + String.join(", ", missing) + ")" : "recent player activity";
            this.markers.report(center, box, what);
            return;
         }
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }
}

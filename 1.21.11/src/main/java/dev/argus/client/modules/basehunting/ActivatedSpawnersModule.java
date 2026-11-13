package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.BaseSpawnerAccessor;
import java.awt.Color;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.phys.AABB;

/**
 * Finds mob spawners a player has been near. An untouched spawner keeps its starting delay of 20 ticks; once a player
 * comes within 16 blocks it starts counting, so any other delay means someone was there. Ported from Trouser Streak's
 * ActivatedSpawnerDetector, including trial spawners, spawners lit up with torches to stop them, rooms someone dug
 * into, storage left nearby, and the area the player must have stood in.
 */
public class ActivatedSpawnersModule extends Module {
   private static final Set<Block> GEODE = Set.of(
      Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD,
      Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD
   );
   private static final Set<Block> LIGHTS = Set.of(
      Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
      Blocks.JACK_O_LANTERN, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT, Blocks.SEA_LANTERN, Blocks.LANTERN, Blocks.SOUL_LANTERN,
      Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT
   );
   private final Setting<Boolean> dungeonsSetting = this.add(new BooleanSetting("Dungeons", true));
   private final Setting<Boolean> mineshaftsSetting = this.add(new BooleanSetting("Mineshafts", true));
   private final Setting<Boolean> strongholdsSetting = this.add(new BooleanSetting("Strongholds", true));
   private final Setting<Boolean> mansionsSetting = this.add(new BooleanSetting("Mansions", true));
   private final Setting<Boolean> fortressesSetting = this.add(new BooleanSetting("Fortresses", true));
   private final Setting<Boolean> bastionsSetting = this.add(new BooleanSetting("Bastions", true));
   private final Setting<Boolean> trialSetting = this.add(new BooleanSetting("TrialSpawners", true));
   private final Setting<Boolean> lightsSetting = this.add(new BooleanSetting("Torched", true).markVisibilityDependency());
   private final Setting<Integer> lightRadiusSetting = this.add(new NumberSetting<>("TorchRadius", 5, 1, 10).dependsOn(this.lightsSetting));
   private final Setting<Boolean> airSetting = this.add(new BooleanSetting("DugInto", true).markVisibilityDependency());
   private final Setting<Boolean> geodesSetting = this.add(new BooleanSetting("IgnoreGeodes", true).dependsOn(this.airSetting));
   private final Setting<Boolean> storageSetting = this.add(new BooleanSetting("StorageCheck", true).markVisibilityDependency());
   private final Setting<Integer> storageRadiusSetting = this.add(new NumberSetting<>("StorageRadius", 16, 4, 48).withUnit("m").dependsOn(this.storageSetting));
   private final Setting<Boolean> storageOnlySetting = this.add(new BooleanSetting("OnlyWithStorage", false).dependsOn(this.storageSetting));
   private final Setting<Boolean> rangeSetting = this.add(new BooleanSetting("ShowRange", false).markVisibilityDependency());
   private final Setting<Color> rangeColorSetting = this.add(new ColorSetting("RangeColor", new Color(255, 80, 80, 120)).dependsOn(this.rangeSetting));
   private final Set<BlockPos> checked = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 40, 40, 50), new Color(255, 40, 40, 230), true);
   private int ticks;

   public ActivatedSpawnersModule() {
      super("ActivatedSpawners", "Finds mob spawners a player has been near, which can lead to stashes in dungeons and mineshafts.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onDisable() {
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

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || ++this.ticks < 10) {
         return;
      }

      this.ticks = 0;
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      DimensionType dimension = WorldUtils.getDimension();
      this.markers.pruneFarFrom(ClientAccess.argusclientMinecraft.player.position());

      for (BlockEntity blockEntity : WorldUtils.getLoadedBlockEntities()) {
         BlockPos position = blockEntity.getBlockPos();
         if (this.markers.contains(position)) {
            continue;
         }

         if (blockEntity instanceof SpawnerBlockEntity spawner) {
            int delay = ((BaseSpawnerAccessor)spawner.getSpawner()).getSpawnDelay();
            // Nether spawners can sit at 0 without anyone nearby, so only count other values there.
            boolean untouched = delay == 20 || dimension == DimensionType.THE_NETHER && delay == 0;
            String structure = this.structure(level, position, dimension);
            if (structure == null) {
               continue;
            }

            String what;
            if (!untouched) {
               what = "an activated " + structure + " spawner";
            } else if (this.airSetting.getValue() && this.checked.add(position.immutable()) && this.dugInto(level, position)) {
               // The delay resets when the chunk reloads, but cave air mixed with plain air means someone dug in.
               what = "a " + structure + " spawner whose room was dug into";
            } else {
               continue;
            }

            if (this.lightsSetting.getValue() && this.hasLightNearby(level, position)) {
               what = what + " that was lit up to stop it";
            }

            if (this.storageSetting.getValue()) {
               int storage = this.storageNearby(position);
               if (storage > 0) {
                  what = what + " with " + storage + (storage == 1 ? " storage block" : " storage blocks") + " nearby";
               } else if (this.storageOnlySetting.getValue()) {
                  continue;
               }
            }

            this.markers.report(position, new AABB(position), what);
         } else if (this.trialSetting.getValue() && blockEntity instanceof TrialSpawnerBlockEntity trialSpawner) {
            TrialSpawnerState state = trialSpawner.getState();
            if (state != TrialSpawnerState.WAITING_FOR_PLAYERS && state != TrialSpawnerState.INACTIVE) {
               this.markers.report(position, new AABB(position), "a used trial spawner");
            }
         }
      }
   }

   /** Names the structure from the block under the spawner, or null when that kind is switched off. */
   private String structure(ClientLevel level, BlockPos position, DimensionType dimension) {
      Block below = level.getBlockState(position.below()).getBlock();
      if (dimension == DimensionType.THE_NETHER) {
         if (below == Blocks.NETHER_BRICKS || below == Blocks.NETHER_BRICK_FENCE || below == Blocks.NETHER_BRICK_STAIRS) {
            return this.fortressesSetting.getValue() ? "fortress" : null;
         }

         return this.bastionsSetting.getValue() ? "bastion" : null;
      }

      if (below == Blocks.BIRCH_PLANKS) {
         return this.mansionsSetting.getValue() ? "woodland mansion" : null;
      }

      if (below == Blocks.MOSSY_COBBLESTONE || below == Blocks.COBBLESTONE) {
         return this.dungeonsSetting.getValue() ? "dungeon" : null;
      }

      if (below == Blocks.STONE_BRICKS || below == Blocks.MOSSY_STONE_BRICKS || below == Blocks.CRACKED_STONE_BRICKS) {
         return this.strongholdsSetting.getValue() ? "stronghold" : null;
      }

      for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-2, -1, -2), position.offset(2, 1, 2))) {
         if (level.getBlockState(nearby).getBlock() == Blocks.COBWEB) {
            return this.mineshaftsSetting.getValue() ? "mineshaft" : null;
         }
      }

      return this.dungeonsSetting.getValue() ? "mob" : null;
   }

   /** Plain air next to cave air in a spawner room: generated rooms hold only cave air, so someone opened it up. */
   private boolean dugInto(ClientLevel level, BlockPos position) {
      if (this.geodesSetting.getValue()) {
         for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-5, -5, -5), position.offset(5, 5, 5))) {
            if (GEODE.contains(level.getBlockState(nearby).getBlock())) {
               return false;
            }
         }
      }

      boolean air = false;
      boolean caveAir = false;
      for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-3, -1, -3), position.offset(3, 3, 3))) {
         Block block = level.getBlockState(nearby).getBlock();
         air |= block == Blocks.AIR;
         caveAir |= block == Blocks.CAVE_AIR;
         if (air && caveAir) {
            return position.getY() < level.getSeaLevel() - 8;
         }
      }

      return false;
   }

   private int storageNearby(BlockPos position) {
      int radius = this.storageRadiusSetting.getValue();
      double radiusSquared = (double)radius * radius;
      int count = 0;
      int plainChests = 0;
      for (BlockEntity blockEntity : WorldUtils.getLoadedBlockEntities()) {
         if (HuntBlocks.isStorage(blockEntity) && blockEntity.getBlockPos().distSqr(position) <= radiusSquared) {
            if (blockEntity.getBlockState().is(Blocks.CHEST)) {
               plainChests++;
            } else {
               count++;
            }
         }
      }

      // Dungeons generate up to two chests of their own, so only more than that counts.
      return count + (plainChests > 2 ? plainChests : 0);
   }

   private boolean hasLightNearby(ClientLevel level, BlockPos position) {
      int radius = this.lightRadiusSetting.getValue();
      for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-radius, -radius, -radius), position.offset(radius, radius, radius))) {
         if (LIGHTS.contains(level.getBlockState(nearby).getBlock())) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
      if (this.rangeSetting.getValue() && !this.isWorldUnavailable()) {
         // A spawner only runs with a player within 16 blocks, so this is where someone stood.
         Color color = this.rangeColorSetting.getValue();
         for (BlockPos position : this.markers.keys()) {
            WorldBoxRenderer.outlineBox(event.getPoseStack(), new AABB(position).inflate(16.0), color, 1.0F);
         }
      }
   }
}

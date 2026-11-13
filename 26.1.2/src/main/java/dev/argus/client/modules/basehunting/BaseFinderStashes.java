package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.misc.StashEntry;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.WorldUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * BaseFinder's stash checks, which were the separate StashFinder module: chunks with many chests, any shulker box, a
 * pile of other storage, stacked chest minecarts or chested donkeys and llamas. Storage you placed yourself is ignored,
 * and so, optionally, is loot storage that generated in structures. Finds are also saved to Argus's stash list.
 */
final class BaseFinderStashes {
   private final Setting<Boolean> enabledSetting;
   private final Setting<Boolean> chestsSetting;
   private final Setting<Integer> chestCountSetting;
   private final Setting<Boolean> shulkersSetting;
   private final Setting<Boolean> storageSetting;
   private final Setting<Integer> storageCountSetting;
   private final Setting<Boolean> cartsSetting;
   private final Setting<Integer> cartCountSetting;
   private final Setting<Boolean> chestedSetting;
   private final Setting<Boolean> ignoreNaturalSetting;
   private final Setting<Boolean> dungeonSetting;
   private final Setting<Boolean> fortressSetting;
   private final Setting<Boolean> bastionSetting;
   private final Setting<Boolean> ancientCitySetting;
   private final Setting<Boolean> trialChambersSetting;
   private final Setting<Boolean> saveSetting;
   private final HuntMarkers markers;
   private final HuntDiscordAlerts discord;

   BaseFinderStashes(Module module, HuntMarkers markers, HuntDiscordAlerts discord) {
      this.markers = markers;
      this.discord = discord;
      this.enabledSetting = module.add(new BooleanSetting("Stashes", true).markAsGroup());
      this.chestsSetting = module.add(new BooleanSetting("StashChests", true).markVisibilityDependency().dependsOn(this.enabledSetting));
      this.chestCountSetting = module.add(new NumberSetting<>("StashChestCount", 5, 2, 64).dependsOn(this.enabledSetting, this.chestsSetting));
      this.shulkersSetting = module.add(new BooleanSetting("StashShulkers", true).dependsOn(this.enabledSetting));
      this.storageSetting = module.add(new BooleanSetting("StashStorage", true).markVisibilityDependency().dependsOn(this.enabledSetting));
      this.storageCountSetting = module.add(new NumberSetting<>("StashStorageCount", 8, 2, 128).dependsOn(this.enabledSetting, this.storageSetting));
      this.cartsSetting = module.add(new BooleanSetting("StackedCarts", true).markVisibilityDependency().dependsOn(this.enabledSetting));
      this.cartCountSetting = module.add(new NumberSetting<>("StackedCartCount", 2, 2, 8).dependsOn(this.enabledSetting, this.cartsSetting));
      this.chestedSetting = module.add(new BooleanSetting("ChestedMounts", false).dependsOn(this.enabledSetting));
      this.ignoreNaturalSetting = module.add(new BooleanSetting("IgnoreNatural", true).markVisibilityDependency().dependsOn(this.enabledSetting));
      this.dungeonSetting = module.add(new BooleanSetting("NaturalDungeon", true).dependsOn(this.enabledSetting, this.ignoreNaturalSetting));
      this.fortressSetting = module.add(new BooleanSetting("NaturalFortress", true).dependsOn(this.enabledSetting, this.ignoreNaturalSetting));
      this.bastionSetting = module.add(new BooleanSetting("NaturalBastion", true).dependsOn(this.enabledSetting, this.ignoreNaturalSetting));
      this.ancientCitySetting = module.add(new BooleanSetting("NaturalAncientCity", true).dependsOn(this.enabledSetting, this.ignoreNaturalSetting));
      this.trialChambersSetting = module.add(new BooleanSetting("NaturalTrialChambers", true).dependsOn(this.enabledSetting, this.ignoreNaturalSetting));
      this.saveSetting = module.add(new BooleanSetting("SaveStashes", true).dependsOn(this.enabledSetting));
   }

   boolean saveStashes() {
      return this.saveSetting.getValue();
   }

   private boolean isNatural(LevelChunk chunk, BlockPos position) {
      return this.ignoreNaturalSetting.getValue()
         && WorldUtils.isStructureChest(
            chunk,
            position,
            this.fortressSetting.getValue(),
            this.bastionSetting.getValue(),
            this.ancientCitySetting.getValue(),
            this.dungeonSetting.getValue(),
            this.trialChambersSetting.getValue()
         );
   }

   /** Checks a chunk the server just sent; runs on the game thread. */
   void onChunk(LevelChunk chunk) {
      if (!this.enabledSetting.getValue() || ClientAccess.argusclientMinecraft.level == null) {
         return;
      }

      ChunkPos chunkPosition = chunk.getPos();
      int chests = 0;
      int storage = 0;
      BlockPos firstChest = null;
      BlockPos firstStorage = null;
      List<BlockEntity> blockEntities;
      try {
         blockEntities = List.copyOf(chunk.getBlockEntities().values());
      } catch (ConcurrentModificationException changedWhileReading) {
         return;
      }

      for (BlockEntity blockEntity : blockEntities) {
         BlockPos position = blockEntity.getBlockPos();
         if (!HuntBlocks.isStorage(blockEntity) || ClientContext.worldStateManager.hasKnownPlacement(position) || this.isNatural(chunk, position)) {
            continue;
         }

         if (blockEntity instanceof ShulkerBoxBlockEntity && this.shulkersSetting.getValue()) {
            this.found(chunkPosition, position, "a shulker box");
            return;
         }

         if (blockEntity instanceof ChestBlockEntity && blockEntity.getBlockState().is(Blocks.CHEST)) {
            chests++;
            firstChest = firstChest == null ? position : firstChest;
         } else {
            storage++;
            firstStorage = firstStorage == null ? position : firstStorage;
         }
      }

      if (this.chestsSetting.getValue() && chests >= this.chestCountSetting.getValue()) {
         this.found(chunkPosition, firstChest, chests + " chests (" + StashReportModule.formatDubs(chests) + " dubs)");
         return;
      }

      if (this.storageSetting.getValue() && chests + storage >= this.storageCountSetting.getValue()) {
         this.found(chunkPosition, firstStorage != null ? firstStorage : firstChest, chests + storage + " storage blocks");
         return;
      }

      if (this.cartsSetting.getValue() || this.chestedSetting.getValue()) {
         // Entities arrive a little after the chunk itself, so look for them shortly.
         ClientContext.taskScheduler.scheduleRotationTask(() -> this.checkEntities(chunkPosition), 15);
      }
   }

   private void checkEntities(ChunkPos chunkPosition) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null) {
         return;
      }

      List<MinecartChest> carts = new ArrayList<>();
      int stacked = 0;
      for (Entity entity : level.entitiesForRendering()) {
         if (!entity.chunkPosition().equals(chunkPosition)) {
            continue;
         }

         if (entity instanceof MinecartChest cart) {
            for (MinecartChest previous : carts) {
               if (cart.getBoundingBox().intersects(previous.getBoundingBox())) {
                  stacked++;
                  break;
               }
            }

            carts.add(cart);
         } else if (this.chestedSetting.getValue() && entity instanceof AbstractChestedHorse horse && horse.hasChest()) {
            this.found(chunkPosition, horse.blockPosition(), "a " + horse.getType().getDescription().getString() + " carrying a chest");
            return;
         }
      }

      if (this.cartsSetting.getValue() && stacked + 1 >= this.cartCountSetting.getValue() && stacked > 0) {
         this.found(chunkPosition, carts.get(0).blockPosition(), stacked + 1 + " stacked chest minecarts");
      }
   }

   private void found(ChunkPos chunkPosition, BlockPos position, String what) {
      if (!this.markers.report(position, new AABB(position).inflate(0.5), "a stash: " + what)) {
         return;
      }

      this.discord.post(true, what, position);

      if (this.saveSetting.getValue() && ClientContext.stashRegistry != null) {
         String time = new SimpleDateFormat("MM/dd HH:mm").format(new Date());
         StashEntry entry = new StashEntry(
            what + " (" + time + ")", chunkPosition.getMiddleBlockX(), chunkPosition.getMiddleBlockZ(), WorldUtils.getDimension().name(), HuntCompat.serverKey()
         );
         boolean known = ClientContext.stashRegistry.findFirst(stash -> stash.getX() == entry.getX() && stash.getZ() == entry.getZ()).isPresent();
         if (!known) {
            ClientContext.stashRegistry.registerEntry(entry);
         }
      }
   }
}

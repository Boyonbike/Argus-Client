package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.world.WorldUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public class StashFinderModule extends Module {
   public final Setting<Boolean> logSetting;
   public final Setting<Boolean> xaeroSetting;
   private final Setting<Boolean> notifySetting;
   private final Setting<Boolean> censorCoordsSetting;
   private final Setting<Boolean> soundSetting;
   private final Setting<NamedIdentifier> typeSetting;
   private final Setting<Float> volumeSetting;
   private final Setting<Boolean> ignoreNaturalSetting;
   private final Setting<Boolean> dungeonSetting;
   private final Setting<Boolean> fortressSetting;
   private final Setting<Boolean> bastionSetting;
   private final Setting<Boolean> ancientCitySetting;
   private final Setting<Boolean> trialChambersSetting;
   private final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> shulkersSetting;
   private final Setting<Boolean> chestEntitiesSetting;
   private final Setting<Boolean> chestsSetting;
   private final Setting<Integer> chestCountSetting;
   private final Setting<Boolean> stackedCartsSetting;
   private final Setting<Integer> minimumSetting;
   private final ElapsedTimer soundTimer;

   private boolean isNaturalStorage(BlockPos position) {
      ChunkAccess chunkSnapshot = ClientAccess.argusclientMinecraft.level.getChunk(position);
      Setting<Boolean> fortressOption = this.fortressSetting;
      boolean fortressSnapshot = fortressOption.getValue();
      Setting<Boolean> bastionOption = this.bastionSetting;
      boolean bastionSnapshot = bastionOption.getValue();
      Setting<Boolean> ancientCityOption = this.ancientCitySetting;
      boolean ancientCitySnapshot = ancientCityOption.getValue();
      Setting<Boolean> dungeonOption = this.dungeonSetting;
      boolean dungeonSnapshot = dungeonOption.getValue();
      Setting<Boolean> trialChambersOption = this.trialChambersSetting;
      boolean trialChambers = trialChambersOption.getValue();
      BlockPos storagePosition = position;
      return WorldUtils.isStructureChest(chunkSnapshot, storagePosition, fortressSnapshot, bastionSnapshot, ancientCitySnapshot, dungeonSnapshot, trialChambers);
   }

   private boolean hasChestEntity(ChunkPos chunk) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof AbstractChestedHorse horse && horse.hasChest() && horse.chunkPosition().equals(chunk)) {
            return true;
         }
      }

      return false;
   }

   private String appendTimestamp(String description) {
      return description + " (" + new SimpleDateFormat("MM/dd HH:mm a").format(new Date()) + ")";
   }

   @EventHandler
   private void onClientChunkCacheReplaceWithPacketDataEvent(ClientChunkCacheReplaceWithPacketDataEvent event) {
      LevelChunk chunk = event.getChunk();
      ChunkPos chunkPosition = chunk.getPos();
      int chestCount = 0;

      for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
         Setting<Boolean> ignoreNaturalOption = this.ignoreNaturalSetting;
         Boolean ignoreNatural = ignoreNaturalOption.getValue();
         if (ignoreNatural) {
            BlockPos naturalPositionSnapshot = blockEntity.getBlockPos();
            if (this.isNaturalStorage(naturalPositionSnapshot)) {
               continue;
            }
         }

         if (blockEntity instanceof ChestBlockEntity) {
            WorldStateManager chestTrackerSnapshot = ClientContext.worldStateManager;
            BlockPos chestPositionSnapshot = blockEntity.getBlockPos();
            if (!chestTrackerSnapshot.hasKnownPlacement(chestPositionSnapshot)) {
               chestCount++;
            }
         }

         Setting<Boolean> chestsOption = this.chestsSetting;
         Boolean findChests = chestsOption.getValue();
         if (findChests) {
            Setting<Integer> chestCountOption = this.chestCountSetting;
            Integer requiredChestCount = chestCountOption.getValue();
            if (chestCount >= requiredChestCount) {
               String chestDescription = chestCount + " chests";
               String chestTimestamp = this.appendTimestamp(chestDescription);
               StashEntry chestStash = this.createStash(chestTimestamp, chunkPosition);
               if (this.registerStash(chestStash, chestDescription)) {
                  break;
               }
               continue;
            }
         }

         if (blockEntity instanceof ShulkerBoxBlockEntity shulker) {
            Setting<Boolean> shulkersOption = this.shulkersSetting;
            Boolean findShulkers = shulkersOption.getValue();
            if (findShulkers) {
               WorldStateManager shulkerTrackerSnapshot = ClientContext.worldStateManager;
               BlockPos shulkerPositionSnapshot = shulker.getBlockPos();
               if (!shulkerTrackerSnapshot.hasKnownPlacement(shulkerPositionSnapshot)) {
                  String shulkerDescription = "a shulkerbox";
                  String shulkerTimestamp = this.appendTimestamp(shulkerDescription);
                  StashEntry shulkerStash = this.createStash(shulkerTimestamp, chunkPosition);
                  if (this.registerStash(shulkerStash, shulkerDescription)) {
                     break;
                  }
               }
            }
         }
      }

      Setting<Boolean> stackedCartsOption = this.stackedCartsSetting;
      Boolean findStackedCarts = stackedCartsOption.getValue();
      if (findStackedCarts) {
         TaskScheduler deferredExecutor = ClientContext.taskScheduler;
         Runnable scanEntities = () -> {
            int stackedMinecartCount = this.countStackedMinecarts(chunkPosition);
            Setting<Integer> minimumOption = this.minimumSetting;
            Integer requiredMinecarts = minimumOption.getValue();
            if (stackedMinecartCount >= requiredMinecarts) {
               String minecartTimestamp = this.appendTimestamp("Stacked minecarts");
               StashEntry minecartStash = this.createStash(minecartTimestamp, chunkPosition);
               this.registerStash(minecartStash, "Stacked minecarts");
            }

            Setting<Boolean> chestEntitiesOption = this.chestEntitiesSetting;
            Boolean findChestEntities = chestEntitiesOption.getValue();
            if (findChestEntities && this.hasChestEntity(chunkPosition)) {
               String entityTimestamp = this.appendTimestamp("Entity with chest");
               StashEntry entityStash = this.createStash(entityTimestamp, chunkPosition);
               this.registerStash(entityStash, "Entity with chest");
            }
         };
         deferredExecutor.scheduleRotationTask(scanEntities, 15);
      }
   }

   public StashEntry createStash(String description, ChunkPos chunk) {
      DimensionType dimensionOption = WorldUtils.getDimension();
      String dimension = dimensionOption.name();
      ClientPacketListener serverCheckConnection = ClientAccess.argusclientMinecraft.player.connection;
      String serverAddress;
      if (serverCheckConnection.getServerData() == null) {
         serverAddress = "singleplayer";
      } else {
         ClientPacketListener serverReadConnection = ClientAccess.argusclientMinecraft.player.connection;
         serverAddress = serverReadConnection.getServerData().ip;
      }

      String server = serverAddress;
      int middleBlockX = chunk.getMiddleBlockX();
      return new StashEntry(description, middleBlockX, chunk.getMiddleBlockZ(), dimension, server);
   }

   protected boolean registerStash(StashEntry stash, String description) {
      Optional existingStash = ClientContext.stashRegistry.findFirst(candidate -> {
         double candidateCoordinate = candidate.getX();
         if (candidateCoordinate == stash.getX()) {
            candidateCoordinate = candidate.getZ();
            if (candidateCoordinate == stash.getZ()) {
               return true;
            }
         }

         return false;
      });
      if (existingStash.isEmpty() && ClientContext.stashRegistry.registerEntry(stash)) {
         Setting<Boolean> xaeroOption = this.xaeroSetting;
         Boolean addXaeroWaypoint = xaeroOption.getValue();
         if (addXaeroWaypoint && ClientContext.waypointAdapter.isAvailable()) {
            String waypointName = "Stash " + stash.getName();
            int waypointX = (int)stash.getX();
            BlockPos waypointPosition = new BlockPos(waypointX, 64, (int)stash.getZ());
            ClientContext.waypointAdapter.addWaypoint(waypointName, waypointPosition);
         }

         Setting<Boolean> notifyOption = this.notifySetting;
         Boolean notify = notifyOption.getValue();
         if (notify) {
            String moduleName = this.getName();
            MutableComponent moduleNameComponent = Component.literal(moduleName);
            String foundDescription = " has found " + description + " at: ";
            MutableComponent notification = moduleNameComponent.append(foundDescription);
            Setting<Boolean> censorCoordsOption = this.censorCoordsSetting;
            Boolean censorCoordinates = censorCoordsOption.getValue();
            if (!censorCoordinates) {
               Object[] highlightFormatArguments = new Object[]{CommandRegistry.getPrefix(), null, null};
               int highlightX = (int)stash.getX();
               highlightFormatArguments[1] = highlightX;
               int highlightZ = (int)stash.getZ();
               highlightFormatArguments[2] = highlightZ;
               String highlightCommand = "%shighlight %d ~ %d".formatted(highlightFormatArguments);
               SuggestCommand highlightClick = new SuggestCommand(highlightCommand);
               String coordinateString = stash.formatCoordinates();
               MutableComponent coordinates = ChatUtils.watermarkText(coordinateString).withStyle(style -> style.withClickEvent(highlightClick));
               notification.append(coordinates);
            } else {
               notification.append(ChatUtils.watermarkText("### ### ###"));
            }

            ChatUtils.printChat(notification, ChatUtils.createSignature(-13579), ChatUtils.MessagePriority.LOW);
         }

         Setting<Boolean> soundOption = this.soundSetting;
         Boolean playSound = soundOption.getValue();
         if (playSound) {
            ElapsedTimer timerSnapshot = this.soundTimer;
            TimeUnit timeoutUnit = TimeUnit.SECONDS;
            ElapsedTimer elapsedTimer = timerSnapshot;
            if (elapsedTimer.hasElapsed(5.0, timeoutUnit)) {
               SoundManager soundManagerSnapshot = ClientContext.soundManager;
               Setting<NamedIdentifier> soundTypeOption = this.typeSetting;
               NamedIdentifier soundTypeSnapshot = soundTypeOption.getValue();
               Setting<Float> volumeOption = this.volumeSetting;
               float soundVolume = volumeOption.getValue();
               soundManagerSnapshot.playSound(soundTypeSnapshot, soundVolume);
               timerSnapshot = this.soundTimer;
               timerSnapshot.reset();
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private int countStackedMinecarts(ChunkPos chunk) {
      int overlappingCount = 0;
      ArrayList<MinecartChest> seenMinecarts = new ArrayList<>();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof MinecartChest minecart && minecart.chunkPosition().equals(chunk)) {
            for (MinecartChest previousMinecart : seenMinecarts) {
               AABB boundsSnapshot = minecart.getBoundingBox();
               AABB previousBounds = previousMinecart.getBoundingBox();
               if (boundsSnapshot.intersects(previousBounds)) {
                  overlappingCount++;
                  break;
               }
            }

            seenMinecarts.add(minecart);
         }
      }

      return overlappingCount;
   }

   public StashFinderModule() {
      super("StashFinder", "Logs and/or notifies you about possible stashes.", ModuleCategory.MISC);
      Boolean logDefault = true;
      this.logSetting = this.add(new BooleanSetting("Log", logDefault));
      Boolean xaeroDefault = false;
      this.xaeroSetting = this.add(new BooleanSetting("Xaero", xaeroDefault));
      Boolean notifyDefault = true;
      BooleanSetting notifyDefinition = new BooleanSetting("Notify", notifyDefault);
      notifyDefinition.markVisibilityDependency();
      this.notifySetting = this.add(notifyDefinition);
      Boolean censorDefault = false;
      BooleanSetting censorDefinition = new BooleanSetting("CensorCoords", censorDefault);
      this.censorCoordsSetting = this.add(censorDefinition.dependsOn(this.notifySetting));
      Boolean soundDefault = true;
      BooleanSetting soundDefinition = new BooleanSetting("Sound", soundDefault);
      soundDefinition.markVisibilityDependency();
      this.soundSetting = this.add(soundDefinition);
      BuiltinSound warningSound = BuiltinSound.WARNING;
      NamedIdentifier defaultSound = warningSound.getIdentifier();
      IdentifierSetting typeDefinition = new IdentifierSetting("Type", defaultSound);
      this.typeSetting = this.add(typeDefinition.dependsOn(this.soundSetting));
      NumberSetting<Float> volumeDefinition = new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F);
      this.volumeSetting = this.add(volumeDefinition.dependsOn(this.soundSetting));
      Boolean ignoreNaturalDefault = false;
      BooleanSetting ignoreNaturalDefinition = new BooleanSetting("IgnoreNatural", ignoreNaturalDefault);
      ignoreNaturalDefinition.markVisibilityDependency();
      this.ignoreNaturalSetting = this.add(ignoreNaturalDefinition);
      Boolean dungeonDefault = true;
      BooleanSetting dungeonDefinition = new BooleanSetting("Dungeon", dungeonDefault);
      this.dungeonSetting = this.add(dungeonDefinition.dependsOn(this.ignoreNaturalSetting));
      Boolean fortressDefault = true;
      BooleanSetting fortressDefinition = new BooleanSetting("Fortress", fortressDefault);
      this.fortressSetting = this.add(fortressDefinition.dependsOn(this.ignoreNaturalSetting));
      Boolean bastionDefault = true;
      BooleanSetting bastionDefinition = new BooleanSetting("Bastion", bastionDefault);
      this.bastionSetting = this.add(bastionDefinition.dependsOn(this.ignoreNaturalSetting));
      Boolean ancientCityDefault = false;
      BooleanSetting ancientCityDefinition = new BooleanSetting("AncientCity", ancientCityDefault);
      this.ancientCitySetting = this.add(ancientCityDefinition.dependsOn(this.ignoreNaturalSetting));
      Boolean trialChambersDefault = true;
      BooleanSetting trialChambersDefinition = new BooleanSetting("TrialChambers", trialChambersDefault);
      this.trialChambersSetting = this.add(trialChambersDefinition.dependsOn(this.ignoreNaturalSetting));
      Boolean targetsDefault = false;
      BooleanSetting targetsDefinition = new BooleanSetting("Targets", targetsDefault);
      targetsDefinition.markAsGroup();
      targetsDefinition.markVisibilityDependency();
      this.targetsSetting = this.add(targetsDefinition);
      Boolean shulkersDefault = true;
      BooleanSetting shulkersDefinition = new BooleanSetting("Shulkers", shulkersDefault);
      this.shulkersSetting = this.add(shulkersDefinition.dependsOn(this.targetsSetting));
      Boolean chestEntitiesDefault = false;
      BooleanSetting chestEntitiesDefinition = new BooleanSetting("ChestEntities", chestEntitiesDefault);
      this.chestEntitiesSetting = this.add(chestEntitiesDefinition.dependsOn(this.targetsSetting));
      Boolean chestsDefault = true;
      BooleanSetting chestsDefinition = new BooleanSetting("Chests", chestsDefault);
      chestsDefinition.markVisibilityDependency();
      this.chestsSetting = this.add(chestsDefinition.dependsOn(this.targetsSetting));
      NumberSetting<Integer> chestCountDefinition = new NumberSetting<>("ChestCount", 5, 2, 20);
      this.chestCountSetting = this.add(chestCountDefinition.dependsOn(this.targetsSetting, this.chestsSetting));
      Boolean stackedCartsDefault = true;
      BooleanSetting stackedCartsDefinition = new BooleanSetting("StackedCarts", stackedCartsDefault);
      stackedCartsDefinition.markVisibilityDependency();
      this.stackedCartsSetting = this.add(stackedCartsDefinition.dependsOn(this.targetsSetting));
      NumberSetting<Integer> minimumDefinition = new NumberSetting<>("Minimum", 2, 2, 8);
      this.minimumSetting = this.add(minimumDefinition.dependsOn(this.targetsSetting, this.stackedCartsSetting));
      this.soundTimer = new ElapsedTimer();
      if (!ClientContext.waypointAdapter.isAvailable()) {
         this.unregister(this.xaeroSetting);
      }

      this.setDrawn(false);
   }
}

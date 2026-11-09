package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.BlockColorRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.inventory.EquipmentUtils;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SearchModule extends Module {
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   private static final int MAX_CACHED_BLOCKS = 100000;
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 512.0F, 0.0F, 512.0F).withUnit("m"));
   private final Setting<Boolean> blocksSetting = this.add(new BooleanSetting("Blocks", true).markVisibilityDependency());
   private final Setting<Set<Block>> selectedBlocksSetting = this.add(
      new RegistrySetSetting<>("Selected", BuiltInRegistries.BLOCK, Blocks.NETHER_PORTAL, Blocks.ENDER_CHEST, Blocks.END_PORTAL).dependsOn(this.blocksSetting)
   );
   private final Setting<SearchModule.HeightChoice> heightSetting = this.add(
      new EnumSetting<>("Height", SearchModule.HeightChoice.NEW).dependsOn(this.blocksSetting)
   );
   private final Setting<Boolean> onlyOminousSetting = this.add(new BooleanSetting("OnlyOminous", false).dependsOn(this.blocksSetting));
   private final Setting<Boolean> ignoreNaturalSetting = this.add(
      new BooleanSetting("IgnoreNatural", false).markVisibilityDependency().dependsOn(this.blocksSetting)
   );
   private final Setting<Boolean> dungeonSetting = this.add(new BooleanSetting("Dungeon", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
   private final Setting<Boolean> fortressSetting = this.add(new BooleanSetting("Fortress", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
   private final Setting<Boolean> bastionSetting = this.add(new BooleanSetting("Bastion", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting));
   private final Setting<Boolean> ancientCitySetting = this.add(
      new BooleanSetting("AncientCity", false).dependsOn(this.blocksSetting, this.ignoreNaturalSetting)
   );
   private final Setting<Boolean> trialChambersSetting = this.add(
      new BooleanSetting("TrialChambers", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting)
   );
   private final Setting<Boolean> entitiesSetting = this.add(new BooleanSetting("Entities", false).markVisibilityDependency());
   private final Setting<Set<EntityType<?>>> selectedEntitiesSetting = this.add(
      new RegistrySetSetting<>("Selected", BuiltInRegistries.ENTITY_TYPE).dependsOn(this.entitiesSetting)
   );
   private final Setting<Boolean> ignoreVehiclesSetting = this.add(new BooleanSetting("IgnoreVehicles", false).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.entitiesSetting));
   private final Setting<Boolean> soundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency());
   private final Setting<NamedIdentifier> soundTypeSetting = this.add(
      new IdentifierSetting("Type", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.soundSetting)
   );
   private final Setting<Float> soundVolumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.soundSetting));
   private final Setting<Boolean> renderSetting = this.add(new BooleanSetting("Render", false).markAsGroup());
   private final Setting<Integer> fillSetting = this.add(new NumberSetting<>("Fill", 0, 0, 100).withUnit("%").dependsOn(this.renderSetting));
   private final Setting<Integer> outlineSetting = this.add(new NumberSetting<>("Outline", 100, 0, 100).withUnit("%").dependsOn(this.renderSetting));
   private final Setting<Integer> tracersSetting = this.add(new NumberSetting<>("Tracers", 50, 0, 100).withUnit("%").dependsOn(this.renderSetting));
   private final Map<BlockPos, SearchModule.SearchResult> blocksByPosition = Collections.synchronizedMap(new HashMap<>());
   private final ElapsedTimer soundTimer = new ElapsedTimer();
   private final ChunkScanner chunkScanner;
   private final AtomicBoolean alertPending = new AtomicBoolean();
   private volatile ScanContext scanContext;

   private boolean shouldSearchEntity(Entity entity) {
      if (entity == ClientAccess.argusclientMinecraft.player) {
         return false;
      }

      Set<?> selectedEntities = this.selectedEntitiesSetting.getValue();
      if (!selectedEntities.contains(entity.getType())) {
         return false;
      }

      Boolean ignoreVehicles = this.ignoreVehiclesSetting.getValue();
      if (ignoreVehicles && entity.isVehicle()) {
         return false;
      }

      if (entity instanceof Player player && this.ignoreNakedsSetting.getValue()) {
         boolean wearingArmor = false;

         for (ItemStack armorPiece : EquipmentUtils.getArmorStacks(player)) {
            if (!armorPiece.isEmpty()) {
               wearingArmor = true;
               break;
            }
         }

         if (!wearingArmor) {
            return false;
         }
      }

      if (!this.rangeSetting.isAtMaximum()) {
         float range = this.rangeSetting.getValue();
         if (this.isOutsideRange(entity.blockPosition(), range * range)) {
            return false;
         }
      }

      return true;
   }

   private static Color getBlockColor(ClientLevel level, BlockState state) {
      BlockColorRegistry blockColors = ClientContext.blockColorRegistry;
      Block block = state.getBlock();
      Color configuredColor = blockColors.getColor(block);
      if (configuredColor != null) {
         return configuredColor;
      }

      if (state.getBlock() == Blocks.NETHER_PORTAL) {
         return new Color(128, 0, 255);
      }

      if (state.getBlock() == Blocks.ENDER_CHEST) {
         return new Color(64, 32, 96);
      }

      BlockPos colorPosition = BlockPos.ZERO;
      MapColor mapColor = state.getMapColor(level, colorPosition);
      Brightness brightness = Brightness.HIGH;
      int argb = mapColor.calculateARGBColor(brightness);
      if (state.getBlock() instanceof BedBlock bed) {
         DyeColor bedDye = bed.getColor();
         MapColor bedMapColor = bedDye.getMapColor();
         Brightness bedBrightness = Brightness.HIGH;
         argb = bedMapColor.calculateARGBColor(bedBrightness);
      }

      if (mapColor == MapColor.NONE) {
         argb = -1;
      }

      return new Color(argb, false);
   }

   private Color getEntityColor(Entity entity) {
      Color color = Color.GRAY;
      if (entity instanceof AbstractMinecart) {
         color = Color.blue;
      }

      if (EntityUtils.isHostile(entity)) {
         color = Color.RED;
      }

      if (entity instanceof Player player) {
         SocialRegistry friends = ClientContext.socialRegistry;
         Component nameComponent = player.getName();
         String playerName = nameComponent.getString();
         Color fallbackColor = color;
         color = friends.getColor(playerName, fallbackColor);
      }

      if (entity instanceof Animal || entity instanceof AbstractFish) {
         color = Color.GREEN;
      }

      return color;
   }

   public void restartSearch() {
      if (this.isToggled()) {
         this.refreshScan(true);
      }
   }

   private void refreshScan(boolean force) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      ScanContext previous = this.scanContext;
      if (this.isWorldUnavailable() || !this.blocksSetting.getValue()) {
         if (force || previous != null) {
            this.resetScan(null);
         }
         return;
      }

      if (!force && previous != null && previous.level() == level
         && previous.selectedBlocks().equals(this.selectedBlocksSetting.getValue())
         && previous.height() == this.heightSetting.getValue() && previous.onlyOminous() == this.onlyOminousSetting.getValue()
         && previous.ignoreNatural() == this.ignoreNaturalSetting.getValue() && previous.fortress() == this.fortressSetting.getValue()
         && previous.bastion() == this.bastionSetting.getValue() && previous.ancientCity() == this.ancientCitySetting.getValue()
         && previous.dungeon() == this.dungeonSetting.getValue() && previous.trialChambers() == this.trialChambersSetting.getValue()
         && previous.colors() == colorsModule.schemeSetting.getValue()) {
         return;
      }

      this.resetScan(new ScanContext(
         level, new HashSet<>(this.selectedBlocksSetting.getValue()), this.heightSetting.getValue(), this.onlyOminousSetting.getValue(),
         this.ignoreNaturalSetting.getValue(), this.fortressSetting.getValue(), this.bastionSetting.getValue(),
         this.ancientCitySetting.getValue(), this.dungeonSetting.getValue(), this.trialChambersSetting.getValue(),
         colorsModule.schemeSetting.getValue()
      ));
      this.chunkScanner.scanLoadedChunks();
   }

   private void resetScan(ScanContext current) {
      synchronized (this.blocksByPosition) {
         this.chunkScanner.restartExecutor();
         this.scanContext = current;
         this.blocksByPosition.clear();
         this.alertPending.set(false);
      }
   }

   private void detectExistingEntities() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null) {
         return;
      }

      for (Entity entity : level.entitiesForRendering()) {
         if (this.shouldSearchEntity(entity)) {
            this.alertPending.set(true);
            return;
         }
      }
   }

   @Override
   public void onEnable() {
      this.chunkScanner.start();
      this.refreshScan(true);
   }

   @Override
   public void onDisable() {
      synchronized (this.blocksByPosition) {
         this.chunkScanner.stop();
         this.scanContext = null;
         this.blocksByPosition.clear();
         this.alertPending.set(false);
      }
   }

   private void renderBlocks(WorldRenderEvent event) {
      boolean drawFill = !this.fillSetting.isAtMinimum();
      boolean drawOutline = !this.outlineSetting.isAtMinimum();
      boolean drawTracers = !this.tracersSetting.isAtMinimum();
      boolean limitRange = !this.rangeSetting.isAtMaximum();
      float range = this.rangeSetting.getValue();
      float squaredRange = range * range;
      this.blocksByPosition.forEach((position, result) -> {
         result.updateVisibility();
         if (!limitRange || !this.isOutsideRange(position, squaredRange)) {
            if (result.visible) {
               if (drawFill) {
                  float alpha = this.fillSetting.getValue().intValue() * 0.01F;
                  WorldBoxRenderer.fillBox(event.getPoseStack(), result.bounds, ARGB.color(alpha, result.argb));
               }

               if (drawOutline) {
                  float alpha = this.outlineSetting.getValue().intValue() * 0.01F;
                  int color = ARGB.color(alpha, result.argb);
                  WorldBoxRenderer.outlineBox(event.getPoseStack(), result.bounds, color, 1.0F);
               }
            }

            if (drawTracers) {
               float alpha = this.tracersSetting.getValue().intValue() * 0.01F;
               LineMeshRenderer.addTracer(result.center, ARGB.color(alpha, result.argb));
            }
         }
      });
   }

   private static boolean isPositionInChunk(BlockPos position, ChunkPos chunkPosition) {
      return position.getX() >= chunkPosition.getMinBlockX()
         && position.getX() <= chunkPosition.getMaxBlockX()
         && position.getZ() >= chunkPosition.getMinBlockZ()
         && position.getZ() <= chunkPosition.getMaxBlockZ();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.refreshScan(false);
      if (this.soundSetting.getValue() && this.alertPending.getAndSet(false)) {
         ElapsedTimer timer = this.soundTimer;
         long soundInterval = 100L;
         if (timer.hasElapsedAndReset(soundInterval)) {
            SoundManager soundManager = ClientContext.soundManager;
            NamedIdentifier soundIdentifier = this.soundTypeSetting.getValue();
            SoundClip sound = soundManager.getSound(soundIdentifier);
            float playbackVolume = this.soundVolumeSetting.getValue();
            sound.play(playbackVolume);
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.resetScan(null);
   }

   private void renderEntities(WorldRenderEvent event) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (this.shouldSearchEntity(entity)) {
            AABB bounds = BoundingBoxUtils.getRenderBounds(entity, event.getPartialTick());
            Color color = colorsModule.schemeSetting.getValue().transform(this.getEntityColor(entity));
            if (RotationManager.isVisible(bounds)) {
               if (!this.fillSetting.isAtMinimum()) {
                  float alpha = this.fillSetting.getValue().intValue() * 0.01F;
                  WorldBoxRenderer.fillBox(event.getPoseStack(), bounds, ColorUtils.withAlpha(color, alpha));
               }

               if (!this.outlineSetting.isAtMinimum()) {
                  float alpha = this.outlineSetting.getValue().intValue() * 0.01F;
                  Color outlineColor = ColorUtils.withAlpha(color, alpha);
                  WorldBoxRenderer.outlineBox(event.getPoseStack(), bounds, outlineColor, 1.0F);
               }
            }

            if (!this.tracersSetting.isAtMinimum()) {
               float alpha = this.tracersSetting.getValue().intValue() * 0.01F;
               Color tracerColor = ColorUtils.withAlpha(color, alpha);
               Color transformedTracerColor = colorsModule.schemeSetting.getValue().transform(tracerColor);
               LineMeshRenderer.addTracer(bounds.getCenter(), transformedTracerColor);
            }
         }
      }
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      Boolean soundEnabled = this.soundSetting.getValue();
      if (soundEnabled) {
         Boolean searchEntities = this.entitiesSetting.getValue();
         if (searchEntities) {
            Entity entity = event.getEntity();
            if (this.shouldSearchEntity(entity)) {
               this.alertPending.set(true);
            }

            return;
         }
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      LineMeshRenderer.begin(event.getPoseStack(), 1.0F);
      Boolean searchEntities = this.entitiesSetting.getValue();
      if (searchEntities) {
         this.renderEntities(event);
      }

      synchronized (this.blocksByPosition) {
         ScanContext scan = this.scanContext;
         if (scan != null && scan.level() == ClientAccess.argusclientMinecraft.level && this.blocksSetting.getValue()) {
            this.renderBlocks(event);
         }
      }

      LineMeshRenderer.draw();
   }

   private boolean isCurrentScan(ScanContext scan) {
      return scan != null && scan == this.scanContext && scan.level() == ClientAccess.argusclientMinecraft.level
         && !Thread.currentThread().isInterrupted();
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      ScanContext scan = this.scanContext;
      if (!this.isCurrentScan(scan) || chunk.getLevel() != scan.level() || this.blocksByPosition.size() >= MAX_CACHED_BLOCKS) {
         return;
      }

      MutableBlockPos scanPosition = new MutableBlockPos();
      Map<BlockPos, SearchResult> matches = new HashMap<>();
      int firstY = scan.firstY();
      int endY = scan.level().getMinY() + scan.level().getHeight();
      for (int localX = 0; localX < 16; localX++) {
         if (!this.isCurrentScan(scan)) {
            return;
         }

         int x = chunkPosition.getMinBlockX() + localX;
         for (int y = firstY; y < endY; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
               scanPosition.set(x, y, chunkPosition.getMinBlockZ() + localZ);
               BlockState state = chunk.getBlockState(scanPosition);
               if (scan.matches(scanPosition, state) && !scan.isNaturalChest(chunk, scanPosition, state)) {
                  BlockPos position = scanPosition.immutable();
                  matches.put(position, SearchResult.create(scan, position, state));
               }
            }
         }
      }

      synchronized (this.blocksByPosition) {
         if (!this.isCurrentScan(scan)) {
            return;
         }

         for (Map.Entry<BlockPos, SearchResult> entry : matches.entrySet()) {
            if (this.blocksByPosition.size() >= MAX_CACHED_BLOCKS) {
               break;
            }
            this.publishMatch(entry.getKey(), entry.getValue());
         }
      }
   }

   private void unloadChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      ScanContext scan = this.scanContext;
      synchronized (this.blocksByPosition) {
         if (this.isCurrentScan(scan) && chunk.getLevel() == scan.level()) {
            this.blocksByPosition.keySet().removeIf(position -> isPositionInChunk(position, chunkPosition));
         }
      }
   }

   private void updateBlock(BlockPos position, BlockState state) {
      ScanContext scan = this.scanContext;
      synchronized (this.blocksByPosition) {
         if (!this.isCurrentScan(scan)) {
            return;
         }

         if (!scan.matches(position, state)) {
            this.blocksByPosition.remove(position);
         } else if (this.blocksByPosition.size() < MAX_CACHED_BLOCKS || this.blocksByPosition.containsKey(position)) {
            this.publishMatch(position, SearchResult.create(scan, position, state));
         }
      }
   }

   private void publishMatch(BlockPos position, SearchResult result) {
      if (this.blocksByPosition.put(position, result) == null) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         float range = this.rangeSetting.getValue();
         if (player != null && (this.rangeSetting.isAtMaximum()
            || player.getEyePosition().distanceToSqr(position.getX(), position.getY(), position.getZ()) <= range * range)) {
            this.alertPending.set(true);
         }
      }
   }

   private boolean isOutsideRange(BlockPos position, float squaredRange) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      double x = position.getX();
      double y = position.getY();
      double z = position.getZ();
      return eyePosition.distanceToSqr(x, y, z) > squaredRange;
   }

   public static void bindModules(ModuleRegistry registry) {
      colorsModule = (ColorsModule)registry.getModule(ColorsModule.class);
   }

   public SearchModule() {
      super("Search", "Helps finding whitelisted stuff.", ModuleCategory.RENDER);
      this.blocksSetting.setConfigName("SearchBlocks");
      this.entitiesSetting.setConfigName("SearchEntities");
      this.selectedBlocksSetting.setConfigName("Blocks");
      this.selectedEntitiesSetting.setConfigName("Entities");
      this.fillSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.outlineSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.tracersSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
      this.onlyOminousSetting.addVisibilityPredicate(ominousIgnoredValue -> this.selectedBlocksSetting.getValue().contains(Blocks.VAULT));
      this.chunkScanner = new ChunkScanner(1, this::scanChunk, this::unloadChunk, this::updateBlock);
      this.entitiesSetting.setChangeCallback(() -> {
         if (this.entitiesSetting.getValue()) {
            this.detectExistingEntities();
         }
      });
   }

   private record ScanContext(ClientLevel level, Set<Block> selectedBlocks, HeightChoice height, boolean onlyOminous,
                              boolean ignoreNatural, boolean fortress, boolean bastion, boolean ancientCity,
                              boolean dungeon, boolean trialChambers, ColorsSchemeChoice colors) {
      int firstY() {
         return this.height == HeightChoice.OLD ? Math.max(0, this.level.getMinY()) : this.level.getMinY();
      }

      boolean matches(BlockPos position, BlockState state) {
         return position.getY() >= this.firstY() && !state.isAir() && this.selectedBlocks.contains(state.getBlock())
            && (!this.onlyOminous || !state.is(Blocks.VAULT) || state.getValueOrElse(VaultBlock.OMINOUS, false));
      }

      boolean isNaturalChest(LevelChunk chunk, BlockPos position, BlockState state) {
         return this.ignoreNatural && state.is(Blocks.CHEST) && WorldUtils.isStructureChest(
            chunk, position, this.fortress, this.bastion, this.ancientCity, this.dungeon, this.trialChambers
         );
      }
   }

   public enum HeightChoice implements NamedEntry {
      NEW("1.18"),
      OLD("1.12");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      HeightChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   private static class SearchResult {
      private final Vec3 center;
      private final AABB bounds;
      private final int argb;
      private boolean visible;

      public void updateVisibility() {
         AABB box = this.bounds;
         this.visible = RotationManager.isVisible(box);
      }

      public SearchResult(Vec3 center, AABB bounds, int argb) {
         this.center = center;
         this.bounds = bounds;
         this.argb = argb;
      }

      static SearchModule.SearchResult create(ScanContext scan, BlockPos position, BlockState state) {
         TagKey shulkerTag = BlockTags.SHULKER_BOXES;
         VoxelShape selectedShape;
         if (state.is(shulkerTag)) {
            selectedShape = Shapes.block();
         } else {
            selectedShape = state.getShape(scan.level(), position);
         }

         AABB worldBounds;
         if (selectedShape.isEmpty()) {
            worldBounds = new AABB(position);
         } else {
            AABB shapeBounds = selectedShape.bounds();
            worldBounds = shapeBounds.move(position);
         }

         Color color = scan.colors().transform(SearchModule.getBlockColor(scan.level(), state));
         return new SearchModule.SearchResult(getBlockCenter(position), worldBounds, color.hashCode());
      }

      private static Vec3 getBlockCenter(BlockPos position) {
         return position.getCenter();
      }
   }
}

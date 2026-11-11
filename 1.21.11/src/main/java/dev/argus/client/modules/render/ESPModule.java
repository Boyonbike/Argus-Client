package dev.argus.client.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.render.targets.BlockMeshCache;
import dev.argus.client.modules.render.targets.BlockTargetIndex;
import dev.argus.client.modules.render.targets.CellIndex;
import dev.argus.client.modules.render.targets.SpecificRules;
import dev.argus.client.modules.render.targets.SpecificTargets;
import dev.argus.client.modules.render.targets.TargetMatcher;
import dev.argus.client.modules.render.targets.TargetSettings;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.PostProcessShaderPass;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import dev.argus.client.settings.options.ESPModeChoice;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights players, mobs, dropped items and any chosen blocks through walls. The four target cards are shared in
 * shape with Tracers (see {@link TargetSettings}) and can be toggled or mirrored there from the pairing card; the look
 * settings here (boxes or 2D, outline, glow) stay ESP's own. Blocks come from the shared {@link BlockTargetIndex} and
 * are drawn from cached GPU meshes ({@link BlockMeshCache}), so big stashes stay cheap. This replaces the old Search
 * module, whose settings are merged into the Blocks and Mobs cards on first load.
 */
public class ESPModule extends Module {
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);

   // ---- General: entity look ------------------------------------------------------------------------------------
   final Setting<ESPModule.EntityMode> entityModeSetting;
   final Setting<ESPModule.ShadowMode> entityShadowSetting;
   final Setting<Float> entityLengthSetting;
   final Setting<Float> entityOutlineSetting;
   final Setting<Float> entityFillSetting;
   final Setting<Float> entityTintSetting;
   private final Setting<Float> lineWidthSetting;
   // ---- General: block look ---------------------------------------------------------------------------------------
   public final Setting<Integer> blockOutlineSetting;
   public final Setting<Float> blockFillOpacitySetting;
   public final Setting<Float> blockGlowSetting;
   private final Setting<Float> rebuildBudgetSetting;
   // ---- Info (2D) ------------------------------------------------------------------------------------------------
   final Setting<Boolean> infoSetting;
   final Setting<Boolean> nameSetting;
   final Setting<Boolean> heldItemSetting;
   final Setting<Boolean> distanceSetting;
   final Setting<Boolean> effectsSetting;
   final Setting<Boolean> healthBarSetting;
   final Setting<Boolean> healthTextSetting;
   final Setting<Boolean> healthColorSetting;
   // ---- Targets ---------------------------------------------------------------------------------------------------
   public final TargetSettings targets;
   // ---- Item labels -----------------------------------------------------------------------------------------------
   private final Setting<Boolean> labelsSetting;
   protected final Setting<ESPModeChoice> modeSetting;
   protected final Setting<Boolean> groupSetting;
   protected final Setting<Color> itemsTextSetting;
   protected final Setting<Color> backgroundSetting;
   protected final Setting<Float> scaleSetting;
   // ---- Others ----------------------------------------------------------------------------------------------------
   protected final Setting<Boolean> othersSetting;
   protected final Setting<Boolean> pearlsSetting;
   protected final Setting<Color> pearlFillSetting;
   protected final Setting<Color> pearlOutlineSetting;
   protected final Setting<Boolean> experienceSetting;
   protected final Setting<Color> experienceColorSetting;
   protected final Setting<Boolean> mobOwnerSetting;
   protected final Setting<Boolean> lightSetting;
   protected final Setting<Boolean> chorusSetting;
   protected final Setting<Color> chorusTextSetting;
   private final Setting<Boolean> searchMergedSetting;

   private final ESP2DRenderer entity2DRenderer;
   public final ESPRenderHelper renderHelper;
   private final BlockMeshCache blockMeshes = new BlockMeshCache();
   /** Meshes for specific block targets, one per range in use (cells are culled by range, so ranges can't share). */
   private final Map<Float, BlockMeshCache> ruleMeshes = new HashMap<>();
   private final List<Entity> renderEntities = new ArrayList<>();
   private static final int LIGHT_RADIUS = 12;
   private final LongArrayList lightSpots = new LongArrayList();
   private boolean lightSpotsStale = true;
   private final Map<Vec3, Long> chorusPositions = Collections.synchronizedMap(new HashMap<>());
   private boolean renderingBlockEntities;
   private int blockLook;
   private int ignoredStructures;
   private int pruneTicks;
   private long alertMark;

   public ESPModule() {
      super("ESP", "Highlights players, mobs, items and chosen blocks through walls.", ModuleCategory.RENDER);
      this.entityModeSetting = this.add(new EnumSetting<>("Mode", ESPModule.EntityMode.BOX));
      this.entityModeSetting.setConfigName("EntityMode");
      this.entityShadowSetting = this.add(new EnumSetting<>("Shadow", ESPModule.ShadowMode.BLACK, ignored -> this.isEntity2D()));
      this.entityLengthSetting = this.add(new NumberSetting<>("Length", 1.0F, 0.1F, 1.0F, ignored -> this.isEntity2D()));
      this.entityOutlineSetting = this.add(new NumberSetting<>("Outline", 1.0F, 0.0F, 1.0F));
      this.entityFillSetting = this.add(new NumberSetting<>("Fill", 0.3F, 0.0F, 0.5F));
      this.entityTintSetting = this.add(new NumberSetting<>("Tint", 0.3F, 0.0F, 0.5F, ignored -> this.isEntity2D()));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.0F, 4.0F, ignored -> !this.isEntity2D()));
      this.blockOutlineSetting = this.add(new NumberSetting<>("BlockOutline", 2, 0, 6).withUnit("px"));
      this.blockFillOpacitySetting = this.add(new NumberSetting<>("BlockFill", 0.0F, 0.0F, 1.0F));
      this.blockFillOpacitySetting.setConfigName("StorageFill");
      this.blockFillOpacitySetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.blockGlowSetting = this.add(new NumberSetting<>("BlockGlow", 1.0F, 0.0F, 5.0F));
      this.rebuildBudgetSetting = this.add(new NumberSetting<>("RebuildBudget", 2.0F, 0.25F, 5.0F).withUnit("ms"));

      this.infoSetting = this.add(new BooleanSetting("Info", false, ignored -> this.isEntity2D()).markAsGroup());
      this.nameSetting = this.add(new BooleanSetting("Name", true).dependsOn(this.infoSetting));
      this.heldItemSetting = this.add(new BooleanSetting("HeldItem", false).dependsOn(this.infoSetting));
      this.distanceSetting = this.add(new BooleanSetting("Distance", false).dependsOn(this.infoSetting));
      this.effectsSetting = this.add(new BooleanSetting("Effects", true).dependsOn(this.infoSetting));
      this.healthBarSetting = this.add(new BooleanSetting("HealthBar", false).markVisibilityDependency().dependsOn(this.infoSetting));
      this.healthTextSetting = this.add(new BooleanSetting("Text", false).dependsOn(this.infoSetting, this.healthBarSetting));
      this.healthTextSetting.setConfigName("HealthText");
      this.healthColorSetting = this.add(new BooleanSetting("Color", false).dependsOn(this.infoSetting, this.healthBarSetting));
      this.healthColorSetting.setConfigName("HealthColor");

      this.targets = new TargetSettings(this, TargetSettings.Kind.BLOCKS);

      this.labelsSetting = this.add(new BooleanSetting("ItemLabels", true).markAsGroup());
      this.modeSetting = this.add(new EnumSetting<>("Mode", ESPModeChoice.PLAIN).dependsOn(this.labelsSetting));
      this.modeSetting.setConfigName("ItemsMode");
      this.groupSetting = this.add(new BooleanSetting("Group", false, ignored -> this.showsItemText()).dependsOn(this.labelsSetting));
      this.itemsTextSetting = this.add(new ColorSetting("Text", new Color(-1, true), ignored -> this.showsItemText()).dependsOn(this.labelsSetting));
      this.itemsTextSetting.setConfigName("ItemsText");
      this.backgroundSetting = this.add(
         new ColorSetting("Background", new Color(657930, true), ignored -> this.modeSetting.getValue() == ESPModeChoice.TEXT).dependsOn(this.labelsSetting)
      );
      this.scaleSetting = this.add(
         new NumberSetting<>("Scale", 1.0F, 0.5F, 2.0F, ignored -> this.modeSetting.getValue() == ESPModeChoice.TEXT).dependsOn(this.labelsSetting)
      );

      this.othersSetting = this.add(new BooleanSetting("Others", false).markAsGroup());
      this.pearlsSetting = this.add(new BooleanSetting("Pearls", false, ignored -> !this.isEntity2D()).markVisibilityDependency().dependsOn(this.othersSetting));
      this.pearlFillSetting = this.add(new ColorSetting("Fill", new Color(364747263, true)).dependsOn(this.othersSetting, this.pearlsSetting));
      this.pearlFillSetting.setConfigName("PearlsFill");
      this.pearlOutlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true)).dependsOn(this.othersSetting, this.pearlsSetting));
      this.pearlOutlineSetting.setConfigName("PearlsOutline");
      this.experienceSetting = this.add(new BooleanSetting("Exp", false).markVisibilityDependency().dependsOn(this.othersSetting));
      this.experienceColorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.othersSetting, this.experienceSetting));
      this.experienceColorSetting.setConfigName("ExpColor");
      this.mobOwnerSetting = this.add(new BooleanSetting("MobOwner", false).dependsOn(this.othersSetting));
      this.lightSetting = this.add(new BooleanSetting("Light", false).dependsOn(this.othersSetting));
      this.chorusSetting = this.add(new BooleanSetting("Chorus", false).dependsOn(this.othersSetting));
      this.chorusTextSetting = this.add(new ColorSetting("ChorusText", new Color(-1, true)).dependsOn(this.othersSetting, this.chorusSetting));
      this.searchMergedSetting = this.add(new BooleanSetting("SearchMerged", false, ignored -> false));

      this.entity2DRenderer = new ESP2DRenderer(this);
      this.renderHelper = new ESPRenderHelper(this);
      BlockTargetIndex.get().register(this, this.targets);
      this.setDrawn(false);
   }

   public static void bindModules(ModuleRegistry modules) {
      colorsModule = (ColorsModule)modules.getModule(ColorsModule.class);
   }

   private boolean isEntity2D() {
      return this.entityModeSetting.getValue() == ESPModule.EntityMode.TWO_D;
   }

   private boolean showsItemText() {
      return this.modeSetting.getValue() != ESPModeChoice.PLAIN;
   }

   public boolean isRenderingBlockEntities() {
      return this.renderingBlockEntities;
   }

   /** Kept for mixins that check which block is being drawn; cached meshes never draw single blocks. */
   public boolean isRenderingBlock(Block block) {
      return false;
   }

   @Override
   public void onDisable() {
      this.blockMeshes.close();
      this.closeRuleMeshes();
      this.chorusPositions.clear();
      this.lightSpots.clear();
      this.lightSpotsStale = true;
   }

   @Override
   public String getInfoString() {
      if (!this.targets.blocks.active() && this.ruleMeshes.isEmpty()) {
         return super.getInfoString();
      }

      int drawn = this.targets.blocks.active() ? this.blockMeshes.drawnCount() : 0;
      for (BlockMeshCache meshes : this.ruleMeshes.values()) {
         drawn += meshes.drawnCount();
      }

      return Integer.toString(drawn);
   }

   // ---- ticking ---------------------------------------------------------------------------------------------------

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      this.chorusPositions.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 3500L);
      TargetSettings.Blocks card = this.targets.blocks;
      this.ignoredStructures = card.ignoredStructures();
      this.blockLook = Objects.hash(
         card.list.getValue().hashCode(),
         card.colorMode.getValue(),
         card.color.getValue().getRGB(),
         card.markOpened.getValue(),
         card.openedColor.getValue().getRGB(),
         card.hideOpened.getValue(),
         card.height.getValue(),
         card.onlyOminous.getValue(),
         this.ignoredStructures,
         colorsModule == null ? null : colorsModule.schemeSetting.getValue(),
         this.targets.specific.version()
      );
      if (card.active() && card.sound.getValue()) {
         this.alertNewBlocks(card);
      }

      // Light-level spots only change when blocks or light change, so scan them every few ticks
      // instead of re-checking ~7,000 blocks on every rendered frame.
      if (this.lightSetting.getValue()) {
         if (this.pruneTicks % 4 == 0 || this.lightSpots.isEmpty() && this.lightSpotsStale) {
            this.rebuildLightSpots();
         }
      } else if (!this.lightSpots.isEmpty()) {
         this.lightSpots.clear();
      }

      if (++this.pruneTicks % 100 == 0) {
         TargetMatcher.pruneAnnounced();
      }
   }

   private void alertNewBlocks(TargetSettings.Blocks card) {
      if (argusclientMinecraft.player == null) {
         return;
      }

      Vec3 eye = argusclientMinecraft.player.getEyePosition();
      CellIndex<BlockTargetIndex.Entry> index = BlockTargetIndex.get().index();
      boolean[] alert = new boolean[1];
      this.alertMark = BlockTargetIndex.get().forEachAddedSince(this.alertMark, packed -> {
         BlockTargetIndex.Entry entry = index.get(packed);
         if (!alert[0] && entry != null && TargetMatcher.acceptsBlock(card, this.ignoredStructures, packed, entry)
            && card.inRange(eye.distanceToSqr(CellIndex.unpackX(packed) + 0.5, CellIndex.unpackY(packed) + 0.5, CellIndex.unpackZ(packed) + 0.5))) {
            alert[0] = true;
         }
      });
      if (alert[0]) {
         TargetMatcher.playAlert();
      }
   }

   @EventHandler
   private void onEntityAdded(ClientLevelAddEntityEvent event) {
      TargetSettings.Mobs card = this.targets.mobs;
      if (card.sound.getValue() && argusclientMinecraft.player != null) {
         Entity entity = event.getEntity();
         if (TargetMatcher.kindOf(this.targets, entity, argusclientMinecraft.player.getEyePosition()) == TargetSettings.Kind.MOBS) {
            TargetMatcher.playAlert();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSoundPacket soundPacket && soundPacket.getSound().value().equals(SoundEvents.CHORUS_FRUIT_TELEPORT)) {
         this.chorusPositions.put(new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ()), System.currentTimeMillis());
      }
   }

   // ---- entities ----------------------------------------------------------------------------------------------------

   @EventHandler(priority = -200)
   private void onEntityRender(WorldRenderEvent.Main event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      this.renderEntities.clear();
      for (Entity entity : argusclientMinecraft.level.entitiesForRendering()) {
         this.renderEntities.add(entity);
      }

      if (!this.isEntity2D()) {
         this.renderEntityBoxes(event);
      }

      List<ESPItemGroup> itemGroups = new ArrayList<>();
      Vec3 camera = argusclientMinecraft.gameRenderer.getMainCamera().position();
      boolean labels = this.modeSetting.getValue() != ESPModeChoice.PLAIN;
      for (Entity entity : this.renderEntities) {
         if (entity instanceof ItemEntity item && TargetMatcher.kindOf(this.targets, item, camera) == TargetSettings.Kind.ITEMS) {
            if (this.targets.items.chatAlert.getValue()) {
               TargetMatcher.announceItem(this.targets.items, item);
            }

            if (labels) {
               this.addItemLabel(event, item, itemGroups);
            }
         }

         if (this.mobOwnerSetting.getValue()) {
            String owner = this.renderHelper.getOwnerName(entity);
            if (owner != null) {
               Vec3 position = entity.getPosition(event.getPartialTick()).add(0.0, 0.2, 0.0);
               if (!(entity instanceof ThrownEnderpearl)) {
                  position = position.add(0.0, 1.3, 0.0);
               }

               this.renderHelper.drawLabel(event, "- " + owner + " -", position, 1.2F, Color.white, null);
            }
         }
      }

      if (this.groupSetting.getValue()) {
         this.renderGroupedItemLabels(event, itemGroups);
      }

      if (this.chorusSetting.getValue()) {
         synchronized (this.chorusPositions) {
            for (Entry<Vec3, Long> entry : this.chorusPositions.entrySet()) {
               this.renderHelper.drawLabel(event, "Player teleport", entry.getKey(), 1.0F, this.chorusTextSetting.getValue(), null);
            }
         }
      }

      GuiDrawUtils.flush(true);
   }

   private void renderEntityBoxes(WorldRenderEvent event) {
      float partialTick = event.getPartialTick();
      for (Entity entity : this.renderEntities) {
         if (this.renderHelper.shouldRenderEntity(entity)) {
            AABB bounds = BoundingBoxUtils.getRenderBounds(entity, partialTick);
            WorldBoxRenderer.fillBox(event.getPoseStack(), bounds, this.renderHelper.getEntityColor(entity, true));
            WorldBoxRenderer.outlineBox(event.getPoseStack(), bounds, this.renderHelper.getEntityColor(entity, false), this.lineWidthSetting.getValue());
         }
      }
   }

   @EventHandler(priority = -200)
   private void onEntityHudRender(HudRenderEvent.Before event) {
      if (!this.isWorldUnavailable() && this.isEntity2D()) {
         this.entity2DRenderer.render(event, this.renderEntities);
      }
   }

   /** Whether dropped items get boxes (not just text labels). */
   boolean drawsItemBoxes() {
      return this.modeSetting.getValue() != ESPModeChoice.TEXT;
   }

   boolean drawsPearls() {
      return this.pearlsSetting.getValue() && !this.isEntity2D();
   }

   boolean drawsExperience() {
      return this.experienceSetting.getValue();
   }

   Color pearlColor(boolean fill) {
      return (fill ? this.pearlFillSetting : this.pearlOutlineSetting).getValue();
   }

   Color experienceColor() {
      return this.experienceColorSetting.getValue();
   }

   private void addItemLabel(WorldRenderEvent event, ItemEntity item, List<ESPItemGroup> groups) {
      if (!this.groupSetting.getValue()) {
         String text = item.getItem().getHoverName().getString();
         if (item.getItem().getCount() > 1) {
            text = text + " x" + item.getItem().getCount();
         }

         Vec3 position = item.getPosition(event.getPartialTick()).add(0.0, 0.6F, 0.0);
         Color background = this.modeSetting.getValue() == ESPModeChoice.TEXT ? this.backgroundSetting.getValue() : null;
         this.renderHelper.drawLabel(event, text, position, this.scaleSetting.getValue(), this.itemsTextSetting.getValue(), background);
         return;
      }

      for (ESPItemGroup group : groups) {
         if (group.tryAddItem(item)) {
            return;
         }
      }

      groups.add(new ESPItemGroup(item));
   }

   private void renderGroupBackground(PoseStack poses, ESPItemGroup group, double scale, float height) {
      Vec3 position = group.getRenderBounds().getCenter().add(0.0, 0.5, 0.0);
      RenderUtils.INSTANCE.drawWorldRect(poses, position, 0.0F, 0.0F, group.getMaximumTextWidth() + 1.0F, height, scale, this.backgroundSetting.getValue());
   }

   private void renderGroupedItemLabels(WorldRenderEvent event, List<ESPItemGroup> groups) {
      Vec3 camera = argusclientMinecraft.gameRenderer.getMainCamera().position();
      for (ESPItemGroup group : groups) {
         Vec3 labelPosition = group.getRenderBounds().getCenter().add(0.0, 0.5, 0.0);
         double labelScale = MathUtils.getDistanceScale(camera, labelPosition, this.scaleSetting.getValue().floatValue());
         float lineHeight = FontRenderer.INSTANCE.getHeight();
         if (this.modeSetting.getValue() == ESPModeChoice.TEXT) {
            if (group.getItemCounts().size() == 1) {
               this.renderGroupBackground(event.getPoseStack(), group, labelScale, lineHeight);
            } else {
               this.renderGroupBackground(event.getPoseStack(), group, labelScale, lineHeight);
               this.renderGroupBackground(event.getPoseStack(), group, labelScale, (group.getItemCounts().size() - 1) * -lineHeight - 2.0F);
            }
         }

         float lineY = 0.0F;
         for (Entry<String, Integer> itemEntry : group.getItemCounts().entrySet()) {
            String text = ESPItemGroup.formatItemCount(itemEntry.getKey(), itemEntry.getValue());
            float textX = -FontRenderer.INSTANCE.getWidth(text) / 2.0F;
            RenderUtils.INSTANCE.drawWorldText(event.getPoseStack(), text, labelPosition, 0.0F, 0.0F, textX, lineY, labelScale, this.itemsTextSetting.getValue(), true);
            lineY -= lineHeight;
         }
      }
   }

   // ---- light levels ------------------------------------------------------------------------------------------------

   @EventHandler
   private void onLightRender(WorldRenderEvent.BeforeBufferSource event) {
      if (this.lightSetting.getValue() && !this.isWorldUnavailable()) {
         this.renderLightLevels(event);
      }
   }

   private void rebuildLightSpots() {
      this.lightSpots.clear();
      this.lightSpotsStale = false;
      if (this.isWorldUnavailable()) {
         return;
      }

      int maximumSpawnLight = WorldUtils.getDimension().isNether() ? 11 : 0;
      ClientLevel level = argusclientMinecraft.level;
      LayerLightEventListener blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
      BlockPos center = BlockPos.containing(argusclientMinecraft.player.getEyePosition());
      MutableBlockPos position = new MutableBlockPos();
      MutableBlockPos above = new MutableBlockPos();
      int radius = LIGHT_RADIUS;
      double radiusSquared = (double)radius * radius;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (dx * dx + dy * dy + dz * dz >= radiusSquared) {
                  continue;
               }

               position.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               BlockState state = level.getBlockState(position);
               if (state.isAir()) {
                  continue;
               }

               above.set(position.getX(), position.getY() + 1, position.getZ());
               if (level.getBlockState(above).isAir() && blockLight.getLightValue(above) <= maximumSpawnLight
                  && state.isValidSpawn(level, position, EntityType.ZOMBIE) && !level.getBiome(position).is(Biomes.MUSHROOM_FIELDS)) {
                  this.lightSpots.add(position.asLong());
               }
            }
         }
      }
   }

   private void renderLightLevels(WorldRenderEvent event) {
      if (this.lightSpotsStale) {
         this.rebuildLightSpots();
      }

      if (this.lightSpots.isEmpty()) {
         return;
      }

      Vec3 camera = argusclientMinecraft.getEntityRenderDispatcher().camera.position();
      PoseStack poses = event.getPoseStack();
      BufferBuilder lines = Tesselator.getInstance().begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
      int red = Color.red.hashCode();
      for (int i = 0; i < this.lightSpots.size(); i++) {
         long packed = this.lightSpots.getLong(i);
         int x = BlockPos.getX(packed);
         int y = BlockPos.getY(packed);
         int z = BlockPos.getZ(packed);
         double distance = Math.sqrt(camera.distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
         int color = red;
         if (distance >= 8.0) {
            color = ColorUtils.toArgb(Color.red, 1.0F - (float)Mth.clamp((distance - 8.0) / 2.0, 0.0, 1.0));
         }

         poses.pushPose();
         poses.translate(x - camera.x, y - camera.y + 1.02, z - camera.z);
         Pose pose = poses.last();
         VertexGeometry.appendLine(lines, pose, 0.25F, 0.0F, 0.25F, 0.75F, 0.0F, 0.75F, 1.0F, color);
         VertexGeometry.appendLine(lines, pose, 0.75F, 0.0F, 0.25F, 0.25F, 0.0F, 0.75F, 1.0F, color);
         poses.popPose();
      }

      MeshData mesh = lines.build();
      if (mesh != null) {
         RenderType type = ClientRenderTypes.worldLinesDepth();
         type.draw(mesh);
      }
   }

   // ---- blocks ------------------------------------------------------------------------------------------------------

   @EventHandler(priority = -99999999)
   private void onBlockRender(WorldRenderEvent.Overlay event) {
      TargetSettings.Blocks card = this.targets.blocks;
      SpecificTargets specific = this.targets.specific;
      if (this.isWorldUnavailable() || !card.active() && !specific.hasBlocks()) {
         if (!this.ruleMeshes.isEmpty()) {
            this.closeRuleMeshes();
         }

         return;
      }

      ClientContext.shaderRegistry.render("outline", () -> {
         this.renderingBlockEntities = true;
         try {
            ColorsSchemeChoice scheme = colorsModule == null ? null : colorsModule.schemeSetting.getValue();
            double budget = this.rebuildBudgetSetting.getValue();
            boolean drew = false;
            if (card.active()) {
               double range = card.range.isAtMaximum() ? 0.0 : card.range.getValue();
               drew = this.blockMeshes.render(
                  event.getPoseStack(),
                  event.getPartialTick(),
                  PostProcessShaderPass.POST_TARGET,
                  // Blocks with a specific target are drawn by that target's mesh instead.
                  (packed, entry) -> specific.forBlock(entry.state().getBlock()) == null && TargetMatcher.acceptsBlock(card, this.ignoredStructures, packed, entry)
                     ? TargetMatcher.blockColor(card, scheme, entry)
                     : 0,
                  this.blockLook,
                  range * range,
                  budget
               );
            }

            drew |= this.renderRuleMeshes(event, specific, scheme, budget);
            if (drew) {
               ShaderRegistry.getBufferSource().setColorArgb(-1);
            }
         } catch (RuntimeException failure) {
            this.blockMeshes.close();
            this.closeRuleMeshes();
            card.enabled.setValue(false);
            dev.argus.client.utils.chat.ChatUtils.printChat(
               net.minecraft.network.chat.Component.literal("ESP couldn't draw blocks (" + failure.getMessage() + "). Blocks turned off; see the log."),
               dev.argus.client.utils.chat.ChatUtils.moduleSignature(this)
            );
            failure.printStackTrace();
         } finally {
            this.renderingBlockEntities = false;
         }
      });
   }

   /** Draws specific block targets, one mesh cache per range in use. */
   private boolean renderRuleMeshes(WorldRenderEvent event, SpecificTargets specific, ColorsSchemeChoice scheme, double budget) {
      Set<Float> ranges = new HashSet<>();
      for (SpecificRules.Rule rule : specific.rules().all()) {
         if (rule.enabled && rule.type == SpecificRules.Type.BLOCK) {
            ranges.add(rule.range);
         }
      }

      this.ruleMeshes.entrySet().removeIf(entry -> {
         if (ranges.contains(entry.getKey())) {
            return false;
         }

         entry.getValue().close();
         return true;
      });
      int look = Objects.hash(specific.version(), scheme);
      boolean drew = false;
      for (float range : ranges) {
         BlockMeshCache meshes = this.ruleMeshes.computeIfAbsent(range, ignored -> new BlockMeshCache());
         double limit = range >= SpecificRules.UNLIMITED ? 0.0 : range;
         drew |= meshes.render(
            event.getPoseStack(),
            event.getPartialTick(),
            PostProcessShaderPass.POST_TARGET,
            (packed, entry) -> {
               SpecificRules.Rule rule = specific.forBlock(entry.state().getBlock());
               return rule != null && rule.range == range ? TargetMatcher.ruleColor(rule, scheme) : 0;
            },
            look,
            limit * limit,
            budget
         );
      }

      return drew;
   }

   private void closeRuleMeshes() {
      for (BlockMeshCache meshes : this.ruleMeshes.values()) {
         meshes.close();
      }

      this.ruleMeshes.clear();
   }

   // ---- config migration ------------------------------------------------------------------------------------------

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonObject() && json.getAsJsonObject().get("settings") instanceof JsonObject settings && !settings.has("BlocksEnabled")) {
         JsonObject migrated = json.getAsJsonObject().deepCopy();
         migrateLegacy(settings, migrated.getAsJsonObject("settings"));
         super.fromJson(migrated);
      } else {
         super.fromJson(json);
      }
   }

   /** Maps the old fixed groups and storage toggles onto the target cards. */
   private static void migrateLegacy(JsonObject old, JsonObject out) {
      boolean drawEntities = !old.has("DrawEntities") || old.get("DrawEntities").getAsBoolean();
      copy(old, out, "Players", "PlayersEnabled", drawEntities);
      copy(old, out, "PlayersColor", "PlayersColor", true);
      boolean hostiles = bool(old, "Hostiles");
      boolean animals = bool(old, "Animals");
      out.addProperty("MobsEnabled", drawEntities && (hostiles || animals));
      out.addProperty("MobsHostile", hostiles);
      out.addProperty("MobsNeutral", hostiles);
      out.addProperty("MobsPassive", animals);
      copy(old, out, "HostilesColor", "MobsHostileColor", true);
      copy(old, out, "AnimalsColor", "MobsPassiveColor", true);
      boolean drawItems = old.has("DrawItems") ? old.get("DrawItems").getAsBoolean() : bool(old, "Items");
      out.addProperty("ItemsEnabled", drawEntities && drawItems);
      copy(old, out, "List", "ItemsList", true);
      copy(old, out, "Selection", "ItemsFilter", true);
      copy(old, out, "ItemRange", "ItemsRange", true);
      out.addProperty("ItemsEnchants", "Ignore");
      out.addProperty("ItemsDistanceColor", false);
      if (old.has("ItemsColor")) {
         out.add("ItemsColor", old.get("ItemsColor").deepCopy());
      } else if (old.has("ItemsOutline")) {
         out.add("ItemsColor", old.get("ItemsOutline").deepCopy());
      }

      out.addProperty("BlocksEnabled", !old.has("DrawBlocks") || old.get("DrawBlocks").getAsBoolean());
      JsonArray blocks = new JsonArray();
      addBlocks(old, blocks, "Chests", true, "chest", "trapped_chest");
      addBlocks(old, blocks, "Barrels", true, "barrel");
      addBlocks(old, blocks, "EChests", true, "ender_chest");
      addBlocks(old, blocks, "Shulkers", true, shulkerIds());
      addBlocks(old, blocks, "Beds", false, bedIds());
      addBlocks(old, blocks, "Signs", false, signIds());
      addBlocks(old, blocks, "Dispensers", false, "dispenser", "dropper");
      addBlocks(old, blocks, "Hoppers", false, "hopper");
      addBlocks(old, blocks, "Furnaces", false, "furnace", "blast_furnace", "smoker");
      addBlocks(old, blocks, "Pots", false, "decorated_pot");
      out.add("BlocksList", blocks);
      out.addProperty("BlocksMarkOpened", bool(old, "Looted"));
   }

   /**
    * Merges a saved Search module into the Blocks (and, if ESP wasn't showing mobs, Mobs) card once. Called by the
    * module registry while loading, with Search's saved section if there is one.
    */
   public void mergeSearch(JsonElement search) {
      if (this.searchMergedSetting.getValue() || search == null || !search.isJsonObject()) {
         return;
      }

      this.searchMergedSetting.setValue(true);
      JsonObject object = search.getAsJsonObject();
      boolean searchOn = object.has("enabled") && object.get("enabled").getAsBoolean();
      if (!(object.get("settings") instanceof JsonObject settings)) {
         return;
      }

      boolean searchBlocks = !settings.has("SearchBlocks") || settings.get("SearchBlocks").getAsBoolean();
      TargetSettings.Blocks card = this.targets.blocks;
      if (searchBlocks && settings.get("Blocks") instanceof JsonArray list) {
         JsonArray merged = card.list.toJson().getAsJsonArray().deepCopy();
         for (JsonElement id : list) {
            if (!merged.contains(id)) {
               merged.add(id);
            }
         }

         card.list.fromJson(merged);
         copyInto(settings, "IgnoreNatural", card.ignoreNatural);
         copyInto(settings, "Dungeon", card.dungeon);
         copyInto(settings, "Fortress", card.fortress);
         copyInto(settings, "Bastion", card.bastion);
         copyInto(settings, "AncientCity", card.ancientCity);
         copyInto(settings, "TrialChambers", card.trialChambers);
         copyInto(settings, "Height", card.height);
         copyInto(settings, "OnlyOminous", card.onlyOminous);
         copyInto(settings, "Sound", card.sound);
         if (searchOn) {
            card.enabled.setValue(true);
         }
      }

      boolean searchEntities = settings.has("SearchEntities") && settings.get("SearchEntities").getAsBoolean();
      TargetSettings.Mobs mobs = this.targets.mobs;
      if (searchOn && searchEntities && !mobs.active() && settings.get("Entities") instanceof JsonArray entities && !entities.isEmpty()) {
         mobs.list.fromJson(entities);
         mobs.filter.setValue(dev.argus.client.settings.options.ListFilterMode.WHITELIST);
         copyInto(settings, "IgnoreVehicles", mobs.ignoreVehicles);
         mobs.enabled.setValue(true);
      }

      if (searchOn && !this.isToggled()) {
         // Search was on and ESP wasn't: keep showing what Search showed, and nothing else.
         this.targets.players.enabled.setValue(false);
         this.targets.items.enabled.setValue(false);
         if (!searchEntities) {
            mobs.enabled.setValue(false);
         }

         this.enable();
      }
   }

   private static void copyInto(JsonObject from, String key, Setting<?> setting) {
      if (from.has(key)) {
         try {
            setting.fromJson(from.get(key));
         } catch (RuntimeException ignored) {
         }
      }
   }

   private static boolean bool(JsonObject object, String key) {
      return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
   }

   private static void copy(JsonObject from, JsonObject to, String fromKey, String toKey, boolean condition) {
      if (from.has(fromKey)) {
         JsonElement value = from.get(fromKey).deepCopy();
         if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            value = new JsonPrimitive(value.getAsBoolean() && condition);
         }

         to.add(toKey, value);
      }
   }

   private static void addBlocks(JsonObject old, JsonArray out, String key, boolean defaultOn, String... ids) {
      boolean on = old.has(key) ? old.get(key).getAsBoolean() : defaultOn;
      if (on) {
         for (String id : ids) {
            out.add(id);
         }
      }
   }

   private static String[] shulkerIds() {
      return BuiltInRegistries.BLOCK.stream()
         .filter(block -> block instanceof net.minecraft.world.level.block.ShulkerBoxBlock)
         .map(block -> BuiltInRegistries.BLOCK.getKey(block).getPath())
         .toArray(String[]::new);
   }

   private static String[] bedIds() {
      return BuiltInRegistries.BLOCK.stream()
         .filter(block -> block instanceof net.minecraft.world.level.block.BedBlock)
         .map(block -> BuiltInRegistries.BLOCK.getKey(block).getPath())
         .toArray(String[]::new);
   }

   private static String[] signIds() {
      return BuiltInRegistries.BLOCK.stream()
         .filter(block -> block instanceof net.minecraft.world.level.block.SignBlock)
         .map(block -> BuiltInRegistries.BLOCK.getKey(block).getPath())
         .toArray(String[]::new);
   }

   public enum EntityMode implements NamedEntry {
      BOX("Box"),
      TWO_D("2D");

      private final String name;

      EntityMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   public enum ShadowMode implements NamedEntry {
      BLACK("Black"),
      DARK("Dark"),
      NONE("None");

      private final String name;

      ShadowMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }
}

package dev.argus.client.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.gui.components.ColorSubmitNodeStorage;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorOutlineBufferSource;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.settings.options.ESPModeChoice;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ESPModule extends Module {
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   protected final Setting<Boolean> entitiesSetting;
   protected final Setting<Boolean> entityDrawSetting;
   final Setting<ESPModule.EntityMode> entityModeSetting;
   final Setting<ESPModule.ShadowMode> entityShadowSetting;
   final Setting<Float> entityLengthSetting;
   final Setting<Float> entityOutlineSetting;
   final Setting<Float> entityFillSetting;
   final Setting<Float> entityTintSetting;
   final Setting<Boolean> socialSetting;
   final Setting<Boolean> infoSetting;
   final Setting<Boolean> nameSetting;
   final Setting<Boolean> heldItemSetting;
   final Setting<Boolean> distanceSetting;
   final Setting<Boolean> effectsSetting;
   final Setting<Boolean> healthBarSetting;
   final Setting<Boolean> healthTextSetting;
   final Setting<Boolean> healthColorSetting;
   private final ESP2DRenderer entity2DRenderer;
   private final List<Entity> renderEntities = new ArrayList<>();
   protected final Setting<Boolean> hostilesSetting;
   protected final Setting<Color> hostileColorSetting;
   protected final Setting<Boolean> playersSetting;
   protected final Setting<Color> playerColorSetting;
   protected final Setting<Boolean> animalsSetting;
   protected final Setting<Color> animalColorSetting;
   protected final Setting<Boolean> pearlsSetting;
   protected final Setting<Color> pearlFillSetting;
   protected final Setting<Color> pearlOutlineSetting;
   protected final Setting<Boolean> itemsSetting;
   final Setting<Boolean> itemDrawSetting;
   private final Setting<Set<Item>> itemListSetting;
   protected final Setting<ListFilterMode> selectionSetting;
   protected final Setting<ESPModeChoice> modeSetting;
   protected final Setting<Integer> rangeSetting;
   protected final Setting<Boolean> groupSetting;
   protected final Setting<Color> itemsTextSetting;
   protected final Setting<Color> backgroundSetting;
   protected final Setting<Float> scaleSetting;
   protected final Setting<Color> itemColorSetting;
   protected final Setting<Boolean> experienceSetting;
   protected final Setting<Color> experienceColorSetting;
   protected final Setting<Boolean> blocksSetting;
   protected final Setting<Boolean> blockDrawSetting;
   public final Setting<Float> blockFillOpacitySetting;
   protected final Setting<Boolean> chestsSetting;
   protected final Setting<Boolean> barrelsSetting;
   protected final Setting<Boolean> enderChestsSetting;
   protected final Setting<Boolean> shulkersSetting;
   protected final Setting<Boolean> bedsSetting;
   protected final Setting<Boolean> signsSetting;
   protected final Setting<Boolean> dispensersSetting;
   protected final Setting<Boolean> hoppersSetting;
   protected final Setting<Boolean> furnacesSetting;
   protected final Setting<Boolean> potsSetting;
   protected final Setting<Boolean> lootedSetting;
   protected Setting<Boolean> othersSetting;
   protected final Setting<Boolean> mobOwnerSetting;
   protected final Setting<Boolean> lightSetting;
   protected final Setting<Boolean> chorusSetting;
   protected final Setting<Color> chorusTextSetting;
   private final Setting<Float> lineWidthSetting;
   private boolean renderingBlockEntities;
   private final Map<Vec3, Long> chorusPositions;
   private final ObjectArrayList<ESPModule.BlockEntityRenderEntry> visibleBlockEntities;
   private final ElapsedTimer blockRefreshTimer;
   public final ESPRenderHelper renderHelper;
   private final LootedStorageTracker lootedStorage;
   private Block currentlyRenderingBlock;

   private void refreshBlockEntityCache() {
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      this.visibleBlockEntities.clear();

      for (BlockEntity blockEntity : ClientContext.worldStateManager.getBlockEntities()) {
         if (this.renderHelper.shouldRenderBlockEntity(blockEntity)) {
            AABB blockBounds = new AABB(blockEntity.getBlockPos());
            if (RotationManager.isVisible(blockBounds)) {
               ObjectArrayList<ESPModule.BlockEntityRenderEntry> cacheSnapshot = this.visibleBlockEntities;
               ESPModule.BlockEntityRenderEntry sortedEntry = new ESPModule.BlockEntityRenderEntry(
                  blockEntity, -cameraPosition.distanceToSqr(blockEntity.getBlockPos().getCenter())
               );
               cacheSnapshot.add(sortedEntry);
            }
         }
      }

      this.visibleBlockEntities.sort(Comparator.comparingDouble(ESPModule.BlockEntityRenderEntry::negativeDistanceSquared));
   }

   public boolean isRenderingBlock(Block block) {
      return this.isRenderingBlockEntities() && block == this.currentlyRenderingBlock;
   }

   public boolean isRenderingBlockEntities() {
      return this.renderingBlockEntities;
   }

   private void renderItemLabel(WorldRenderEvent event, ItemEntity itemEntity) {
      String itemText = itemEntity.getItem().getHoverName().getString();
      if (itemEntity.getItem().getCount() > 1) {
         itemText = itemText + " x" + itemEntity.getItem().getCount();
      }

      ESPRenderHelper labelHelper = this.renderHelper;
      float partialTick = event.getPartialTick();
      Vec3 labelPosition = itemEntity.getPosition(partialTick);
      labelPosition = labelPosition.add(0.0, 0.6F, 0.0);
      float labelScale = this.scaleSetting.getValue();
      Color configuredItemsText = this.itemsTextSetting.getValue();
      Color configuredBackground = this.backgroundSetting.getValue();
      labelHelper.drawLabel(event, itemText, labelPosition, labelScale, configuredItemsText, configuredBackground);
   }

   private void renderGroupBackground(PoseStack poses, ESPItemGroup group, double scale, float height) {
      RenderUtils backgroundRenderer = RenderUtils.INSTANCE;
      Vec3 backgroundPosition = group.getRenderBounds().getCenter().add(0.0, 0.5, 0.0);
      float maximumTextWidth = group.getMaximumTextWidth();
      float backgroundWidth = maximumTextWidth + 1.0F;
      Setting<Color> backgroundOption = this.backgroundSetting;
      backgroundRenderer.drawWorldRect(poses, backgroundPosition, 0.0F, 0.0F, backgroundWidth, height, scale, backgroundOption.getValue());
   }

   @EventHandler
   private void expireChorusPositions(LocalPlayerTickEvent event) {
      this.chorusPositions.entrySet().removeIf(chorusEntry -> System.currentTimeMillis() - chorusEntry.getValue() > 3500L);
   }

   private void renderGroupedItemLabels(WorldRenderEvent event, List<ESPItemGroup> groups) {
      for (ESPItemGroup group : groups) {
         Boolean configuredGroup = this.groupSetting.getValue();
         if (!configuredGroup) {
            break;
         }

         AABB groupBounds = group.getRenderBounds();
         Vec3 positionSnapshot = groupBounds.getCenter();
         Vec3 labelPosition = positionSnapshot.add(0.0, 0.5, 0.0);
         GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
         positionSnapshot = gameRenderer.getMainCamera().position();
         double scaleAsDouble = this.scaleSetting.getValue().floatValue();
         Vec3 cameraForScale = positionSnapshot;
         double labelScale = MathUtils.getDistanceScale(cameraForScale, labelPosition, scaleAsDouble);
         float lineHeight = FontRenderer.INSTANCE.getHeight();
         float lineY = 0.0F;
         if (group.getItemCounts().size() == 1) {
            PoseStack singleRowPoses = event.getPoseStack();
            float singleRowHeight = group.getItemCounts().size() * lineHeight;
            this.renderGroupBackground(singleRowPoses, group, labelScale, singleRowHeight);
         } else {
            PoseStack firstRowPoses = event.getPoseStack();
            this.renderGroupBackground(firstRowPoses, group, labelScale, lineHeight);
            PoseStack remainingRowsPoses = event.getPoseStack();
            float remainingRowsHeight = (group.getItemCounts().size() - 1) * -lineHeight;
            float remainingRowHeight = remainingRowsHeight - 2.0F;
            this.renderGroupBackground(remainingRowsPoses, group, labelScale, remainingRowHeight);
         }

         for (Entry<String, Integer> itemEntry : group.getItemCounts().entrySet()) {
            String itemText = ESPItemGroup.formatItemCount(itemEntry.getKey(), itemEntry.getValue());
            float negativeTextWidth = -FontRenderer.INSTANCE.getWidth(itemText);
            float textX = negativeTextWidth / 2.0F;
            RenderUtils textRenderer = RenderUtils.INSTANCE;
            PoseStack textPoses = event.getPoseStack();
            Setting<Color> itemsTextOption = this.itemsTextSetting;
            textRenderer.drawWorldText(textPoses, itemText, labelPosition, 0.0F, 0.0F, textX, lineY, labelScale, itemsTextOption.getValue(), true);
            lineY -= lineHeight;
         }
      }
   }

   @EventHandler(priority = -200)
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (this.lootedSetting.getValue()) {
         this.lootedStorage.onUseItemOn(event);
      }
   }

   @EventHandler
   private void onLightRender(WorldRenderEvent.BeforeBufferSource event) {
      if (this.lightSetting.getValue()) {
         this.renderLightLevels(event);
      }
   }

   private void renderLightLevels(WorldRenderEvent event) {
      int maximumSpawnLight = 0;
      DimensionType spawnPolicy = WorldUtils.getDimension();
      if (spawnPolicy.isNether()) {
         maximumSpawnLight = 11;
      }

      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position();
      PoseStack poses = event.getPoseStack();
      Tesselator tesselator = Tesselator.getInstance();
      VertexFormat lineFormat = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH;
      Mode lineMode = Mode.LINES;
      BufferBuilder lineBuffer = tesselator.begin(lineMode, lineFormat);
      LevelLightEngine levelLightEngine = ClientAccess.argusclientMinecraft.level.getLightEngine();
      LightLayer blockLightLayer = LightLayer.BLOCK;
      LayerLightEventListener blockLight = levelLightEngine.getLayerListener(blockLightLayer);
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();

      for (BlockPos position : WorldUtils.collectSphereBlocks(eyePosition, 12.0F, true)) {
         BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
         BlockPos aboveBlock = position.above();
         if (WorldUtils.isAir(aboveBlock) && blockLight.getLightValue(position.above()) <= maximumSpawnLight) {
            Holder<Biome> biome = ClientAccess.argusclientMinecraft.level.getBiome(position);
            ResourceKey<Biome> mushroomFields = Biomes.MUSHROOM_FIELDS;
            boolean mushroomBiome = biome.is(mushroomFields);
            ClientLevel spawnLevel = ClientAccess.argusclientMinecraft.level;
            EntityType<?> spawnType = EntityType.ZOMBIE;
            if (state.isValidSpawn(spawnLevel, position, spawnType) && !mushroomBiome) {
               Vec3 distanceCameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
               Vec3 blockCenter = position.getCenter();
               double cameraDistance = distanceCameraPosition.distanceTo(blockCenter);
               double distancePastFadeStart = cameraDistance - 8.0;
               double normalizedFadeDistance = distancePastFadeStart / 2.0;
               float distanceAlpha = 1.0F - (float)Mth.clamp(normalizedFadeDistance, 0.0, 1.0);
               int markerRgba = Color.red.hashCode();
               if (cameraDistance >= 8.0) {
                  markerRgba = ColorUtils.toArgb(Color.red, distanceAlpha);
               }

               poses.pushPose();
               double relativeX = position.getX() - cameraPosition.x;
               double relativeY = position.getY() - cameraPosition.y;
               relativeY += 1.02;
               double translateZ = position.getZ() - cameraPosition.z;
               poses.translate(relativeX, relativeY, translateZ);
               Pose markerPose = poses.last();
               VertexGeometry.appendLine(lineBuffer, markerPose, 0.25F, 0.0F, 0.25F, 0.75F, 0.0F, 0.75F, 1.0F, markerRgba);
               VertexGeometry.appendLine(lineBuffer, markerPose, 0.75F, 0.0F, 0.25F, 0.25F, 0.0F, 0.75F, 1.0F, markerRgba);
               poses.popPose();
            }
         }
      }

      MeshData lineMesh = lineBuffer.build();
      if (lineMesh != null) {
         RenderType meshRenderType = ClientRenderTypes.worldLinesDepth();
         meshRenderType.draw(lineMesh);
      }
   }

   private void renderEntityBoxes(WorldRenderEvent event, Iterable<Entity> entities) {
      float partialTick = event.getPartialTick();

      for (Entity entity : entities) {
         ESPRenderHelper helperSnapshot = this.renderHelper;
         if (helperSnapshot.shouldRenderEntity(entity)) {
            PoseStack fillPoses = event.getPoseStack();
            AABB interpolatedBounds = BoundingBoxUtils.getRenderBounds(entity, partialTick);
            ESPRenderHelper fillColorHelper = this.renderHelper;
            WorldBoxRenderer.fillBox(fillPoses, interpolatedBounds, fillColorHelper.getEntityColor(entity, true));
            PoseStack outlinePoses = event.getPoseStack();
            interpolatedBounds = BoundingBoxUtils.getRenderBounds(entity, partialTick);
            ESPRenderHelper outlineColorHelper = this.renderHelper;
            Color outlineColor = outlineColorHelper.getEntityColor(entity, false);
            Float configuredLineWidth = this.lineWidthSetting.getValue();
            WorldBoxRenderer.outlineBox(outlinePoses, interpolatedBounds, outlineColor, configuredLineWidth);
         }
      }
   }

   private void renderItemLabels(WorldRenderEvent event, ItemEntity itemEntity, List<ESPItemGroup> itemGroups) {
      ListFilterMode itemSelection = this.selectionSetting.getValue();
      Item selectedItem = itemEntity.getItem().getItem();
      Setting<Set<Item>> itemListOption = this.itemListSetting;
      if (ListFilterMode.matches(itemSelection, selectedItem, itemListOption.getValue())) {
         Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
         double itemDistance = itemEntity.position().distanceTo(cameraPosition);
         Integer configuredRange = this.rangeSetting.getValue();
         if (!(itemDistance > configuredRange.intValue())) {
            Boolean configuredGroup = this.groupSetting.getValue();
            if (!configuredGroup) {
               this.renderItemLabel(event, itemEntity);
            }

            boolean addedToGroup = false;

            for (ESPItemGroup group : itemGroups) {
               if (group.tryAddItem(itemEntity)) {
                  addedToGroup = true;
               }
            }

            if (!addedToGroup) {
               itemGroups.add(new ESPItemGroup(itemEntity));
            }
         }
      }
   }

   @EventHandler(priority = -200)
   private void onEntityRender(WorldRenderEvent.Main event) {
      if (!this.isWorldUnavailable()) {
         List<Entity> entities = this.renderEntities;
         entities.clear();
         ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;

         for (Entity collectedEntity : entityLevel.entitiesForRendering()) {
            entities.add(collectedEntity);
         }

         ArrayList<ESPItemGroup> itemGroups = new ArrayList<>();
         if (this.entityDrawSetting.getValue() && this.entityModeSetting.getValue() == ESPModule.EntityMode.BOX) {
            this.renderEntityBoxes(event, entities);
         }

         for (Entity entity : entities) {
            if (entity instanceof ItemEntity itemEntity && this.itemDrawSetting.getValue()) {
               ESPModeChoice itemMode = this.modeSetting.getValue();
               if (itemMode == ESPModeChoice.BOTH || itemMode == ESPModeChoice.TEXT) {
                  this.renderItemLabels(event, itemEntity, itemGroups);
               }
            }

            if (this.mobOwnerSetting.getValue()) {
               ESPRenderHelper helperSnapshot = this.renderHelper;
               String ownerLabel = helperSnapshot.getOwnerName(entity);
               if (ownerLabel != null) {
                  ownerLabel = "- " + ownerLabel + " -";
                  float ownerPartialTick = event.getPartialTick();
                  Vec3 ownerLabelPosition = entity.getPosition(ownerPartialTick).add(0.0, 0.2, 0.0);
                  if (!(entity instanceof ThrownEnderpearl)) {
                     ownerLabelPosition = ownerLabelPosition.add(0.0, 1.3, 0.0);
                  }

                  helperSnapshot = this.renderHelper;
                  Color ownerColor = Color.white;
                  helperSnapshot.drawLabel(event, ownerLabel, ownerLabelPosition, 1.2F, ownerColor, null);
               }
            }
         }

         Boolean configuredGroup2 = this.groupSetting.getValue();
         if (configuredGroup2) {
            this.renderGroupedItemLabels(event, itemGroups);
         }

         Boolean configuredChorus = this.chorusSetting.getValue();
         if (configuredChorus) {
            synchronized (this.chorusPositions) {
               for (Entry<Vec3, Long> chorusEntry : this.chorusPositions.entrySet()) {
                  ESPRenderHelper chorusHelperSnapshot = this.renderHelper;
                  Vec3 teleportPosition = chorusEntry.getKey();
                  Color configuredChorusText = this.chorusTextSetting.getValue();
                  chorusHelperSnapshot.drawLabel(event, "Player teleport", teleportPosition, 1.0F, configuredChorusText, null);
               }
            }
         }

         GuiDrawUtils.flush(true);
      }
   }

   @EventHandler(priority = -200)
   private void onEntityHudRender(HudRenderEvent.Before event) {
      if (!this.isWorldUnavailable() && this.entityDrawSetting.getValue() && this.entityModeSetting.getValue() == ESPModule.EntityMode.TWO_D) {
         this.entity2DRenderer.render(event, this.renderEntities);
      }
   }

   @EventHandler(priority = -99999999)
   private void onBlockEntityRender(WorldRenderEvent.Overlay event) {
      if (!this.isWorldUnavailable() && this.renderHelper.hasStorageTargets()) {
         Boolean configuredBlockDraw = this.blockDrawSetting.getValue();
         if (configuredBlockDraw) {
            ShaderRegistry blockShader = ClientContext.shaderRegistry;
            Runnable blockRenderPass = () -> this.renderBlockEntities(event);
            blockShader.render("outline", blockRenderPass);
            return;
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSoundPacket soundPacket && soundPacket.getSound().value().equals(SoundEvents.CHORUS_FRUIT_TELEPORT)) {
         Map<Vec3, Long> chorusPositionMap = this.chorusPositions;
         Vec3 teleportPosition = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
         long receivedAtMillis = System.currentTimeMillis();
         chorusPositionMap.put(teleportPosition, receivedAtMillis);
      }

      if (event.getPacket() instanceof ClientboundBlockUpdatePacket blockPacket) {
         Boolean configuredLooted = this.lootedSetting.getValue();
         if (configuredLooted) {
            Minecraft blockUpdateTaskClient = ClientAccess.argusclientMinecraft;
            Runnable blockUpdateTask = () -> {
               LootedStorageTracker blockTrackerSnapshot = this.lootedStorage;
               BlockPos deferredPosition = blockPacket.getPos();
               BlockState updatedState = blockPacket.getBlockState();
               blockTrackerSnapshot.onBlockUpdate(deferredPosition, updatedState);
            };
            blockUpdateTaskClient.execute(blockUpdateTask);
         }
      }

      if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
         Boolean configuredLooted2 = this.lootedSetting.getValue();
         if (configuredLooted2) {
            Minecraft sectionUpdateTaskClient = ClientAccess.argusclientMinecraft;
            Runnable sectionUpdateTask = () -> {
               LootedStorageTracker sectionTrackerForCallback = this.lootedStorage;
               sectionPacket.runUpdates(sectionTrackerForCallback::onBlockUpdate);
            };
            sectionUpdateTaskClient.execute(sectionUpdateTask);
         }
      }
   }

   private void renderBlockEntities(WorldRenderEvent event) {
      event.getPoseStack().pushPose();
      this.renderingBlockEntities = true;
      CameraRenderState cameraRenderState = ClientAccess.argusclientMinecraft.gameRenderer.getLevelRenderState().cameraRenderState;
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      ColorOutlineBufferSource renderTarget = ShaderRegistry.getBufferSource();
      EntityCullingOverrides.disableCulling();
      if (this.blockRefreshTimer.hasElapsedAndReset(10L)) {
         this.refreshBlockEntityCache();
      }

      for (ObjectListIterator var5 = this.visibleBlockEntities.iterator(); var5.hasNext(); event.getPoseStack().popPose()) {
         ESPModule.BlockEntityRenderEntry entry = (ESPModule.BlockEntityRenderEntry)var5.next();
         BlockEntity blockEntity = entry.blockEntity();
         ESPRenderHelper colorHelperSnapshot = this.renderHelper;
         Color highlightColor = colorHelperSnapshot.getBlockEntityColor(blockEntity);
         Boolean configuredLooted = this.lootedSetting.getValue();
         if (configuredLooted && this.lootedStorage.isLooted(blockEntity)) {
            highlightColor = LootedStorageTracker.getLootedColor();
         }

         Color opaqueColor = new Color(colorsModule.schemeSetting.getValue().transform(highlightColor).hashCode(), false);
         BlockPos xPosition = blockEntity.getBlockPos();
         double relativeX = xPosition.getX() - cameraPosition.x;
         BlockPos yPosition = blockEntity.getBlockPos();
         double relativeY = yPosition.getY() - cameraPosition.y;
         BlockPos zPosition = blockEntity.getBlockPos();
         double relativeZ = zPosition.getZ() - cameraPosition.z;
         event.getPoseStack().pushPose();
         event.getPoseStack().translate(relativeX, relativeY, relativeZ);
         ColorSubmitNodeStorage colorBuffer = ShaderRegistry.getSubmitNodes();
         colorBuffer.setColorArgb(opaqueColor.hashCode());
         renderTarget.setColorArgb(opaqueColor.hashCode());
         BlockEntityRenderDispatcher extractDispatcherSnapshot = ClientAccess.argusclientMinecraft.getBlockEntityRenderDispatcher();
         float partialTick = event.getPartialTick();
         BlockEntityRenderState blockRenderState = extractDispatcherSnapshot.tryExtractRenderState(blockEntity, partialTick, null);
         if (blockRenderState != null) {
            BlockEntityRenderDispatcher rendererLookupDispatcherSnapshot = ClientAccess.argusclientMinecraft.getBlockEntityRenderDispatcher();
            if (rendererLookupDispatcherSnapshot.getRenderer(blockRenderState) != null) {
               BlockEntityRenderDispatcher submitDispatcher = ClientAccess.argusclientMinecraft.getBlockEntityRenderDispatcher();
               PoseStack submitPoses = event.getPoseStack();
               ColorSubmitNodeStorage submitNodes = ShaderRegistry.getSubmitNodes();
               submitDispatcher.submit(blockRenderState, submitPoses, submitNodes, cameraRenderState);
               ShaderRegistry.finishFeatureRendering();
               continue;
            }
         }

         BlockState fallbackState = blockEntity.getBlockState();
         this.currentlyRenderingBlock = fallbackState.getBlock();
         BlockRenderDispatcher fallbackRenderer = ClientAccess.argusclientMinecraft.getBlockRenderer();
         BlockState fallbackDrawState = blockEntity.getBlockState();
         PoseStack fallbackPoses = event.getPoseStack();
         fallbackRenderer.renderSingleBlock(fallbackDrawState, fallbackPoses, renderTarget, 16711935, 0);
         this.currentlyRenderingBlock = null;
      }

      ShaderRegistry.finishFeatureRendering();
      EntityCullingOverrides.restoreCulling();
      this.renderingBlockEntities = false;
      event.getPoseStack().popPose();
   }

   public static void bindModules(ModuleRegistry modules) {
      colorsModule = (ColorsModule)modules.getModule(ColorsModule.class);
   }

   private boolean showItemGroups(Object ignored) {
      return this.modeSetting.getValue() == ESPModeChoice.TEXT || this.modeSetting.getValue() == ESPModeChoice.BOTH;
   }

   private boolean showItemText(Object ignored) {
      return (this.modeSetting.getValue() == ESPModeChoice.TEXT || this.modeSetting.getValue() == ESPModeChoice.BOTH)
         && this.entitiesSetting.isExpanded()
         && this.itemsSetting.isExpanded();
   }

   private boolean showTextOnlyOptions(Object ignored) {
      return this.modeSetting.getValue() == ESPModeChoice.TEXT && this.entitiesSetting.isExpanded() && this.itemsSetting.isExpanded();
   }

   private boolean showItemBoxes(Object ignored) {
      return (this.modeSetting.getValue() == ESPModeChoice.PLAIN || this.modeSetting.getValue() == ESPModeChoice.BOTH)
         && this.entitiesSetting.isExpanded()
         && this.itemsSetting.isExpanded();
   }

   public ESPModule() {
      super("ESP", "Highlights entities through walls.", ModuleCategory.RENDER);
      this.entitiesSetting = this.add(new BooleanSetting("Entities", true).markAsGroup().markVisibilityDependency());
      this.entityDrawSetting = this.add(new BooleanSetting("Draw", true).dependsOn(this.entitiesSetting));
      this.entityModeSetting = this.add(new EnumSetting<>("Mode", ESPModule.EntityMode.BOX).dependsOn(this.entitiesSetting));
      this.entityModeSetting.setConfigName("EntityMode");
      this.entityShadowSetting = this.add(new EnumSetting<>("Shadow", ESPModule.ShadowMode.BLACK, ignored -> this.isEntity2D()).dependsOn(this.entitiesSetting));
      this.entityLengthSetting = this.add(new NumberSetting<>("Length", 1.0F, 0.1F, 1.0F, ignored -> this.isEntity2D()).dependsOn(this.entitiesSetting));
      this.entityOutlineSetting = this.add(new NumberSetting<>("Outline", 1.0F, 0.0F, 1.0F).dependsOn(this.entitiesSetting));
      this.entityFillSetting = this.add(new NumberSetting<>("Fill", 0.3F, 0.0F, 0.5F).dependsOn(this.entitiesSetting));
      this.entityTintSetting = this.add(new NumberSetting<>("Tint", 0.3F, 0.0F, 0.5F, ignored -> this.isEntity2D()).dependsOn(this.entitiesSetting));
      this.infoSetting = this.add(
         new BooleanSetting("Info", false, ignored -> this.isEntity2D()).markAsGroup().markVisibilityDependency().dependsOn(this.entitiesSetting)
      );
      this.nameSetting = this.add(new BooleanSetting("Name", true).dependsOn(this.infoSetting));
      this.heldItemSetting = this.add(new BooleanSetting("HeldItem", false).dependsOn(this.infoSetting));
      this.distanceSetting = this.add(new BooleanSetting("Distance", false).dependsOn(this.infoSetting));
      this.effectsSetting = this.add(new BooleanSetting("Effects", true).dependsOn(this.infoSetting));
      this.healthBarSetting = this.add(new BooleanSetting("HealthBar", false).markVisibilityDependency().dependsOn(this.infoSetting));
      this.healthTextSetting = this.add(new BooleanSetting("Text", false).dependsOn(this.infoSetting, this.healthBarSetting));
      this.healthColorSetting = this.add(new BooleanSetting("Color", false).dependsOn(this.infoSetting, this.healthBarSetting));
      this.entity2DRenderer = new ESP2DRenderer(this);
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", false).markVisibilityDependency().dependsOn(this.entitiesSetting));
      this.hostileColorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.hostilesSetting, this.entitiesSetting));
      this.playersSetting = this.add(new BooleanSetting("Players", false).markVisibilityDependency().dependsOn(this.entitiesSetting));
      this.socialSetting = this.add(new BooleanSetting("Social", true).dependsOn(this.entitiesSetting, this.playersSetting));
      this.playerColorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.playersSetting, this.entitiesSetting));
      this.animalsSetting = this.add(new BooleanSetting("Animals", false).markVisibilityDependency().dependsOn(this.entitiesSetting));
      this.animalColorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.animalsSetting, this.entitiesSetting));
      this.pearlsSetting = this.add(
         new BooleanSetting("Pearls", false, ignored -> this.entityModeSetting.getValue() == ESPModule.EntityMode.BOX)
            .markVisibilityDependency()
            .dependsOn(this.entitiesSetting)
      );
      this.pearlFillSetting = this.add(new ColorSetting("Fill", new Color(364747263, true)).dependsOn(this.pearlsSetting, this.entitiesSetting));
      this.pearlOutlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true)).dependsOn(this.pearlsSetting, this.entitiesSetting));
      this.itemsSetting = this.add(new BooleanSetting("Items", false).markAsGroup().markVisibilityDependency().dependsOn(this.entitiesSetting));
      this.itemDrawSetting = this.add(new BooleanSetting("Draw", false).dependsOn(this.entitiesSetting, this.itemsSetting));
      this.itemDrawSetting.setConfigName("DrawItems");
      this.itemListSetting = this.add(new RegistrySetSetting<>("List", BuiltInRegistries.ITEM).dependsOn(this.entitiesSetting, this.itemsSetting));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY).dependsOn(this.entitiesSetting, this.itemsSetting));
      this.modeSetting = this.add(new EnumSetting<>("Mode", ESPModeChoice.PLAIN).dependsOn(this.entitiesSetting, this.itemsSetting));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 256, 1, 256).withUnit("m").dependsOn(this.entitiesSetting, this.itemsSetting));
      this.groupSetting = this.add(new BooleanSetting("Group", false, this::showItemGroups).dependsOn(this.entitiesSetting, this.itemsSetting));
      this.itemsTextSetting = this.add(new ColorSetting("Text", new Color(-1, true), this::showItemText));
      this.backgroundSetting = this.add(new ColorSetting("Background", new Color(657930, true), this::showTextOnlyOptions));
      this.scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.5F, 2.0F, this::showTextOnlyOptions));
      this.itemColorSetting = this.add(new ColorSetting("Color", new Color(364747263, true), this::showItemBoxes));
      this.experienceSetting = this.add(new BooleanSetting("Exp", false).markVisibilityDependency().dependsOn(this.entitiesSetting));
      this.experienceColorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.experienceSetting, this.entitiesSetting));
      this.blocksSetting = this.add(new BooleanSetting("Blocks", true).markAsGroup().markVisibilityDependency());
      this.blockDrawSetting = this.add(new BooleanSetting("Draw", true).dependsOn(this.blocksSetting));
      this.blockFillOpacitySetting = this.add(new NumberSetting<>("Fill", 0.0F, 0.0F, 1.0F).dependsOn(this.blocksSetting));
      this.chestsSetting = this.add(new BooleanSetting("Chests", true).dependsOn(this.blocksSetting));
      this.barrelsSetting = this.add(new BooleanSetting("Barrels", true).dependsOn(this.blocksSetting));
      this.enderChestsSetting = this.add(new BooleanSetting("EChests", true).dependsOn(this.blocksSetting));
      this.shulkersSetting = this.add(new BooleanSetting("Shulkers", true).dependsOn(this.blocksSetting));
      this.bedsSetting = this.add(new BooleanSetting("Beds", false).dependsOn(this.blocksSetting));
      this.signsSetting = this.add(new BooleanSetting("Signs", false).dependsOn(this.blocksSetting));
      this.dispensersSetting = this.add(new BooleanSetting("Dispensers", false).dependsOn(this.blocksSetting));
      this.hoppersSetting = this.add(new BooleanSetting("Hoppers", false).dependsOn(this.blocksSetting));
      this.furnacesSetting = this.add(new BooleanSetting("Furnaces", false).dependsOn(this.blocksSetting));
      this.potsSetting = this.add(new BooleanSetting("Pots", false).dependsOn(this.blocksSetting));
      this.lootedSetting = this.add(new BooleanSetting("Looted", false).dependsOn(this.blocksSetting));
      this.othersSetting = this.add(new BooleanSetting("Others", false).markAsGroup().markVisibilityDependency());
      this.mobOwnerSetting = this.add(new BooleanSetting("MobOwner", false).dependsOn(this.othersSetting));
      this.lightSetting = this.add(new BooleanSetting("Light", false).dependsOn(this.othersSetting));
      this.chorusSetting = this.add(new BooleanSetting("Chorus", false).dependsOn(this.othersSetting));
      this.chorusTextSetting = this.add(new ColorSetting("ChorusText", new Color(-1, true)).dependsOn(this.othersSetting, this.chorusSetting));
      this.lineWidthSetting = this.add(
         new NumberSetting<>("LineWidth", 1.0F, 0.0F, 4.0F, ignored -> this.entityModeSetting.getValue() == ESPModule.EntityMode.BOX)
            .dependsOn(this.entitiesSetting)
      );
      this.chorusPositions = Collections.synchronizedMap(new HashMap<>());
      this.visibleBlockEntities = new ObjectArrayList(64);
      this.blockRefreshTimer = new ElapsedTimer();
      this.renderHelper = new ESPRenderHelper(this);
      this.lootedStorage = new LootedStorageTracker();
      this.pearlFillSetting.setConfigName("PearlsFill");
      this.pearlOutlineSetting.setConfigName("PearlsOutline");
      this.playerColorSetting.setConfigName("PlayersColor");
      this.hostileColorSetting.setConfigName("HostilesColor");
      this.animalColorSetting.setConfigName("AnimalsColor");
      this.experienceColorSetting.setConfigName("ExpColor");
      this.itemColorSetting.setConfigName("ItemsColor");
      this.healthTextSetting.setConfigName("HealthText");
      this.healthColorSetting.setConfigName("HealthColor");
      this.itemsTextSetting.setConfigName("ItemsText");
      this.modeSetting.setConfigName("ItemsMode");
      this.rangeSetting.setConfigName("ItemRange");
      this.blockFillOpacitySetting.setConfigName("StorageFill");
      this.blockFillOpacitySetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.entityDrawSetting.setConfigName("DrawEntities");
      this.blockDrawSetting.setConfigName("DrawBlocks");
      this.setDrawn(false);
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject object = json.getAsJsonObject();
      JsonObject settings = object.getAsJsonObject("settings");
      boolean legacyDraw = settings != null && !settings.has("DrawItems") && settings.has("Items");
      boolean legacyMode = settings != null && settings.has("ItemsMode") && "Box".equalsIgnoreCase(settings.get("ItemsMode").getAsString());
      boolean legacyColors = settings != null
         && List.of("Players", "Hostiles", "Animals", "Exp", "Items")
            .stream()
            .anyMatch(group -> settings.has(group + "Outline") || settings.has(group + "Fill"));
      boolean legacyPearls = settings != null
         && List.of("Fill", "Outline").stream().anyMatch(keyx -> settings.has(keyx) && (settings.get(keyx).isJsonArray() || settings.get(keyx).isJsonObject()));
      if (!legacyDraw && !legacyMode && !legacyColors && !legacyPearls) {
         super.fromJson(json);
      } else {
         JsonObject migrated = object.deepCopy();
         JsonObject migratedSettings = migrated.getAsJsonObject("settings");
         if (legacyDraw) {
            migratedSettings.add("DrawItems", settings.get("Items").deepCopy());
         }

         if (legacyMode) {
            migratedSettings.addProperty("ItemsMode", "Plain");
         }

         for (String group : List.of("Players", "Hostiles", "Animals", "Exp", "Items")) {
            if (!settings.has(group + "Color")) {
               JsonElement legacyColor = settings.has(group + "Outline") ? settings.get(group + "Outline") : settings.get(group + "Fill");
               if (legacyColor != null) {
                  migratedSettings.add(group + "Color", legacyColor.deepCopy());
               }
            }

            migratedSettings.remove(group + "Outline");
            migratedSettings.remove(group + "Fill");
         }

         for (String key : List.of("Fill", "Outline")) {
            JsonElement legacyColor = settings.get(key);
            if (legacyColor != null && (legacyColor.isJsonArray() || legacyColor.isJsonObject())) {
               if (!settings.has("Pearls" + key)) {
                  migratedSettings.add("Pearls" + key, legacyColor.deepCopy());
               }

               migratedSettings.remove(key);
            }
         }

         super.fromJson(migrated);
      }
   }

   private boolean isEntity2D() {
      return this.entityModeSetting.getValue() == ESPModule.EntityMode.TWO_D;
   }

   record BlockEntityRenderEntry(BlockEntity blockEntity, double negativeDistanceSquared) {
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

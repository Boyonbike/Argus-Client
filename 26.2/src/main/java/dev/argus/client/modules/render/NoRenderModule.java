package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EntityRenderEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LivingEntityModelSubmitEvent;
import dev.argus.client.events.NameTagRenderEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.ParticleEngineAddEvent;
import dev.argus.client.events.StatusEffectQueryEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class NoRenderModule extends Module {
   private final Setting<Boolean> selfGroupSetting = this.add(new BooleanSetting("Self", false).markAsGroup());
   public final Setting<Boolean> hurtCamSetting = this.add(new BooleanSetting("HurtCam", true).dependsOn(this.selfGroupSetting));
   private final Setting<Boolean> potionParticlesSetting = this.add(new BooleanSetting("Potions", false).dependsOn(this.selfGroupSetting));
   public final Setting<Boolean> darknessSetting = this.add(new BooleanSetting("Darkness", true).dependsOn(this.selfGroupSetting));
   public final Setting<Boolean> blindnessSetting = this.add(new BooleanSetting("Blindness", true).dependsOn(this.selfGroupSetting));
   public final Setting<Boolean> newSneakingSetting = this.add(new BooleanSetting("NewSneaking", false).dependsOn(this.selfGroupSetting));
   public final Setting<Boolean> totemOverlaySetting = this.add(new BooleanSetting("TotemOverlay", false).dependsOn(this.selfGroupSetting));
   public final Setting<Boolean> handsSetting = this.add(new BooleanSetting("Hands", false).markVisibilityDependency().dependsOn(this.selfGroupSetting));
   private final Setting<Float> handOpacitySetting = this.add(
      new NumberSetting<>("Opacity", 0.0F, 0.0F, 1.0F).dependsOn(this.selfGroupSetting, this.handsSetting)
   );
   private final Setting<Boolean> uiGroupSetting = this.add(new BooleanSetting("UI", false).markAsGroup());
   public final Setting<Boolean> messageIndicatorSetting = this.add(new BooleanSetting("MessageIndicator", true).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> unsecureServerSetting = this.add(new BooleanSetting("UnsecureServer", true).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> tutorialToastSetting = this.add(new BooleanSetting("TutorialToast", true).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> textShadowSetting = this.add(new BooleanSetting("TextShadow", false).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> bossBarsSetting = this.add(new BooleanSetting("BossBars", false).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> scoreBoardSetting = this.add(new BooleanSetting("ScoreBoard", false).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> actionBarSetting = this.add(new BooleanSetting("ActionBar", true).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> tabIconsSetting = this.add(new BooleanSetting("TabIcons", false).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> heldTooltipsSetting = this.add(new BooleanSetting("HeldTooltips", false).dependsOn(this.uiGroupSetting));
   public final Setting<Boolean> tintSetting = this.add(new BooleanSetting("Tint", false).dependsOn(this.uiGroupSetting));
   private final Setting<Boolean> worldGroupSetting = this.add(new BooleanSetting("World", false).markAsGroup());
   public final Setting<Boolean> explosionsSetting = this.add(new BooleanSetting("Explosions", true).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> worldBorderSetting = this.add(new BooleanSetting("WorldBorder", true).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> skyLightSetting = this.add(new BooleanSetting("SkyLight", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> celestialBodiesSetting = this.add(new BooleanSetting("CelestialBodies", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> starsSetting = this.add(new BooleanSetting("Stars", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> mapsSetting = this.add(new BooleanSetting("Maps", false).dependsOn(this.worldGroupSetting));
   private final Setting<Boolean> chunkRadiusPacketsSetting = this.add(new BooleanSetting("Chunks", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> blockFireSetting = this.add(new BooleanSetting("Fire", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> vinesSetting = this.add(new BooleanSetting("Vines", false).dependsOn(this.worldGroupSetting));
   public final Setting<Boolean> fogSetting = this.add(new BooleanSetting("Fog", false).markVisibilityDependency().dependsOn(this.worldGroupSetting));
   public final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 0.1F, 10.0F).dependsOn(this.fogSetting, this.worldGroupSetting));
   public Setting<Boolean> skySetting = this.add(new BooleanSetting("Sky", false).dependsOn(this.fogSetting, this.worldGroupSetting));
   public final Setting<Boolean> particlesSetting = this.add(
      new BooleanSetting("Particles", false).markVisibilityDependency().dependsOn(this.worldGroupSetting)
   );
   public final Setting<Set<ParticleType<?>>> particleTypesSetting = this.add(
      new RegistrySetSetting<>("ParticleTypes", BuiltInRegistries.PARTICLE_TYPE).dependsOn(this.particlesSetting, this.worldGroupSetting)
   );
   public final Setting<ListFilterMode> particleSelectionSetting = this.add(
      new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.particlesSetting, this.worldGroupSetting)
   );
   private final Setting<Boolean> blockBreakingParticlesSetting = this.add(
      new BooleanSetting("NoBlockBreaking", false).dependsOn(this.particlesSetting, this.worldGroupSetting)
   );
   private final Setting<Boolean> entityGroupSetting = this.add(new BooleanSetting("Entities", false).markAsGroup());
   public final Setting<Boolean> hurtSetting = this.add(new BooleanSetting("Hurt", true).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> shadowSetting = this.add(new BooleanSetting("Shadow", false).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> glowSetting = this.add(new BooleanSetting("Glow", true).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> nameTagsSetting = this.add(new BooleanSetting("NameTags", false).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> interpolationSetting = this.add(new BooleanSetting("Interpolation", false).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> armorTrimSetting = this.add(new BooleanSetting("ArmorTrim", false).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> elytraSetting = this.add(new BooleanSetting("Elytra", false).dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> fireSetting = this.add(new BooleanSetting("Fire", true).markVisibilityDependency().dependsOn(this.entityGroupSetting));
   public final Setting<Boolean> othersSetting = this.add(new BooleanSetting("Others", false).dependsOn(this.entityGroupSetting, this.fireSetting));
   public final Setting<Boolean> armorSetting = this.add(new BooleanSetting("Armor", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
   public final Setting<Float> armorOpacitySetting = this.add(
      new NumberSetting<>("Opacity", 0.0F, 0.0F, 1.0F).dependsOn(this.entityGroupSetting, this.armorSetting)
   );
   protected final Setting<Boolean> noClusterSetting = this.add(
      new BooleanSetting("NoCluster", false).markVisibilityDependency().dependsOn(this.entityGroupSetting)
   );
   protected final Setting<Integer> clusterAlphaSetting = this.add(
      new NumberSetting<>("ClusterAlpha", 120, 0, 255).dependsOn(this.noClusterSetting, this.entityGroupSetting)
   );
   public final Setting<Boolean> wardensSetting = this.add(new BooleanSetting("Wardens", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
   public final Setting<Integer> wardenDistanceSetting = this.add(
      new NumberSetting<>("WardenDistance", 32, 1, 128).withUnit("m").dependsOn(this.entityGroupSetting, this.wardensSetting)
   );
   public final Setting<Boolean> itemsSetting = this.add(new BooleanSetting("Items", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
   public final Setting<Integer> limitSetting = this.add(new NumberSetting<>("Limit", 128, 0, 256).dependsOn(this.entityGroupSetting, this.itemsSetting));
   public final Setting<Boolean> tileEntitiesSetting = this.add(
      new BooleanSetting("TileEntities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting)
   );
   public final Setting<Set<BlockEntityType<?>>> tileTypesSetting = this.add(
      new RegistrySetSetting<>("TileTypes", BuiltInRegistries.BLOCK_ENTITY_TYPE).dependsOn(this.entityGroupSetting, this.tileEntitiesSetting)
   );
   public final Setting<ListFilterMode> blockEntitySelectionSetting = this.add(
      new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.tileEntitiesSetting)
   );
   public final Setting<Integer> tileDistanceSetting = this.add(
      new NumberSetting<>("TileDistance", 64, 1, 256).withUnit("m").dependsOn(this.entityGroupSetting, this.tileEntitiesSetting)
   );
   public final Setting<Boolean> entitiesSetting = this.add(new BooleanSetting("Entities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting));
   public final Setting<Set<EntityType<?>>> entityTypesSetting = this.add(
      new RegistrySetSetting<>("EntityTypes", BuiltInRegistries.ENTITY_TYPE).dependsOn(this.entitiesSetting, this.entityGroupSetting)
   );
   public final Setting<NoRenderModule.RemovalChoice> removalSetting = this.add(
      new EnumSetting<>("Removal", NoRenderModule.RemovalChoice.PARTIAL).dependsOn(this.entityGroupSetting, this.entitiesSetting)
   );
   public final Setting<ListFilterMode> entitySelectionSetting = this.add(
      new EnumSetting<>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.entitiesSetting)
   );
   private static final Map<Block, BlockEntityType<?>> BLOCK_ENTITY_TYPES_BY_BLOCK;
   private boolean worldRenderPassActive;
   private float lastEntityAlpha;
   private BlockPos pendingCrystalFirePosition;

   @Override
   public void onToggle() {
      Setting<Boolean> tileEntities = this.tileEntitiesSetting;
      if (tileEntities.getValue()) {
         WorldUtils.reloadRenderer();
      }
   }

   public boolean shouldHideBlock(BlockState state) {
      if (!this.isToggled()) {
         return false;
      }

      Setting<Boolean> fire = this.blockFireSetting;
      if (fire.getValue()) {
         Block fireBlock = Blocks.FIRE;
         if (state.is(fireBlock)) {
            return true;
         }
      }

      return !this.vinesSetting.getValue() ? false : this.isVineLikeBlock(state);
   }

   public boolean shouldHideEntity(Entity entity) {
      EntityType type = entity.getType();
      return this.shouldHideEntityType(type);
   }

   public boolean shouldHideTabIcons() {
      return !this.isToggled() ? false : this.tabIconsSetting.getValue();
   }

   public float getHandOpacity() {
      if (this.isToggled()) {
         Setting<Boolean> hands = this.handsSetting;
         if (hands.getValue() && !ClientContext.shaderRegistry.renderingIntoTarget && !ClientContext.shaderRegistry.renderingHands) {
            float alpha = this.handOpacitySetting.getValue();
            if (alpha == 0.0F) {
               return 0.0F;
            }

            return Math.max(alpha, 0.11F);
         }
      }

      return 1.0F;
   }

   public boolean shouldHideScreenTint() {
      return !this.isToggled() ? false : this.tintSetting.getValue();
   }

   public boolean shouldHideCelestialBodies() {
      return !this.isToggled() ? false : this.celestialBodiesSetting.getValue();
   }

   public boolean shouldHideStars() {
      return !this.isToggled() ? false : this.starsSetting.getValue();
   }

   public boolean shouldHideMaps() {
      return !this.isToggled() ? false : this.mapsSetting.getValue() && this.worldRenderPassActive;
   }

   public float getEntityAlpha() {
      return !this.isToggled() ? 1.0F : this.lastEntityAlpha;
   }

   public boolean shouldHideEntityShadow() {
      return !this.isToggled() ? false : this.shadowSetting.getValue();
   }

   public boolean areHandsFullyHidden() {
      if (!this.isToggled()) {
         return false;
      } else {
         return this.handsSetting.getValue() && !ClientContext.shaderRegistry.renderingHands && !ClientContext.shaderRegistry.renderingIntoTarget
            ? this.handOpacitySetting.getValue() == 0.0F
            : false;
      }
   }

   @EventHandler
   private void onWorldRenderFinished(WorldRenderEvent.Main event) {
      this.worldRenderPassActive = false;
   }

   public boolean shouldHideBlockBreakingParticles() {
      if (!this.isToggled()) {
         return false;
      } else {
         return !this.particlesSetting.getValue() ? false : this.blockBreakingParticlesSetting.getValue();
      }
   }

   private void pruneExcessItems() {
      if (!this.isWorldUnavailable()) {
         if (this.itemsSetting.getValue()) {
            IntArrayList excessIds = new IntArrayList(50);
            int itemCount = 0;
            ClientLevel level = ClientAccess.argusclientMinecraft.level;

            for (Entity entity : level.entitiesForRendering()) {
               if (entity instanceof ItemEntity) {
                  int previousCount = itemCount++;
                  Integer maximum = this.limitSetting.getValue();
                  if (previousCount >= maximum) {
                     int id = entity.getId();
                     excessIds.add(id);
                  }
               }
            }

            IntConsumer removeItem = idx -> {
               ClientLevel removalLevel = ClientAccess.argusclientMinecraft.level;
               int entityId = idx;
               RemovalReason reason = RemovalReason.KILLED;
               removalLevel.removeEntity(entityId, reason);
            };
            excessIds.forEach(removeItem);
         }
      }
   }

   public float getArmorOpacity() {
      if (this.isToggled()) {
         Setting<Boolean> armor = this.armorSetting;
         if (armor.getValue()) {
            float alpha = this.armorOpacitySetting.getValue();
            if (alpha == 0.0F) {
               return 0.0F;
            }

            return Math.max(alpha, 0.11F);
         }
      }

      return 1.0F;
   }

   @EventHandler
   private void onEntityRenderCheck(EntityRenderEvent.Pre event) {
      Entity entity = event.getEntity();
      if (this.shouldHideEntity(entity)) {
         if (event.getEntity() != ClientAccess.argusclientMinecraft.player) {
            Setting<NoRenderModule.RemovalChoice> removal = this.removalSetting;
            if (removal.getValue() == NoRenderModule.RemovalChoice.FULL) {
               event.cancel();
            }
         }
      }
   }

   public void applyFogSettings(FogData fog, int viewDistance) {
      Setting<Float> range = this.rangeSetting;
      if (range.isAtMaximum()) {
         float distance = Math.max(1024.0F, viewDistance * 160.0F);
         fog.renderDistanceEnd = distance;
         fog.renderDistanceStart = distance;
         if (this.skySetting.getValue()) {
            fog.environmentalEnd = distance;
            fog.environmentalStart = distance;
         }
      } else {
         fog.renderDistanceStart = 0.0F;
         Float configuredRange = this.rangeSetting.getValue();
         float scaledRange = 0.5F * configuredRange;
         fog.renderDistanceEnd = scaledRange * (viewDistance * 16);
         Setting<Boolean> sky = this.skySetting;
         if (sky.getValue()) {
            fog.environmentalStart = 0.0F;
            Float configuredSkyRange = this.rangeSetting.getValue();
            float scaledSkyRange = 0.5F * configuredSkyRange;
            fog.environmentalEnd = scaledSkyRange * (viewDistance * 16);
         }
      }
   }

   @EventHandler
   private void onStatusEffectRender(StatusEffectQueryEvent event) {
      Setting<Boolean> blindness = this.blindnessSetting;
      if (blindness.getValue() && event.getEffect() == MobEffects.BLINDNESS) {
         event.cancel();
      }

      Setting<Boolean> darkness = this.darknessSetting;
      if (darkness.getValue() && event.getEffect() == MobEffects.DARKNESS) {
         event.cancel();
      }
   }

   @EventHandler(priority = -100)
   private void updateEntityAlpha(LivingEntityModelSubmitEvent event) {
      Setting<Boolean> noCluster = this.noClusterSetting;
      if (noCluster.getValue()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 playerPosition = player.position();
         Vec3 entityPosition = event.getEntity().position();
         double distance = playerPosition.distanceTo(entityPosition);
         float width = event.getEntity().getBbWidth();
         if (distance <= width && event.getEntity() != ClientAccess.argusclientMinecraft.player) {
            float distanceRatio = (float)(distance / width);
            float alphaValue = this.clusterAlphaSetting.getValue().intValue();
            float minimumAlpha = alphaValue / 255.0F;
            float alpha = minimumAlpha + distanceRatio * (1.0F - minimumAlpha);
            event.setAlpha(alpha);
            this.lastEntityAlpha = event.getAlpha();
            return;
         }

         this.lastEntityAlpha = 1.0F;
      } else {
         this.lastEntityAlpha = 1.0F;
      }
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      if (event.getEntity() instanceof ItemEntity) {
         if (!this.isWorldUnavailable()) {
            if (this.itemsSetting.getValue()) {
               int itemCount = 0;
               ClientLevel level = ClientAccess.argusclientMinecraft.level;

               for (Entity entity : level.entitiesForRendering()) {
                  if (entity instanceof ItemEntity) {
                     int previousCount = itemCount++;
                     Integer maximum = this.limitSetting.getValue();
                     if (previousCount > maximum) {
                        event.cancel();
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   public boolean shouldHideEntityType(EntityType type) {
      if (!this.isToggled()) {
         return false;
      }

      if (!this.entitiesSetting.getValue()) {
         return false;
      }

      Setting<ListFilterMode> entitySelectionSetting = this.entitySelectionSetting;
      ListFilterMode selection = entitySelectionSetting.getValue();
      Collection selectedTypes = this.entityTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   public boolean shouldHideParticle(ParticleOptions options) {
      if (!this.isToggled()) {
         return false;
      }

      if (!this.particlesSetting.getValue()) {
         return false;
      }

      ListFilterMode selection = this.particleSelectionSetting.getValue();
      ParticleType type = options.getType();
      Collection selectedTypes = this.particleTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   public boolean shouldHideBlockEntityType(BlockEntityType type) {
      if (!this.isToggled()) {
         return false;
      }

      if (!this.tileEntitiesSetting.getValue()) {
         return false;
      }

      ListFilterMode selection = this.blockEntitySelectionSetting.getValue();
      Collection selectedTypes = this.tileTypesSetting.getValue();
      return !ListFilterMode.matches(selection, type, selectedTypes);
   }

   @EventHandler
   private void onNameTagRender(NameTagRenderEvent event) {
      Setting<Boolean> nameTags = this.nameTagsSetting;
      if (nameTags.getValue()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onParticleEngineAddEvent(ParticleEngineAddEvent event) {
      Particle particle = event.getParticle();
      if (particle instanceof SmokeParticle smoke) {
         double speedSquared = smoke.xd * smoke.xd + smoke.yd * smoke.yd + smoke.zd * smoke.zd;
         double speed = Math.sqrt(speedSquared);
         if (speed > 0.25) {
            Setting<Boolean> explosions = this.explosionsSetting;
            if (explosions.getValue()) {
               event.cancel();
            }
         }

         if (speed <= 0.25) {
            Setting<Boolean> fire = this.blockFireSetting;
            if (fire.getValue()) {
               event.cancel();
            }
         }
      }

      if (particle instanceof HugeExplosionParticle || particle instanceof ExplodeParticle) {
         Setting<Boolean> explosions = this.explosionsSetting;
         if (explosions.getValue()) {
            event.cancel();
         }
      }

      Setting<Boolean> potions = this.potionParticlesSetting;
      if (potions.getValue() && particle instanceof SpellParticle) {
         event.cancel();
      }
   }

   private boolean isVineLikeBlock(BlockState state) {
      if (state.getBlock() instanceof VineBlock) {
         return true;
      }

      Block caveVines = Blocks.CAVE_VINES;
      if (state.is(caveVines)) {
         return true;
      }

      Block caveVinesPlant = Blocks.CAVE_VINES_PLANT;
      if (state.is(caveVinesPlant)) {
         return true;
      }

      Block glowLichen = Blocks.GLOW_LICHEN;
      return state.is(glowLichen);
   }

   @EventHandler
   private void onWorldRenderStarted(WorldRenderEvent.BeforeBufferSource event) {
      this.worldRenderPassActive = true;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSetChunkCacheRadiusPacket) {
         Setting<Boolean> chunks = this.chunkRadiusPacketsSetting;
         if (chunks.getValue()) {
            event.cancel();
         }
      }

      if (this.fireSetting.getValue()) {
         if (WorldUtils.getDimension() == DimensionType.THE_END) {
            if (event.getPacket() instanceof ClientboundBlockUpdatePacket update && this.pendingCrystalFirePosition != null) {
               if (!update.getPos().equals(this.pendingCrystalFirePosition)) {
                  return;
               }

               BlockState state = update.getBlockState();
               Block fireBlock = Blocks.FIRE;
               if (!state.is(fireBlock)) {
                  return;
               }

               this.pendingCrystalFirePosition = null;
               event.cancel();
            }

            if (event.getPacket() instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityTypes.END_CRYSTAL) {
               double x = spawn.getX();
               double y = spawn.getY();
               double z = spawn.getZ();
               this.pendingCrystalFirePosition = BlockPos.containing(x, y, z);
            }
         }
      }
   }

   public boolean shouldHideBlockEntity(BlockState state) {
      if (!this.isToggled() || !this.tileEntitiesSetting.getValue()) {
         return false;
      }

      Map<Block, BlockEntityType<?>> typesByBlock = BLOCK_ENTITY_TYPES_BY_BLOCK;
      Block block = state.getBlock();
      BlockEntityType<?> type = typesByBlock.get(block);
      return type == null ? false : this.shouldHideBlockEntityType(type);
   }

   private static NoRenderModule glyphShadowModule;

   /**
    * Shadow offset for every glyph drawn. Called once per character per frame, so the module is
    * resolved once instead of through the registry on each call.
    */
   public static float textShadowOffset() {
      NoRenderModule module = glyphShadowModule;
      if (module == null) {
         module = ClientContext.getModule(NoRenderModule.class);
         if (module == null) {
            return 1.0F;
         }

         glyphShadowModule = module;
      }

      return module.isToggled() && module.textShadowSetting.getValue() ? 0.6F : 1.0F;
   }

   public NoRenderModule() {
      super("NoRender", "Removes the annoying stuff from your screen.", ModuleCategory.RENDER);
      this.setDrawn(false);
      this.itemsSetting.setChangeCallback(this::pruneExcessItems);
      this.tileEntitiesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.tileTypesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockEntitySelectionSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockFireSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.vinesSetting.setChangeCallback(WorldUtils::reloadRenderer);
      this.blockFireSetting.setConfigName("FireBlock");
      this.entityGroupSetting.setConfigName("EntitiesPage");
      this.particleSelectionSetting.setConfigName("ParticleSelection");
      this.entitySelectionSetting.setConfigName("EntitySelection");
      this.handOpacitySetting.setConfigName("HandsOpacity");
      this.blockFireSetting.setConfigName("FireBlock");
   }

   static {
      Object2ObjectOpenHashMap<Block, BlockEntityType<?>> typesByBlock = new Object2ObjectOpenHashMap();
      Registry<BlockEntityType<?>> blockEntityTypes = BuiltInRegistries.BLOCK_ENTITY_TYPE;
      blockEntityTypes.forEach(blockEntityType -> {
         Set<Block> validBlocks = blockEntityType.validBlocks;
         validBlocks.forEach(block -> typesByBlock.put(block, blockEntityType));
      });
      BLOCK_ENTITY_TYPES_BY_BLOCK = typesByBlock;
   }

   public enum RemovalChoice implements NamedEntry {
      PARTIAL("Partial"),
      FULL("Remove");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      RemovalChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}

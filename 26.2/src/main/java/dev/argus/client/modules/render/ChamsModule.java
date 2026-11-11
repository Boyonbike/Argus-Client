package dev.argus.client.modules.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EntityRenderEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.GlintTextureTransformEvent;
import dev.argus.client.events.PlayerTotemEvent;
import dev.argus.client.events.RenderTypeDrawEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.render.ChamsColorProvider;
import dev.argus.client.render.ClientRenderPipelines;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.EntityChamsRenderer;
import dev.argus.client.render.EntityRenderRelation;
import dev.argus.client.render.ForcedEntityLayers;
import dev.argus.client.render.GameOptionsOverrides;
import dev.argus.client.render.MaskShaderRenderer;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.AutoCrystalInstantChoice;
import dev.argus.client.settings.options.ChamsStyle;
import dev.argus.client.utils.combat.EndCrystalState;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.SidePlayer;
import dev.argus.mixin.GameRendererAccessor;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ChamsModule extends Module {
   private static final Identifier SHINE_TEXTURE = Identifier.fromNamespaceAndPath("argusclient-mount", "textures/shine.png");
   private static final Vector3f GLINT_COLOR_VECTOR = new Vector3f(1.0F, 1.0F, 1.0F);
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static AnimationsModule animationsModule = ClientContext.getModule(AnimationsModule.class);
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   public final Setting<Boolean> cullingSetting = this.add(new BooleanSetting("Culling", false));
   public final Setting<Boolean> blendSetting = this.add(new BooleanSetting("Blend", false));
   private final Setting<Boolean> globalExtraLayerSetting = this.add(new BooleanSetting("ExtraLayer", true));
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 5.0F));
   public final Setting<Boolean> fadeSetting;
   public final Setting<Integer> fadeRadiusSetting;
   public final Setting<Boolean> modelSetting;
   public final Setting<Boolean> xQZSetting;
   public final Setting<Integer> opacitySetting;
   public final Setting<Boolean> shineSetting;
   public final Setting<Float> speedSetting;
   private final Setting<Float> strengthSetting;
   public final Setting<Integer> progressSetting;
   private final Setting<Integer> spreadSetting;
   private final Setting<Boolean> bloomSetting;
   private final Setting<Float> radiusSetting;
   private final Setting<Float> factorSetting;
   private final Setting<Float> overlapSetting;
   private final Setting<ChamsStyle> animalsSetting;
   private final Setting<ChamsStyle> hostilesSetting;
   public final Setting<ChamsStyle> playerStyleSetting;
   public final Setting<ChamsStyle> selfSetting;
   public final Setting<ChamsStyle> crystalStyleSetting;
   private final Setting<Boolean> popSetting;
   private final Setting<ChamsWireframeChoice> wireframeSetting;
   private final Setting<Boolean> extraLayerSetting;
   private final Setting<Boolean> easeSetting;
   private final Setting<Boolean> deathSetting;
   private final Setting<Boolean> animateSetting;
   private final Setting<Boolean> boostSetting;
   private final Setting<Float> timeSetting;
   private final Setting<Float> motionSetting;
   public final Setting<Integer> playersSetting;
   public final Setting<Integer> crystalsSetting;
   public final Setting<Integer> othersSetting;
   public final Setting<Boolean> colorsSetting;
   public final Setting<Boolean> friendsSetting;
   public final Setting<Color> fillSetting;
   public final Setting<Color> outlineSetting;
   public final Setting<Color> shineColorSetting;
   protected final Setting<Color> popFillSetting;
   protected final Setting<Color> popLineSetting;
   public static boolean renderingShineModels = false;
   public static boolean renderingGlintBatch = false;
   private final Map<SidePlayer, Long> popGhostTimes;
   private EntityRenderRelation activeGlintRelation;
   private boolean renderingBaseModel;

   private void renderBloomMask() {
      Color fillColorForMix = this.fillSetting.getValue();
      Color outlineColorForMix = this.outlineSetting.getValue();
      Color maskColor = ColorUtils.blend(fillColorForMix, outlineColorForMix, 0.5F);
      Boolean blendValue = this.blendSetting.getValue();
      float resolvedMaskOpacity;
      if (blendValue) {
         resolvedMaskOpacity = 1.0F;
      } else {
         int capturedFillAlpha = this.fillSetting.getValue().getAlpha();
         int outlineAlpha = this.outlineSetting.getValue().getAlpha();
         int fillAlpha = capturedFillAlpha;
         float maximumAlpha = Math.max(fillAlpha, outlineAlpha);
         resolvedMaskOpacity = maximumAlpha / 255.0F;
      }

      float maskOpacity = resolvedMaskOpacity;
      RenderUtils.clearColor(MaskShaderRenderer.MASK_TARGET, maskColor);
      RenderTarget mainTarget = ClientAccess.argusclientMinecraft.getMainRenderTarget();
      RenderTargetUtils.setMainRenderTarget(MaskShaderRenderer.MASK_TARGET);
      EntityChamsRenderer.flush();
      Boolean blendEnabled = this.blendSetting.getValue();
      if (blendEnabled) {
         RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET, ClientRenderPipelines.BLIT_LIGHTNING);
      } else {
         RenderTarget maskTarget = MaskShaderRenderer.MASK_TARGET;
         GpuTextureView mainColorView = mainTarget.getColorTextureView();
         maskTarget.blitAndBlendToTexture(mainColorView);
      }

      float bloomRadius = this.radiusSetting.getValue();
      Float factorValue = this.factorSetting.getValue();
      float bloomFactor = factorValue;
      Setting<Float> overlapSettingSnapshot = this.overlapSetting;
      Float overlapValue = overlapSettingSnapshot.getValue();
      MaskShaderRenderer.setParameters(bloomRadius, bloomFactor, maskOpacity, overlapValue);
      MaskShaderRenderer.renderEffect("bloom_mask");
      RenderTargetUtils.setMainRenderTarget(mainTarget);
      Setting<Boolean> compositeBlendOption = this.blendSetting;
      Boolean compositeBlendEnabled = compositeBlendOption.getValue();
      if (compositeBlendEnabled) {
         RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET, ClientRenderPipelines.BLIT_LIGHTNING);
      } else {
         RenderTarget finalMaskTarget = MaskShaderRenderer.MASK_TARGET;
         GpuTextureView finalColorView = mainTarget.getColorTextureView();
         finalMaskTarget.blitAndBlendToTexture(finalColorView);
      }
   }

   @EventHandler(priority = -1000)
   private void onWorldRenderFinished(WorldRenderEvent.Main event) {
      Boolean bloomValue = this.bloomSetting.getValue();
      if (bloomValue || this.blendSetting.getValue()) {
         EntityChamsRenderer.flush();
      }

      Float lineWidthValue = this.lineWidthSetting.getValue();
      EntityChamsRenderer.setLineWidth(lineWidthValue.floatValue());

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (this.shouldRenderEntityInRange(entity)) {
            ChamsColorProvider style = getStyleSetting(this, entity).getValue();
            if (style.isEnabled()) {
               Boolean modelValue = this.modelSetting.getValue();
               if (modelValue) {
                  Boolean xQZValue = this.xQZSetting.getValue();
                  if (xQZValue) {
                     NoRenderModule noRenderSnapshot = noRenderModule;
                     if (!noRenderSnapshot.shouldHideEntity(entity)) {
                        Integer opacityValue = this.opacitySetting.getValue();
                        if (opacityValue > 0) {
                           this.renderingBaseModel = true;
                           float baseOpacity = this.opacitySetting.getValue().intValue();
                           RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, baseOpacity / 100.0F);
                           RenderUtils renderer = RenderUtils.INSTANCE;
                           float partialTick = event.getPartialTick();
                           PoseStack poseStackSnapshot = event.getPoseStack();
                           RenderBuffers renderBuffers = ClientAccess.argusclientMinecraft.renderBuffers();
                           renderer.renderEntity(entity, partialTick, poseStackSnapshot, renderBuffers.bufferSource());
                           this.renderingBaseModel = false;
                        }
                     }
                  }
               }

               if (entity instanceof ForcedEntityLayers forcedLayers) {
                  Boolean extraLayers = this.globalExtraLayerSetting.getValue();
                  forcedLayers.argusclient$setForcedLayers(!extraLayers);
               }

               ChamsColorProvider colorProvider = getStyleSetting(this, entity).getValue();
               PoseStack colorPoses = event.getPoseStack();
               colorProvider.renderEntity(this, entity, colorPoses);
            }
         }
      }

      Setting<Boolean> throughWallOption = this.xQZSetting;
      Boolean throughWallEnabled = throughWallOption.getValue();
      if (throughWallEnabled) {
         Setting<Integer> wallOpacityOption = this.opacitySetting;
         Integer wallOpacityValue = wallOpacityOption.getValue();
         float wallOpacity = wallOpacityValue.intValue();
         RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, wallOpacity / 100.0F);
         RenderUtils.INSTANCE.flushEntities();
         RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      Setting<Boolean> popSettingSnapshot = this.popSetting;
      Boolean popValue = popSettingSnapshot.getValue();
      if (popValue) {
         this.renderPopGhosts(event);
      }

      Setting<Boolean> shineSettingSnapshot = this.shineSetting;
      Boolean shineValue = shineSettingSnapshot.getValue();
      if (shineValue) {
         EntityRenderRelation.forEachMesh(this::renderGlintBatch);
      }

      Setting<Boolean> bloomOption = this.bloomSetting;
      Boolean bloomEnabled = bloomOption.getValue();
      if (bloomEnabled) {
         this.renderBloomMask();
      } else {
         Setting<Boolean> blendOption = this.blendSetting;
         Boolean blendEnabled = blendOption.getValue();
         if (blendEnabled) {
            EntityChamsRenderer.drawMeshes(ClientRenderTypes.worldFillLightning(), ClientRenderTypes.worldLinesLightning());
         }
      }
   }

   @EventHandler
   private void onGlintTextureTransform(GlintTextureTransformEvent event) {
      if (renderingGlintBatch) {
         int capturedSpread = this.spreadSetting.getValue();
         int spread = capturedSpread;
         float glintScale = 6.25F * GlintModule.getSpreadMultiplier(spread);
         event.setScale(glintScale);
      }
   }

   private void renderGlintBatch(EntityRenderRelation relation, MeshBuffer buffer) {
      if (!buffer.isEmpty()) {
         this.activeGlintRelation = relation;
         OptionInstance optionSnapshot = ClientAccess.argusclientMinecraft.options.glintSpeed();
         double previousSpeed = (Double)optionSnapshot.get();
         OptionInstance glintStrengthOption = ClientAccess.argusclientMinecraft.options.glintStrength();
         double previousStrength = (Double)glintStrengthOption.get();
         OptionInstance speedOption = ClientAccess.argusclientMinecraft.options.glintSpeed();
         double requestedSpeed = this.speedSetting.getValue().doubleValue();
         GameOptionsOverrides.forceSetOptionValue(speedOption, requestedSpeed);
         double capturedStrength = this.strengthSetting.getValue().floatValue();
         this.updateGlobalGlintStrength(capturedStrength);
         renderingGlintBatch = true;
         MeshDrawContext.setRenderType(ClientRenderTypes.shineGlint());
         buffer.draw();
         renderingGlintBatch = false;
         this.updateGlobalGlintStrength(previousStrength);
         speedOption = ClientAccess.argusclientMinecraft.options.glintSpeed();
         speedOption.set(previousSpeed);
      }
   }

   public boolean shouldRenderGlintForEntity(Entity entity) {
      ChamsColorProvider style = getStyleSetting(this, entity).getValue();
      boolean usesStyleOrIsSelf = style.isEnabled() || entity == ClientAccess.argusclientMinecraft.player;
      if (this.isToggled() && usesStyleOrIsSelf) {
         Boolean shineValue = this.shineSetting.getValue();
         if (shineValue && renderingShineModels) {
            return true;
         }
      }

      return false;
   }

   public boolean shouldRenderEntity(Entity entity, boolean checkRange) {
      if (LogoutSpotsModule.isRenderingTexturedModel()) {
         return false;
      }

      boolean outsideFrustum;
      try {
         AABB entityBounds = entity.getBoundingBox();
         outsideFrustum = !RotationManager.isVisible(entityBounds);
         Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
         if (checkRange) {
            Vec3 entityPosition = entity.position();
            double cameraDistance = cameraPosition.distanceTo(entityPosition);
            EntityType entityType = entity.getType();
            if (cameraDistance > getRenderRange(this, entityType)) {
               return false;
            }
         }
      } catch (Exception unavailableEntity) {
         return false;
      }

      if (outsideFrustum) {
         return false;
      }

      if (EntityUtils.isFirstPersonCameraEntity(entity)) {
         return false;
      }

      if (entity instanceof EndCrystal crystal) {
         long crystalAgeMillis = System.currentTimeMillis() - ((EndCrystalState)crystal).getSpawnTime();
         int latency = ClientContext.serverStateManager.getLatencyMillis();
         if (crystalAgeMillis > Math.max(latency, 50)) {
            int crystalTicks = crystal.tickCount;
            if (crystalTicks < 10) {
               AutoCrystalModule autoCrystalSnapshot = autoCrystalModule;
               if (autoCrystalSnapshot.isToggled()) {
                  AutoCrystalInstantChoice instantChoice = autoCrystalModule.instantSetting.getValue();
                  if (instantChoice.isEnabled() && ((EndCrystalState)crystal).isClientAttacked()) {
                     return false;
                  }
               }
            }
         }

         ChamsStyle styleChoice = this.crystalStyleSetting.getValue();
         return styleChoice.isEnabled();
      } else if (!(entity instanceof Player)) {
         if (entity instanceof AgeableMob) {
            ChamsStyle styleChoice4 = this.animalsSetting.getValue();
            if (styleChoice4.isEnabled()) {
               return true;
            }
         }

         if (entity instanceof Enemy) {
            ChamsStyle styleChoice5 = this.hostilesSetting.getValue();
            if (styleChoice5.isEnabled()) {
               return true;
            }
         }

         return false;
      } else {
         FreecamModule freecamSnapshot = freecamModule;
         boolean selfVisibleInCamera = true;
         if (!freecamSnapshot.isToggled()) {
            selfVisibleInCamera = ClientAccess.argusclientMinecraft.options.getCameraType() != CameraType.FIRST_PERSON;
         }

         if (entity != ClientAccess.argusclientMinecraft.player) {
            ChamsStyle styleChoice2 = this.playerStyleSetting.getValue();
            return styleChoice2.isEnabled();
         } else {
            ChamsStyle styleChoice3 = this.selfSetting.getValue();
            return styleChoice3.isEnabled() && selfVisibleInCamera;
         }
      }
   }

   public boolean shouldRenderThroughWalls(Entity entity) {
      if (this.isToggled()) {
         Boolean xQZValue = this.xQZSetting.getValue();
         if (xQZValue) {
            return this.shouldRenderEntityInRange(entity);
         }
      }

      return false;
   }

   public float getDistanceFade(Entity entity) {
      float fadePercent = this.fadeRadiusSetting.getValue().intValue();
      float fadeStart = fadePercent / 100.0F;
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      Vec3 entityPosition = entity.position();
      double cameraDistance = cameraPosition.distanceTo(entityPosition);
      EntityType entityType = entity.getType();
      double relativeDistance = cameraDistance / getRenderRange(this, entityType);
      if (relativeDistance < fadeStart) {
         return 1.0F;
      }

      relativeDistance -= fadeStart;
      double unclampedFade = relativeDistance / (1.0F - fadeStart);
      double fadeFraction = unclampedFade;
      return (float)(1.0 - Mth.clamp(fadeFraction, 0.0, 1.0));
   }

   private void updateGlobalGlintStrength(double strength) {
      GlobalSettingsUniform globalSettings = ((GameRendererAccessor)ClientAccess.argusclientMinecraft.gameRenderer).getGlobalSettings();
      int windowWidth = ClientAccess.argusclientMinecraft.getWindow().getWidth();
      int windowHeight = ClientAccess.argusclientMinecraft.getWindow().getHeight();
      long gameTime = ClientAccess.argusclientMinecraft.level == null ? 0L : ClientAccess.argusclientMinecraft.level.getGameTime();
      DeltaTracker deltaTracker = ClientAccess.argusclientMinecraft.getDeltaTracker();
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera();
      OptionInstance filteringOption = ClientAccess.argusclientMinecraft.options.textureFiltering();
      boolean rgssFiltering = filteringOption.get() == TextureFilteringMethod.RGSS;
      Options currentOptions = ClientAccess.argusclientMinecraft.options;
      globalSettings.update(windowWidth, windowHeight, strength, gameTime, deltaTracker, currentOptions.getMenuBackgroundBlurriness(), camera.position(), rgssFiltering);
   }

   private static Setting<ChamsStyle> getStyleSetting(ChamsModule owner, Entity entity) {
      if (entity == ClientAccess.argusclientMinecraft.player) {
         return owner.selfSetting;
      } else if (entity instanceof EndCrystal) {
         return owner.crystalStyleSetting;
      } else if (entity instanceof Player) {
         return owner.playerStyleSetting;
      } else {
         return entity instanceof AgeableMob ? owner.animalsSetting : owner.hostilesSetting;
      }
   }

   @EventHandler
   private void onRenderTypeDrawEvent(RenderTypeDrawEvent event) {
      if (EventStage.Post != event.getStage() && renderingGlintBatch) {
         TextureManager capturedTextureManager = Minecraft.getInstance().getTextureManager();
         Identifier shineIdentifier = SHINE_TEXTURE;
         AbstractTexture shineTexture = capturedTextureManager.getTexture(shineIdentifier);
         GpuTextureView shineView = shineTexture.getTextureView();
         event.setOverride(shineView);
      }
   }

   private static float getRenderRange(ChamsModule owner, EntityType entityType) {
      if (entityType == EntityType.PLAYER) {
         return owner.playersSetting.getValue().intValue();
      } else {
         return entityType == EntityType.END_CRYSTAL ? owner.crystalsSetting.getValue().intValue() : owner.othersSetting.getValue().intValue();
      }
   }

   @EventHandler
   private void onEntityRender(EntityRenderEvent.Pre event) {
      if (!ClientContext.shaderRegistry.renderingIntoTarget) {
         Entity entity = event.getEntity();
         Vec3 positionSnapshot = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
         Boolean cullingValue = this.cullingSetting.getValue();
         boolean outsideCullingRange = false;
         if (cullingValue) {
            Vec3 entityPosition = entity.position();
            double cameraDistance = positionSnapshot.distanceTo(entityPosition);
            EntityType entityType = entity.getType();
            outsideCullingRange = cameraDistance > getRenderRange(this, entityType);
         }

         if (outsideCullingRange && this.shouldRenderEntity(entity, false) && !this.renderingBaseModel) {
            event.cancel();
         }

         Boolean modelValue = this.modelSetting.getValue();
         if (!modelValue && this.shouldRenderEntityInRange(entity) && !this.renderingBaseModel) {
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onPlayerPop(PlayerTotemEvent event) {
      Boolean popValue = this.popSetting.getValue();
      if (popValue) {
         PlayerTotemEvent.Kind expectedKind = PlayerTotemEvent.Kind.DEATH;
         if (event.getKind() == expectedKind) {
            Boolean deathValue = this.deathSetting.getValue();
            if (!deathValue) {
               return;
            }
         }

         Player player = event.getPlayer();
         if (player != ClientAccess.argusclientMinecraft.player) {
            SidePlayer ghost = new SidePlayer(ClientAccess.argusclientMinecraft.level);
            ghost.copyPlayerState(player);
            WalkAnimationState walkAnimation = ghost.walkAnimation;
            float walkPosition = ghost.walkAnimation.position;
            walkAnimation.position = walkPosition * 2.0F;
            Boolean animateValue = this.animateSetting.getValue();
            if (!animateValue) {
               ghost.walkAnimation.position = 0.0F;
               ghost.walkAnimation.speed = 0.0F;
               ghost.walkAnimation.speedOld = 0.0F;
            } else {
               Boolean boostValue = this.boostSetting.getValue();
               if (boostValue) {
                  boolean boostAnimation = true;
                  ghost.setAnimationBoosted(boostAnimation);
                  ghost.attackAnim = 0.0F;
                  walkAnimation = ghost.walkAnimation;
                  double randomSample = Math.random();
                  double scaledRandom = randomSample * 0.8F;
                  walkAnimation.speed = (float)(scaledRandom + 0.2F);
                  ghost.walkAnimation.speedOld = ghost.walkAnimation.speed;
                  ghost.walkAnimation.position = 0.0F;
                  InteractionHand mainHand = InteractionHand.MAIN_HAND;
                  ItemStack mainHandEmptyStack = ItemStack.EMPTY;
                  ghost.setItemInHand(mainHand, mainHandEmptyStack);
                  InteractionHand offHand = InteractionHand.OFF_HAND;
                  ItemStack offHandEmptyStack = ItemStack.EMPTY;
                  ghost.setItemInHand(offHand, offHandEmptyStack);
               }
            }

            synchronized (this.popGhostTimes) {
               Map<SidePlayer, Long> ghostTimes = this.popGhostTimes;
               long createdAt = System.currentTimeMillis();
               ghostTimes.putIfAbsent(ghost, createdAt);
            }
         }
      }
   }

   public boolean shouldRenderEntityInRange(Entity entity) {
      boolean checkRange = true;
      return this.shouldRenderEntity(entity, checkRange);
   }

   private EntityRenderRelation getEntityRelation(Entity entity) {
      Boolean friendsValue = this.friendsSetting.getValue();
      if (!friendsValue) {
         return EntityRenderRelation.DEFAULT;
      }

      SocialRegistry relationships = ClientContext.socialRegistry;
      if (relationships.isFriendEntity(entity)) {
         return EntityRenderRelation.FRIEND;
      }

      relationships = ClientContext.socialRegistry;
      return relationships.isEnemyEntity(entity) ? EntityRenderRelation.ENEMY : EntityRenderRelation.DEFAULT;
   }

   public boolean usesStaticPlayerAnimation() {
      AnimationsModule animationsSnapshot = animationsModule;
      return animationsSnapshot.isStaticPlayerAnimationEnabled();
   }

   public Vector3f getGlintColor() {
      EntityRenderRelation relation = this.activeGlintRelation;

      Setting<Color> colorSetting = switch (relation) {
         case DEFAULT -> this.shineColorSetting;
         case FRIEND -> colorsModule.friendColorSetting;
         case ENEMY -> colorsModule.enemyColorSetting;
      };
      Boolean friendsValue = this.friendsSetting.getValue();
      if (!friendsValue) {
         colorSetting = this.shineColorSetting;
      }

      Color color = colorSetting.getValue();
      Vector3f capturedColorVector = GLINT_COLOR_VECTOR;
      float capturedRed = color.getRed();
      float capturedGreen = color.getGreen();
      float blue = color.getBlue();
      capturedColorVector.set(capturedRed, capturedGreen, blue);
      Vector3f colorVectorForDivide = GLINT_COLOR_VECTOR;
      colorVectorForDivide.div(255.0F);
      return GLINT_COLOR_VECTOR;
   }

   private void renderPopGhosts(WorldRenderEvent event) {
      synchronized (this.popGhostTimes) {
         this.popGhostTimes.entrySet().removeIf(entry -> {
            float expiryTime = (float)entry.getValue().longValue();
            Setting<Float> timeSettingSnapshot = this.timeSetting;
            Float timeValue = timeSettingSnapshot.getValue();
            float durationSeconds = timeValue;
            expiryTime += durationSeconds * 1000.0F;
            return expiryTime < (float)System.currentTimeMillis();
         });
         this.popGhostTimes.forEach((ghost, createdAt) -> {
            Boolean extraLayerValue = this.extraLayerSetting.getValue();
            if (!extraLayerValue) {
               ((ForcedEntityLayers)ghost).argusclient$setForcedLayers(true);
            }

            long now = System.currentTimeMillis();
            float elapsedMillis = (float)(now - createdAt);
            Setting<Float> timeSettingSnapshot = this.timeSetting;
            Float timeValue = timeSettingSnapshot.getValue();
            float durationSeconds = timeValue;
            float unclampedElapsedFraction = elapsedMillis / (durationSeconds * 1000.0F);
            float elapsedFraction = unclampedElapsedFraction;
            float opacity = 1.0F - Mth.clamp(elapsedFraction, 0.0F, 1.0F);
            Boolean easeValue = this.easeSetting.getValue();
            if (easeValue) {
               double remainingOpacity = 1.0F - opacity;
               double easeInput = remainingOpacity;
               opacity = (float)(1.0 - Math.pow(easeInput, 3.0));
            }

            SidePlayer ghostForX = ghost;
            double capturedGhostX = ghostForX.getX();
            SidePlayer ghostForBaseY = ghost;
            double baseY = ghostForBaseY.getCapturedPosition().y;
            float motionProgress = 1.0F - opacity;
            Float motionValue = this.motionSetting.getValue();
            double movedY = baseY + motionProgress * motionValue;
            SidePlayer ghostForZ = ghost;
            double ghostZ = ghostForZ.getZ();
            double ghostY = movedY;
            double ghostX = capturedGhostX;
            SidePlayer ghostForPosition = ghost;
            ghostForPosition.setPos(ghostX, ghostY, ghostZ);
            ChamsWireframeChoice wireframe = this.wireframeSetting.getValue();
            Color[] wireframeColors = wireframe.getColors(this);
            Color lineColorForAlpha;
            Color lineColor = lineColorForAlpha = wireframeColors[0];
            float lineAlpha = lineColorForAlpha.getAlpha();
            Color fadedLineColor = ColorUtils.withAlpha(lineColor, lineAlpha / 255.0F * opacity);
            Color fillColorForAlpha;
            Color fillColor = fillColorForAlpha = wireframeColors[1];
            float fillAlpha = fillColorForAlpha.getAlpha();
            EntityChamsRenderer.setColors(fadedLineColor, ColorUtils.withAlpha(fillColor, fillAlpha / 255.0F * opacity));
            EntityChamsRenderer.renderEntity(event.getPoseStack(), ghost);
         });
      }
   }

   @EventHandler(priority = 200)
   private void onWorldRenderStarted(WorldRenderEvent.BeforeBufferSource event) {
      EntityCullingOverrides.disableCulling();

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (this.shouldRenderEntityInRange(entity)) {
            Boolean shineValue = this.shineSetting.getValue();
            if (shineValue) {
               renderingShineModels = true;
               event.getPoseStack().pushPose();
               RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
               RenderUtils.applyCameraRotation(event.getPoseStack());
               EntityChamsRenderer.setRelation(this.getEntityRelation(entity));
               EntityChamsRenderer.renderEntity(event.getPoseStack(), entity);
               event.getPoseStack().popPose();
               renderingShineModels = false;
            }
         }
      }

      EntityCullingOverrides.restoreCulling();
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      autoCrystalModule = (AutoCrystalModule)modules.getModule(AutoCrystalModule.class);
      noRenderModule = (NoRenderModule)modules.getModule(NoRenderModule.class);
      animationsModule = (AnimationsModule)modules.getModule(AnimationsModule.class);
      freecamModule = (FreecamModule)modules.getModule(FreecamModule.class);
      colorsModule = (ColorsModule)modules.getModule(ColorsModule.class);
      ChamsColorProvider.bindModuleRegistry(modules);
   }

   public ChamsModule() {
      super("Chams", "Wallhack on entities.", ModuleCategory.RENDER);
      Setting<Boolean> fadeRegistered = this.add(new BooleanSetting("Fade", false).markVisibilityDependency());
      this.fadeSetting = fadeRegistered;
      this.fadeRadiusSetting = this.add(new NumberSetting<>("FadeRadius", 75, 1, 100).withUnit("%").dependsOn(fadeRegistered));
      Setting<Boolean> modelRegistered = this.add(new BooleanSetting("Model", true).markVisibilityDependency());
      this.modelSetting = modelRegistered;
      Setting<Boolean> xQZRegistered = this.add(new BooleanSetting("XQZ", false).dependsOn(modelRegistered));
      this.xQZSetting = xQZRegistered;
      this.opacitySetting = this.add(new NumberSetting<>("Opacity", 100, 0, 100).withUnit("%").dependsOn(modelRegistered, xQZRegistered));
      Setting<Boolean> shineRegistered = this.add(new BooleanSetting("Shine", false).markVisibilityDependency().dependsOn(modelRegistered));
      this.shineSetting = shineRegistered;
      this.speedSetting = this.add(new NumberSetting<>("Speed", 0.5F, 0.0F, 2.0F).dependsOn(modelRegistered, shineRegistered));
      this.strengthSetting = this.add(new NumberSetting<>("Strength", 1.0F, 0.0F, 1.0F).dependsOn(modelRegistered, shineRegistered));
      Predicate<Object> progressVisibilityPredicate = ignored -> this.speedSetting.isAtMinimum();
      this.progressSetting = this.add(
         new NumberSetting<>("Progress", 0, 0, 100, progressVisibilityPredicate).withUnit("%").dependsOn(modelRegistered, shineRegistered)
      );
      this.spreadSetting = this.add(new NumberSetting<>("Spread", 1, 1, 5).dependsOn(modelRegistered, shineRegistered));
      Setting<Boolean> bloomRegistered = this.add(new BooleanSetting("Bloom", false).markVisibilityDependency());
      this.bloomSetting = bloomRegistered;
      this.radiusSetting = this.add(new NumberSetting<>("Radius", 8.0F, 1.0F, 20.0F).dependsOn(bloomRegistered));
      this.factorSetting = this.add(new NumberSetting<>("Factor", 1.0F, 0.5F, 2.0F).dependsOn(bloomRegistered));
      this.overlapSetting = this.add(new NumberSetting<>("Overlap", 1.0F, 0.0F, 3.0F).dependsOn(bloomRegistered));
      Setting<Boolean> targetsRegistered = this.add(new BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency());
      this.animalsSetting = this.add(new EnumSetting<>("Animals", ChamsStyle.OFF).dependsOn(targetsRegistered));
      this.hostilesSetting = this.add(new EnumSetting<>("Hostiles", ChamsStyle.OFF).dependsOn(targetsRegistered));
      this.playerStyleSetting = this.add(new EnumSetting<>("Players", ChamsStyle.OFF).dependsOn(targetsRegistered));
      this.selfSetting = this.add(new EnumSetting<>("Self", ChamsStyle.OFF).dependsOn(targetsRegistered));
      this.crystalStyleSetting = this.add(new EnumSetting<>("Crystals", ChamsStyle.OFF).dependsOn(targetsRegistered));
      Setting<Boolean> popRegistered = this.add(new BooleanSetting("Pop", false).markVisibilityDependency().dependsOn(targetsRegistered));
      this.popSetting = popRegistered;
      this.wireframeSetting = this.add(new EnumSetting<>("Wireframe", ChamsWireframeChoice.BOTH).dependsOn(targetsRegistered, popRegistered));
      this.extraLayerSetting = this.add(new BooleanSetting("ExtraLayer", false).dependsOn(targetsRegistered, popRegistered));
      this.easeSetting = this.add(new BooleanSetting("Ease", false).dependsOn(targetsRegistered, popRegistered));
      this.deathSetting = this.add(new BooleanSetting("Death", false).dependsOn(targetsRegistered, popRegistered));
      Setting<Boolean> animateRegistered = this.add(new BooleanSetting("Animate", true).dependsOn(targetsRegistered, popRegistered));
      this.animateSetting = animateRegistered;
      this.boostSetting = this.add(new BooleanSetting("Boost", false).dependsOn(targetsRegistered, popRegistered, animateRegistered));
      this.timeSetting = this.add(new NumberSetting<>("Time", 1.0F, 0.0F, 5.0F).dependsOn(targetsRegistered, popRegistered));
      this.motionSetting = this.add(new NumberSetting<>("Motion", 0.0F, -8.0F, 8.0F).dependsOn(targetsRegistered, popRegistered));
      Setting<Boolean> rangesRegistered = this.add(new BooleanSetting("Ranges", false).markAsGroup().markVisibilityDependency());
      this.playersSetting = this.add(new NumberSetting<>("Players", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered));
      this.crystalsSetting = this.add(new NumberSetting<>("Crystals", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered));
      this.othersSetting = this.add(new NumberSetting<>("Others", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered));
      Setting<Boolean> colorsRegistered = this.add(new BooleanSetting("Colors", false).markAsGroup().markVisibilityDependency());
      this.colorsSetting = colorsRegistered;
      this.friendsSetting = this.add(new BooleanSetting("Friends", false).dependsOn(colorsRegistered));
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(935172607, true)).dependsOn(colorsRegistered));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-4351489, true)).dependsOn(colorsRegistered));
      this.shineColorSetting = this.add(new ColorSetting("Shine", new Color(2076023295, true)).dependsOn(colorsRegistered));
      this.popFillSetting = this.add(new ColorSetting("PopFill", new Color(935172607, true)).dependsOn(colorsRegistered));
      this.popLineSetting = this.add(new ColorSetting("PopLine", new Color(12425727, true)).dependsOn(colorsRegistered));
      this.popGhostTimes = new HashMap<>();
      this.playersSetting.setConfigName("PlayerRange");
      this.crystalsSetting.setConfigName("CrystalRange");
      this.othersSetting.setConfigName("OtherRange");
      this.globalExtraLayerSetting.setConfigName("ExtraLayerGlobal");
      this.wireframeSetting.setConfigName("PopMode");
      this.shineColorSetting.setConfigName("ShineColor");
      this.spreadSetting.setConfigName("ShineSpread");
      this.speedSetting.setBoundaryLabel("Static", SettingLabelCondition.MIN);
   }
}

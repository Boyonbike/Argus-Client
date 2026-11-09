package dev.argus.client.modules.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.entity.EntityUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.Color;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ShaderModule extends Module {
   private static NameTagsModule nameTagsModule = ClientContext.getModule(NameTagsModule.class);
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   public final Setting<ShaderModule.ShaderChoice> shaderSetting = this.add(new EnumSetting<>(new String("Shader"), ShaderModule.ShaderChoice.SOLID));
   private final Setting<Integer> rangeSetting;
   public final Setting<Float> fadeSetting;
   public final Setting<Integer> lineWidthSetting;
   public final Setting<Boolean> fastLinesSetting;
   public final Setting<Float> stepSetting;
   public final Setting<Float> speedSetting;
   public final Setting<Float> rainbowSpeedSetting;
   public final Setting<Float> lineStrengthSetting;
   public final Setting<Float> fillStrengthSetting;
   public final Setting<Boolean> imageSetting;
   public final Setting<Float> overlayAlphaSetting;
   public final Setting<Boolean> glowSetting;
   public final Setting<Integer> qualitySetting;
   public final Setting<Float> weightSetting;
   public final Setting<Boolean> innerSetting;
   public final Setting<Boolean> decoratorSetting;
   public final Setting<ShaderModule.TypeChoice> typeSetting;
   public final Setting<Integer> radiusSetting;
   public final Setting<Float> alphaSetting;
   public final Setting<Boolean> smoothSetting;
   public final Setting<Color> fillSetting;
   public final Setting<Color> fillSecondSetting;
   public final Setting<Color> outlineColorSetting;
   public final Setting<Color> outlineSecondSetting;
   public final Setting<Boolean> handsSetting;
   public final Setting<Boolean> handFillSetting;
   public final Setting<Boolean> outlineSetting;
   public final Setting<Boolean> optimizedSetting;
   private final Setting<Boolean> crystalsSetting;
   private final Setting<Integer> crystalRangeSetting;
   private final Setting<Boolean> itemsSetting;
   private final Setting<Integer> itemRangeSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> playersSetting;
   private final Setting<Boolean> selfSetting;
   private final Setting<Boolean> expSetting;
   private final Setting<Boolean> pearlsSetting;
   private final Setting<Boolean> minecartsSetting;
   private final Setting<Boolean> deadSetting;
   private final Setting<Boolean> nameTagsSetting;
   private final ObjectArrayList<ShaderModule.RenderTargetEntry> renderTargets;
   private float minimumClusterAlpha;

   public Color applyClusterAlpha(Color color) {
      float alphaMultiplier = this.minimumClusterAlpha;
      if (ClientContext.shaderRegistry.renderingHands) {
         alphaMultiplier = 1.0F;
      }

      return ColorUtils.withAlpha(color, (int)(alphaMultiplier * color.getAlpha()));
   }

   public float getOverlayAlpha() {
      return ClientContext.shaderRegistry.renderingHands ? this.overlayAlphaSetting.getValue() : this.overlayAlphaSetting.getValue() * this.minimumClusterAlpha;
   }

   public boolean shouldSkipHandFill() {
      return ClientContext.shaderRegistry.renderingHands && !this.handFillSetting.getValue();
   }

   public boolean includesNameTags() {
      return this.isToggled() && this.nameTagsSetting.getValue();
   }

   public boolean shouldSkipHandOutline() {
      return ClientContext.shaderRegistry.renderingHands && !this.outlineSetting.getValue();
   }

   private GpuBufferSlice getHandProjectionBuffer() {
      DeltaTracker deltaTracker = ClientAccess.argusclientMinecraft.getDeltaTracker();
      float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera();
      CachedPerspectiveProjectionMatrixBuffer projectionBuffer = ClientAccess.argusclientMinecraft.gameRenderer.hud3dProjectionMatrixBuffer;
      int width = ClientAccess.argusclientMinecraft.getWindow().getWidth();
      int height = ClientAccess.argusclientMinecraft.getWindow().getHeight();
      float fov = ClientAccess.argusclientMinecraft.gameRenderer.getFov(camera, partialTick, false);
      return projectionBuffer.getBuffer(width, height, fov);
   }

   @Override
   public String getInfo() {
      Setting<ShaderModule.ShaderChoice> shaderOption = this.shaderSetting;
      return FontRenderer.capitalize(shaderOption.getValue());
   }

   private void renderEntity(WorldRenderEvent event, Entity entity) {
      if (!entity.isAlive()) {
         Boolean renderDead = this.deadSetting.getValue();
         if (!renderDead && entity instanceof LivingEntity) {
            return;
         }
      }

      PoseStack poseStack = event.getPoseStack();
      CameraRenderState cameraState = ClientAccess.argusclientMinecraft.gameRenderer.getLevelRenderState().cameraRenderState;
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      float partialTick = event.getPartialTick();
      double xPartialTick = partialTick;
      double previousX = entity.xOld;
      double currentX = entity.getX();
      double relativeX = Mth.lerp(xPartialTick, previousX, currentX) - cameraPosition.x;
      double yPartialTick = partialTick;
      double previousY = entity.yOld;
      double currentY = entity.getY();
      double relativeY = Mth.lerp(yPartialTick, previousY, currentY) - cameraPosition.y;
      double zPartialTick = partialTick;
      double previousZ = entity.zOld;
      double currentZ = entity.getZ();
      double relativeZ = Mth.lerp(zPartialTick, previousZ, currentZ) - cameraPosition.z;
      float rawFadePercent = this.fadeSetting.getValue();
      float fadeStart = rawFadePercent / 100.0F;
      float positionPartialTick = partialTick;
      Vec3 interpolatedPosition = entity.getPosition(positionPartialTick);
      double distanceToCamera = cameraPosition.distanceTo(interpolatedPosition);
      float distanceAlpha = (float)(distanceToCamera / this.getEntityRange(entity));
      if (distanceAlpha >= fadeStart) {
         float distanceBeyondFade = distanceAlpha - fadeStart;
         float fadeProgressValue = distanceBeyondFade / (1.0F - fadeStart);
         float fadeProgress = fadeProgressValue;
         distanceAlpha = 1.0F - Mth.clamp(fadeProgress, 0.0F, 1.0F);
      } else {
         distanceAlpha = 1.0F;
      }

      double alphaForVisibility = distanceAlpha;
      if (!(alphaForVisibility <= 0.01)) {
         byte renderColorArgb = -1;
         ShaderRegistry.setRenderColor(renderColorArgb);
         RenderUtils.setColorAlpha(distanceAlpha);
         EntityRenderDispatcher dispatcherForExtraction = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher();
         float extractPartialTick = partialTick;
         EntityRenderState renderState = dispatcherForExtraction.extractEntity(entity, extractPartialTick);
         EntityRenderDispatcher dispatcherForRenderer = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher();
         EntityRenderer entityRenderer = dispatcherForRenderer.getRenderer(renderState);
         Vec3 renderOffset = entityRenderer.getRenderOffset(renderState);
         poseStack.pushPose();
         double translatedX = relativeX + renderOffset.x;
         double translatedY = relativeY + renderOffset.y;
         double translatedZ = relativeZ + renderOffset.z;
         poseStack.translate(translatedX, translatedY, translatedZ);
         SubmitNodeCollector submitNodes = ShaderRegistry.getSubmitNodes();
         entityRenderer.submit(renderState, poseStack, submitNodes, cameraState);
         ShaderRegistry.finishFeatureRendering();
         poseStack.popPose();
         RenderUtils.setColorAlpha(1.0F);
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Optional<Float> minimumAlphaCandidate = this.renderTargets.stream().map(this::getTargetClusterAlpha).min(Comparator.naturalOrder());
      this.minimumClusterAlpha = minimumAlphaCandidate.orElse(1.0F);
   }

   @EventHandler(priority = -1000)
   private void onWorldRender(WorldRenderEvent.Overlay event) {
      EntityCullingOverrides.disableCulling();
      event.getPoseStack().pushPose();
      Supplier nameTagTargetSupplier = NameTagsModule.NAME_TAG_RENDER_TARGET;
      RenderTarget nameTagTarget = (RenderTarget)nameTagTargetSupplier.get();
      ShaderModule.ShaderChoice shader = this.shaderSetting.getValue();
      String shaderId = shader.getId();
      ObjectArrayList<ShaderModule.RenderTargetEntry> targetsToClear = this.renderTargets;
      targetsToClear.clear();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (this.isShaderTarget(entity)) {
            Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
            Vec3 entityPosition = entity.position();
            double negativeDistanceSquared = -cameraPosition.distanceToSqr(entityPosition);
            ObjectArrayList<ShaderModule.RenderTargetEntry> targetsForAdd = this.renderTargets;
            ShaderModule.RenderTargetEntry target = new ShaderModule.RenderTargetEntry(entity, negativeDistanceSquared);
            targetsForAdd.add(target);
         }
      }

      this.renderTargets.sort(Comparator.comparingDouble(ShaderModule.RenderTargetEntry::negativeDistanceSquared));
      Boolean handsEnabled = this.handsSetting.getValue();
      if (handsEnabled) {
         Boolean optimizedHands = this.optimizedSetting.getValue();
         if (optimizedHands) {
            this.renderHands(event);
         }
      }

      Boolean nameTagsEnabled = this.nameTagsSetting.getValue();
      if (nameTagsEnabled) {
         NameTagsModule nameTagsForBlit = nameTagsModule;
         if (nameTagsForBlit.isToggled()) {
            GpuTextureView mainColorTexture = ClientAccess.argusclientMinecraft.getMainRenderTarget().getColorTextureView();
            nameTagTarget.blitAndBlendToTexture(mainColorTexture);
         }
      }

      ShaderRegistry registryForMainPass = ClientContext.shaderRegistry;
      Runnable mainPass = () -> {
         Boolean callbackNameTagsEnabled = this.nameTagsSetting.getValue();
         if (callbackNameTagsEnabled) {
            NameTagsModule callbackNameTagsModule = nameTagsModule;
            if (callbackNameTagsModule.isToggled()) {
               ShaderRegistry.getBufferSource().setColorArgb(-1);
               RenderUtils.blitToMain(nameTagTarget);
            }
         }

         ObjectArrayList<ShaderModule.RenderTargetEntry> targetsForRender = this.renderTargets;
         Consumer<ShaderModule.RenderTargetEntry> renderTarget = targetToRender -> {
            Entity entityToRender = targetToRender.entity();
            this.renderEntity(event, entityToRender);
         };
         targetsForRender.forEach(renderTarget);
         ShaderRegistry.getBufferSource().setColorArgb(-1);
         Boolean callbackOptimizedEnabled = this.optimizedSetting.getValue();
         if (callbackOptimizedEnabled) {
            Boolean callbackHandsEnabled = this.handsSetting.getValue();
            if (callbackHandsEnabled) {
               ClientContext.shaderRegistry.renderingHands = true;
               this.renderHands(event);
               ClientContext.shaderRegistry.renderingHands = false;
            }
         }
      };
      registryForMainPass.render(shaderId, mainPass);
      Setting<Boolean> separatePassOption = this.optimizedSetting;
      Boolean optimizedPassEnabled = separatePassOption.getValue();
      if (!optimizedPassEnabled) {
         Setting<Boolean> separateHandsOption = this.handsSetting;
         Boolean separateHandsEnabled = separateHandsOption.getValue();
         if (separateHandsEnabled) {
            this.renderHands(event);
            ClientContext.shaderRegistry.renderingHands = true;
            ShaderRegistry registryForHandsPass = ClientContext.shaderRegistry;
            Runnable handsPass = () -> {
               ShaderRegistry.getBufferSource().setColorArgb(-1);
               this.renderHands(event);
            };
            registryForHandsPass.render(shaderId, handsPass);
            ClientContext.shaderRegistry.renderingHands = false;
         }
      }

      PoseStack poseForPop = event.getPoseStack();
      poseForPop.popPose();
      EntityCullingOverrides.restoreCulling();
   }

   public Color blendSettingColors(Setting<Color> firstSetting, Setting<Color> secondSetting) {
      Color firstColorValue = firstSetting.getValue();
      Color secondColor = secondSetting.getValue();
      return this.blendColorsHsb(firstColorValue, secondColor);
   }

   private float getTargetClusterAlpha(ShaderModule.RenderTargetEntry target) {
      Entity entity = target.entity();
      if (entity != null) {
         NoRenderModule noRenderForToggle = noRenderModule;
         if (noRenderForToggle.isToggled()) {
            Boolean noClusterEnabled = noRenderModule.noClusterSetting.getValue();
            if (noClusterEnabled) {
               Vec3 playerPositionValue = ClientAccess.argusclientMinecraft.player.position();
               Vec3 entityPosition = entity.position();
               double distance = playerPositionValue.distanceTo(entityPosition);
               float entityWidth = entity.getBbWidth();
               if (distance <= entityWidth && entity != ClientAccess.argusclientMinecraft.player) {
                  float distanceFraction = (float)(distance / entityWidth);
                  float rawClusterAlphaPercent = noRenderModule.clusterAlphaSetting.getValue().intValue();
                  float clusterAlpha = rawClusterAlphaPercent / 255.0F;
                  return clusterAlpha + distanceFraction * (1.0F - clusterAlpha);
               }
            }
         }
      }

      return 1.0F;
   }

   public Color blendColorsHsb(Color firstColor, Color secondColor) {
      int firstRed = firstColor.getRed();
      int firstGreen = firstColor.getGreen();
      int firstBlue = firstColor.getBlue();
      float[] firstHsb = Color.RGBtoHSB(firstRed, firstGreen, firstBlue, null);
      int secondRed = secondColor.getRed();
      int secondGreen = secondColor.getGreen();
      int secondBlue = secondColor.getBlue();
      float[] secondHsb = Color.RGBtoHSB(secondRed, secondGreen, secondBlue, null);
      float hueDifference = secondHsb[0] - firstHsb[0];
      if (hueDifference > 0.5F) {
         float firstHueToWrap = (float)(firstHsb[0]++);
      } else if (hueDifference < -0.5F) {
         float secondHueToWrap = (float)(secondHsb[0]++);
      }

      float[] hsbComponents = new float[3];
      float firstHue = firstHsb[0];
      float weightedFirstHue = firstHue * 0.5F;
      float secondHue = secondHsb[0];
      hsbComponents[0] = weightedFirstHue + secondHue * 0.5F;
      float firstSaturation = firstHsb[1];
      float weightedFirstSaturation = firstSaturation * 0.5F;
      float secondSaturation = secondHsb[1];
      hsbComponents[1] = weightedFirstSaturation + secondSaturation * 0.5F;
      float firstBrightness = firstHsb[2];
      float weightedFirstBrightness = firstBrightness * 0.5F;
      float secondBrightness = secondHsb[2];
      hsbComponents[2] = weightedFirstBrightness + secondBrightness * 0.5F;
      float[] blendedHsb = hsbComponents;
      float hueBeforeWrap = blendedHsb[0];
      float offsetHue = hueBeforeWrap + 1.0F;
      blendedHsb[0] = offsetHue % 1.0F;
      float hueForColor = blendedHsb[0];
      float saturationForColor = blendedHsb[1];
      float blendedBrightness = blendedHsb[2];
      float blendedSaturation = saturationForColor;
      float blendedHue = hueForColor;
      Color blendedColor = Color.getHSBColor(blendedHue, blendedSaturation, blendedBrightness);
      float firstAlpha = firstColor.getAlpha();
      float normalizedFirstAlpha = firstAlpha / 255.0F;
      float weightedFirstAlpha = normalizedFirstAlpha * 0.5F;
      float secondAlpha = secondColor.getAlpha();
      float normalizedSecondAlpha = secondAlpha / 255.0F;
      return ColorUtils.withAlpha(blendedColor, weightedFirstAlpha + normalizedSecondAlpha * 0.5F);
   }

   private void renderHands(WorldRenderEvent event) {
      Minecraft clientForCameraEntity = ClientAccess.argusclientMinecraft;
      boolean cameraSleeping;
      if (clientForCameraEntity.getCameraEntity() instanceof LivingEntity cameraEntity) {
         cameraSleeping = cameraEntity.isSleeping();
      } else {
         cameraSleeping = false;
      }

      RenderSystem.backupProjectionMatrix();
      GpuBufferSlice projectionBuffer = this.getHandProjectionBuffer();
      ProjectionType projectionType = ProjectionType.PERSPECTIVE;
      RenderSystem.setProjectionMatrix(projectionBuffer, projectionType);
      Quaternionf cameraRotation = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().rotation();
      Quaternionf inverseRotationDestination = new Quaternionf();
      Quaternionf inverseCameraRotation = cameraRotation.conjugate(inverseRotationDestination);
      Matrix4f viewMatrix = new Matrix4f();
      viewMatrix.rotation(inverseCameraRotation);
      ClientAccess.argusclientMinecraft.gameRenderer.renderItemInHand(event.getPartialTick(), cameraSleeping, viewMatrix);
      RenderSystem.restoreProjectionMatrix();
   }

   public boolean isShaderTarget(Entity entity) {
      float range = this.getEntityRange(entity);
      AABB entityBoundsValue = entity.getBoundingBox();
      boolean outsideViewOrRange;
      if (RotationManager.isVisible(entityBoundsValue)) {
         outsideViewOrRange = ClientAccess.argusclientMinecraft.player.distanceTo(entity) > range;
      } else {
         outsideViewOrRange = true;
      }

      if (entity instanceof Player player && player.isDeadOrDying()) {
         return false;
      } else {
         if (outsideViewOrRange) {
            return false;
         }

         if (EntityUtils.isFirstPersonCameraEntity(entity)) {
            return false;
         }

         boolean detachedView = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().isDetached();
         if (!detachedView) {
            FreecamModule freecamForToggle = freecamModule;
            detachedView = freecamForToggle.isToggled();
         }

         if (entity instanceof Player) {
            return detachedView && this.selfSetting.getValue() && entity == ClientAccess.argusclientMinecraft.player
               ? this.playersSetting.getValue()
               : this.playersSetting.getValue() && entity != ClientAccess.argusclientMinecraft.player;
         } else {
            return entity instanceof ItemEntity && this.itemsSetting.getValue()
               || entity instanceof ThrownExperienceBottle && this.expSetting.getValue()
               || (entity instanceof AgeableMob || entity instanceof AbstractFish || entity instanceof Squid) && this.animalsSetting.getValue()
               || entity instanceof Enemy && this.hostilesSetting.getValue()
               || entity instanceof ThrownEnderpearl && this.pearlsSetting.getValue()
               || entity instanceof EndCrystal && this.crystalsSetting.getValue()
               || entity instanceof AbstractMinecart && this.minecartsSetting.getValue();
         }
      }
   }

   private float getEntityRange(Entity entity) {
      if (entity instanceof EndCrystal) {
         return this.crystalRangeSetting.getValue().intValue();
      } else {
         return entity instanceof ItemEntity ? this.itemRangeSetting.getValue().intValue() : this.rangeSetting.getValue().intValue();
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      nameTagsModule = (NameTagsModule)registry.getModule(NameTagsModule.class);
      freecamModule = (FreecamModule)registry.getModule(FreecamModule.class);
      noRenderModule = (NoRenderModule)registry.getModule(NoRenderModule.class);
   }

   public ShaderModule() {
      super("Shader", "Advanced ESP using shaders.", ModuleCategory.RENDER);
      String rangeKey = new String("Range");
      this.rangeSetting = this.add(new NumberSetting<>(rangeKey, 60, 1, 300).withUnit(new String("m")));
      this.fadeSetting = this.add(new NumberSetting<>(new String("Fade"), 100.0F, 25.0F, 100.0F).withUnit(new String("%")));
      this.lineWidthSetting = this.add(new NumberSetting<>(new String("LineWidth"), 1, 0, 10));
      Predicate<Object> fastLinesVisibility = ignored -> this.lineWidthSetting.getValue() > 2;
      this.fastLinesSetting = this.add(new BooleanSetting(new String("FastLines"), false, fastLinesVisibility));
      Predicate<Object> stepVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.GRADIENT;
      this.stepSetting = this.add(new NumberSetting<>(new String("Step"), 0.5F, 0.1F, 2.0F, stepVisibility));
      Predicate<Object> speedVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.GRADIENT;
      this.speedSetting = this.add(new NumberSetting<>(new String("Speed"), 1.0F, 0.1F, 5.0F, speedVisibility));
      Predicate<Object> rainbowSpeedVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.RAINBOW;
      this.rainbowSpeedSetting = this.add(new NumberSetting<>(new String("Speed"), 1.0F, 0.0F, 5.0F, rainbowSpeedVisibility));
      Predicate<Object> lineStrengthVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.RAINBOW;
      this.lineStrengthSetting = this.add(new NumberSetting<>(new String("LineStrength"), 1.0F, 0.0F, 5.0F, lineStrengthVisibility));
      Predicate<Object> fillStrengthVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.RAINBOW;
      this.fillStrengthSetting = this.add(new NumberSetting<>(new String("FillStrength"), 1.0F, 0.0F, 5.0F, fillStrengthVisibility));
      Setting<Boolean> imageRegistered = this.add(new BooleanSetting(new String("Image"), false).markVisibilityDependency());
      this.imageSetting = imageRegistered;
      this.overlayAlphaSetting = this.add(new NumberSetting<>(new String("OverlayAlpha"), 1.0F, 0.0F, 1.0F).dependsOn(imageRegistered));
      Predicate<Object> glowVisibility = ignored -> this.lineWidthSetting.getValue() > 0;
      Setting<Boolean> glowRegistered = this.add(new BooleanSetting(new String("Glow"), false, glowVisibility).markAsGroup().markVisibilityDependency());
      this.glowSetting = glowRegistered;
      this.qualitySetting = this.add(new NumberSetting<>(new String("Quality"), 1, 1, 6).dependsOn(glowRegistered));
      this.weightSetting = this.add(new NumberSetting<>(new String("Weight"), 1.0F, 0.0F, 10.0F).dependsOn(glowRegistered));
      this.innerSetting = this.add(new BooleanSetting(new String("Inner"), true).dependsOn(glowRegistered));
      Setting<Boolean> decoratorRegistered = this.add(new BooleanSetting(new String("Decorator"), false).markAsGroup().markVisibilityDependency());
      this.decoratorSetting = decoratorRegistered;
      this.typeSetting = this.add(new EnumSetting<>(new String("Type"), ShaderModule.TypeChoice.NONE).dependsOn(decoratorRegistered));
      this.radiusSetting = this.add(new NumberSetting<>(new String("Radius"), 5, 2, 20).dependsOn(decoratorRegistered));
      this.alphaSetting = this.add(new NumberSetting<>(new String("Alpha"), 1.0F, 0.0F, 1.0F).dependsOn(decoratorRegistered));
      Setting<Boolean> colorsRegistered = this.add(new BooleanSetting(new String("Colors"), false).markAsGroup().markVisibilityDependency());
      this.smoothSetting = this.add(new BooleanSetting(new String("Smooth"), true).dependsOn(colorsRegistered));
      this.fillSetting = this.add(new ColorSetting(new String("Fill"), new Color(2025691647, true)).dependsOn(colorsRegistered));
      Predicate<Object> fillSecondVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.GRADIENT;
      this.fillSecondSetting = this.add(
         new ColorSetting(new String("FillSecond"), new Color(1936677274, true), fillSecondVisibility).dependsOn(colorsRegistered)
      );
      this.outlineColorSetting = this.add(new ColorSetting(new String("Outline"), new Color(-4351489, true)).dependsOn(colorsRegistered));
      Predicate<Object> outlineSecondVisibility = ignored -> this.shaderSetting.getValue() == ShaderModule.ShaderChoice.GRADIENT;
      this.outlineSecondSetting = this.add(
         new ColorSetting(new String("OutlineSecond"), new Color(-8625746, true), outlineSecondVisibility).dependsOn(colorsRegistered)
      );
      Setting<Boolean> targetsRegistered = this.add(new BooleanSetting(new String("Targets"), true).markAsGroup().markVisibilityDependency());
      Setting<Boolean> handsRegistered = this.add(new BooleanSetting(new String("Hands"), true).markVisibilityDependency().dependsOn(targetsRegistered));
      this.handsSetting = handsRegistered;
      this.handFillSetting = this.add(new BooleanSetting(new String("Fill"), true).dependsOn(targetsRegistered, handsRegistered));
      this.outlineSetting = this.add(new BooleanSetting(new String("Outline"), true).dependsOn(targetsRegistered, handsRegistered));
      this.optimizedSetting = this.add(new BooleanSetting(new String("Optimized"), true).dependsOn(targetsRegistered, handsRegistered));
      Setting<Boolean> crystalsRegistered = this.add(new BooleanSetting(new String("Crystals"), false).markVisibilityDependency().dependsOn(targetsRegistered));
      this.crystalsSetting = crystalsRegistered;
      this.crystalRangeSetting = this.add(
         new NumberSetting<>(new String("Range"), 60, 1, 300).withUnit(new String("m")).dependsOn(targetsRegistered, crystalsRegistered)
      );
      Setting<Boolean> itemsRegistered = this.add(new BooleanSetting(new String("Items"), false).markVisibilityDependency().dependsOn(targetsRegistered));
      this.itemsSetting = itemsRegistered;
      this.itemRangeSetting = this.add(
         new NumberSetting<>(new String("Range"), 60, 1, 300).withUnit(new String("m")).dependsOn(targetsRegistered, itemsRegistered)
      );
      this.animalsSetting = this.add(new BooleanSetting(new String("Animals"), false).dependsOn(targetsRegistered));
      this.hostilesSetting = this.add(new BooleanSetting(new String("Hostiles"), false).dependsOn(targetsRegistered));
      Setting<Boolean> playersRegistered = this.add(new BooleanSetting(new String("Players"), false).dependsOn(targetsRegistered));
      this.playersSetting = playersRegistered;
      this.selfSetting = this.add(new BooleanSetting(new String("Self"), false).dependsOn(targetsRegistered, playersRegistered));
      this.expSetting = this.add(new BooleanSetting(new String("Exp"), false).dependsOn(targetsRegistered));
      this.pearlsSetting = this.add(new BooleanSetting(new String("Pearls"), false).dependsOn(targetsRegistered));
      this.minecartsSetting = this.add(new BooleanSetting(new String("Minecarts"), false).dependsOn(targetsRegistered));
      this.deadSetting = this.add(new BooleanSetting(new String("Dead"), false).dependsOn(targetsRegistered));
      this.nameTagsSetting = this.add(new BooleanSetting(new String("NameTags"), false).dependsOn(targetsRegistered));
      this.renderTargets = new ObjectArrayList(128);
      this.minimumClusterAlpha = 1.0F;
      this.radiusSetting.setConfigName("DecoratorRadius");
      this.alphaSetting.setConfigName("DecoratorAlpha");
      this.handFillSetting.setConfigName("HandsFill");
      this.outlineSetting.setConfigName("HandsOutline");
      this.speedSetting.setConfigName("GradientSpeed");
      this.itemRangeSetting.setConfigName("ItemRange");
      this.crystalRangeSetting.setConfigName("CrystalRange");
      this.fadeSetting.setBoundaryLabel("None", SettingLabelCondition.MAX);
      this.fadeSetting.addVisibilityPredicate(ignored -> this.shaderSetting.getValue() != ShaderModule.ShaderChoice.SOLID);
      this.handFillSetting.addVisibilityPredicate(ignored -> !this.optimizedSetting.getValue());
      this.outlineSetting.addVisibilityPredicate(ignored -> !this.optimizedSetting.getValue());
   }

   private record RenderTargetEntry(Entity entity, double negativeDistanceSquared) {
   }

   public enum ShaderChoice implements NamedEntry {
      SOLID("Solid"),
      RAINBOW("Rainbow"),
      GRADIENT("Gradient"),
      BLOOM("Bloom"),
      CUSTOM("Custom");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ShaderChoice(String displayName) {
         this.displayName = displayName;
      }

      public String getId() {
         Locale locale = Locale.ROOT;
         return this.displayName.toLowerCase(locale);
      }
   }

   public enum TypeChoice implements NamedEntry {
      NONE("None"),
      DOTS("Dots"),
      GRID("Grid");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TypeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}

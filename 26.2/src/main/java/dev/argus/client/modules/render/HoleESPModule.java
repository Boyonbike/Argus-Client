package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.HoleInfo;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HoleESPModule extends Module {
   private final Setting<HoleESPModule.DrawMode> fillSetting = this.add(new EnumSetting<>(new String("Fill"), HoleESPModule.DrawMode.GRADIENT));
   private final Setting<HoleESPModule.DrawMode> lineSetting = this.add(new EnumSetting<>(new String("Line"), HoleESPModule.DrawMode.SOLID));
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>(new String("LineWidth"), 1.0F, 0.1F, 3.0F));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>(new String("Height"), 0.0F, -1.0F, 1.0F).withUnit(new String("m")));
   private final Setting<Integer> radiusSetting;
   private final Setting<Boolean> bloomSetting;
   private final Setting<Boolean> hideOwnSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeRadiusSetting;
   final Setting<Boolean> trappedSetting;
   private final Setting<Color> trappedFillSetting;
   private final Setting<Color> trappedOutlineSetting;
   final Setting<Boolean> safeSetting;
   private final Setting<Color> safeFillSetting;
   private final Setting<Color> safeOutlineSetting;
   final Setting<Boolean> unsafeSetting;
   private final Setting<Color> unsafeFillSetting;
   private final Setting<Color> unsafeOutlineSetting;

   @Override
   public String getInfo() {
      HoleManager holeManagerSnapshot = ClientContext.holeManager;
      int holeCount = holeManagerSnapshot.getHoles().size();
      return String.valueOf(holeCount);
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      double renderPartialTick = event.getPartialTick();
      double previousPlayerY = ClientAccess.argusclientMinecraft.player.yo;
      double currentPlayerY = ClientAccess.argusclientMinecraft.player.getY();
      double interpolatedPlayerY = Mth.lerp(renderPartialTick, previousPlayerY, currentPlayerY);
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      AABB ownBlockBounds = new AABB(ownBlockPosition());
      HoleManager holeManagerSnapshot = ClientContext.holeManager;

      for (HoleInfo hole : holeManagerSnapshot.getHoles()) {
         Boolean configuredTrapped = this.trappedSetting.getValue();
         if (configuredTrapped || !hole.trapped()) {
            BlockPos rangePosition = hole.pos();
            double rangeRadius = this.radiusSetting.getValue().intValue();
            if (rangePosition.closerToCenterThan(cameraPosition, rangeRadius)) {
               AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
               AABB ownIntersectionBounds = hole.bounds();
               if (playerBounds.intersects(ownIntersectionBounds)) {
                  Boolean configuredHideOwn = this.hideOwnSetting.getValue();
                  if (configuredHideOwn) {
                     Boolean configuredFade = this.fadeSetting.getValue();
                     if (!configuredFade) {
                        continue;
                     }
                  }
               }

               AABB holeBounds = hole.bounds();
               if (RotationManager.isVisible(holeBounds)) {
                  Color fillColor = this.safeFillSetting.getValue();
                  Color outlineColor = this.safeOutlineSetting.getValue();
                  if (hole.trapped()) {
                     fillColor = this.trappedFillSetting.getValue();
                     outlineColor = this.trappedOutlineSetting.getValue();
                  } else if (hole.safety() == HoleInfo.Safety.UNSAFE) {
                     fillColor = this.unsafeFillSetting.getValue();
                     outlineColor = this.unsafeOutlineSetting.getValue();
                  }

                  BlockPos distancePosition = hole.pos();
                  Vec3 holeCenter = distancePosition.getCenter();
                  double cameraDistance = cameraPosition.distanceTo(holeCenter);
                  AABB ownFadeHoleBounds = hole.bounds();
                  boolean fadeOwnHole = ownFadeHoleBounds.intersects(ownBlockBounds) && this.hideOwnSetting.getValue();
                  Boolean configuredFade2 = this.fadeSetting.getValue();
                  if (configuredFade2) {
                     Float configuredFadeRadius = this.fadeRadiusSetting.getValue();
                     if (cameraDistance >= configuredFadeRadius.floatValue() || fadeOwnHole) {
                        float cameraDistanceAsFloat = (float)cameraDistance;
                        float fadeRadiusAsFloat = this.fadeRadiusSetting.getValue();
                        float radiusAsFloat = this.radiusSetting.getValue().intValue();
                        float alphaFactor = 1.0F - MathUtils.rangeProgress(cameraDistanceAsFloat, fadeRadiusAsFloat, radiusAsFloat);
                        if (fadeOwnHole) {
                           alphaFactor = (float)(interpolatedPlayerY - Math.floor(interpolatedPlayerY));
                        }

                        Color fillAlphaSource = fillColor;
                        fillColor = ColorUtils.withAlpha(fillColor, (int)(alphaFactor * fillAlphaSource.getAlpha()));
                        Color outlineAlphaSource = outlineColor;
                        outlineColor = ColorUtils.withAlpha(outlineColor, (int)(alphaFactor * outlineAlphaSource.getAlpha()));
                     }
                  }

                  HoleESPModule.DrawMode configuredFill = this.fillSetting.getValue();
                  switch (configuredFill) {
                     case SOLID:
                        PoseStack solidFillPoses = event.getPoseStack();
                        AABB solidFillBounds = hole.bounds();
                        BlockPos solidFillPosition = hole.pos();
                        float solidFillBaseY = solidFillPosition.getY();
                        Float configuredHeight = this.heightSetting.getValue();
                        double solidFillMaxY = solidFillBaseY + configuredHeight;
                        WorldBoxRenderer.fillBox(solidFillPoses, solidFillBounds.setMaxY(solidFillMaxY), fillColor);
                        break;
                     case GRADIENT:
                        PoseStack gradientFillPoses = event.getPoseStack();
                        AABB gradientFillBounds = hole.bounds();
                        BlockPos gradientFillPosition = hole.pos();
                        double gradientFillMaxY = gradientFillPosition.getY() + 1;
                        WorldBoxRenderer.fillFadingBox(gradientFillPoses, gradientFillBounds.setMaxY(gradientFillMaxY), fillColor);
                  }

                  if (this.lineSetting.getValue() != HoleESPModule.DrawMode.NONE) {
                     float lineWidth = this.lineWidthSetting.getValue();
                     if (this.lineSetting.getValue() == HoleESPModule.DrawMode.GRADIENT) {
                        AABB gradientLineBounds = hole.bounds();
                        BlockPos gradientLinePosition = hole.pos();
                        double gradientLineMaxY = gradientLinePosition.getY() + 1;
                        AABB gradientOutlineBounds = gradientLineBounds.setMaxY(gradientLineMaxY);
                        PoseStack gradientLinePoses = event.getPoseStack();
                        WorldBoxRenderer.outlineFadingBox(gradientLinePoses, gradientOutlineBounds, outlineColor.hashCode(), lineWidth);
                     }

                     Boolean configuredBloom = this.bloomSetting.getValue();
                     if (configuredBloom) {
                        float distanceToRadius = this.radiusSetting.getValue().intValue() - (float)cameraDistance;
                        float maximumBloomWidth = lineWidth + 6.0F;
                        float bloomLineWidth = Math.clamp(distanceToRadius, 1.0F, maximumBloomWidth);
                        Color bloomColor = ColorUtils.multiplyAlpha(outlineColor, 0.15F);
                        PoseStack bloomPoses = event.getPoseStack();
                        AABB bloomBounds = hole.bounds();
                        BlockPos bloomPosition = hole.pos();
                        float bloomBaseY = bloomPosition.getY();
                        Float configuredHeight2 = this.heightSetting.getValue();
                        double bloomMaxY = bloomBaseY + configuredHeight2;
                        WorldBoxRenderer.outlineBox(bloomPoses, bloomBounds.setMaxY(bloomMaxY), bloomColor, bloomLineWidth);
                        if (this.lineSetting.getValue() == HoleESPModule.DrawMode.GRADIENT) {
                           AABB bloomGradientBounds = hole.bounds();
                           BlockPos bloomGradientPosition = hole.pos();
                           double bloomGradientMaxY = bloomGradientPosition.getY() + 1;
                           AABB bloomGradientOutlineBounds = bloomGradientBounds.setMaxY(bloomGradientMaxY);
                           PoseStack bloomGradientPoses = event.getPoseStack();
                           WorldBoxRenderer.outlineFadingBox(bloomGradientPoses, bloomGradientOutlineBounds, bloomColor.hashCode(), bloomLineWidth);
                        }
                     }

                     PoseStack outlinePoses = event.getPoseStack();
                     AABB outlineBounds = hole.bounds();
                     BlockPos outlinePosition = hole.pos();
                     float outlineBaseY = outlinePosition.getY();
                     Float configuredHeight3 = this.heightSetting.getValue();
                     double outlineMaxY = outlineBaseY + configuredHeight3;
                     WorldBoxRenderer.outlineBox(outlinePoses, outlineBounds.setMaxY(outlineMaxY), outlineColor, lineWidth);
                  }
               }
            }
         }
      }
   }

   private static BlockPos ownBlockPosition() {
      return PlayerStateUtils.getLocalRoundedBlockPosition();
   }

   public HoleESPModule() {
      super("HoleESP", "Highlights the spots that are safe from end crystals.", ModuleCategory.RENDER);
      String radiusKey = new String("Radius");
      this.radiusSetting = this.add(new NumberSetting<>(radiusKey, 10, 1, 16).withUnit(new String("m")));
      this.bloomSetting = this.add(new BooleanSetting(new String("Bloom"), false));
      this.hideOwnSetting = this.add(new BooleanSetting(new String("HideOwn"), false));
      this.fadeSetting = this.add(new BooleanSetting(new String("Fade"), false).markVisibilityDependency());
      Predicate<Object> fadeRadiusVisibility = ignored -> this.fadeSetting.isExpanded();
      this.fadeRadiusSetting = this.add(new NumberSetting<>(new String("FadeRadius"), 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit(new String("m")));
      this.trappedSetting = this.add(new BooleanSetting(new String("Trapped"), false).markVisibilityDependency());
      Predicate<Object> trappedFillVisibility = ignored -> this.trappedSetting.isExpanded();
      this.trappedFillSetting = this.add(new ColorSetting(new String("TrappedFill"), new Color(233622784, true), trappedFillVisibility));
      Predicate<Object> trappedOutlineVisibility = ignored -> this.trappedSetting.isExpanded();
      this.trappedOutlineSetting = this.add(new ColorSetting(new String("TrappedOutline"), new Color(-1073745408, true), trappedOutlineVisibility));
      BooleanSetting safe = new BooleanSetting(new String("Safe"), true);
      safe.markAsGroup();
      safe.markVisibilityDependency();
      this.safeSetting = this.add(safe);
      Predicate<Object> safeFillVisibility = ignored -> this.safeSetting.isExpanded();
      this.safeFillSetting = this.add(new ColorSetting(new String("SafeFill"), new Color(540373280, true), safeFillVisibility));
      Predicate<Object> safeOutlineVisibility = ignored -> this.safeSetting.isExpanded();
      this.safeOutlineSetting = this.add(new ColorSetting(new String("SafeOutline"), new Color(-1037662665, true), safeOutlineVisibility));
      this.unsafeSetting = this.add(new BooleanSetting(new String("Unsafe"), true).markAsGroup().markVisibilityDependency());
      Predicate<Object> unsafeFillVisibility = ignored -> this.unsafeSetting.isExpanded();
      this.unsafeFillSetting = this.add(new ColorSetting(new String("UnsafeFill"), new Color(634257408, true), unsafeFillVisibility));
      Predicate<Object> unsafeOutlineVisibility = ignored -> this.unsafeSetting.isExpanded();
      this.unsafeOutlineSetting = this.add(new ColorSetting(new String("UnsafeOutline"), new Color(-1077018624, true), unsafeOutlineVisibility));
   }

   public enum DrawMode implements NamedEntry {
      NONE("None"),
      SOLID("Solid"),
      GRADIENT("Gradient");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      DrawMode(String displayName) {
         this.displayName = displayName;
      }
   }
}

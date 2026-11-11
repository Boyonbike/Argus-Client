package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ColorSubmitNodeStorage extends SubmitNodeStorage {
   private int colorArgb;
   private int[] itemTintColors;

   @Override
   public SubmitNodeCollection order(int order) {
      return (SubmitNodeCollection)this.getSubmitsPerOrder()
         .computeIfAbsent(order, ignoredOrder -> new ColorSubmitNodeStorage.ColoredSubmitNodeCollection());
   }

   public void setColorArgb(int argb) {
      this.colorArgb = argb;
   }

   public void clear() {
      this.getSubmitsPerOrder().clear();
   }

   private class ColoredSubmitNodeCollection extends SubmitNodeCollection {
      public ColoredSubmitNodeCollection() {
         super(false, new net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase());
      }

      @Override
      public void submitShadow(PoseStack poseStack, float shadowRadius, List shadowPieces) {
      }

      @Override
      public void submitNameTag(
         PoseStack poseStack,
         Vec3 nameTagPosition,
         int y,
         Component nameTag,
         boolean notSneaking,
         int packedLight,
         CameraRenderState cameraState
      ) {
      }

      @Override
      public void submitText(
         PoseStack poseStack,
         float x,
         float y,
         FormattedCharSequence text,
         boolean dropShadow,
         DisplayMode displayMode,
         int packedLight,
         int color,
         int backgroundColor,
         int outlineColor
      ) {
      }

      @Override
      public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
      }

      @Override
      public void submitLeash(PoseStack poseStack, LeashState leashState) {
      }

      @Override
      public <S> void submitModel(
         Model<? super S> model,
         S state,
         PoseStack poseStack,
         RenderType renderType,
         int packedLight,
         int packedOverlay,
         int tintColor,
         net.minecraft.client.renderer.texture.UvMapping uvMapping,
         int outlineColor
      ) {
         super.submitModel(
            model, state, poseStack, renderType, packedLight, packedOverlay,
            ColorSubmitNodeStorage.this.colorArgb, uvMapping, 0);
      }

      @Override
      public <S> void submitCrumblingOverlay(
         Model<? super S> model,
         S state,
         PoseStack poseStack,
         RenderType renderType,
         int packedLight,
         int packedOverlay,
         int tintColor,
         CrumblingOverlay crumblingOverlay
      ) {
         super.submitCrumblingOverlay(
            model, state, poseStack, renderType, packedLight, packedOverlay,
            ColorSubmitNodeStorage.this.colorArgb, crumblingOverlay);
      }

      @Override
      public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockState, int outlineColor) {
      }

      @Override
      public void submitBlockModel(
         PoseStack poseStack,
         RenderType renderType,
         List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts,
         int[] tintColors,
         int packedLight,
         int packedOverlay,
         int outlineColor
      ) {
         int[] colors = tintColors == null ? new int[1] : java.util.Arrays.copyOf(tintColors, tintColors.length);
         for (int i = 0; i < colors.length; i++) colors[i] = ColorSubmitNodeStorage.this.colorArgb;
         super.submitBlockModel(poseStack, renderType, parts, colors, packedLight, packedOverlay, outlineColor);
      }

      @Override
      public void submitItem(
         PoseStack poseStack,
         ItemDisplayContext displayContext,
         int packedLight,
         int packedOverlay,
         int outlineColor,
         int[] tintColors,
         net.minecraft.client.resources.model.geometry.ItemQuads quads,
         FoilType foilType
      ) {
         if (ColorSubmitNodeStorage.this.itemTintColors == null || ColorSubmitNodeStorage.this.itemTintColors[0] != ColorSubmitNodeStorage.this.colorArgb) {
            ColorSubmitNodeStorage.this.itemTintColors = new int[]{
               ColorSubmitNodeStorage.this.colorArgb,
               ColorSubmitNodeStorage.this.colorArgb,
               ColorSubmitNodeStorage.this.colorArgb,
               ColorSubmitNodeStorage.this.colorArgb
            };
         }

         super.submitItem(
            poseStack, displayContext, packedLight, packedOverlay, outlineColor, ColorSubmitNodeStorage.this.itemTintColors, quads, foilType
         );
      }

      @Override
      public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
      }

   }
}

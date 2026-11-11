package dev.argus.client.modules.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.math.Axis;
import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.math.MathUtils;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11C;

public final class MarkerTracerRenderer extends TracerRenderer {
   public MarkerTracerRenderer(TracersModule module) {
      super(module);
   }

   private void drawMarker(PoseStack poseStack, float targetYaw, float distance, float screenHeight, float screenWidth, float[] cameraAngles, Color color) {
      float yawDifference = targetYaw - cameraAngles[0];
      float relativeYaw = Mth.wrapDegrees(yawDifference);
      double capturedRelativeYaw = relativeYaw;
      double angleRadians = Math.toRadians(capturedRelativeYaw);
      float capturedCameraPitch = cameraAngles[1];
      float negatedPitchLimit = -90.0F;
      float maximumPitch = 90.0F;
      float clampedPitch = Mth.clamp(capturedCameraPitch, negatedPitchLimit, maximumPitch);
      float pitchFraction = Math.abs(clampedPitch) / 90.0F;
      double centerX = screenWidth * 0.5;
      float markerX = (float)(centerX + Math.sin(angleRadians) * distance);
      double centerY = screenHeight * 0.5;
      double cosine = Math.cos(angleRadians);
      float markerY = (float)(centerY - cosine * (distance * Math.max(pitchFraction, 0.4F)));
      poseStack.pushPose();
      poseStack.translate(markerX, markerY, 0.0F);
      Axis markerAxis = Axis.ZP;
      float markerRotation = relativeYaw - 90.0F;
      Quaternionf rotation = markerAxis.rotationDegrees(markerRotation);
      poseStack.mulPose(rotation);
      int packedColor = color.hashCode();
      this.drawArrow(poseStack, -4.0F, -4.0F, 8.0F, packedColor);
      poseStack.popPose();
   }

   private void drawArrow(PoseStack poseStack, float x, float y, float size, int packedColor) {
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      float vertex1X = x + 0.1F * size;
      float vertex1Y = y + 0.1F * size;
      float vertex2X = x + 0.1F * size;
      float vertex2Y = y + 0.3F * size;
      float vertex3X = x + 0.1F * size;
      float vertex3Y = y + 0.7F * size;
      float vertex4X = x + 0.1F * size;
      float vertex4Y = y + 0.9F * size;
      float vertex5X = x + 0.4F * size;
      float vertex5Y = y + 0.5F * size;
      float vertex6X = x + 0.25F * size;
      float vertex6Y = y + 0.5F * size;
      addVertex(matrix, vertex1X, vertex1Y, packedColor);
      addVertex(matrix, vertex2X, vertex2Y, packedColor);
      addVertex(matrix, vertex6X, vertex6Y, packedColor);
      addVertex(matrix, vertex6X, vertex6Y, packedColor);
      addVertex(matrix, vertex5X, vertex5Y, packedColor);
      addVertex(matrix, vertex1X, vertex1Y, packedColor);
      addVertex(matrix, vertex3X, vertex3Y, packedColor);
      addVertex(matrix, vertex4X, vertex4Y, packedColor);
      addVertex(matrix, vertex5X, vertex5Y, packedColor);
      addVertex(matrix, vertex5X, vertex5Y, packedColor);
      addVertex(matrix, vertex6X, vertex6Y, packedColor);
      addVertex(matrix, vertex3X, vertex3Y, packedColor);
   }

   private static void addVertex(Matrix4f matrix, float x, float y, int packedColor) {
      MeshBuffer buffer = MeshDrawContext.getBuffer();
      VertexConsumer vertex = buffer.addVertex(matrix, x, y, 0.0F);
      vertex.setColor(packedColor);
   }

   @Override
   public void render(PoseStack poseStack, float tickDelta) {
      float[] cameraAngles = getCameraAngles();
      Entity cameraEntity = ClientAccess.argusclientMinecraft.getCameraEntity();
      Vec3 localPosition = ClientAccess.argusclientMinecraft.player.getPosition(tickDelta);
      if (cameraEntity != null) {
         MeshDrawContext.getBuffer().begin(PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
         for (TracersModule.Target target : this.module.collect(tickDelta)) {
            Vec3 cameraPosition = cameraEntity.getPosition(tickDelta);
            float targetYaw = RotationManager.calculateRotations(cameraPosition, target.aim())[0];
            if (target.entity() == null || !target.entity().equals(cameraEntity)) {
               AABB bounds = target.bounds();
               float opacity = 1.0F;
               Setting<Boolean> ignoreVisibleSetting = this.module.ignoreVisibleSetting;
               Boolean ignoreVisible = ignoreVisibleSetting.getValue();
               if (ignoreVisible) {
                  float cameraYaw = cameraAngles[0];
                  float yawDifference = Mth.degreesDifferenceAbs(targetYaw, cameraYaw);
                  cameraPosition = BoundingBoxUtils.clampToBounds(localPosition, bounds);
                  float boundsDistance = (float)cameraPosition.distanceTo(localPosition);
                  OptionInstance firstFovSetting = ClientAccess.argusclientMinecraft.options.fov();
                  float fovValue = ((Integer)firstFovSetting.get()).intValue();
                  OptionInstance secondFovSetting = ClientAccess.argusclientMinecraft.options.fov();
                  int secondFovValue = (Integer)secondFovSetting.get();
                  float fovEnd = secondFovValue + 20;
                  opacity *= MathUtils.rangeProgress(yawDifference, fovValue, fovEnd);
                  opacity *= MathUtils.rangeProgress(boundsDistance, 0.0F, 0.1F);
               }

               double capturedOpacity = opacity;
               if (!(capturedOpacity < 0.1)) {
                  Color color = ColorUtils.multiplyAlpha(target.color(), opacity);
                  Setting<Integer> capturedShadowSetting = this.module.shadowSetting;
                  if (!capturedShadowSetting.isAtMinimum()) {
                     poseStack.pushPose();
                     float shadowValue = this.module.shadowSetting.getValue().intValue();
                     float shadowOffset = shadowValue * 0.5F;
                     poseStack.translate(shadowOffset, shadowOffset, 0.0F);
                     float configuredShadowRadius = this.module.distanceSetting.getValue();
                     float scaledShadowHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
                     Minecraft shadowWidthClient = ClientAccess.argusclientMinecraft;
                     Window shadowWidthWindow = shadowWidthClient.getWindow();
                     float scaledShadowWidth = shadowWidthWindow.getGuiScaledWidth();
                     Color shadowColor = ColorUtils.darken(color, 0.25F);
                     this.drawMarker(poseStack, targetYaw, configuredShadowRadius, scaledShadowHeight, scaledShadowWidth, cameraAngles, shadowColor);
                     poseStack.popPose();
                  }

                  Setting<Float> distanceSetting = this.module.distanceSetting;
                  Float distance = distanceSetting.getValue();
                  float configuredRadius = distance;
                  Minecraft heightClient = ClientAccess.argusclientMinecraft;
                  Window heightWindow = heightClient.getWindow();
                  float scaledHeight = heightWindow.getGuiScaledHeight();
                  Minecraft widthClient = ClientAccess.argusclientMinecraft;
                  Window widthWindow = widthClient.getWindow();
                  float scaledWidth = widthWindow.getGuiScaledWidth();
                  this.drawMarker(poseStack, targetYaw, configuredRadius, scaledHeight, scaledWidth, cameraAngles, color);
               }
            }
         }

         GL11C.glEnable(2881);
         MeshDrawContext.getBuffer().draw(ClientRenderTypes.guiTriangles());
         GL11C.glDisable(2881);
      }
   }

   private static float[] getCameraAngles() {
      Entity cameraEntity = ClientAccess.argusclientMinecraft.getCameraEntity();
      if (!cameraEntity.equals(ClientAccess.argusclientMinecraft.player)) {
         float[] entityAngles = new float[2];
         float yawTickDelta = RenderUtils.getPartialTick();
         entityAngles[0] = cameraEntity.getYRot(yawTickDelta);
         float pitchTickDelta = RenderUtils.getPartialTick();
         entityAngles[1] = cameraEntity.getXRot(pitchTickDelta);
         return entityAngles;
      } else {
         float[] playerAngles = new float[2];
         float interpolationTickDelta = RenderUtils.getPartialTick();
         float previousYawBob = ClientAccess.argusclientMinecraft.player.yBobO;
         float currentYawBob = ClientAccess.argusclientMinecraft.player.yBob;
         playerAngles[0] = Mth.lerp(interpolationTickDelta, previousYawBob, currentYawBob);
         interpolationTickDelta = RenderUtils.getPartialTick();
         float previousPitchBob = ClientAccess.argusclientMinecraft.player.xBobO;
         float currentPitchBob = ClientAccess.argusclientMinecraft.player.xBob;
         playerAngles[1] = Mth.lerp(interpolationTickDelta, previousPitchBob, currentPitchBob);
         return playerAngles;
      }
   }
}

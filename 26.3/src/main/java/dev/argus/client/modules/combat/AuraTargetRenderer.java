package dev.argus.client.modules.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.PrimitiveTopology;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.options.AuraTypeChoice;
import dev.argus.client.utils.FloatAnimation;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import net.minecraft.client.Camera;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class AuraTargetRenderer implements ClientAccess {
   private final AuraModule aura;
   private final FloatAnimation fadeAnimation = new FloatAnimation();
   private AABB targetBox;

   public void updateTarget(Entity target) {
      if (target == null) {
         FloatAnimation animation = this.fadeAnimation;
         animation.setTarget(0.0F, 250L);
      } else {
         if (target instanceof LivingEntity livingTarget && livingTarget.hurtDuration == livingTarget.hurtTime) {
            FloatAnimation animation = this.fadeAnimation;
            animation.setValue(1.0F);
         } else {
            FloatAnimation animation = this.fadeAnimation;
            animation.setTarget(0.5F, 250L);
         }

         float partialTick = RenderUtils.getPartialTick();
         this.targetBox = BoundingBoxUtils.getRenderBounds(target, partialTick);
      }
   }

   AuraTargetRenderer(AuraModule aura) {
      this.aura = aura;
   }

   private void drawTargetVertices(PoseStack poseStack, MeshBuffer buffer, boolean filled, AABB bounds, int segments, int color, float lineWidth) {
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      float cyclePhase = (float)(System.currentTimeMillis() % 3000L) / 1500.0F;
      boolean firstHalf = cyclePhase < 1.0F;
      float mirroredPhase = cyclePhase > 1.0F ? 2.0F - cyclePhase : cyclePhase;
      double cosine = Math.cos(Math.PI * mirroredPhase);
      double easedHeight = -(cosine - 1.0);
      float heightProgress = (float)(easedHeight / 2.0);
      double width = bounds.getXsize();
      double horizontalSize = width + bounds.getZsize();
      float radius = Math.min((float)(horizontalSize / 2.0), 4.0F);
      Camera camera = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera;
      Vec3 cameraPosition = camera.position();
      Vec3 bottomCenter = bounds.getBottomCenter();
      Vec3 relativeCenter = bottomCenter.subtract(cameraPosition);
      Vector3f relativePosition = relativeCenter.toVector3f();
      double boxHeight = bounds.getYsize();
      float verticalOffset = (float)(heightProgress * (boxHeight - 0.1)) + 0.2F;
      Vector3f ringPosition = relativePosition.add(0.0F, verticalOffset, 0.0F);
      VertexGeometry.forEachCircleSegment(
         radius,
         segments,
         (firstPoint, secondPoint) -> {
            if (filled) {
               float trailFade = heightProgress * 2.0F;
               if (trailFade > 1.0F) {
                  trailFade = 2.0F;
                  trailFade -= heightProgress * 2.0F;
               } else {
                  trailFade = heightProgress * 2.0F;
               }

               float direction = firstHalf ? -1.0F : 1.0F;
               float trailHeight = direction * (0.3F * trailFade);
               float vertexX = ringPosition.x + firstPoint.x;
               float vertexY = ringPosition.y;
               float vertexZ = ringPosition.z + firstPoint.z;
               VertexConsumer vertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ);
               vertex.setColor(color);
               vertexX = ringPosition.x + firstPoint.x;
               vertexY = ringPosition.y + trailHeight;
               vertexZ = ringPosition.z + firstPoint.z;
               VertexConsumer firstTopVertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ);
               int firstTopColor = ARGB.color(0, color);
               firstTopVertex.setColor(firstTopColor);
               vertexX = ringPosition.x + secondPoint.x;
               vertexY = ringPosition.y + trailHeight;
               vertexZ = ringPosition.z + secondPoint.z;
               VertexConsumer secondTopVertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ);
               int secondTopColor = ARGB.color(0, color);
               secondTopVertex.setColor(secondTopColor);
               vertexX = ringPosition.x + secondPoint.x;
               vertexY = ringPosition.y;
               vertexZ = ringPosition.z + secondPoint.z;
               vertex = buffer.addVertex(matrix, vertexX, vertexY, vertexZ);
               vertex.setColor(color);
            } else {
               VertexGeometry.appendLine(
                  buffer,
                  pose,
                  ringPosition.x + firstPoint.x,
                  ringPosition.y,
                  ringPosition.z + firstPoint.z,
                  ringPosition.x + secondPoint.x,
                  ringPosition.y,
                  ringPosition.z + secondPoint.z,
                  lineWidth,
                  color
               );
            }
         }
      );
   }

   private void drawBox(WorldRenderEvent event, AABB bounds, int fillColor, int outlineColor) {
      WorldBoxRenderer.fillBox(event.getPoseStack(), bounds, fillColor);
      PoseStack outlinePoseStack = event.getPoseStack();
      Float lineWidth = this.aura.lineWidthSetting.getValue();
      WorldBoxRenderer.outlineBox(outlinePoseStack, bounds, outlineColor, lineWidth);
   }

   @EventHandler
   public void renderTarget(WorldRenderEvent.Main event) {
      if (this.aura.renderSetting.getValue()) {
         Entity target = this.aura.getTarget();
         this.updateTarget(target);
         this.renderTarget((WorldRenderEvent)event);
      }
   }

   public void renderTarget(WorldRenderEvent event) {
      float fade = this.fadeAnimation.getValue();
      if (this.targetBox != null && fade != 0.0F) {
         int fillColor = ARGB.multiplyAlpha(this.aura.fillSetting.getValue().hashCode(), fade);
         int outlineColor = ARGB.multiplyAlpha(this.aura.outlineSetting.getValue().hashCode(), fade);
         if (this.aura.typeSetting.getValue() == AuraTypeChoice.BOX) {
            AABB bounds = this.targetBox;
            this.drawBox(event, bounds, fillColor, outlineColor);
         } else {
            AABB bounds = this.targetBox;
            this.drawWireBox(event, bounds, fillColor, outlineColor);
         }
      }
   }

   private void drawWireBox(WorldRenderEvent event, AABB bounds, int fillColor, int outlineColor) {
      MeshBuffer buffer = MeshDrawContext.getBuffer();
      buffer.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
      PoseStack poseStack = event.getPoseStack();
      this.drawTargetVertices(poseStack, buffer, true, bounds, 32, fillColor, 1.0F);
      buffer.draw(ClientRenderTypes.worldFill());
      buffer.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
      poseStack = event.getPoseStack();
      this.drawTargetVertices(poseStack, buffer, false, bounds, 32, outlineColor, this.aura.lineWidthSetting.getValue());
      buffer.draw(ClientRenderTypes.worldLines());
   }
}

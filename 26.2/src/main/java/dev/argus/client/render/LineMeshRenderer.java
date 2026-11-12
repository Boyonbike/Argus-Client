package dev.argus.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.PrimitiveTopology;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.WorldRenderEvent;
import java.awt.Color;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class LineMeshRenderer implements ClientAccess {
   private static final Matrix4f projectionMatrix = new Matrix4f();
   private static final Matrix4f inverseProjectionMatrix = new Matrix4f();
   private static final Vector3f positionVector = new Vector3f();
   private static final Vector2f projectedPosition = new Vector2f();
   private static final Vector2f tracerPosition = new Vector2f();
   private static final Vector4f clipPosition = new Vector4f();
   private static final Vector3f pendingPosition = new Vector3f();
   /** Where every tracer starts (the screen centre), worked out once per projection rather than per tracer. */
   private static final Vector3f tracerOrigin = new Vector3f();
   private static final Vector3f tracerTarget = new Vector3f();
   static final MeshBuffer vertices = MeshBuffer.create();
   static PoseStack poseStack;
   /** View-space distance tracers are drawn at; about where 26.1.2's NDC z=0 landed (2 x near plane). */
   private static final float TRACER_DEPTH = 0.1F;
   /** NDC depth of {@link #TRACER_DEPTH}. 26.2+ uses reversed-Z, where a fixed z=0 is the far plane. */
   private static float tracerNdcZ;
   private static int pendingColor;
   private static boolean pendingVertex;
   private static float lineWidth;

   public static void updateProjection(PoseStack poses) {
      projectionMatrix.set(RenderUtils.WORLD_PROJECTION_MATRIX).mul(poses.last().pose());
      inverseProjectionMatrix.set(projectionMatrix).invert();
      RenderUtils.WORLD_PROJECTION_MATRIX.transform(clipPosition.set(0.0F, 0.0F, -TRACER_DEPTH, 1.0F));
      tracerNdcZ = clipPosition.w != 0.0F ? clipPosition.z / clipPosition.w : 0.0F;
      unproject(0.0F, 0.0F, tracerOrigin);
   }

   public static void begin(WorldRenderEvent event, float width) {
      begin(event.getPoseStack(), width);
   }

   public static void begin(PoseStack poses, float width) {
      vertices.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
      poseStack = poses;
      pendingVertex = false;
      lineWidth = width;
      updateProjection(poses);
   }

   public static void addVertex(double x, double y, double z, Color color) {
      addVertex(x, y, z, color.hashCode());
   }

   public static void addVertex(double x, double y, double z, int color) {
      addVertex(x, y, z, color, false, false);
   }

   public static void addVertex(double x, double y, double z, int color, boolean relativeToCamera, boolean clamp) {
      if (relativeToCamera) {
         x = (float)(x - ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position().x());
         y = (float)(y - ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position().y());
         z = (float)(z - ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position().z());
      }

      if (clamp) {
         Vector3f clampedPosition = clampToViewport((float)x, (float)y, (float)z);
         x = clampedPosition.x;
         y = clampedPosition.y;
         z = clampedPosition.z;
      }

      if (pendingVertex) {
         VertexGeometry.appendLine(
            vertices,
            poseStack.last(),
            pendingPosition.x(),
            pendingPosition.y(),
            pendingPosition.z(),
            (float)x,
            (float)y,
            (float)z,
            lineWidth,
            pendingColor,
            color
         );
      } else {
         pendingPosition.set(x, y, z);
         pendingColor = color;
      }

      pendingVertex = !pendingVertex;
   }

   public static void addTracer(Vec3 position, Color color) {
      addTracer(position, color.hashCode());
   }

   public static void addTracer(Vec3 position, int color) {
      Vector2f projected = projectTracer(position);
      if (projected != null) {
         Vector3f target = unproject(projected.x, projected.y, tracerTarget);
         addVertex(tracerOrigin.x, tracerOrigin.y, tracerOrigin.z, color, false, false);
         addVertex(target.x, target.y, target.z, color, false, false);
      }
   }

   public static void addLine(Vec3 from, Vec3 to, Color color) {
      addLine(from, to, color.hashCode());
   }

   public static void addLine(Vec3 from, Vec3 to, int color) {
      addVertex(from.x, from.y, from.z, color, true, false);
      addVertex(to.x, to.y, to.z, color, true, true);
   }

   public static void draw() {
      vertices.draw(ClientRenderTypes.worldLines());
   }

   private static Vector3f clampToViewport(float x, float y, float z) {
      positionVector.set(x, y, z);
      projectionMatrix.transform(clipPosition.set(positionVector, 1.0F));
      float inverseW = 1.0F / clipPosition.w;
      float projectedX = clipPosition.x * inverseW;
      float projectedY = clipPosition.y * inverseW;
      boolean outsideViewport = clipPosition.w <= 0.0F || projectedX < -1.0F || projectedX > 1.0F || projectedY < -1.0F || projectedY > 1.0F;
      if (!outsideViewport) {
         return positionVector;
      }

      projectedPosition.set(projectedX, projectedY);
      float edgeScale = Math.min(1.0F / Math.abs(projectedPosition.x), 1.0F / Math.abs(projectedPosition.y));
      projectedPosition.mul(edgeScale);
      clipPosition.set(projectedPosition.x, projectedPosition.y, clipPosition.z * inverseW, 1.0F);
      clipPosition.mul(inverseProjectionMatrix);
      return clipPosition.w == 0.0F ? positionVector : new Vector3f(clipPosition.x, clipPosition.y, clipPosition.z).div(clipPosition.w);
   }

   private static Vector3f unproject(float x, float y, Vector3f out) {
      clipPosition.set(x, y, tracerNdcZ, 1.0F);
      inverseProjectionMatrix.transform(clipPosition);
      return out.set(clipPosition.x, clipPosition.y, clipPosition.z).div(clipPosition.w);
   }

   private static Vector2f projectTracer(Vec3 position) {
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position();
      projectionMatrix.transform(
         clipPosition.set((float)(position.x - cameraPosition.x), (float)(position.y - cameraPosition.y), (float)(position.z - cameraPosition.z), 1.0F)
      );
      float w = clipPosition.w;
      boolean behind = w <= 1.0E-4F;
      float x = behind ? clipPosition.x : clipPosition.x / w;
      float y = behind ? clipPosition.y : clipPosition.y / w;
      float extent = Math.max(Math.abs(x), Math.abs(y));
      if (behind && extent < 1.0E-6F) {
         x = 0.0F;
         y = -1.0F;
         extent = 1.0F;
      }

      if (!behind && !(extent > 1.0F)) {
         if (extent < 1.0E-5F) {
            return null;
         }
      } else {
         x /= extent;
         y /= extent;
      }

      return tracerPosition.set(x, y);
   }
}

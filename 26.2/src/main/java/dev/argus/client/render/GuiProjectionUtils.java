package dev.argus.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import dev.argus.client.ClientAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public final class GuiProjectionUtils implements ClientAccess {
   private static final Projection GUI_PROJECTION = new Projection();
   public static final ProjectionMatrixBuffer GUI_PROJECTION_CACHE = new ProjectionMatrixBuffer("gui_argus");
   private static GpuBufferSlice savedProjectionBuffer;
   private static ProjectionType savedProjectionType;
   private static boolean active;

   public static void restoreProjection() {
      if (active) {
         RenderSystem.setProjectionMatrix(savedProjectionBuffer, savedProjectionType);
         RenderSystem.getModelViewStack().popMatrix();
         savedProjectionBuffer = null;
         savedProjectionType = null;
         active = false;
      }
   }

   public static void beginGuiProjection() {
      if (!active) {
         Window window = argusclientMinecraft.getWindow();
         float guiWidth = (float)window.getWidth() / window.getGuiScale();
         float guiHeight = (float)window.getHeight() / window.getGuiScale();
         savedProjectionBuffer = RenderSystem.getProjectionMatrixBuffer();
         savedProjectionType = RenderSystem.getProjectionType();
         GUI_PROJECTION.setupOrtho(1000.0F, 11000.0F, guiWidth, guiHeight, true);
         RenderSystem.setProjectionMatrix(GUI_PROJECTION_CACHE.getBuffer(GUI_PROJECTION), ProjectionType.ORTHOGRAPHIC);
         // Same camera placement as vanilla GuiRenderer: the ortho range is 1000..11000, so GUI geometry must sit
         // at z = -11000 (+ its own z). Identity left every Argus GUI mesh behind the near plane, clipped away.
         Matrix4fStack modelView = RenderSystem.getModelViewStack();
         modelView.pushMatrix();
         modelView.identity().translate(0.0F, 0.0F, -11000.0F);
         active = true;
      }
   }

   public static PoseStack toPoseStack(Matrix3x2fStack guiPose) {
      PoseStack poseStack = new PoseStack();
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      matrix.set(
         guiPose.m00(), guiPose.m01(), 0.0F, 0.0F, guiPose.m10(), guiPose.m11(), 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, guiPose.m20(), guiPose.m21(), 0.0F, 1.0F
      );
      return poseStack;
   }

   public static PoseStack toPoseStack(GuiGraphicsExtractor graphics) {
      return toPoseStack(graphics.pose());
   }

   private GuiProjectionUtils() {
   }
}

package dev.argus.client.render;

import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.argus.client.ClientAccess;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class GuiDrawUtils implements ClientAccess {
   static final MeshBuffer quadBuffer = MeshBuffer.create();
   static final MeshBuffer lineBuffer = MeshBuffer.create();
   private static final List<SubmitNodeStorage> pendingItems = new ArrayList<>();
   static float lineWidth = 1.0F;

   public static void flushPendingItems() {
      for (SubmitNodeStorage storage : pendingItems) {
         ImmediateSubmitRenderer.flush(storage);
      }
      pendingItems.clear();
   }

   public static void flush() {
      flush(true);
   }

   public static VertexConsumer getLineVertices() {
      return lineBuffer;
   }

   private GuiDrawUtils() {
   }

   private static void fillVerticalStrip(PoseStack poseStack, float x, float top, float bottom, float thickness, Color color) {
      fillRect(poseStack, x, top + thickness, x + thickness, bottom, color);
   }

   private static void drawTexturedQuad(
      PoseStack poseStack, Identifier texture, int left, int right, int top, int bottom, float minU, float maxU, float minV, float maxV, int color
   ) {
      RenderType renderType = ClientRenderTypes.image(texture, false);
      MeshBuffer vertices = MeshBuffer.create().setFormat(renderType.primitiveTopology(), renderType.format());
      Matrix4f matrix = poseStack.last().pose();
      vertices.addVertex(matrix, left, top, 0.0F).setUv(minU, minV).setColor(color);
      vertices.addVertex(matrix, left, bottom, 0.0F).setUv(minU, maxV).setColor(color);
      vertices.addVertex(matrix, right, bottom, 0.0F).setUv(maxU, maxV).setColor(color);
      vertices.addVertex(matrix, right, top, 0.0F).setUv(maxU, minV).setColor(color);
      vertices.draw(renderType);
   }

   public static void drawItem(PoseStack poseStack, ItemStack stack, float x, float y, float z) {
      drawItem(poseStack, stack, x, y, z, true);
   }

   public static void drawTexture(
      PoseStack poseStack,
      Identifier texture,
      int x,
      int y,
      float u,
      float v,
      int width,
      int height,
      int regionWidth,
      int regionHeight,
      int textureWidth,
      int textureHeight,
      int color
   ) {
      drawTexturedQuad(
         poseStack,
         texture,
         x,
         x + width,
         y,
         y + height,
         (u + 0.0F) / textureWidth,
         (u + regionWidth) / textureWidth,
         (v + 0.0F) / textureHeight,
         (v + regionHeight) / textureHeight,
         color
      );
   }

   public static void drawTexture(
      PoseStack poseStack, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color
   ) {
      drawTexture(poseStack, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
   }

   public static void drawItem(PoseStack poseStack, ItemStack stack, float x, float y, float z, boolean flushItemBuffers) {
      if (!stack.isEmpty()) {
         ItemStackRenderState renderState = new ItemStackRenderState();
         SubmitNodeStorage submitNodes = new SubmitNodeStorage();
         ClientAccess.argusclientMinecraft
            .getItemModelResolver()
            .updateForTopItem(renderState, stack, ItemDisplayContext.GUI, ClientAccess.argusclientMinecraft.level, null, 0);
         int depthScale = 1;
         if (renderState.usesBlockLight()) {
            ClientAccess.argusclientMinecraft.gameRenderer.lighting().setupFor(Entry.ITEMS_3D);
            depthScale = 16;
         } else {
            ClientAccess.argusclientMinecraft.gameRenderer.lighting().setupFor(Entry.ITEMS_FLAT);
         }

         poseStack.pushPose();
         poseStack.translate(x + 8.0F, y + 8.0F, 150.0F + z);

         try {
            poseStack.scale(16.0F, -16.0F, depthScale);
            renderState.submit(poseStack, submitNodes, 15728880, OverlayTexture.NO_OVERLAY, 0);
            if (flushItemBuffers) {
               ImmediateSubmitRenderer.flush(submitNodes);
            } else {
               pendingItems.add(submitNodes);
            }
         } catch (Throwable failure) {
            throw new ReportedException(CrashReport.forThrowable(failure, "Rendering item"));
         }

         poseStack.popPose();
      }
   }

   public static void drawDurabilityBar(PoseStack poseStack, ItemStack stack, float x, float y) {
      if (stack.isBarVisible()) {
         float barX = x + 2.0F;
         float barY = y + 13.0F;
         poseStack.translate(0.0F, 0.0F, 1.0F);
         fillRect(poseStack, barX, barY, barX + 13.0F, barY + 2.0F, Color.black);
         fillRect(poseStack, barX, barY, barX + stack.getBarWidth(), barY + 1.0F, ARGB.opaque(stack.getBarColor()));
         poseStack.translate(0.0F, 0.0F, -1.0F);
      }
   }

   public static void fillGradientRect(
      Matrix4f matrix, float x, float y, float width, float height, int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor
   ) {
      quadBuffer.addVertex(matrix, x, y + height, 0.0F).setColor(bottomLeftColor);
      quadBuffer.addVertex(matrix, x + width, y + height, 0.0F).setColor(bottomRightColor);
      quadBuffer.addVertex(matrix, x + width, y, 0.0F).setColor(topRightColor);
      quadBuffer.addVertex(matrix, x, y, 0.0F).setColor(topLeftColor);
   }

   public static void fillHorizontalGradient(Matrix4f matrix, float x, float y, float width, float height, int... colors) {
      if (colors.length >= 2) {
         float segmentWidth = width / colors.length;

         for (int index = 1; index < colors.length; index++) {
            float segmentEnd = index == colors.length - 1 ? width : segmentWidth * index;
            int rightColor = colors[index];
            int leftColor = colors[index - 1];
            quadBuffer.addVertex(matrix, x + segmentEnd, y, 0.0F).setColor(rightColor);
            quadBuffer.addVertex(matrix, x + segmentWidth * (index - 1), y, 0.0F).setColor(leftColor);
            quadBuffer.addVertex(matrix, x + segmentWidth * (index - 1), y + height, 0.0F).setColor(leftColor);
            quadBuffer.addVertex(matrix, x + segmentEnd, y + height, 0.0F).setColor(rightColor);
         }
      }
   }

   public static void drawText(PoseStack poseStack, String text, int x, int y, int color, boolean shadow) {
      if (shadow) {
         FontRenderer var6 = FontRenderer.INSTANCE;
      }

      VanillaTextRenderer.draw(
         poseStack, net.minecraft.network.chat.Component.nullToEmpty(text).getVisualOrderText(), x, y, color, shadow, DisplayMode.NORMAL, 15728880
      );
   }

   public static void drawLineRect(PoseStack poseStack, float left, float top, float right, float bottom, Color color) {
      int packedColor = color.hashCode();
      if (color.getAlpha() != 0) {
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, bottom, 0.0F, right, bottom, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, bottom, 0.0F, right, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, top, 0.0F, left, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, top, 0.0F, left, bottom, 0.0F, lineWidth, packedColor);
      }
   }

   public static void drawRectOutline(PoseStack poseStack, float left, float top, float right, float bottom, Color color) {
      fillHorizontalStrip(poseStack, left, right, top, 1.0F, color);
      fillVerticalStrip(poseStack, right, top, bottom, 1.0F, color);
      fillHorizontalStrip(poseStack, left, right, bottom, 1.0F, color);
      fillVerticalStrip(poseStack, left, top, bottom, 1.0F, color);
   }

   public static void drawItemCooldown(PoseStack poseStack, ItemStack stack, float x, float y) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float partialTick = ClientAccess.argusclientMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      float cooldown = player == null ? 0.0F : player.getCooldowns().getCooldownPercent(stack, partialTick);
      if (cooldown > 0.0F) {
         float top = y + Mth.floor(16.0F * (1.0F - cooldown));
         float bottom = top + Mth.ceil(16.0F * cooldown);
         fillRect(poseStack, x, top, x + 16.0F, bottom, Integer.MAX_VALUE);
      }
   }

   public static void fillRect(PoseStack poseStack, float left, float top, float right, float bottom, int color) {
      if (ARGB.alpha(color) != 0) {
         quadBuffer.addVertex(poseStack.last().pose(), left, bottom, 0.0F).setColor(color);
         quadBuffer.addVertex(poseStack.last().pose(), right, bottom, 0.0F).setColor(color);
         quadBuffer.addVertex(poseStack.last().pose(), right, top, 0.0F).setColor(color);
         quadBuffer.addVertex(poseStack.last().pose(), left, top, 0.0F).setColor(color);
      }
   }

   public static void fillVerticalGradient(PoseStack poseStack, float x, float y, float width, float height, int topColor, int bottomColor) {
      fillGradientRect(poseStack.last().pose(), x, y, width, height, topColor, topColor, bottomColor, bottomColor);
   }

   public static void fillRect(PoseStack poseStack, float left, float top, float right, float bottom, Color color) {
      fillRect(poseStack, left, top, right, bottom, color.hashCode());
   }

   private static void fillHorizontalStrip(PoseStack poseStack, float left, float right, float y, float thickness, Color color) {
      fillRect(poseStack, left, y, right + thickness, y + thickness, color);
   }

   public static void fillHorizontalGradient(PoseStack poseStack, float x, float y, float width, float height, int leftColor, int rightColor) {
      fillGradientRect(poseStack.last().pose(), x, y, width, height, leftColor, rightColor, rightColor, leftColor);
   }

   public static void flush(boolean flushText) {
      MeshDrawContext.setRenderType(ClientRenderTypes.gui());
      quadBuffer.draw();
      MeshDrawContext.setRenderType(ClientRenderTypes.worldLines());
      lineBuffer.draw();
      if (flushText) {
         FontRenderer.INSTANCE.flush();
      }
   }

   static {
      quadBuffer.setFormat(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
      lineBuffer.setFormat(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
   }
}

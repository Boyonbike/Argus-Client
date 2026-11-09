package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.exploit.ChunkKind;
import dev.argus.client.modules.exploit.DetectedChunk;
import dev.argus.client.modules.exploit.NewChunksModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class MapModule extends HudWidgetModule {
   private static final NewChunksModule newChunks = ClientContext.getModule(NewChunksModule.class);
   private static final Identifier NAVIGATION_TEXTURE;
   private final Setting<MapModule.PointerMode> pointerModeSetting = this.add(new EnumSetting<>("Pointer", MapModule.PointerMode.ARROW));
   private final Setting<Float> widthSetting = this.add(new NumberSetting<>("Width", 76.0F, 50.0F, 500.0F));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>("Height", 76.0F, 50.0F, 250.0F));
   private final Setting<Color> pointerColorSetting = this.add(new ColorSetting("Pointer", Color.white));
   private final Setting<Color> backgroundSetting = this.add(new ColorSetting("Background", new Color(10, 10, 10, 50)));
   private final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(10, 10, 10, 100)));

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      Setting<Float> widthOption = this.widthSetting;
      Float width = widthOption.getValue();
      dimensions[0] = width;
      Setting<Float> heightOption = this.heightSetting;
      Float height = heightOption.getValue();
      dimensions[1] = height;
      return dimensions;
   }

   private boolean isChunkWithinRange(ChunkPos chunk) {
      Setting<Float> widthOption = this.widthSetting;
      Float width = widthOption.getValue();
      double radiusOperand = width.floatValue();
      Setting<Float> heightOption = this.heightSetting;
      Float height = heightOption.getValue();
      double mapHeight = height.floatValue();
      double mapWidth = radiusOperand;
      radiusOperand = Math.hypot(mapWidth, mapHeight);
      double radius = radiusOperand / 16.0;
      int chunkRadius = (int)Math.ceil(radius);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ChunkPos playerChunk = player.chunkPosition();
      return chunk.distanceSquared(playerChunk) <= chunkRadius * chunkRadius;
   }

   @Override
   public void renderContent(PoseStack poses) {
      float[] widgetSize = this.renderer.getDimensions();
      if (widgetSize != null) {
         int[] renderColorValues = RenderUtils.getRenderColor();
         int previousRenderState = renderColorValues[3];
         RenderUtils.setColorAlpha(255);
         GuiDrawUtils.flush();
         RenderUtils.setColorAlpha(previousRenderState);
         RenderUtils backgroundRenderer = RenderUtils.INSTANCE;
         Setting<Float> backgroundWidthOption = this.widthSetting;
         Float backgroundWidth = backgroundWidthOption.getValue();
         float rectangleWidth = backgroundWidth;
         Setting<Float> backgroundHeightOption = this.heightSetting;
         Float backgroundHeight = backgroundHeightOption.getValue();
         float rectangleHeight = backgroundHeight;
         Setting<Color> backgroundColorOption = this.backgroundSetting;
         backgroundRenderer.fillRect(poses, 0.0F, 0.0F, rectangleWidth, rectangleHeight, backgroundColorOption.getValue());
         RenderUtils outlineRenderer = RenderUtils.INSTANCE;
         float outlineX = -1.0F;
         float outlineY = -1.0F;
         Setting<Float> outlineWidthOption = this.widthSetting;
         Float outlineWidth = outlineWidthOption.getValue();
         rectangleWidth = outlineWidth;
         Setting<Float> outlineHeightOption = this.heightSetting;
         Float outlineHeight = outlineHeightOption.getValue();
         rectangleHeight = outlineHeight;
         Setting<Color> outlineColorOption = this.outlineSetting;
         outlineRenderer.drawRectOutline(poses, outlineX, outlineY, rectangleWidth, rectangleHeight, outlineColorOption.getValue());
         Setting<Float> centerWidthOption = this.widthSetting;
         Float centerWidth = centerWidthOption.getValue();
         float mapWidth = centerWidth;
         float centerX = mapWidth / 2.0F;
         Setting<Float> centerHeightOption = this.heightSetting;
         Float centerHeight = centerHeightOption.getValue();
         float mapHeight = centerHeight;
         float centerY = mapHeight / 2.0F;
         poses.pushPose();
         float mapDepth = 0.0F;
         poses.translate(centerX, centerY, mapDepth);
         NewChunksModule dungeonSource = newChunks;

         for (BlockPos dungeonPosition : dungeonSource.getDetectedDungeons()) {
            ChunkPos dungeonChunk = new ChunkPos(dungeonPosition);
            if (this.isChunkWithinRange(dungeonChunk)) {
               Setting<Color> dungeonColorOption = newChunks.dungeonFillSetting;
               Color dungeonFill = dungeonColorOption.getValue();
               Color dungeonColor = ColorUtils.withAlpha(dungeonFill, 0.4F);
               this.renderChunk(poses, dungeonChunk, dungeonColor);
            }
         }

         NewChunksModule visibleChunkSource = newChunks;

         for (DetectedChunk visibleChunk : visibleChunkSource.getVisibleChunks()) {
            ChunkPos chunkPosition = visibleChunk.chunkPosition();
            if (this.isChunkWithinRange(chunkPosition)) {
               ChunkKind visibleKind = visibleChunk.kind();
               NewChunksModule moduleForKindColors = newChunks;
               if (visibleKind.getColors(moduleForKindColors) != null) {
                  ChunkKind visibleColoredKind = visibleChunk.kind();
                  NewChunksModule moduleForFillColor = newChunks;
                  Color chunkColor = visibleColoredKind.getColors(moduleForFillColor)[0];
                  chunkColor = ColorUtils.withAlpha(chunkColor, 0.25F);
                  ChunkPos drawnChunkPosition = visibleChunk.chunkPosition();
                  Color drawnChunkColor = chunkColor;
                  this.renderChunk(poses, drawnChunkPosition, drawnChunkColor);
               }
            }
         }

         poses.popPose();
         ScissorStack.push((int)this.renderer.getX(), (int)this.renderer.getY(), (int)widgetSize[0], (int)widgetSize[1]);
         GuiDrawUtils.flush();
         ScissorStack.pop();
         this.renderPointer(poses, centerX, centerY);
      }
   }

   private void renderChunk(PoseStack poses, ChunkPos chunk, Color color) {
      GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = gameRenderer.getMainCamera();
      Vec3 cameraPosition = camera.position();
      double cameraCoordinate = cameraPosition.x;
      float cameraOffsetX = (float)(cameraCoordinate - chunk.getMinBlockX());
      float scaledOffsetX = cameraOffsetX / 2.0F;
      float offsetX = scaledOffsetX - 8.0F;
      cameraCoordinate = cameraPosition.z;
      float cameraOffsetZ = (float)(cameraCoordinate - chunk.getMinBlockZ());
      float scaledOffsetZ = cameraOffsetZ / 2.0F;
      float offsetZ = scaledOffsetZ - 8.0F;
      poses.pushPose();
      float markerDepth = 0.0F;
      poses.translate(offsetX, offsetZ, markerDepth);
      float markerWidth = 8.0F;
      GuiDrawUtils.fillRect(poses, 0.0F, 0.0F, markerWidth, 8.0F, color);
      poses.popPose();
   }

   private void renderPointer(PoseStack poses, float centerX, float centerY) {
      poses.pushPose();
      float pointerDepth = 0.0F;
      poses.translate(centerX, centerY, pointerDepth);
      Setting<MapModule.PointerMode> dotModeOption = this.pointerModeSetting;
      if (dotModeOption.getValue() == MapModule.PointerMode.DOT) {
         RenderUtils dotRenderer = RenderUtils.INSTANCE;
         float dotX = -1.0F;
         float dotY = -1.0F;
         float dotWidth = 1.0F;
         float dotHeight = 1.0F;
         Setting<Color> dotColorOption = this.pointerColorSetting;
         dotRenderer.fillRect(poses, dotX, dotY, dotWidth, dotHeight, dotColorOption.getValue());
      } else {
         Setting<MapModule.PointerMode> arrowModeOption = this.pointerModeSetting;
         if (arrowModeOption.getValue() == MapModule.PointerMode.ARROW) {
            float arrowScaleX = 0.05F;
            float arrowScaleY = 0.05F;
            float arrowScaleZ = 1.0F;
            poses.scale(arrowScaleX, arrowScaleY, arrowScaleZ);
            Axis rotationAxis = Axis.ZP;
            float rotationOffset = 45.0F;
            GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
            Camera camera = gameRenderer.getMainCamera();
            float rotationDegrees = rotationOffset + camera.yRot();
            Quaternionf arrowRotation = rotationAxis.rotationDegrees(rotationDegrees);
            poses.mulPose(arrowRotation);
            int[] renderStateValues = RenderUtils.getRenderColor();
            float renderAlpha = renderStateValues[3];
            Setting<Color> arrowColorOption = this.pointerColorSetting;
            int pointerArgb = ColorUtils.toArgb(arrowColorOption.getValue(), renderAlpha);
            Identifier texture = NAVIGATION_TEXTURE;
            GuiDrawUtils.drawTexture(poses, texture, -128, -128, 0.0F, 0.0F, 256, 256, 256, 256, pointerArgb);
            RenderBuffers renderBuffers = ClientAccess.argusclientMinecraft.renderBuffers();
            BufferSource bufferSource = renderBuffers.bufferSource();
            bufferSource.endBatch();
         }
      }

      poses.popPose();
   }

   public MapModule() {
      super("Map", "minimap");
      this.pointerColorSetting.setConfigName("PointerColor");
      this.pointerColorSetting.forceOpaqueColor();
      ChatFormatting descriptionColor = ChatFormatting.YELLOW;
      String descriptionPrefix = String.valueOf(descriptionColor);
      String descriptionText = descriptionPrefix + "Primarily used for NewChunks";
      this.setDescription(descriptionText);
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.enableResizing(this);
      this.setRenderer(widget);
   }

   static {
      String texturePath = "textures/nav.png";
      String textureNamespace = "argusclient";
      NAVIGATION_TEXTURE = Identifier.fromNamespaceAndPath(textureNamespace, texturePath);
   }

   private enum PointerMode implements NamedEntry {
      NONE("None"),
      ARROW("Arrow"),
      DOT("Dot");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      PointerMode(String displayName) {
         this.displayName = displayName;
      }
   }
}

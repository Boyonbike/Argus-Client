package dev.argus.client.commands;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HighlightCommand extends Command {
   private static final ColorSetting FILL_SETTING = new ColorSetting("Fill", Color.WHITE);
   private static final Color TRANSPARENT_OUTLINE = new Color(0, 0, 0, 0);
   private Vec3 trackedPosition;

   @Override
   public void exec(LiteralArgumentBuilder root) {
      PositionArgument positionParser = new PositionArgument();
      RequiredArgumentBuilder positionArgument = Command.argument("pos", positionParser);
      LiteralArgumentBuilder trackBuilder = Command.literal("track");
      com.mojang.brigadier.Command<Object> trackCallback = context -> {
         Class<Vec3> positionType = Vec3.class;
         String positionName = "pos";
         CommandContext argumentContext = context;
         Vec3 position = (Vec3)argumentContext.getArgument(positionName, positionType);
         this.trackedPosition = position;
         HighlightCommand command = this;
         EventBus eventBus = ClientAccess.eventBus;
         eventBus.subscribe(command);
         return 1;
      };
      ArgumentBuilder trackBranch = trackBuilder.executes(trackCallback);
      RequiredArgumentBuilder highlightBuilder = (RequiredArgumentBuilder)positionArgument.then(trackBranch);
      com.mojang.brigadier.Command<Object> highlightCallback = context -> {
         Class<Vec3> positionType = Vec3.class;
         String positionName = "pos";
         CommandContext argumentContext = context;
         Position argumentPosition = (Position)argumentContext.getArgument(positionName, positionType);
         BlockPos blockPosition = BlockPos.containing(argumentPosition);
         Vec3 blockCenter = Vec3.atCenterOf(blockPosition);
         AABB box = AABB.ofSize(blockCenter, 0.5, 0.5, 0.5);
         AABB pointBox = box;
         HighlightCommand pointFeature = this;
         FeatureBoxRenderer pointRenderer = ClientContext.featureBoxRenderer;
         pointRenderer.addBox(pointFeature, pointBox);
         AABB columnBounds = AABB.ofSize(blockCenter, 0.25, 0.25, 0.25);
         double topY = WorldUtils.getTopY();
         columnBounds = columnBounds.setMaxY(topY);
         LevelReader level = ClientAccess.argusclientMinecraft.level;
         double minY = level.getMinY();
         box = columnBounds.setMinY(minY);
         AABB columnBox = box;
         HighlightCommand columnFeature = this;
         FeatureBoxRenderer columnRenderer = ClientContext.featureBoxRenderer;
         columnRenderer.addBox(columnFeature, columnBox);
         return 1;
      };
      ArgumentBuilder highlightBranch = highlightBuilder.executes(highlightCallback);
      root.then(highlightBranch);
   }

   @EventHandler
   public void onWorldRender(WorldRenderEvent.Main event) {
      if (this.trackedPosition != null) {
         GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
         Camera camera = gameRenderer.mainCamera();
         Vec3 cameraPosition = camera.position();
         Vec3 targetPosition = this.trackedPosition;
         float distance = Vec3Utils.horizontalDistance(cameraPosition, targetPosition);
         if (distance <= 2.0F) {
            this.trackedPosition = null;
            HighlightCommand command = this;
            EventBus eventBus = ClientAccess.eventBus;
            eventBus.unsubscribe(command);
         } else {
            Vec3 renderPosition = this.trackedPosition;
            if (distance >= 120.0F) {
               targetPosition = this.trackedPosition;
               Vec3 rotationTarget = targetPosition;
               Vec3 rotationOrigin = cameraPosition;
               float[] rotations = RotationManager.calculateRotations(rotationOrigin, rotationTarget);
               double cameraX = cameraPosition.x();
               float xRadius = 100.0F;
               double xYaw = rotations[0] + 90.0F;
               double xYawRadians = Math.toRadians(xYaw);
               double renderX = cameraX + xRadius * (float)Math.cos(xYawRadians);
               double renderY = this.trackedPosition.y;
               double cameraZ = cameraPosition.z();
               double zYaw = rotations[0] + 90.0F;
               double zYawRadians = Math.toRadians(zYaw);
               renderPosition = new Vec3(renderX, renderY, cameraZ + 100.0F * (float)Math.sin(zYawRadians));
            }

            Object[] labelArguments = new Object[]{distance};
            String distanceLabel = "[%.0fm]".formatted(labelArguments);
            float fadeAmount = distance - 2.0F;
            fadeAmount = Mth.clamp(fadeAmount, 0.0F, 9.0F);
            float opacity = fadeAmount / 9.0F;
            AABB column = AABB.ofSize(renderPosition, 0.25, 0.25, 0.25);
            double topY = WorldUtils.getTopY();
            AABB topBoundedColumn = column.setMaxY(topY);
            LevelReader level = ClientAccess.argusclientMinecraft.level;
            double minY = level.getMinY();
            column = topBoundedColumn.setMinY(minY);
            ColorSetting colorSetting = FILL_SETTING;
            Color configuredFillColor = colorSetting.getColor();
            Color fillColor = ColorUtils.withAlpha(configuredFillColor, opacity * 0.3F);
            Color labelColor = ColorUtils.withAlpha(Color.white, opacity);
            WorldRenderEvent.Main boxRenderEvent = event;
            WorldBoxRenderer.fillBox(boxRenderEvent.getPoseStack(), column, fillColor);
            RenderUtils labelRenderer = RenderUtils.INSTANCE;
            PoseStack poseStack = event.getPoseStack();
            Vec3 labelPosition = renderPosition;
            labelRenderer.drawWorldText(poseStack, distanceLabel, renderPosition, MathUtils.getDistanceScale(labelPosition, 1.7F), labelColor, true);
         }
      }
   }

   public HighlightCommand() {
      super("highlight");
      FeatureBoxRenderer capturedRenderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration configuration = new FeatureBoxRenderer.RenderConfiguration(this, () -> {
         ColorSetting fillSetting = FILL_SETTING;
         Color fillColor = fillSetting.getColor();
         return ColorUtils.withAlpha(fillColor, 0.5F);
      }, () -> TRANSPARENT_OUTLINE, () -> 1.0F, () -> 5.0F, () -> true, () -> true, configurationFadeTimeScale());
      capturedRenderer.register(configuration);
   }

   private static int configurationFadeTimeScale() {
      return 1000;
   }

   static {
      boolean synced = true;
      ColorSetting fillSetting = FILL_SETTING;
      fillSetting.setSynced(synced);
   }
}

package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VoidESPModule extends Module {
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>(new String("LineWidth"), 1.0F, 0.1F, 3.0F));
   private final Setting<Integer> heightSetting;
   private final Setting<Integer> radiusSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeRadiusSetting;
   final Setting<Boolean> colorsSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> lineSetting;
   private final Set<BlockPos> voidBlocks;
   private boolean resetPending;
   private final Long2IntOpenHashMap columnHeights = new Long2IntOpenHashMap();

   private boolean shouldRemoveVoidBlock(BlockPos position) {
      GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Vec3 cameraPosition = gameRenderer.getMainCamera().position();
      Integer radiusValue = this.radiusSetting.getValue();
      return !position.closerToCenterThan(cameraPosition, radiusValue.intValue()) ? true : isBedrock(position);
   }

   private boolean isVoidBlock(BlockPos position) {
      return position.getY() == ClientAccess.argusclientMinecraft.level.getMinY() && !isBedrock(position);
   }

   private static boolean isBedrock(BlockPos position) {
      return WorldUtils.getBlock(position) == Blocks.BEDROCK;
   }

   private static int getColumnHeight(VoidESPModule owner, BlockPos position) {
      int columnHeight = owner.heightSetting.getValue();
      if (columnHeight <= 1) {
         return columnHeight;
      }

      for (int offset = 0; offset <= columnHeight; offset++) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockPos columnPosition = position.above(offset);
         BlockState blockState = level.getBlockState(columnPosition);
         if (!blockState.canBeReplaced()) {
            columnHeight -= columnHeight - offset;
         }
      }

      return Math.max(columnHeight, 1);
   }

   @Override
   public String getInfo() {
      int blockCount = this.voidBlocks.size();
      return String.valueOf(blockCount);
   }

   @Override
   public void onDisable() {
      this.voidBlocks.clear();
      this.columnHeights.clear();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.resetPending) {
         this.voidBlocks.clear();
         this.resetPending = false;
      }

      this.voidBlocks.removeIf(this::shouldRemoveVoidBlock);
      GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Vec3 cameraPosition = gameRenderer.getMainCamera().position();
      int scanRadius = this.radiusSetting.getValue();
      // Void holes can only be on the bottom layer, so scan that flat disc of the sphere
      // instead of every block in it (about 20x fewer lookups at radius 16).
      BlockPos center = BlockPos.containing(cameraPosition);
      int minY = ClientAccess.argusclientMinecraft.level.getMinY();
      int dy = minY - center.getY();
      int radiusSquared = scanRadius * scanRadius;
      if (dy * dy < radiusSquared) {
         MutableBlockPos position = new MutableBlockPos();
         for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
               if (dx * dx + dy * dy + dz * dz >= radiusSquared) {
                  continue;
               }

               position.set(center.getX() + dx, minY, center.getZ() + dz);
               if (!isBedrock(position)) {
                  this.voidBlocks.add(position.immutable());
               }
            }
         }
      }

      // Column heights only change with blocks, so work them out per tick rather than per block per frame.
      this.columnHeights.clear();
      for (BlockPos position : this.voidBlocks) {
         this.columnHeights.put(position.asLong(), getColumnHeight(this, position));
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      for (BlockPos position : this.voidBlocks) {
         AABB blockBox = new AABB(position);
         int baseY = position.getY();
         long key = position.asLong();
         double maxY = baseY + (this.columnHeights.containsKey(key) ? this.columnHeights.get(key) : getColumnHeight(this, position));
         AABB box = blockBox.setMaxY(maxY);
         if (RotationManager.isVisible(box)) {
            GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
            Vec3 cameraPosition = gameRenderer.getMainCamera().position();
            Color fillColor = this.fillSetting.getValue();
            Color lineColor = this.lineSetting.getValue();
            Vec3 blockCenter = position.getCenter();
            double distance = cameraPosition.distanceTo(blockCenter);
            Boolean fadeEnabled = this.fadeSetting.getValue();
            if (fadeEnabled) {
               Float fadeRadiusValue = this.fadeRadiusSetting.getValue();
               if (distance >= fadeRadiusValue.floatValue()) {
                  Float fadeStartValue = this.fadeRadiusSetting.getValue();
                  double distancePastFadeStart = distance - fadeStartValue.floatValue();
                  float renderRadius = this.radiusSetting.getValue().intValue();
                  Float fadeRangeStartValue = this.fadeRadiusSetting.getValue();
                  double fadeProgress = distancePastFadeStart / (renderRadius - fadeRangeStartValue);
                  float alphaFactor = 1.0F - (float)Mth.clamp(fadeProgress, 0.0, 1.0);
                  Color fillAlphaSource = fillColor;
                  fillColor = ColorUtils.withAlpha(fillColor, (int)(alphaFactor * fillAlphaSource.getAlpha()));
                  Color lineAlphaSource = lineColor;
                  lineColor = ColorUtils.withAlpha(lineColor, (int)(alphaFactor * lineAlphaSource.getAlpha()));
               }
            }

            WorldBoxRenderer.fillBox(event.getPoseStack(), box, fillColor);
            PoseStack poseStack = event.getPoseStack();
            Float lineWidthValue = this.lineWidthSetting.getValue();
            WorldBoxRenderer.outlineBox(poseStack, box, lineColor, lineWidthValue);
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundRespawnPacket || event.getPacket() instanceof ClientboundLoginPacket) {
         this.resetPending = true;
      }
   }

   public VoidESPModule() {
      super("VoidESP", "Highlights void blocks.", ModuleCategory.RENDER);
      String heightKey = new String("Height");
      this.heightSetting = this.add(new NumberSetting<>(heightKey, 0, -1, 4).withUnit(new String("m")));
      this.radiusSetting = this.add(new NumberSetting<>(new String("Radius"), 10, 1, 16).withUnit(new String("m")));
      this.fadeSetting = this.add(new BooleanSetting(new String("Fade"), true));
      Predicate<Object> fadeRadiusVisibility = ignored -> this.fadeSetting.getValue();
      this.fadeRadiusSetting = this.add(new NumberSetting<>(new String("FadeRadius"), 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit(new String("m")));
      this.colorsSetting = this.add(new BooleanSetting(new String("Colors"), true).markAsGroup().markVisibilityDependency());
      Predicate<Object> fillVisibility = ignored -> {
         Setting<Boolean> colors = this.colorsSetting;
         return colors.isExpanded();
      };
      this.fillSetting = this.add(new ColorSetting(new String("Fill"), new Color(822018048, true), fillVisibility));
      Predicate<Object> lineVisibility = ignored -> {
         Setting<Boolean> colors = this.colorsSetting;
         return colors.isExpanded();
      };
      this.lineSetting = this.add(new ColorSetting(new String("Line"), new Color(-1140916224, true), lineVisibility));
      this.voidBlocks = new HashSet();
   }
}

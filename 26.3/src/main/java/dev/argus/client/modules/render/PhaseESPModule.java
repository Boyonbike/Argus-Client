package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PhaseESPModule extends Module {
   private final Setting<Boolean> crawlingSetting = this.add(new BooleanSetting(new String("Crawling"), false));
   private final Setting<Boolean> inBlocksSetting = this.add(new BooleanSetting(new String("InBlocks"), false));
   private final Setting<Boolean> insideSetting = this.add(new BooleanSetting(new String("Inside"), false).dependsOn(this.inBlocksSetting));
   private final Setting<Color> safeSetting = this.add(new ColorSetting(new String("Safe"), new Color(540373280, true)));
   private final Setting<Color> semiSafeSetting = this.add(new ColorSetting(new String("SemiSafe"), new Color(637525248, true)));
   private final Setting<Color> unsafeSetting = this.add(new ColorSetting(new String("Unsafe"), new Color(634257408, true)));
   private final Setting<Boolean> outlineSetting = this.add(new BooleanSetting(new String("Outline"), false).markVisibilityDependency());
   private final Setting<Float> alphaSetting = this.add(new NumberSetting<>(new String("Alpha"), 1.0F, 0.0F, 1.0F).dependsOn(this.outlineSetting));
   private final Map<BlockPos, PhaseESPModule.BlockAnimation> blockAnimations = new HashMap<>();

   private boolean isNotBedrock(BlockPos position) {
      return BlockInteractionUtils.getBlock(position) != Blocks.BEDROCK;
   }

   private boolean shouldHighlightPosition(BlockPos position) {
      Boolean allowInBlocks = this.inBlocksSetting.getValue();
      if (allowInBlocks) {
         Boolean allowInside = this.insideSetting.getValue();
         if (allowInside) {
            return true;
         }
      }

      AABB blockBounds = new AABB(position);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState shapeState = level.getBlockState(position);
      ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
      VoxelShape collisionShape = shapeState.getCollisionShape(shapeLevel, position);
      if (!collisionShape.isEmpty()) {
         AABB localBounds = collisionShape.bounds();
         blockBounds = localBounds.move(position);
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      AABB playerBounds = player.getBoundingBox();
      AABB insetBounds = blockBounds.inflate(-0.05);
      return !playerBounds.intersects(insetBounds);
   }

   private boolean isPhaseCandidate(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      Block block = state.getBlock();
      float explosionResistance = block.getExplosionResistance();
      return explosionResistance >= 600.0F && BlockInteractionUtils.isCollidable(position) && !state.canBeReplaced();
   }

   private PhaseESPModule.Safety classifySafetyForRender(BlockPos position) {
      return this.classifySafety(position);
   }

   private PhaseESPModule.Safety classifySafety(BlockPos position) {
      Block block = WorldUtils.getBlock(position);
      float explosionResistance = block.getExplosionResistance();
      boolean resistantBlock = explosionResistance >= 600.0F;
      BlockPos collisionFloorPosition = position.below();
      if (BlockInteractionUtils.isCollidable(collisionFloorPosition) && resistantBlock) {
         BlockPos resistanceFloorPosition = position.below();
         Block floorBlock = WorldUtils.getBlock(resistanceFloorPosition);
         explosionResistance = floorBlock.getExplosionResistance();
         if (!(explosionResistance < 600.0F)) {
            if (!this.isNotBedrock(position)) {
               BlockPos bedrockFloorPosition = position.below();
               if (!this.isNotBedrock(bedrockFloorPosition)) {
                  return PhaseESPModule.Safety.SAFE;
               }
            }

            return PhaseESPModule.Safety.SEMI;
         }
      }

      return PhaseESPModule.Safety.UNSAFE;
   }

   private void refreshPosition(BlockPos position) {
      Map<BlockPos, PhaseESPModule.BlockAnimation> animations = this.blockAnimations;
      PhaseESPModule.BlockAnimation animation = animations.getOrDefault(position, PhaseESPModule.BlockAnimation.create());
      animations = this.blockAnimations;
      animations.put(position, animation.refresh());
   }

   private AABB getBlockBounds(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState shapeState = level.getBlockState(position);
      ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
      VoxelShape shape = shapeState.getShape(shapeLevel, position);
      if (shape.isEmpty()) {
         shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      }

      AABB localBounds = shape.bounds();
      return localBounds.move(position);
   }

   private static boolean isPlayerInCollidableBlock() {
      BlockPos playerPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      return BlockInteractionUtils.isCollidable(playerPosition);
   }

   private void removeExpiredPositions() {
      this.blockAnimations.entrySet().removeIf(entry -> {
         long nowMillis = System.currentTimeMillis();
         PhaseESPModule.BlockAnimation animation = entry.getValue();
         return nowMillis > animation.expiresAtMillis();
      });
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.removeExpiredPositions();
      double playerMinY = ClientAccess.argusclientMinecraft.player.getBoundingBox().minY;
      float roundedPlayerY = (float)Math.round(playerMinY);
      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      AABB minimumYBounds = playerBounds.setMinY(roundedPlayerY);
      AABB flatBounds = minimumYBounds.setMaxY(roundedPlayerY);
      AABB searchBounds = flatBounds.inflate(0.01, 0.0, 0.01);
      boolean crawlingBlocked = PlayerStateUtils.isLocalHeightBelowOne() && !this.crawlingSetting.getValue();
      boolean inBlockBlocked = isPlayerInCollidableBlock() && !this.inBlocksSetting.getValue();
      if (!inBlockBlocked && !crawlingBlocked) {
         BlockPos.betweenClosedStream(searchBounds)
            .map(BlockPos::immutable)
            .filter(this::isPhaseCandidate)
            .filter(this::shouldHighlightPosition)
            .forEach(this::refreshPosition);
      }

      for (Entry<BlockPos, PhaseESPModule.BlockAnimation> entry : this.blockAnimations.entrySet()) {
         BlockPos position = entry.getKey();
         PhaseESPModule.BlockAnimation animation = entry.getValue();
         PhaseESPModule.Safety safety = this.classifySafetyForRender(position);
         Color fillColor = safety.getFadedColor(this, animation);
         AABB blockBounds = this.getBlockBounds(position);
         PoseStack fillPoses = event.getPoseStack();
         BlockPos fillPosition = position;
         double fillMaxY = fillPosition.getY();
         WorldBoxRenderer.fillBox(fillPoses, blockBounds.setMaxY(fillMaxY), fillColor);
         Boolean drawOutline = this.outlineSetting.getValue();
         if (drawOutline) {
            float animationAlpha = animation.getAlpha();
            Float configuredAlpha = this.alphaSetting.getValue();
            float outlineAlpha = animationAlpha * configuredAlpha;
            PoseStack outlinePoses = event.getPoseStack();
            BlockPos outlinePosition = position;
            double outlineMaxY = outlinePosition.getY();
            AABB outlineBounds = blockBounds.setMaxY(outlineMaxY);
            Color outlineColor = ColorUtils.withAlpha(fillColor, outlineAlpha);
            WorldBoxRenderer.outlineBox(outlinePoses, outlineBounds, outlineColor, 1.0F);
         }
      }
   }

   public PhaseESPModule() {
      super("PhaseESP", "Highlights safe blocks to phase into.", ModuleCategory.RENDER);
   }

   private record BlockAnimation(long createdAtMillis, long expiresAtMillis) {
      public static PhaseESPModule.BlockAnimation create() {
         PhaseESPModule.BlockAnimation animation = new PhaseESPModule.BlockAnimation(System.currentTimeMillis(), 0L);
         return animation.refresh();
      }

      public PhaseESPModule.BlockAnimation refresh() {
         return new PhaseESPModule.BlockAnimation(this.createdAtMillis, System.currentTimeMillis() + 200L);
      }

      public float getAlpha() {
         long ageMillis = System.currentTimeMillis() - this.createdAtMillis;
         float alphaValue = (float)ageMillis;
         if (alphaValue <= 75.0F) {
            alphaValue = (float)ageMillis;
            return alphaValue / 75.0F;
         } else {
            alphaValue = (float)ageMillis;
            float lifetimeMillis = (float)(this.expiresAtMillis - this.createdAtMillis);
            if (alphaValue >= lifetimeMillis - 75.0F) {
               alphaValue = (float)(this.expiresAtMillis - this.createdAtMillis - ageMillis);
               alphaValue /= 75.0F;
               return Mth.clamp(alphaValue, 0.0F, 1.0F);
            } else {
               return 1.0F;
            }
         }
      }
   }

   enum Safety {
      SAFE,
      SEMI,
      UNSAFE;

      Color getFadedColor(PhaseESPModule module, PhaseESPModule.BlockAnimation animation) {
         Setting<Color> colorSetting = switch (this) {
            case SAFE -> module.safeSetting;
            case SEMI -> module.semiSafeSetting;
            case UNSAFE -> module.unsafeSetting;
         };
         Color baseColor = colorSetting.getValue();
         float baseAlpha = baseColor.getAlpha();
         return ColorUtils.withAlpha(baseColor, (int)(baseAlpha * animation.getAlpha()));
      }
   }
}

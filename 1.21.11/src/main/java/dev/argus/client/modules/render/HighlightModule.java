package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockOutlineRenderEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.ReachModule;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public class HighlightModule extends Module {
   private static final Set EXCLUDED_AIR_PLACE_BLOCKS;
   private static final MeshBuffer FILL_BUFFER = MeshBuffer.create(8192);
   private static final MeshBuffer OUTLINE_BUFFER = MeshBuffer.create(8192);
   private static ReachModule reach = ClientContext.getModule(ReachModule.class);
   private final Setting<HighlightModule.HighlightMode> modeSetting = this.add(new EnumSetting<>("Mode", HighlightModule.HighlightMode.SIMPLE));
   private final Setting<Boolean> depthSetting = this.add(new BooleanSetting("Depth", false));
   private final Setting<Boolean> unreachableSetting = this.add(new BooleanSetting("Unreachable", false));
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
   private final Setting<Color> colorSetting = this.add(new ColorSetting("Color", new Color(1992137215, true)));
   private final Setting<Color> fillColorSetting = this.add(new ColorSetting("FillColor", new Color(12425727, true)));

   private BlockHitResult getHighlightHitResult() {
      ReachModule reachModule = reach;
      if (reachModule.isToggled()) {
         Setting<Boolean> ghostHandSetting = reach.ghostHandSetting;
         Boolean ghostHandEnabled = ghostHandSetting.getValue();
         if (ghostHandEnabled) {
            ReachModule ghostHandReceiver = reach;
            BlockHitResult ghostHandHit = ghostHandReceiver.getGhostHandHitResult();
            if (ghostHandHit != null && ghostHandHit.getType() != Type.MISS && ghostHandHit.getBlockPos() != null) {
               ghostHandReceiver = reach;
               return ghostHandReceiver.getGhostHandHitResult();
            }
         }
      }

      if (!this.isAirPlaceHighlight()) {
         return ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult crosshairHit && crosshairHit.getType() != Type.MISS ? crosshairHit : null;
      } else {
         return ReachModule.raycastForPlacement() instanceof BlockHitResult placementHit ? placementHit : null;
      }
   }

   private boolean isAirPlaceHighlight() {
      ReachModule reachModule = reach;
      if (!reachModule.isToggled()) {
         return false;
      }

      Setting<Boolean> airPlaceSetting = reach.airPlaceSetting;
      Boolean airPlaceEnabled = airPlaceSetting.getValue();
      if (!airPlaceEnabled) {
         return false;
      }

      Item heldItem = ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem();
      if (this.isAirPlaceBlockItem(heldItem)) {
         return true;
      }

      heldItem = ClientAccess.argusclientMinecraft.player.getOffhandItem().getItem();
      return this.isAirPlaceBlockItem(heldItem);
   }

   private boolean shouldKeepVanillaOutline() {
      return this.unreachableSetting.getValue() && this.unreachableSetting.isVisible();
   }

   private boolean isAirPlaceBlockItem(Item item) {
      if (!(item instanceof BlockItem blockItem)) {
         return false;
      } else {
         Block block = blockItem.getBlock();
         return !(block instanceof VegetationBlock)
            && !(block instanceof GrowingPlantBlock)
            && !(block instanceof CarpetBlock)
            && !(block instanceof CoralFanBlock)
            && !(block instanceof StandingSignBlock)
            && !EXCLUDED_AIR_PLACE_BLOCKS.contains(block);
      }
   }

   @EventHandler
   private void onBlockOutlineRender(BlockOutlineRenderEvent event) {
      if (!this.shouldKeepVanillaOutline()) {
         event.cancel();
      }
   }

   private void renderHighlightBoxes(PoseStack poseStack, HighlightModule.BoxRenderer renderer) {
      BlockPos assisted = reach != null && reach.isToggled() && reach.placementFallbackSetting.getValue()
         ? reach.getAssistedPlacementPos() : null;
      if (assisted != null) {
         renderer.submit(poseStack, Shapes.block().bounds(), assisted);
         return;
      }

      if (!this.isWorldUnavailable()) {
         BlockHitResult hitResult = this.getHighlightHitResult();
         if (hitResult != null) {
            BlockPos blockPos = hitResult.getBlockPos();
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            BlockState blockState = level.getBlockState(blockPos);
            if (this.isAirPlaceHighlight() || !(blockState.getBlock() instanceof AirBlock)) {
               ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
               VoxelShape shape = blockState.getShape(shapeLevel, blockPos);
               if (shape.isEmpty()) {
                  if (this.isAirPlaceHighlight()) {
                     VoxelShape fullBlockShape = Shapes.block();
                     AABB airPlaceBox = fullBlockShape.bounds();
                     renderer.submit(poseStack, airPlaceBox, blockPos);
                  }
               } else if (!this.shouldKeepVanillaOutline()) {
                  Setting<HighlightModule.HighlightMode> modeForComplex = this.modeSetting;
                  if (modeForComplex.getValue() == HighlightModule.HighlightMode.COMPLEX) {
                     for (AABB box : shape.toAabbs()) {
                        renderer.submit(poseStack, box, blockPos);
                     }
                  } else {
                     Setting<HighlightModule.HighlightMode> modeForSide = this.modeSetting;
                     if (modeForSide.getValue() == HighlightModule.HighlightMode.SIDE) {
                        AABB sideBox = shape.bounds();
                        Direction face = hitResult.getDirection();
                        Vec3 boxSize = new Vec3(sideBox.getXsize(), sideBox.getYsize(), sideBox.getZsize());
                        Vec3 halfExtents = new Vec3(face.getStepX() == 0 ? 0.5 : 0.0, face.getStepY() == 0 ? 0.5 : 0.0, face.getStepZ() == 0 ? 0.5 : 0.0);
                        Vec3 faceExtents = halfExtents.multiply(boxSize);
                        AABB centerBox = new AABB(sideBox.getCenter(), sideBox.getCenter());
                        Vec3 faceOffset = face.getUnitVec3().multiply(boxSize).scale(0.5);
                        AABB movedFaceBox = centerBox.move(faceOffset);
                        double extentX = faceExtents.x;
                        double extentY = faceExtents.y;
                        double extentZ = faceExtents.z;
                        sideBox = movedFaceBox.inflate(extentX, extentY, extentZ);
                        renderer.submit(poseStack, sideBox, blockPos);
                     } else {
                        AABB simpleBox = shape.bounds();
                        renderer.submit(poseStack, simpleBox, blockPos);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      Setting<Boolean> depthSetting = this.depthSetting;
      Boolean depthEnabled = depthSetting.getValue();
      if (!depthEnabled) {
         PoseStack highlightPoseStack = event.getPoseStack();
         HighlightModule.BoxRenderer renderer = (poseStack, box, blockPos) -> {
            AABB worldBox = box.move(blockPos);
            Setting<Color> fillColorSetting = this.fillColorSetting;
            WorldBoxRenderer.fillBox(poseStack, worldBox, fillColorSetting.getValue());
            worldBox = box.move(blockPos);
            Color outlineColor = this.colorSetting.getValue();
            Float lineWidth = this.lineWidthSetting.getValue();
            WorldBoxRenderer.outlineBox(poseStack, worldBox, outlineColor, lineWidth);
         };
         this.renderHighlightBoxes(highlightPoseStack, renderer);
      }
   }

   @EventHandler
   private void onBeforeBufferSource(WorldRenderEvent.BeforeBufferSource event) {
      Setting<Boolean> depthSetting = this.depthSetting;
      Boolean depthEnabled = depthSetting.getValue();
      if (depthEnabled) {
         FILL_BUFFER.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         OUTLINE_BUFFER.begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
         PoseStack highlightPoseStack = event.getPoseStack();
         HighlightModule.BoxRenderer renderer = (poseStack, box, blockPos) -> {
            float inflation = 0.002F;
            Setting<HighlightModule.HighlightMode> modeSetting = this.modeSetting;
            if (modeSetting.getValue() == HighlightModule.HighlightMode.SIDE) {
               inflation = -3.0E-4F;
            } else {
               ClientLevel emptyBlockLevel = ClientAccess.argusclientMinecraft.level;
               if (emptyBlockLevel.isEmptyBlock(blockPos)) {
                  inflation = -0.005F;
               }
            }

            AABB worldBox = box.move(blockPos);
            double inflationAmount = inflation;
            AABB expandedBox = worldBox.inflate(inflationAmount);
            MeshBuffer fillBuffer = FILL_BUFFER;
            Pose fillPose = poseStack.last();
            Matrix4f poseMatrix = fillPose.pose();
            Setting<Color> fillColorSetting = this.fillColorSetting;
            Color fillColor = fillColorSetting.getValue();
            VertexGeometry.appendFilledBox(fillBuffer, poseMatrix, expandedBox, fillColor.hashCode());
            MeshBuffer outlineBuffer = OUTLINE_BUFFER;
            Pose outlinePose = poseStack.last();
            Color outlineColor = this.colorSetting.getValue();
            VertexGeometry.appendBoxOutline(outlineBuffer, outlinePose, expandedBox, this.lineWidthSetting.getValue(), outlineColor.hashCode());
         };
         this.renderHighlightBoxes(highlightPoseStack, renderer);
         FILL_BUFFER.draw(ClientRenderTypes.worldFillDepth());
         OUTLINE_BUFFER.draw(ClientRenderTypes.worldLinesDepth());
      }
   }

   public static void bindModuleDependencies(ModuleRegistry registry) {
      reach = (ReachModule)registry.getModule(ReachModule.class);
   }

   public HighlightModule() {
      super("Highlight", "Highlights the block you're looking at.", ModuleCategory.RENDER, "blockhighlight");
      this.setDrawn(false);
      this.unreachableSetting.addVisibilityPredicate(ignoredValue -> {
         ReachModule reachModule = reach;
         if (reachModule.isToggled()) {
            Setting<Boolean> liquidPlaceSetting = reach.liquidPlaceSetting;
            Boolean liquidPlaceEnabled = liquidPlaceSetting.getValue();
            if (liquidPlaceEnabled) {
               return true;
            }

            Setting<Boolean> airPlaceSetting = reach.airPlaceSetting;
            Boolean airPlaceEnabled = airPlaceSetting.getValue();
            if (airPlaceEnabled) {
               return true;
            }
         }

         return false;
      });
   }

   static {
      Block cactus = Blocks.CACTUS;
      Block chorusFlower = Blocks.CHORUS_FLOWER;
      Block snow = Blocks.SNOW;
      Block mossCarpet = Blocks.MOSS_CARPET;
      Block pointedDripstone = Blocks.POINTED_DRIPSTONE;
      Block bamboo = Blocks.BAMBOO;
      EXCLUDED_AIR_PLACE_BLOCKS = Set.of(cactus, chorusFlower, snow, mossCarpet, pointedDripstone, bamboo);
   }

   private interface BoxRenderer {
      void submit(PoseStack var1, AABB var2, BlockPos var3);
   }

   private enum HighlightMode implements NamedEntry {
      SIMPLE("Simple"),
      SIDE("Side"),
      COMPLEX("Complex");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      HighlightMode(String displayName) {
         this.displayName = displayName;
      }
   }
}

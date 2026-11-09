package dev.argus.client.modules.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.BlockBreakEntry;
import dev.argus.client.utils.world.BlockBreakTracker;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BreakHighlightModule extends Module {
   private final Setting<Double> rangeSetting = this.add(new NumberSetting<>("Range", 10.0, 1.0, 25.0).withUnit("m"));
   private final Setting<Boolean> friendsSetting = this.add(new BooleanSetting("Friends", true));
   private final Setting<Boolean> enemyColorSetting = this.add(new BooleanSetting("EnemyColor", true));
   private final Setting<Color> fillSetting = this.add(new ColorSetting("Fill", new Color(279542783, true)));
   private final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(12425727, true)));

   private AABB scaleBoxFromCenter(AABB box, float progress) {
      float scale = Mth.clamp(progress, 0.0F, 1.0F);
      double signedWidth = box.getXsize();
      double width = Math.abs(signedWidth);
      double height = Math.abs(box.getYsize());
      double depth = Math.abs(box.getZsize());
      Vec3 center = box.getCenter();
      return new AABB(
         center.subtract(scale * width / 2.0, scale * height / 2.0, scale * depth / 2.0),
         center.add(scale * width / 2.0, scale * height / 2.0, scale * depth / 2.0)
      );
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      BlockBreakTracker trackerSnapshot = ClientContext.blockBreakTracker;

      for (BlockBreakEntry entry : trackerSnapshot.getEntries()) {
         BlockPos positionForDistance = entry.getPosition();
         double distance = MathUtils.distanceToBlockCenter(positionForDistance);
         Setting<Double> rangeSetting = this.rangeSetting;
         Double range = rangeSetting.getValue();
         if (!(distance > range)) {
            AABB boxForVisibility = new AABB(entry.getPosition());
            if (RotationManager.isVisible(boxForVisibility)) {
               BlockPos positionForMining = entry.getPosition();
               if (WorldUtils.canMineBlock(positionForMining)) {
                  Color fillColor = this.fillSetting.getValue();
                  Color outlineColor = this.outlineSetting.getValue();
                  ClientLevel stateLevel = ClientAccess.argusclientMinecraft.level;
                  BlockPos shapeStatePosition = entry.getPosition();
                  BlockState blockState = stateLevel.getBlockState(shapeStatePosition);
                  ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
                  BlockPos shapePosition = entry.getPosition();
                  VoxelShape shape = blockState.getShape(shapeLevel, shapePosition);
                  float partialTickSnapshot = event.getPartialTick();
                  float progress = entry.getInterpolatedProgress(partialTickSnapshot);
                  Entity breaker = entry.getEntity();
                  AABB localBounds;
                  if (shape.isEmpty()) {
                     localBounds = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                  } else {
                     localBounds = shape.bounds();
                  }

                  AABB box = this.scaleBoxFromCenter(localBounds, progress);
                  BlockPos offsetPosition = entry.getPosition();
                  box = box.move(offsetPosition);
                  if (breaker instanceof Player player) {
                     SocialRegistry registrySnapshot = ClientContext.socialRegistry;
                     GameProfile profile = player.getGameProfile();
                     String playerNameSnapshot = profile.name();
                     Color socialColor = registrySnapshot.getColor(playerNameSnapshot, null);
                     Setting<Boolean> friendsSetting = this.friendsSetting;
                     Boolean renderFriends = friendsSetting.getValue();
                     if (!renderFriends) {
                        SocialRegistry friendRegistry = ClientContext.socialRegistry;
                        if (friendRegistry.isFriendPlayer(player)) {
                           continue;
                        }
                     }

                     if (socialColor != null) {
                        SocialRegistry enemyRegistrySnapshot = ClientContext.socialRegistry;
                        GameProfile enemyProfile = player.getGameProfile();
                        String enemyNameSnapshot = enemyProfile.name();
                        if (!enemyRegistrySnapshot.isEnemyName(enemyNameSnapshot) || this.enemyColorSetting.getValue()) {
                           Color configuredFill = this.fillSetting.getValue();
                           fillColor = ColorUtils.withAlpha(socialColor, configuredFill.getAlpha());
                           Color configuredOutline = this.outlineSetting.getValue();
                           outlineColor = ColorUtils.withAlpha(socialColor, configuredOutline.getAlpha());
                        }
                     }
                  }

                  PoseStack fillPoseStack = event.getPoseStack();
                  WorldBoxRenderer.fillBox(fillPoseStack, this.scaleBoxFromCenter(box, progress), fillColor);
                  PoseStack outlinePoseStack = event.getPoseStack();
                  AABB scaledOutlineBox = this.scaleBoxFromCenter(box, progress);
                  WorldBoxRenderer.outlineBox(outlinePoseStack, scaledOutlineBox, outlineColor, 1.0F);
               }
            }
         }
      }
   }

   public BreakHighlightModule() {
      super("BreakHighlight", "Highlights the blocks that are being broken.", ModuleCategory.RENDER);
   }
}

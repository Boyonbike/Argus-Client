package dev.argus.client.modules.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.EntityChamsRenderer;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.entity.SidePlayer;
import java.awt.Color;
import net.minecraft.world.phys.AABB;

public final class FakeLagRenderer implements ClientAccess {
   private final FakeLagModule module;
   private final ClampedInterpolatedFloat fadeProgress = new ClampedInterpolatedFloat(2.0F, true);

   public FakeLagRenderer(FakeLagModule module) {
      this.module = module;
   }

   private int fadeAlpha(int alpha) {
      return Math.max(alpha - (int)(alpha * this.fadeProgress.getValue()), 0);
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (this.module.renderSetting.getValue() && !this.isWorldUnavailable()) {
         SidePlayer snapshot = this.module.bufferedPlayerSnapshot;
         if (snapshot != null) {
            this.fadeProgress.setTarget(!this.module.isToggled());
            this.fadeProgress.updateAndGet();
            Color configuredFill = this.module.fillSetting.getValue();
            Color fill = ColorUtils.withAlpha(configuredFill, this.fadeAlpha(configuredFill.getAlpha()));
            Color configuredOutline = this.module.outlineSetting.getValue();
            Color outline = ColorUtils.withAlpha(configuredOutline, this.fadeAlpha(configuredOutline.getAlpha()));
            float lineWidth = this.module.lineWidthSetting.getValue();
            PoseStack poseStack = event.getPoseStack();
            if (this.module.chamsSetting.getValue()) {
               EntityChamsRenderer.setColors(outline, fill);
               EntityChamsRenderer.setLineWidth(lineWidth);
               EntityChamsRenderer.renderEntity(poseStack, snapshot);
            } else {
               AABB bounds = snapshot.getBoundingBox();
               WorldBoxRenderer.fillBox(poseStack, bounds, fill);
               WorldBoxRenderer.outlineBox(poseStack, bounds, outline, lineWidth);
            }
         }
      }
   }
}

package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.TracersHitboxChoice;
import dev.argus.client.utils.math.Vec3Utils;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

public final class LineTracerRenderer extends TracerRenderer {
   public LineTracerRenderer(TracersModule module) {
      super(module);
   }

   @Override
   public void render(PoseStack poseStack, float unusedTickDelta) {
      LineMeshRenderer.begin(poseStack, this.module.lineWidthSetting.getValue());
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (AbstractClientPlayer player : level.players()) {
         if (!this.module.shouldSkip(player)) {
            TracersModule geometryModule = this.module;
            TracerEndpoints endpoints = geometryModule.getEndpoints(player);
            Setting<TracersHitboxChoice> hitboxSetting = this.module.hitboxSetting;
            TracersHitboxChoice hitbox = hitboxSetting.getValue();
            float configuredHitboxFraction = hitbox.getHeightFraction();
            Vec3 start = endpoints.start();
            Vec3 capturedEnd = endpoints.end();
            Vec3 hitboxPosition = Vec3Utils.lerp(configuredHitboxFraction, start, capturedEnd);
            Color color = this.module.getColor(player, hitboxPosition);
            LineMeshRenderer.addTracer(hitboxPosition, color);
            Setting<Boolean> stemSetting = this.module.stemSetting;
            Boolean drawStem = stemSetting.getValue();
            if (drawStem) {
               Vec3 stemStart = endpoints.start();
               LineMeshRenderer.addLine(stemStart, endpoints.end(), color);
            }
         }
      }

      LineMeshRenderer.draw();
   }
}

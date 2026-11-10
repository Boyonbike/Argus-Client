package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.render.LineMeshRenderer;

public final class LineTracerRenderer extends TracerRenderer {
   public LineTracerRenderer(TracersModule module) {
      super(module);
   }

   @Override
   public void render(PoseStack poseStack, float tickDelta) {
      LineMeshRenderer.begin(poseStack, this.module.lineWidthSetting.getValue());
      boolean stem = this.module.stemSetting.getValue();
      for (TracersModule.Target target : this.module.collect(tickDelta)) {
         LineMeshRenderer.addTracer(target.aim(), target.color());
         if (stem && target.stemStart() != null) {
            LineMeshRenderer.addLine(target.stemStart(), target.stemEnd(), target.color());
         }
      }

      LineMeshRenderer.draw();
   }
}

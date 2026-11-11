package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class GraphModule extends HudWidgetModule {
   private final Setting<Float> ceilSetting = this.add(new NumberSetting<>("Ceil", 26.0F, 10.0F, 100.0F));
   private final Setting<Integer> heightSetting = this.add(new NumberSetting<>("Height", 25, 10, 30));
   private final Setting<Integer> widthSetting = this.add(new NumberSetting<>("Width", 75, 50, 100));
   private final List<Double> speedSamples = new ArrayList<>();

   @Override
   public void onEnable() {
      this.speedSamples.clear();

      for (int sampleIndex = 0; sampleIndex < 150; sampleIndex++) {
         this.speedSamples.add(0.0);
      }
   }

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = new float[2];
      Integer width = this.widthSetting.getValue();
      dimensions[0] = width.intValue();
      Integer height = this.heightSetting.getValue();
      dimensions[1] = height.intValue();
      return dimensions;
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (this.speedSamples.size() >= 2) {
         MeshBuffer buffer = MeshDrawContext.getBuffer();
         buffer.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

         for (int sampleIndex = 1; sampleIndex < this.speedSamples.size(); sampleIndex++) {
            float previousY = this.getSampleY(this.speedSamples.get(sampleIndex - 1));
            float currentY = this.getSampleY(this.speedSamples.get(sampleIndex));
            VertexGeometry.appendLine(
               buffer,
               poses.last(),
               sampleIndex - 1,
               previousY,
               0.0F,
               sampleIndex,
               currentY,
               0.0F,
               1.0F,
               this.getSampleColor(previousY, sampleIndex - 1),
               this.getSampleColor(currentY, sampleIndex)
            );
         }

         buffer.draw(ClientRenderTypes.worldLines());
      }
   }

   private float getSampleY(double sample) {
      int height = this.heightSetting.getValue();
      double clippedSample = Math.min(sample, this.ceilSetting.getValue().floatValue());
      return 1.0F + (float)((height - 2) * (1.0 - clippedSample / this.ceilSetting.getValue().floatValue()));
   }

   private int getSampleColor(float sampleY, int sampleIndex) {
      Color baseColor = this.getHudColor(sampleY + this.renderer.getY());
      return ColorUtils.withAlpha(baseColor, this.getSampleAlpha(sampleIndex)).hashCode();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      List<Double> samplesForAppend = this.speedSamples;
      double speed = ClientContext.movementSpeedTracker.getDisplaySpeed();
      samplesForAppend.add(speed);

      while (this.speedSamples.size() > this.widthSetting.getValue()) {
         this.speedSamples.remove(0);
      }
   }

   private int getSampleAlpha(int sampleIndex) {
      if (sampleIndex <= 10) {
         float leadingAlpha = sampleIndex;
         leadingAlpha /= 10.0F;
         leadingAlpha *= 255.0F;
         float leadingMaximum = 255.0F;
         return (int)Mth.clamp(leadingAlpha, 0.0F, leadingMaximum);
      } else {
         Integer widthForThreshold = this.widthSetting.getValue();
         int fadePositionOperandScratch = widthForThreshold;
         if (sampleIndex >= fadePositionOperandScratch - 10) {
            float trailingStart = 1.0F;
            Integer widthForRamp = this.widthSetting.getValue();
            int relativePosition = sampleIndex - widthForRamp;
            float trailingPosition = relativePosition + 10;
            float trailingProgress = trailingStart - trailingPosition / 10.0F;
            float trailingAlpha = trailingProgress * 255.0F;
            float trailingMaximum = 255.0F;
            return (int)Mth.clamp(trailingAlpha, 0.0F, trailingMaximum);
         } else {
            return 255;
         }
      }
   }

   public GraphModule() {
      super("Graph");
      this.setRenderer(new ResizableHudWidgetRenderer(this));
   }
}

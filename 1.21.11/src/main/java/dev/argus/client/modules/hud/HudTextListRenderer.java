package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.HudWidgetRenderer;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.Mth;

public class HudTextListRenderer extends HudWidgetRenderer {
   private static final HUDModule hudSettings = ClientContext.getModule(HUDModule.class);
   private final List<? extends HudTextRow> rows;

   public HudTextListRenderer(HudWidgetModule widget, HudTextRow row) {
      this(widget, Collections.singletonList(row));
   }

   public HudTextListRenderer(HudWidgetModule widget, List<? extends HudTextRow> rows) {
      super(widget);
      this.rows = rows;
   }

   @Override
   public float[] calculateDimensions() {
      float height = 0.0F;
      float width = 0.0F;

      for (HudTextRow row : this.rows) {
         row.updateVisibility();
         if (row.getVisibility() != 0.0F) {
            float easedVisibility = row.getVisibility();
            easedVisibility = (float)Mth.clamp(1.0 - Math.pow(1.0F - easedVisibility, 4.0), 0.0, 1.0);
            float textWidth = FontRenderer.INSTANCE.getWidth(row.getText().getString());
            height += this.getHeight() * easedVisibility + 1.0F;
            if (textWidth > width) {
               width = textWidth;
            }
         }
      }

      if (!renderingHud && width == 0.0F) {
         width = 50.0F;
         height = this.getHeight();
      }

      width *= hudSettings.getTextScale();
      height *= hudSettings.getTextScale();
      return new float[]{width, height};
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      super.render(poses, mouseX, mouseY);
      float scale = hudSettings.getTextScale();
      float rowY = this.getContentY(this.getHeight() * scale);

      for (HudTextRow row : this.rows) {
         float easedVisibility = row.getVisibility();
         if (easedVisibility != 0.0F) {
            poses.pushPose();
            easedVisibility = (float)Mth.clamp(1.0 - Math.pow(1.0F - easedVisibility, 4.0), 0.0, 1.0);
            Color rowColor = this.getRowColor(rowY, row);
            float animatedWidth = row.widthAnimation.updateAndGet() * scale;
            float hiddenWidth = animatedWidth * (1.0F - easedVisibility);
            poses.translate(this.getContentX(animatedWidth) - hiddenWidth * this.getHorizontalDirection(), rowY, 0.0F);
            poses.scale(scale, scale, 1.0F);
            row.renderRow(poses, 0.0F, 0.0F, rowColor, hudSettings.shouldDrawTextShadow());
            if (easedVisibility != 0.0F) {
               rowY += (this.getHeight() * easedVisibility + 1.0F) * this.getVerticalDirection() * scale;
            }

            poses.popPose();
         }
      }
   }

   public Color getRowColor(float rowPosition, HudTextRow row) {
      return this.widget.getHudColor(rowPosition);
   }

   public List<? extends HudTextRow> getRows() {
      return this.rows;
   }
}

package dev.argus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.HudGrid;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class HudAnchorGroup implements ClientAccess, GuiElement {
   private static final HUDModule hud = ClientContext.getModule(HUDModule.class);
   private final List<HudWidgetRenderer> widgets = new ArrayList();
   private final HudAnchor anchor;

   public List<HudWidgetRenderer> getWidgets() {
      return this.widgets;
   }

   public HudAnchorGroup(HudAnchor anchor) {
      this.anchor = anchor;
   }

   private void prepareWidgetRender(float enabledProgress) {
      if (!HudWidgetRenderer.renderingHud && enabledProgress != 1.0F) {
         GuiDrawUtils.flush();
      }
   }

   private float getBottomMargin(boolean rightAligned) {
      float sideOffset = rightAligned ? hud.getBottomHudOffset() : hud.getChatInputOffset();
      return Mth.clamp(hud.getSafeY() + sideOffset, 0.0F, 16.0F);
   }

   private float calculateY(float height, HudHorizontalAlignment horizontalAlignment, HudVerticalAlignment verticalAlignment) {
      return switch (verticalAlignment) {
         case TOP -> hud.getSafeY() + (horizontalAlignment == HudHorizontalAlignment.RIGHT ? hud.getEffectIconOffset() : 0.0F);
         case BOTTOM -> ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight()
            - height
            - this.getBottomMargin(horizontalAlignment == HudHorizontalAlignment.RIGHT);
         case CENTER -> (ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight() - height) / 2.0F;
         default -> throw new IllegalStateException("Unexpected value: " + verticalAlignment);
      };
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      poseStack.pushPose();
      if (this.anchor != HudAnchor.NONE) {
         this.applyHudTranslation(poseStack);
      }

      for (int widgetIndex = 0; widgetIndex < this.widgets.size(); widgetIndex++) {
         HudWidgetRenderer widget = (HudWidgetRenderer)this.widgets.get(widgetIndex);
         widget.updateEnabledAnimation();
         float enabledProgress = widget.getEnabledProgress();
         boolean visible = widget.isEnabled() || enabledProgress > 0.0F;
         float[] size;
         if (!visible) {
            size = new float[2];
         } else {
            size = widget.calculateDimensions();
            // Sizes round up to whole grid steps, so elements line up and stacks space evenly on the grid.
            float fitW = HudGrid.fit(size[0]), fitH = HudGrid.fit(size[1]);
            widget.setGridPad(fitW - size[0], fitH - size[1]);
            size = new float[]{fitW, fitH};
         }

         widget.setDimensions(size);
         if (this.anchor != HudAnchor.NONE) {
            this.layoutAnchoredWidget(widget, size, widgetIndex);
         } else {
            this.layoutFreeWidget(widget, size);
         }

         this.prepareWidgetRender(enabledProgress);
         if (visible) {
            RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, enabledProgress);
            float shiftX = widget.contentShiftX(), shiftY = widget.contentShiftY();
            poseStack.pushPose();
            poseStack.translate(shiftX, shiftY, 0.0F);
            widget.render(poseStack, mouseX, mouseY);
            poseStack.popPose();
            this.prepareWidgetRender(enabledProgress);
            if (!HudWidgetRenderer.renderingHud) {
               RenderUtils.getEntityBuffers().endBatch(ClientRenderTypes.guiTriangles());
            }

            RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }

         if (widgetIndex < this.widgets.size() - 1 && this.anchor != HudAnchor.NONE && this.anchor.getVerticalAlignment() != HudVerticalAlignment.BOTTOM) {
            ((HudWidgetRenderer)this.widgets.get(widgetIndex + 1)).setY(widget.getY() + size[1], true);
         }
      }

      poseStack.popPose();
   }

   private void layoutFreeWidget(HudWidgetRenderer widget, float[] size) {
      HudHorizontalAlignment horizontalAlignment = widget.getHorizontalAlignment();
      HudVerticalAlignment verticalAlignment = widget.getVerticalAlignment();
      if (horizontalAlignment != HudHorizontalAlignment.FREE) {
         widget.setX(this.calculateX(size[0], horizontalAlignment), true);
      }

      if (verticalAlignment != HudVerticalAlignment.FREE) {
         widget.setY(this.calculateY(size[1], horizontalAlignment, verticalAlignment), true);
      }
   }

   private void layoutAnchoredWidget(HudWidgetRenderer widget, float[] size, int widgetIndex) {
      widget.setX(this.calculateX(size[0], this.anchor.getHorizontalAlignment()), true);
      if (widgetIndex == 0) {
         widget.setY(this.calculateY(size[1], this.anchor.getHorizontalAlignment(), this.anchor.getVerticalAlignment()), true);
      } else if (this.anchor == HudAnchor.BOTTOM_LEFT || this.anchor == HudAnchor.BOTTOM_RIGHT) {
         widget.setY(((HudWidgetRenderer)this.widgets.get(widgetIndex - 1)).getY() - size[1], true);
      }
   }

   private float calculateX(float width, HudHorizontalAlignment horizontalAlignment) {
      return switch (horizontalAlignment) {
         case LEFT -> hud.getSafeX();
         case RIGHT -> ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() - width - hud.getSafeX();
         case CENTER -> ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() / 2.0F - width / 2.0F;
         default -> throw new IllegalStateException("Unexpected value: " + horizontalAlignment);
      };
   }

   private void applyHudTranslation(PoseStack poseStack) {
      poseStack.translate(hud.getParallaxOffset());
   }

   public HudAnchor getAnchor() {
      return this.anchor;
   }
}

package dev.argus.client.gui.screens;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.components.HudModulePanel;
import dev.argus.client.gui.components.ModuleComponent;
import dev.argus.client.gui.components.StringSettingComponent;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.render.ClientRenderPipelines;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.HudAnchorGroup;
import dev.argus.client.render.HudHoverLabel;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.render.MaskShaderRenderer;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.collections.IntPair;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class HudEditorScreen extends ClientScreen implements ClientAccess {
   private static HUDModule hud = ClientContext.getModule(HUDModule.class);
   private static final List<HudAnchorGroup> anchorGroups = Collections.synchronizedList(new ArrayList<>());
   public static final HudHoverLabel hoverLabel = new HudHoverLabel();
   private final HudMarginController marginController = new HudMarginController();
   private final ClampedInterpolatedFloat hoverLabelOpacity = new ClampedInterpolatedFloat(2.0F, true);
   private final HudModulePanel modulePanel = new HudModulePanel();
   private boolean draggingWidget;
   private boolean centerPanelPending;
   private boolean directionsRefreshPending;
   private float dragOffsetX;
   private float dragOffsetY;
   private float mouseX;
   private int canvasWidth;
   private int canvasHeight;
   private boolean canvasPending;
   private float mouseY;

   private IntPair getArrowKeyOffset(int keyCode) {
      return switch (keyCode) {
         case 262 -> new IntPair(1, 0);
         case 263 -> new IntPair(-1, 0);
         case 264 -> new IntPair(0, 1);
         case 265 -> new IntPair(0, -1);
         default -> null;
      };
   }

   public HudEditorScreen() {
      this.panels.add(this.modulePanel);

      for (HudAnchor anchor : HudAnchor.values()) {
         anchorGroups.add(new HudAnchorGroup(anchor));
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      this.marginController.mouseReleased(event.button());
      boolean released = super.mouseReleased(event);
      if (this.draggingWidget) {
         this.draggingWidget = false;
         HudAnchorGroup freeGroup = this.getAnchorGroup(HudAnchor.NONE);
         HudWidgetRenderer widget = (HudWidgetRenderer)freeGroup.getWidgets().getLast();
         widget.setSelected(false);
         freeGroup.getWidgets().remove(widget);
         int screenWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
         int screenHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
         widget.updateSnapping(screenWidth, screenHeight, 5.0F);

         for (HudAnchorGroup group : anchorGroups) {
            for (Object widgetElement : group.getWidgets()) {
               HudWidgetRenderer otherWidget = (HudWidgetRenderer)widgetElement;
               if (otherWidget.getBounds().intersects(widget.getBounds())) {
                  this.getAnchorGroup(group.getAnchor()).getWidgets().addLast(widget);
                  widget.setAnchor(group.getAnchor());
                  return true;
               }
            }
         }

         for (HudAnchor anchor : HudAnchor.values()) {
            if (anchor.getBounds().intersects(widget.getBounds().expand(5.0F))) {
               this.getAnchorGroup(anchor).getWidgets().addLast(widget);
               widget.setAnchor(anchor);
               return true;
            }
         }
      }

      return released;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      HudAnchor dragAnchor = null;
      HudWidgetRenderer dragCandidate = null;

      for (HudAnchorGroup group : anchorGroups) {
         for (int widgetIndex = group.getWidgets().size() - 1; widgetIndex >= 0; widgetIndex--) {
            HudWidgetRenderer widget = (HudWidgetRenderer)group.getWidgets().get(widgetIndex);
            if (widget.isEnabled()
               && dragCandidate == null
               && widget.isMouseOver(event.x(), event.y())
               && !(widget instanceof ResizableHudWidgetRenderer resizableWidget && resizableWidget.isResizeHandleHovered(event.x(), event.y()))) {
               if (event.button() == 0) {
                  dragAnchor = group.getAnchor();
                  dragCandidate = widget;
                  this.dragOffsetX = (int)(event.x() - widget.getX());
                  this.dragOffsetY = (int)(event.y() - widget.getY());
                  this.draggingWidget = true;
                  widget.setSelected(true);
                  widget.resetSnapping();
               } else if (event.button() == 1) {
                  ModuleComponent moduleRow = ((List<?>)this.modulePanel.getChildren())
                     .stream()
                     .map(ModuleComponent.class::cast)
                     .filter(row -> row.getModule() == widget.getWidgetModule())
                     .findFirst()
                     .orElseThrow();
                  moduleRow.toggleOpen();
               }
            }
         }
      }

      if (dragCandidate != null) {
         this.getAnchorGroup(dragAnchor).getWidgets().remove(dragCandidate);
         this.getAnchorGroup(HudAnchor.NONE).getWidgets().addLast(dragCandidate);
         dragCandidate.setAnchor(HudAnchor.NONE);
      } else {
         this.marginController.mouseClicked(event.x(), event.y(), event.button());
      }

      return super.mouseClicked(event, doubleClick);
   }

   private void snapWidgetAxis(PoseStack poseStack, HudWidgetRenderer widget, float widgetSize, float screenSize, float margin, boolean horizontal) {
      float position = horizontal ? widget.getX() : widget.getY();
      float screenCenter = screenSize / 2.0F;
      float snappedPosition;
      if (Math.abs(position + widgetSize / 2.0F - screenCenter) <= 5.0F) {
         snappedPosition = screenCenter - widgetSize / 2.0F;
         this.renderSnapGuide(poseStack, snappedPosition - 0.5F, horizontal);
         this.renderSnapGuide(poseStack, snappedPosition + widgetSize, horizontal);
      } else if (Math.abs(position - margin) <= 5.0F) {
         snappedPosition = margin;
         this.renderSnapGuide(poseStack, snappedPosition - 0.5F, horizontal);
      } else {
         if (!(Math.abs(position + widgetSize - (screenSize - margin)) <= 5.0F)) {
            return;
         }

         snappedPosition = screenSize - margin - widgetSize;
         this.renderSnapGuide(poseStack, snappedPosition + widgetSize, horizontal);
      }

      if (horizontal) {
         widget.setX(snappedPosition, true);
      } else {
         widget.setY(snappedPosition, true);
      }
   }

   private void snapWidget(PoseStack poseStack, HudWidgetRenderer widget, float[] size) {
      float screenWidth = this.canvasWidth;
      float screenHeight = this.canvasHeight;
      this.snapWidgetAxis(poseStack, widget, size[0], screenWidth, hud.getSafeX(), true);
      this.snapWidgetAxis(poseStack, widget, size[1], screenHeight, hud.getSafeY(), false);
   }

   private void renderSnapGuide(PoseStack poseStack, double position, boolean vertical) {
      int screenWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
      int screenHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
      float coordinate = (float)position;
      if (vertical) {
         GuiDrawUtils.fillRect(poseStack, coordinate, 0.0F, coordinate + 0.5F, screenHeight, Color.yellow);
      } else {
         GuiDrawUtils.fillRect(poseStack, 0.0F, coordinate, screenWidth, coordinate + 0.5F, Color.yellow);
      }
   }

   private void swapAnchorContents(HudAnchor firstAnchor, HudAnchor secondAnchor) {
      HudAnchorGroup firstGroup = this.getAnchorGroup(firstAnchor);
      HudAnchorGroup secondGroup = this.getAnchorGroup(secondAnchor);
      ArrayList firstWidgets = new ArrayList(firstGroup.getWidgets());
      firstGroup.getWidgets().clear();

      for (Object secondWidgetElement : secondGroup.getWidgets()) {
         HudWidgetRenderer secondWidget = (HudWidgetRenderer)secondWidgetElement;
         firstGroup.getWidgets().add(secondWidget);
      }

      secondGroup.getWidgets().clear();

      for (Object firstWidgetElement : firstWidgets) {
         HudWidgetRenderer firstWidget = (HudWidgetRenderer)firstWidgetElement;
         secondGroup.getWidgets().add(firstWidget);
      }
   }

   public void addWidget(HudAnchor anchor, HudWidgetRenderer widget) {
      this.getAnchorGroup(anchor).getWidgets().add(widget);
   }

   public void addWidget(HudWidgetRenderer widget) {
      this.addWidget(HudAnchor.NONE, widget);
   }

   public HudAnchorGroup getAnchorGroup(HudAnchor anchor) {
      for (HudAnchorGroup group : this.getAnchorGroups()) {
         if (group.getAnchor() == anchor) {
            return group;
         }
      }

      return null;
   }

   public void renderWidgets(PoseStack poseStack, double mouseX, double mouseY) {
      if (!hud.bloomSetting.getValue()) {
         this.renderWidgetContents(poseStack, mouseX, mouseY);
      } else {
         GuiDrawUtils.flush();
         MaskShaderRenderer.setParameters(hud.bloomRadiusSetting.getValue().intValue(), hud.bloomStrengthSetting.getValue(), 1.0F, 1.0F);
         RenderTarget mainTarget = ClientAccess.argusclientMinecraft.gameRenderer.mainRenderTarget();

         try {
            MaskShaderRenderer.captureMask(() -> {
               this.renderWidgetContents(poseStack, mouseX, mouseY);
               GuiDrawUtils.flush();
            }, Color.black);
            MaskShaderRenderer.renderEffect("glowy");
            RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET, ClientRenderPipelines.BLIT_PREMULTIPLIED_ALPHA);
         } finally {
            RenderTargetUtils.setMainRenderTarget(mainTarget);
         }
      }
   }

   private void renderWidgetContents(PoseStack poseStack, double mouseX, double mouseY) {
      for (HudAnchorGroup group : this.getAnchorGroups()) {
         group.render(poseStack, mouseX, mouseY);
      }

      if (this.directionsRefreshPending) {
         for (HudAnchorGroup groupToRefresh : anchorGroups) {
            for (Object widgetElement : groupToRefresh.getWidgets()) {
               HudWidgetRenderer widget = (HudWidgetRenderer)widgetElement;
               widget.updateDirections();
            }
         }

         this.directionsRefreshPending = false;
      }
   }

   public void removeWidget(HudWidgetRenderer widget) {
      for (HudAnchorGroup group : this.getAnchorGroups()) {
         if (group.getWidgets().remove(widget)) {
            return;
         }
      }
   }

   @Override
   protected void init() {
      super.init();
      this.centerPanelPending = true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      boolean overPanel = false;

      for (Object panelElement : this.panels) {
         Panel panel = (Panel)panelElement;
         if (panel.isMouseOver(this.mouseX, this.mouseY)) {
            overPanel = true;
            break;
         }
      }

      if (!overPanel) {
         HudWidgetRenderer widget = this.findHoveredWidget();
         IntPair offset = this.getArrowKeyOffset(event.key());
         if (offset != null && widget != null && widget.isEnabled()) {
            widget.setX(widget.getX() + offset.first(), true);
            widget.setY(widget.getY() + offset.second(), true);
            widget.updateDirections();
         } else {
            switch (event.key()) {
               case 73:
                  if (!(ClientContext.getClickGui().getEditingSetting() instanceof StringSettingComponent)) {
                     this.swapAnchorContents(HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT);
                     this.swapAnchorContents(HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT);
                     this.requestDirectionRefresh();
                  }
                  break;
               case 259:
               case 261:
                  if (widget != null && widget.isEnabled()) {
                     widget.getWidgetModule().setToggled(false);
                  }
            }
         }
      }

      return super.keyPressed(event);
   }

   @Override
   public void onClose() {
      super.onClose();
      if (this.draggingWidget) {
         ((HudWidgetRenderer)this.getAnchorGroup(HudAnchor.NONE).getWidgets().getLast()).setSelected(false);
         this.draggingWidget = false;
      }
   }

   /** Draws the HUD canvas for this frame; called from the render phase (see GameRendererMixin). */
   public void renderCanvas() {
      if (!this.canvasPending) {
         return;
      }

      this.canvasPending = false;
      PoseStack poseStack = new PoseStack();
      float mouseX = this.mouseX;
      float mouseY = this.mouseY;
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, this.getTransitionOpacity());

      try {
         hoverLabel.setVisible(false);
         float screenCenterX = this.canvasWidth / 2.0F;
         float screenCenterY = this.canvasHeight / 2.0F;
         Color guideColor = ColorUtils.withAlpha(Color.white, (int)(RenderUtils.getRenderColor()[3] * 0.4F));
         GuiDrawUtils.fillRect(poseStack, screenCenterX - 0.5F, 0.0F, screenCenterX + 0.5F, screenCenterY * 2.0F, guideColor);
         GuiDrawUtils.fillRect(poseStack, 0.0F, screenCenterY - 0.5F, screenCenterX - 0.5F, screenCenterY + 0.5F, guideColor);
         GuiDrawUtils.fillRect(poseStack, screenCenterX + 0.5F, screenCenterY - 0.5F, screenCenterX * 2.0F, screenCenterY + 0.5F, guideColor);
         this.marginController.render(poseStack, (int)mouseX, (int)mouseY);
         this.renderWidgets(poseStack, mouseX, mouseY);
         if (this.draggingWidget) {
            HudAnchorGroup freeGroup = this.getAnchorGroup(HudAnchor.NONE);
            HudWidgetRenderer widget = (HudWidgetRenderer)freeGroup.getWidgets().getLast();
            float[] size = widget.getDimensions();
            widget.setX(Math.max(0.0F, Math.min(this.canvasWidth - size[0], mouseX - this.dragOffsetX)), true);
            widget.setY(Math.max(0.0F, Math.min(this.canvasHeight - size[1], mouseY - this.dragOffsetY)), true);
            widget.updateDirections();
            this.snapWidget(poseStack, widget, size);
         }

         float labelOpacity = this.hoverLabelOpacity.updateAndGet();
         this.hoverLabelOpacity.setTarget(hoverLabel.isVisible());
         if (hoverLabel.isVisible() || labelOpacity > 0.1) {
            FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  hoverLabel.getText(),
                  hoverLabel.getX() + 10.0F * (1.0F - labelOpacity) * hoverLabel.getHorizontalDirection(),
                  hoverLabel.getY(),
                  ColorUtils.withAlpha(Color.white, (int)(labelOpacity * 255.0F))
               );
         }
      } finally {
         RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   /**
    * Records what the canvas needs; the canvas itself (guides, margins, elements, snapping) is drawn by
    * {@link #renderCanvas()} from GameRenderer's render phase. Since 26.x, extraction runs before the
    * world is drawn, so immediate-mode HUD draws made here were painted over and elements showed empty.
    */
   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.canvasWidth = graphics.guiWidth();
      this.canvasHeight = graphics.guiHeight();
      this.canvasPending = true;
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      if (this.centerPanelPending) {
         this.centerModulePanel();
         this.centerPanelPending = false;
      }
   }

   @Override
   public void renderBackgroundOverlay(PoseStack poseStack) {
   }

   public void requestDirectionRefresh() {
      this.directionsRefreshPending = true;
   }

   private HudWidgetRenderer findHoveredWidget() {
      for (HudAnchorGroup group : anchorGroups) {
         for (Object widgetElement : group.getWidgets()) {
            HudWidgetRenderer widget = (HudWidgetRenderer)widgetElement;
            if (widget.isMouseOver(this.mouseX, this.mouseY)) {
               return widget;
            }
         }
      }

      return null;
   }

   private void centerModulePanel() {
      Panel panel = this.panels.getFirst();
      int panelWidth = panel.getWidth();
      int panelHeight = panel.getHeight();
      int panelX = (int)(ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() / 2.0F - panelWidth * 1.5F);
      int panelY = (int)((ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight() - panelHeight) / 2.0F);
      panel.setX(panelX);
      panel.setY(panelY);
   }

   public List<HudAnchorGroup> getAnchorGroups() {
      return anchorGroups;
   }
}

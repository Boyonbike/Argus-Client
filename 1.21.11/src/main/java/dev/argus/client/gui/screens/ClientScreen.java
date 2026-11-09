package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.gui.panels.ModuleCategoryPanel;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.modules.render.BlurModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.GuiProjectionUtils;
import dev.argus.client.render.GuiScaleUtils;
import dev.argus.client.render.MaskShaderRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.world.Season;
import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class ClientScreen extends Screen implements ClientAccess {
   private static final Identifier AERO_TEXTURE = Identifier.fromNamespaceAndPath("argusclient", "textures/aero.png");
   private static final BlurModule BLUR = ClientContext.getModule(BlurModule.class);
   private static final UIModule UI = ClientContext.getModule(UIModule.class);
   private static final long TRANSITION_DURATION_MILLIS = 150L;
   private static final int MAX_SEASONAL_PARTICLES = 25;
   private static final int PANEL_START_X = 10;
   private static final int PANEL_START_Y = 5;
   private static final int PANEL_GAP = 3;
   private static final float TOOLTIP_BACKGROUND_DEPTH = 500.0F;
   private static final float SCREEN_CONTENT_DEPTH = 1000.0F;
   public static CursorType cursor = CursorType.STANDARD;
   protected final ArrayList<Panel> panels = new ArrayList<>();
   protected final List<SnowflakeParticle> seasonalParticles = new ArrayList<>();
   protected final InterpolatedFloat hoverMouseX = new InterpolatedFloat(2.5F, true);
   protected final InterpolatedFloat hoverMouseY = new InterpolatedFloat(2.5F, true);
   protected boolean panelDragging = false;
   protected float guiScale;
   protected boolean closing;
   protected String tooltipText = "";
   protected long lastHoverTime = 0L;
   protected long transitionStartedAt;
   private Screen parentScreen;
   protected boolean escapeCloseAllowed;
   private boolean skipOpeningTransition;

   public Screen getParentScreen() {
      return this.parentScreen;
   }

   public boolean isClosing() {
      return this.closing;
   }

   public void suppressEscapeClose() {
      this.escapeCloseAllowed = false;
   }

   public void markHovered() {
      this.lastHoverTime = System.currentTimeMillis();
   }

   public void resetSeasonalParticles() {
      this.seasonalParticles.clear();

      for (int particleIndex = 0; particleIndex < 25; particleIndex++) {
         this.seasonalParticles.add(new SnowflakeParticle());
      }
   }

   public float getTransitionOpacity() {
      float opacity = (float)(System.currentTimeMillis() - this.transitionStartedAt) / 150.0F;
      if (this.closing) {
         opacity = 1.0F - opacity;
      }

      return Mth.clamp(opacity, 0.0F, 1.0F);
   }

   public void setPanelDragging(boolean dragging) {
      this.panelDragging = dragging;
   }

   public boolean hasMenuParent() {
      return this.getParentScreen() instanceof TitleScreen
         || this.getParentScreen() instanceof DisconnectedScreen
         || this.getParentScreen() instanceof JoinMultiplayerScreen;
   }

   public boolean isPanelDragging() {
      return this.panelDragging;
   }

   public float getGuiScale() {
      return this.guiScale;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   protected ClientScreen() {
      super(Component.literal("Argus Client"));
      this.resetSeasonalParticles();
   }

   protected void reset() {
      this.lastHoverTime = 0L;
   }

   public void onMouseClick(double mouseX, double mouseY, int button) {
   }

   public void setTooltipText(String text) {
      this.tooltipText = text;
   }

   @Override
   public void tick() {
      if (this.closing && System.currentTimeMillis() - this.transitionStartedAt >= 150L) {
         for (Panel panel : this.panels) {
            panel.setDragging(false);
         }

         if (this.parentScreen != null) {
            ClientAccess.argusclientMinecraft.setScreen(this.parentScreen);
         } else {
            this.onClose();
         }

         this.setParentScreen(null);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      event = new MouseButtonEvent(
         event.x() / GuiScaleUtils.calculateScaleMultiplier(this.guiScale),
         event.y() / GuiScaleUtils.calculateScaleMultiplier(this.guiScale),
         event.buttonInfo()
      );

      for (int panelIndex = this.panels.size() - 1; panelIndex >= 0; panelIndex--) {
         Panel panel = this.panels.get(panelIndex);
         panel.setDragging(false);
         panel.mouseReleased(event.x(), event.y(), event.button());
      }

      this.onMouseRelease(event.x(), event.y(), event.button());
      return super.mouseReleased(event);
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      for (int panelIndex = this.panels.size() - 1; panelIndex >= 0; panelIndex--) {
         this.panels.get(panelIndex).charTyped(event.codepointAsString().charAt(0));
      }

      return super.charTyped(event);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (this.closing) {
         return false;
      }

      event = new MouseButtonEvent(
         event.x() / GuiScaleUtils.calculateScaleMultiplier(this.guiScale),
         event.y() / GuiScaleUtils.calculateScaleMultiplier(this.guiScale),
         event.buttonInfo()
      );
      boolean handledByScreen = super.mouseClicked(event, doubleClick);
      Panel draggedPanel = null;

      for (int panelIndex = this.panels.size() - 1; panelIndex >= 0; panelIndex--) {
         Panel panel = this.panels.get(panelIndex);
         if (panel.isMouseOverHeader(event.x(), event.y()) && event.button() == 0 && draggedPanel == null) {
            draggedPanel = panel;
            panel.setDragging(true);
            panel.dragOffsetX = (int)(event.x() - panel.getX());
            panel.dragOffsetY = (int)(event.y() - panel.getY());
         }

         panel.mouseClicked(event.x(), event.y(), event.button());
      }

      if (draggedPanel != null) {
         this.panels.remove(draggedPanel);
         this.panels.add(draggedPanel);
      }

      this.reset();
      this.onMouseClick(event.x(), event.y(), event.button());
      return handledByScreen;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      double scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale);
      mouseX /= scale;
      mouseY /= scale;
      if (verticalAmount != 0.0) {
         for (Panel panel : this.panels) {
            if (!panel.isDragging()) {
               panel.mouseScrolled(mouseX, mouseY, verticalAmount);
            }
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public void renderContents(GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
   }

   public void onMouseRelease(double mouseX, double mouseY, int button) {
   }

   public ArrayList<Panel> getPanels() {
      return this.panels;
   }

   public void setParentScreen(Screen screen) {
      this.parentScreen = screen;
   }

   @Override
   protected void init() {
      int screenWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
      int screenHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
      if (this.getParentScreen() != null) {
         this.getParentScreen().init(screenWidth, screenHeight);
      }

      if (!this.isWorldUnavailable()) {
         this.hoverMouseX.setValue(screenWidth / 2.0F);
         this.hoverMouseY.setValue(screenHeight / 2.0F);
      }

      this.panelDragging = false;
      this.getPanels().forEach(panelx -> panelx.setDragging(false));
      this.guiScale = UIModule.INSTANCE.scaleSetting.getValue();
      this.closing = false;

      for (Panel panel : this.panels) {
         panel.init();
      }

      if (this.skipOpeningTransition) {
         this.transitionStartedAt = -150L;
         this.skipOpeningTransition = false;
      } else {
         this.transitionStartedAt = System.currentTimeMillis();
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      this.escapeCloseAllowed = true;

      for (int panelIndex = this.panels.size() - 1; panelIndex >= 0; panelIndex--) {
         this.panels.get(panelIndex).keyPressed(event.key());
      }

      if (event.key() == 256 && this.escapeCloseAllowed) {
         this.closeWithTransition();
      }

      return false;
   }

   @Override
   public void onClose() {
      super.onClose();
      CursorType.STANDARD.apply();
   }

   @Override
   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      PoseStack poseStack = GuiProjectionUtils.toPoseStack(graphics);
      this.hoverMouseX.setTarget(mouseX);
      this.hoverMouseY.setTarget(mouseY);
      float hoverX = this.hoverMouseX.updateAndGet();
      float hoverY = this.hoverMouseY.updateAndGet();
      poseStack.translate(0.0F, 0.0F, 1000.0F);
      cursor.apply();
      cursor = CursorType.STANDARD;
      this.seasonalParticles.removeIf(SnowflakeParticle::isExpired);
      if (this.seasonalParticles.size() < 25) {
         this.seasonalParticles.add(new SnowflakeParticle());
      }

      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, this.getTransitionOpacity());
      float scale = (float)GuiScaleUtils.calculateScaleMultiplier(this.guiScale);
      GuiScaleUtils.applyGuiScale(this.guiScale);
      poseStack.scale(scale, scale, 1.0F);
      mouseX = (int)(mouseX / scale);
      mouseY = (int)(mouseY / scale);
      hoverX /= scale;
      hoverY /= scale;
      this.tooltipText = "";
      this.renderBackgroundOverlay(poseStack);
      if (UI.aeroSetting.getValue()) {
         this.renderAeroPanels(poseStack);
      }

      float blurStrength = BLUR.getRadius() * this.getTransitionOpacity();
      if (!this.isClosing() && BLUR.isToggled()) {
         GuiDrawUtils.flush();
         MaskShaderRenderer.renderBlur(
            () -> {
               for (Panel blurPanel : this.getPanels()) {
                  float expansion = blurPanel.getExpansionValue();
                  float expandedContentHeight = (blurPanel.getHeight() - blurPanel.getHeaderHeight()) * expansion;
                  GuiDrawUtils.fillRect(
                     poseStack,
                     blurPanel.getX(),
                     blurPanel.getY(),
                     blurPanel.getX() + blurPanel.getWidth(),
                     blurPanel.getY() + blurPanel.getHeaderHeight() + expandedContentHeight,
                     -1
                  );
               }

               GuiDrawUtils.flush();
            },
            blurStrength
         );
      }

      if (Season.WINTER.containsMonth(LocalDate.now().getMonthValue())) {
         for (SnowflakeParticle particle : this.seasonalParticles) {
            particle.render(poseStack);
         }
      }

      for (Panel panel : this.panels) {
         panel.updateDragPosition(mouseX, mouseY);
         panel.render(poseStack, mouseX, mouseY);
      }

      if (!this.tooltipText.isBlank()) {
         GuiDrawUtils.flush();
         String[] tooltipLines = this.tooltipText.split("\n");
         float tooltipWidth = 0.0F;

         for (String tooltipLine : tooltipLines) {
            float lineWidth = FontRenderer.INSTANCE.getWidth(tooltipLine);
            if (lineWidth > tooltipWidth) {
               tooltipWidth = lineWidth;
            }
         }

         float tooltipLeft = hoverX + 9.0F;
         float tooltipRight = hoverX + tooltipWidth + 10.0F;
         if (tooltipRight > GuiScaleUtils.getScaledWidth()) {
            tooltipLeft = hoverX - tooltipWidth - 2.0F;
            tooltipRight = hoverX - 1.0F;
         }

         float blurLeft = tooltipLeft;
         float blurRight = tooltipRight;
         int blurY = (int)hoverY;
         float tooltipBottom = hoverY - 1.0F + (FontRenderer.INSTANCE.getHeight() + 1.0F) * tooltipLines.length;
         MaskShaderRenderer.renderBlur(
            () -> RenderUtils.INSTANCE.fillRect(poseStack, blurLeft, blurY - 1.0F, blurRight, tooltipBottom, ColorUtils.packArgb(10, 10, 10, 140)), 6.0F
         );
         poseStack.pushPose();
         poseStack.translate(0.0F, 0.0F, 500.0F);
         RenderUtils.INSTANCE.fillRect(poseStack, tooltipLeft, hoverY - 1.0F, tooltipRight, tooltipBottom, new Color(10, 10, 10, 80));
         int lineIndex = 0;

         for (String tooltipLine : tooltipLines) {
            FontRenderer.INSTANCE
               .drawWithShadow(poseStack, tooltipLine, tooltipLeft + 1.0F, hoverY + (FontRenderer.INSTANCE.getHeight() + 1.0F) * lineIndex, Color.white);
            lineIndex++;
         }

         this.lastHoverTime = System.currentTimeMillis();
         poseStack.popPose();
      } else if (this.lastHoverTime != 0L && System.currentTimeMillis() > this.lastHoverTime + 750L) {
         this.lastHoverTime = 0L;
      }

      this.renderContents(graphics, poseStack, mouseX, mouseY, partialTick);
      GuiDrawUtils.flush();
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      GuiScaleUtils.restoreGuiScale();
      poseStack.translate(0.0F, 0.0F, -1000.0F);
   }

   public void open() {
      if (ClientAccess.argusclientMinecraft.screen instanceof ClientScreen previousScreen) {
         this.setParentScreen(previousScreen.parentScreen);
         previousScreen.setParentScreen(null);
         this.skipOpeningTransition = true;
      } else {
         this.setParentScreen(ClientAccess.argusclientMinecraft.screen);
      }

      ClientAccess.argusclientMinecraft.setScreen(this);
   }

   public void closeWithTransition() {
      if (!this.closing) {
         this.panelDragging = false;
         this.transitionStartedAt = System.currentTimeMillis();
         if (UIModule.INSTANCE.isToggled()) {
            UIModule.INSTANCE.toggle();
         }
      } else {
         this.onClose();
      }

      this.closing = true;
   }

   public void resetPanelPositions() {
      this.resetSeasonalParticles();
      this.panels.sort(Comparator.comparing(panelEntry -> panelEntry instanceof ModuleCategoryPanel categoryPanel ? categoryPanel.category.ordinal() : 100));
      int panelX = 10;

      for (Panel panel : this.panels) {
         panel.setX(panelX);
         panel.setY(5);
         panelX += panel.getWidth() + 3;
      }
   }

   public void renderAeroPanels(PoseStack poseStack) {
      int textureAlpha = RenderUtils.getRenderColor()[3] / 2;
      int textureColor = ARGB.color(textureAlpha, -1);

      for (Panel panel : this.getPanels()) {
         int outlinePadding = UI.lineSetting.getValue() ? 2 : 0;
         int panelX = panel.getX();
         int panelY = panel.getY();
         float expansion = panel.getExpansionValue();
         float contentHeight = panel.getHeight() - panel.getHeaderHeight();
         float panelWidth = panel.getWidth();
         if (UI.lineSetting.getValue()) {
            panelX--;
            panelY--;
            panelWidth += 2.0F;
         } else {
            contentHeight -= outlinePadding;
         }

         contentHeight *= expansion;
         poseStack.pushPose();
         poseStack.translate(panelX, panelY, 0.0F);
         ScissorStack.push(panelX, panelY, (int)panelWidth, (int)Math.floor(panel.getHeaderHeight() + contentHeight + outlinePadding));
         GuiDrawUtils.drawTexture(poseStack, AERO_TEXTURE, (int)(-300.0F + panelX / 3.0F), 0, 0.0F, 0.0F, 600, 450, 600, 450, textureColor);
         RenderUtils.getEntityBuffers().endBatch();
         ScissorStack.pop();
         poseStack.popPose();
      }
   }

   public void renderBackgroundOverlay(PoseStack poseStack) {
      GuiDrawUtils.fillRect(poseStack, 0.0F, 0.0F, GuiScaleUtils.getScaledWidth(), GuiScaleUtils.getScaledHeight(), UIModule.INSTANCE.tintSetting.getValue());
      GuiDrawUtils.fillVerticalGradient(
         poseStack,
         0.0F,
         0.0F,
         GuiScaleUtils.getScaledWidth(),
         GuiScaleUtils.getScaledHeight(),
         ColorUtils.toArgb(UIModule.INSTANCE.gradientColorSetting.getValue(), 0),
         UIModule.INSTANCE.gradientColorSetting.getValue().hashCode()
      );
      GuiDrawUtils.flush(false);
   }
}

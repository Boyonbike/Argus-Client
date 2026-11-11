package dev.argus.client.gui.panels;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.GuiScaleUtils;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.FloatAnimation;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.inventory.RegistryContainer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class Panel implements RegistryContainer, GuiElement, NamedEntry {
   public final ArrayList<GuiElement> children = new ArrayList<>();
   protected final ClampedInterpolatedFloat expansionAnimation = new ClampedInterpolatedFloat(() -> 2.0F, true);
   private final InterpolatedFloat scrollAnimation = new InterpolatedFloat(() -> this.getUiModule().animSpeedSetting.getValue() * 2.0F, true);
   private final FloatAnimation topScrollFadeAnimation = new FloatAnimation();
   private final FloatAnimation bottomScrollFadeAnimation = new FloatAnimation();
   private final FloatAnimation headerAlphaAnimation = new FloatAnimation();
   private final String name;
   protected final int baseWidth;
   public int dragOffsetX;
   public int dragOffsetY;
   protected boolean expanded;
   protected int y;
   protected int x;
   protected boolean dragging;
   private int height;
   private int contentHeight;
   private boolean heightLimitDisabled;
   private int scrollOffset;
   private int scrollRecoveryCounter;
   private static final float SCROLL_RECOVERY_FPS_BASE = 200.0F;
   private static final float SCROLL_RECOVERY_SPEED = 20.0F;
   private static final int SLOW_FRAME_RATE = 40;
   private static final int MAX_SCROLL_OVERSHOOT = 40;

   public void updateLayout() {
      int var1 = (int)this.scrollAnimation.getValue();
      int var2 = this.getHeaderHeight() + 1 + var1;

      for (GuiElement var4 : this.children) {
         if (!this.isChildVisible(var4)) {
            var4.setYOffset(0);
         } else {
            var4.setYOffset(var2);
            var2 += var4.getHeight();
         }
      }

      int var5 = this.getHeightLimit();
      this.contentHeight = this.expansionAnimation.getValue() == 0.0F ? this.getHeaderHeight() + 1 : var2 - var1;
      if (this.heightLimitDisabled) {
         this.height = this.contentHeight;
      } else {
         this.height = Math.min(this.contentHeight, var5);
      }
   }

   private void recoverScrollOffset() {
      if (!this.heightLimitDisabled) {
         int var1 = Math.min(-this.contentHeight + this.height, 0);
         byte var2 = 0;
         int var3 = ClientContext.frameTiming.getFramesPerSecond();
         float var4 = var3 / 200.0F * 20.0F;
         if (var3 <= 40) {
            var4 /= 2.0F;
         }

         if (this.scrollOffset < var1) {
            this.scrollOffset = this.scrollOffset + (int)Math.min(this.scrollRecoveryCounter++ / var4, var1 - this.scrollOffset);
         } else if (this.scrollOffset > var2) {
            this.scrollOffset = this.scrollOffset - (int)Math.min(this.scrollRecoveryCounter++ / var4, this.scrollOffset - var2);
         } else {
            this.scrollRecoveryCounter = 0;
         }

         this.scrollOffset = Mth.clamp(this.scrollOffset, var1 - 40, var2 + 40);
      }
   }

   public void disableHeightLimit() {
      this.heightLimitDisabled = true;
   }

   protected int getHeightLimit() {
      float var1 = 1.0F;
      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ClientScreen var2) {
         var1 = var2.getGuiScale();
      }

      return (int)((GuiScaleUtils.getBaseScaledHeight() - 50) / var1);
   }

   @Override
   public int getHeight() {
      return this.height;
   }

   public ArrayList getChildren() {
      return this.children;
   }

   public void setExpanded(boolean var1) {
      this.expanded = var1;
   }

   public void setX(int var1) {
      this.x = var1;
   }

   public int getWidth() {
      return this.baseWidth + UIModule.INSTANCE.styleSetting.getValue().getWidthPadding();
   }

   public void setDragging(boolean var1) {
      if (var1 && !ClientContext.getClickGui().isPanelDragging()) {
         ClientContext.getClickGui().setPanelDragging(true);
         this.dragging = true;
      } else {
         if (ClientContext.getClickGui().isPanelDragging()) {
            ClientContext.getClickGui().setPanelDragging(false);
         }

         this.dragging = false;
      }
   }

   public boolean isDragging() {
      return this.dragging;
   }

   private static double getDragScale() {
      return GuiScaleUtils.getScaleMultiplier();
   }

   public void updateDragPosition(int var1, int var2) {
      if (this.dragging) {
         double var3 = getDragScale();
         this.setX((int)Math.clamp(var1 - this.dragOffsetX, 0.0, ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() / var3 - this.getWidth()));
         this.setY((int)Math.clamp(var2 - this.dragOffsetY, 0.0, ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight() / var3 - this.getHeight()));
      }
   }

   public float getExpansionValue() {
      return this.expansionAnimation.getValue();
   }

   @Override
   public void keyPressed(int var1) {
      if (this.expanded) {
         for (GuiElement var3 : this.children) {
            var3.keyPressed(var1);
         }
      }
   }

   public void setY(int var1) {
      this.y = var1;
   }

   public int getHeaderHeight() {
      UIModule.StyleChoice var1 = this.getUiModule().styleSetting.getValue();
      return var1 == UIModule.StyleChoice.ARGUS ? 17 : (var1 == UIModule.StyleChoice.SLIM ? 12 : 14);
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Panel(String var1) {
      this.name = var1;
      this.baseWidth = 92;
      this.dragOffsetX = 0;
      this.expanded = true;
      this.y = 5;
      this.x = 5;
      this.dragging = false;
      this.expansionAnimation.setValue(1.0F);
      this.headerAlphaAnimation.setValue(1.0F);
   }

   @Override
   public boolean register(Object var1) {
      return this.registerChild((GuiElement)var1);
   }

   @Override
   public void init() {
      if (this.expanded) {
         for (GuiElement var2 : this.children) {
            var2.init();
         }
      }
   }

   @Override
   public boolean unregister(Object var1) {
      GuiElement var2 = (GuiElement)var1;
      return this.getRegistry().add(var2);
   }

   public int getY() {
      return this.y;
   }

   public List<GuiElement> getRegistry() {
      return this.children;
   }

   public int getX() {
      return this.x;
   }

   private void renderScrollFade(PoseStack var1, float var2, boolean var3) {
      float var4 = this.getExpansionValue();
      if (var4 < 1.0F) {
         var2 *= var4;
      }

      Color var5 = UIModule.INSTANCE.colorSetting.getValue();
      int var6 = ColorUtils.toArgb(var5, 0);
      int var7 = ColorUtils.toArgb(var5, 0.3F * var2);
      if (var3) {
         var6 = var7;
         var7 = ColorUtils.toArgb(var5, 0);
      }

      if (var2 != 0.0F) {
         GuiDrawUtils.fillVerticalGradient(
            var1, this.x, var3 ? this.y + this.getHeaderHeight() : this.y + this.height * var4 - 10.0F, this.getWidth(), 10.5F, var6, var7
         );
      }
   }

   public void addChildren(GuiElement... var1) {
      for (GuiElement var5 : var1) {
         this.addChild(var5);
      }
   }

   @Override
   public void render(PoseStack var1, double var2, double var4) {
      Color var6 = this.getUiModule().colorSetting.getValue();
      this.expansionAnimation.setTarget(this.expanded ? 1.0F : 0.0F);
      float var7 = this.expansionAnimation.updateAndGet();
      this.scrollAnimation.setTarget(this.scrollOffset);
      this.scrollAnimation.updateAndGet();
      this.headerAlphaAnimation.setTarget(var7 > 0.25F || this.isMouseOverHeader(var2, var4), 200L);
      float var8 = this.getUiModule().lineSetting.getValue() ? 0.0F : 0.5F;
      GuiDrawUtils.fillRect(var1, this.x, this.y, this.x + this.getWidth(), this.y + this.getHeaderHeight() + var8, this.applyHeaderAlpha(var6));
      boolean var9 = this.getUiModule().styleSetting.getValue() == UIModule.StyleChoice.ARGUS;
      FontRenderer.INSTANCE
         .drawWithShadow(
            var1,
            this.name,
            var9 ? this.x + (this.getWidth() - FontRenderer.INSTANCE.getWidth(this.name)) / 2.0F : this.x + 2,
            this.y + this.getHeaderHeight() / 2.0F - FontRenderer.INSTANCE.getHeight() / 2.0F + var8,
            this.applyHeaderAlpha(Color.white)
         );
      if (var7 != 0.0F) {
         GuiDrawUtils.flush();
         ScissorStack.push(this.x - 1, this.y + this.getHeaderHeight(), this.getWidth() + 1, (int)(this.height * var7) + 1 - this.getHeaderHeight());
      }

      Color var10 = UIModule.INSTANCE.backgroundSetting.getValue();
      if (var7 > 0.0F && !this.children.isEmpty()) {
         GuiDrawUtils.fillRect(var1, this.x, this.y + this.getHeaderHeight(), this.x + this.getWidth(), this.y + this.height + 0.5F, var10);
         this.children.forEach(var6x -> {
            if (this.isChildVisible(var6x)) {
               var6x.update(var2, var4);
               int var7x = var6x.getYOffset();
               if (var7x <= this.height && var7x + var6x.getHeight() >= 0) {
                  var6x.render(var1, var2, var4);
               }
            }
         });
      }

      if (var7 != 0.0F) {
         GuiDrawUtils.flush();
         ScissorStack.pop();
      }

      var1.pushPose();
      var1.translate(0.0F, 0.0F, 500.0F);
      this.bottomScrollFadeAnimation.setTarget(this.scrollOffset > Math.min(-this.contentHeight + this.height, 0), 200L);
      this.topScrollFadeAnimation.setTarget(this.scrollOffset < 0, 200L);
      this.renderScrollFade(var1, this.bottomScrollFadeAnimation.getValue(), false);
      this.renderScrollFade(var1, this.topScrollFadeAnimation.getValue(), true);
      if (UIModule.INSTANCE.lineSetting.getValue() && !var9) {
         float var11 = (int)(this.height * var7) + 0.5F;
         if (var11 <= this.getHeaderHeight() + 2) {
            var11 = this.getHeaderHeight();
         }

         GuiDrawUtils.drawRectOutline(var1, this.x - 1, this.y - 1, this.x + this.getWidth(), this.y + var11, this.applyHeaderAlpha(var6));
      }

      var1.popPose();
      this.recoverScrollOffset();
      this.updateLayout();
      GuiDrawUtils.flush();
   }

   protected boolean isChildVisible(GuiElement var1) {
      return true;
   }

   public void addChild(GuiElement var1) {
      this.children.add(var1);
   }

   public boolean isFullyExpanded() {
      return this.expanded && this.expansionAnimation.currentValue >= 1.0F;
   }

   @Override
   public boolean isMouseOver(double var1, double var3) {
      return var1 >= this.x && var1 <= this.x + this.getWidth() && var3 >= this.y && var3 <= this.y + this.height;
   }

   @Override
   public void mouseClicked(double var1, double var3, int var5) {
      if (this.isMouseOverHeader(var1, var3) && var5 == 1) {
         this.setExpanded(!this.expanded);
      }

      if (this.expanded) {
         for (GuiElement var7 : this.children) {
            var7.mouseClicked(var1, var3, var5);
         }
      }
   }

   public boolean registerChild(GuiElement var1) {
      return this.getRegistry().add(var1);
   }

   @Override
   public void mouseReleased(double var1, double var3, int var5) {
      if (this.expanded) {
         for (GuiElement var7 : this.children) {
            var7.mouseReleased(var1, var3, var5);
         }
      }
   }

   @Override
   public void mouseScrolled(double var1, double var3, double var5) {
      if (this.expanded) {
         if (this.isMouseOver(var1, var3) && !this.heightLimitDisabled) {
            this.scrollOffset += (int)(var5 * 20.0);
         }

         for (GuiElement var8 : this.children) {
            var8.mouseScrolled(var1, var3, var5);
         }
      }
   }

   @Override
   public void charTyped(char var1) {
      if (this.expanded) {
         for (GuiElement var3 : this.children) {
            var3.charTyped(var1);
         }
      }
   }

   protected Color applyHeaderAlpha(Color var1) {
      float var2 = this.headerAlphaAnimation.getValue() * 0.5F + 0.5F;
      return ColorUtils.withAlpha(var1, var1.getAlpha() / 255.0F * var2);
   }

   public boolean isMouseOverHeader(double var1, double var3) {
      return var1 >= this.x && var1 <= this.x + this.getWidth() && var3 >= this.y && var3 <= this.y + this.getHeaderHeight();
   }
}

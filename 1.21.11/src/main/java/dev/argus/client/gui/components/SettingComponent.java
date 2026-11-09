package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.text.LanguageTable;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FloatAnimation;

public abstract class SettingComponent<T> extends AbstractComponent {
   protected final FloatAnimation textScrollAnimation = new FloatAnimation();
   protected final CloseState parentCloseState;
   protected final Setting<T> setting;
   private boolean wasHoveredForTextScroll;
   private long textScrollHoverStartedAtMillis;
   public boolean editing;

   @Override
   public int getHeight() {
      return !this.setting.isVisible() ? 0 : this.getBaseHeight();
   }

   public boolean isUnavailable() {
      boolean unavailable = !this.setting.isVisible() || this.parentCloseState.isClosed();
      if (unavailable) {
         this.setting.expanded = false;
      }

      return unavailable;
   }

   public Setting<T> getSetting() {
      return this.setting;
   }

   protected ClickGuiScreen getClickGui() {
      return ClientContext.getClickGui();
   }

   public CloseState getParentCloseState() {
      return this.parentCloseState;
   }

   public SettingComponent(Panel panel, Setting<T> setting) {
      this(panel, () -> false, setting);
   }

   public SettingComponent(Panel panel, CloseState parentCloseState, Setting<T> setting) {
      super(panel, 0);
      this.parentCloseState = parentCloseState;
      this.setting = setting;
   }

   private void updateTextScroll(float textWidth) {
      if (textWidth <= this.panel.getWidth() - 4) {
         this.textScrollAnimation.setValue(0.0F);
      } else {
         if (this.hovered) {
            if (!this.wasHoveredForTextScroll) {
               this.textScrollHoverStartedAtMillis = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() > this.textScrollHoverStartedAtMillis + 750L) {
               this.textScrollAnimation.setTarget(textWidth - (this.panel.getWidth() - 4), 500L);
            }
         } else {
            this.textScrollAnimation.setTarget(0.0F, 500L);
         }

         this.wasHoveredForTextScroll = this.hovered;
      }
   }

   protected void renderWithTextScroll(PoseStack poseStack, String text, Runnable renderer) {
      poseStack.pushPose();
      this.updateTextScroll(FontRenderer.INSTANCE.getWidth(text));
      float scrollOffset = -this.textScrollAnimation.getValue();
      boolean scissorRequired = scrollOffset != 0.0F && !(this instanceof EnumSettingComponent);
      if (scissorRequired) {
         ModuleComponent parentModule = (ModuleComponent)this.parentCloseState;
         GuiDrawUtils.flush();
         ScissorStack.pushIntersection(
            this.panel.getX() + this.getHorizontalPadding(),
            this.panel.getY() + parentModule.getYOffset(),
            this.panel.getWidth() - this.getHorizontalPadding(),
            parentModule.getHeight()
         );
      }

      poseStack.translate(scrollOffset, 0.0F, 0.0F);
      renderer.run();
      if (scissorRequired) {
         GuiDrawUtils.flush();
         ScissorStack.pop();
      }

      poseStack.popPose();
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.getUiModule().descriptionsSetting.getValue()
         && this.getUiModule().settingsSetting.getValue()
         && this.isMouseOver(mouseX, mouseY)
         && this.parentCloseState instanceof ModuleComponent parentModule) {
         String moduleConfigName = parentModule.getModule().getConfigName();
         String settingConfigName = this.setting.getConfigName();
         LanguageTable translations = ClientContext.languageTable;
         String description = translations.getTranslation("descriptions.settings.%s.%s".formatted(moduleConfigName, settingConfigName));
         if (description != null) {
            ClientContext.getClickGui().setTooltipText(description);
         }
      }

      super.render(poseStack, mouseX, mouseY);
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()) {
         super.mouseClicked(mouseX, mouseY, button);
      }
   }
}

package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ClampedInterpolatedFloat;

public class BooleanSettingComponent extends SettingComponent<Boolean> {
   private final ClampedInterpolatedFloat enabledAnimation = new ClampedInterpolatedFloat(() -> 2.0F * UIModule.INSTANCE.animSpeedSetting.getValue(), true);

   public BooleanSettingComponent(Panel panel, CloseState parentCloseState, Setting<Boolean> setting) {
      super(panel, parentCloseState, setting);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.isUnavailable()) {
         this.enabledAnimation.reset();
      } else {
         this.enabledAnimation.setTarget(this.setting.getValue());
         float enabledProgress = this.enabledAnimation.updateAndGet();
         super.render(poseStack, mouseX, mouseY);
         if ((this.setting.getValue() || enabledProgress > 0.01) && !this.setting.groupOnly) {
            GuiDrawUtils.fillRect(
               poseStack,
               this.panel.getX() + this.getHorizontalPadding(),
               this.panel.getY() + this.yOffset + 0.5F,
               this.panel.getX() + Math.max(this.panel.getWidth() * enabledProgress - 1.0F, 2.0F),
               this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
               this.getUiModule().colorSetting.getValue()
            );
         }

         String settingName = this.setting.getName();
         if (!settingName.isEmpty()) {
            this.renderWithTextScroll(
               poseStack,
               settingName,
               () -> FontRenderer.INSTANCE
                  .drawWithShadow(
                     poseStack,
                     settingName,
                     this.panel.getX() + 4,
                     this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                     this.getUiModule().textColorSetting.getValue()
                  )
            );
         }

         if (this.setting.visibilityDependency && !this.setting.groupOnly) {
            String expansionIndicator = this.setting.expanded ? "-" : "+";
            FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  expansionIndicator,
                  this.panel.getX() + this.panel.getWidth() - 4 - FontRenderer.INSTANCE.getWidth(expansionIndicator),
                  this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                  this.getUiModule().textColorSetting.getValue()
               );
         }

         if (this.setting.groupOnly) {
            String groupIndicator = "...";
            FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  groupIndicator,
                  this.panel.getX() + this.panel.getWidth() - 4 - FontRenderer.INSTANCE.getWidth(groupIndicator),
                  this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                  this.getUiModule().textColorSetting.getValue()
               );
         }
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()) {
         if (!this.setting.groupOnly || button != 0) {
            super.mouseClicked(mouseX, mouseY, button);
            if (this.isMouseOver(mouseX, mouseY)) {
               if (button == 0) {
                  this.setting.setValue(!this.setting.getValue());
               }

               if (button == 1 && this.setting.visibilityDependency) {
                  this.setting.expanded = !this.setting.expanded;
               }
            }
         }
      }
   }
}

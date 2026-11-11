package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.gui.screens.SoundSelectionScreen;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.collections.NamedIdentifier;
import java.util.regex.Matcher;

public class SoundSettingComponent extends SettingComponent<NamedIdentifier> {
   public SoundSettingComponent(Panel panel, CloseState parentCloseState, Setting<NamedIdentifier> setting) {
      super(panel, parentCloseState, setting);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (!this.isUnavailable()) {
         super.render(poseStack, mouseX, mouseY);
         if (this.isMouseOver(mouseX, mouseY)) {
            ClientScreen.cursor = CursorType.POINTER;
         }

         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.getX() + this.getHorizontalPadding(),
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + this.panel.getWidth() - 1,
            this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
         String settingName = this.setting.getName();
         String soundName = this.setting.getValue().getName();
         String label = (settingName + "").replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(soundName)));
         this.renderWithTextScroll(
            poseStack,
            label,
            () -> FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  label,
                  this.panel.getX() + 4,
                  this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                  this.getUiModule().textColorSetting.getValue()
               )
         );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable() && this.isMouseOver(mouseX, mouseY) && button == 0) {
         new SoundSelectionScreen(this.setting, this.getClickGui()).open();
      }
   }
}

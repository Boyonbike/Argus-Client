package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.components.ButtonComponent;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.collections.NamedIdentifier;

class SoundOptionButton extends ButtonComponent {
   private final SoundSelectionScreen screen;
   private final NamedIdentifier soundId;

   public SoundOptionButton(SoundSelectionScreen screen, Panel panel, NamedIdentifier soundId) {
      super(panel, soundId.getName());
      this.screen = screen;
      this.soundId = soundId;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.screen.getSelectedSetting().getValue().equals(this.soundId)) {
         RenderUtils.INSTANCE
            .fillRect(
               poseStack,
               this.panel.getX() + 1,
               this.panel.getY() + this.yOffset + 0.5F,
               this.panel.getX() + this.panel.getWidth() - 1,
               this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
               this.getUiModule().colorSetting.getValue()
            );
      } else {
         this.renderButtonBackground(poseStack);
      }

      super.render(poseStack, mouseX, mouseY);
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.hovered) {
         if (button == 0) {
            this.screen.getSelectedSetting().setValue(this.soundId);
            this.screen.closeWithTransition();
         } else if (button == 1) {
            ClientContext.soundManager.playSound(this.soundId, 1.0F);
         }
      }
   }
}

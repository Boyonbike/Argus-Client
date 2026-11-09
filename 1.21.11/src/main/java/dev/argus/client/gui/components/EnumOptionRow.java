package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.NamedEntry;

public class EnumOptionRow<E extends Enum<E> & NamedEntry> extends AbstractComponent {
   final EnumSettingComponent<E> enumComponent;
   final String label;
   final int selectableIndex;

   @Override
   public int getHeight() {
      return this.enumComponent.isUnavailable() ? 0 : super.getHeight();
   }

   public EnumOptionRow(EnumSettingComponent<E> enumComponent, String label, int yOffset, int selectableIndex) {
      super(enumComponent.getPanel(), yOffset);
      this.label = label;
      this.selectableIndex = selectableIndex;
      this.enumComponent = enumComponent;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            this.label,
            this.getPanel().getX() + this.getPanel().getWidth() / 2.0F - FontRenderer.INSTANCE.getWidth(this.label) / 2.0F,
            this.enumComponent.getY() + this.yOffset + this.getTextYOffset() - 1.0F - this.getHoverAnimationValue(),
            this.getUiModule().textColorSetting.getValue()
         );
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.enumComponent.isUnavailable() && this.isMouseOver(mouseX, mouseY) && this.enumComponent.isExpanded()) {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.enumComponent.getParentCloseState().isOpen() && button == 0) {
            for (E candidate : this.enumComponent.getSetting().getValue().getDeclaringClass().getEnumConstants()) {
               if (candidate.getName().equalsIgnoreCase(this.label)) {
                  this.enumComponent.getSetting().setValue(candidate);
                  this.enumComponent.setSelectedIndex(this.selectableIndex);
               }
            }
         }
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX > this.enumComponent.getX()
         && mouseX < this.enumComponent.getX() + this.getPanel().getWidth()
         && mouseY > this.enumComponent.getY() + this.yOffset
         && mouseY < this.enumComponent.getY() + this.getBaseHeight() + this.yOffset;
   }
}

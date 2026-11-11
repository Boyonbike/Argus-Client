package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.Keybind;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.Setting;
import java.util.function.Function;

public class KeybindComponent extends AbstractComponent {
   private final ModuleComponent moduleRow;
   private final Setting<Keybind> bindingSetting;
   private boolean listening;

   @Override
   public void keyPressed(int keyCode) {
      if (this.listening) {
         boolean clearBinding = keyCode == 261 || keyCode == 256;
         this.modifyKeybind(binding -> binding.withCode(clearBinding ? -1 : keyCode).withMouse(false));
         this.listening = false;
         ClientContext.getClickGui().suppressEscapeClose();
      }
   }

   public KeybindComponent(int index, ModuleComponent moduleRow) {
      super(moduleRow.getPanel(), index);
      this.moduleRow = moduleRow;
      this.bindingSetting = null;
   }

   public KeybindComponent(int index, ModuleComponent moduleRow, Setting<Keybind> bindingSetting) {
      super(moduleRow.getPanel(), index);
      this.moduleRow = moduleRow;
      this.bindingSetting = bindingSetting;
   }

   private Keybind getKeybind() {
      return this.bindingSetting == null ? this.moduleRow.module.getKeybind() : this.bindingSetting.getValue();
   }

   private void modifyKeybind(Function<Keybind, Keybind> modifier) {
      if (this.bindingSetting == null) {
         this.moduleRow.module.modifyKeybind(modifier);
      } else {
         this.bindingSetting.setValue(modifier.apply(this.bindingSetting.getValue()));
      }
   }

   @Override
   public void render(PoseStack stack, double mouseX, double mouseY) {
      super.render(stack, mouseX, mouseY);
      if (this.hovered) {
         ClientScreen.cursor = CursorType.INPUT;
      }

      String modeLabel = "Key";
      if (this.hovered || this.getKeybind().mode() != Keybind.Mode.TOGGLE) {
         modeLabel = this.getKeybind().mode().getName();
      }

      String keyLabel = this.getKeybind().getDisplayName();
      if (this.listening) {
         keyLabel = "...";
      }

      FontRenderer.INSTANCE
         .drawWithShadow(
            stack,
            modeLabel + " " + keyLabel,
            this.panel.getX() + 4,
            this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
            this.getUiModule().textColorSetting.getValue()
         );
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.moduleRow.isOpen()) {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.isMouseOver(mouseX, mouseY) && this.moduleRow.isOpen()) {
            if (button == 0) {
               this.listening = !this.listening;
               return;
            }

            if (this.listening) {
               this.modifyKeybind(binding -> binding.withCode(button).withMouse(true));
               this.listening = !this.listening;
               return;
            }

            if (button == 1) {
               this.modifyKeybind(binding -> binding.withMode(Keybind.Mode.values()[(binding.mode().ordinal() + 1) % Keybind.Mode.values().length]));
            }
         }
      }
   }
}

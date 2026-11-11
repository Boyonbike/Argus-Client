package dev.argus.client.gui.components;

import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.panels.ModuleCategoryPanel;
import dev.argus.client.modules.ModuleCategory;

public class HudModulePanel extends ModuleCategoryPanel {
   @Override
   protected int getHeightLimit() {
      int height = 0;

      for (Object childElement : this.children) {
         GuiElement child = (GuiElement)childElement;
         height += child.getBaseHeight() + 1;
      }

      return height;
   }

   public HudModulePanel() {
      super(ModuleCategory.HUD);
   }
}

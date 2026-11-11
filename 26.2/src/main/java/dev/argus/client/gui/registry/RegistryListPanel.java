package dev.argus.client.gui.registry;

import dev.argus.client.ClientAccess;
import dev.argus.client.gui.panels.Panel;

class RegistryListPanel extends Panel {
   public RegistryListPanel(String title) {
      super(title);
   }

   @Override
   protected int getHeightLimit() {
      return ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight() / 2 + 10;
   }
}

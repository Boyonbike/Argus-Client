package dev.argus.client.gui.components;

import dev.argus.client.ClientAccess;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.TradeEditorScreen;
import dev.argus.client.gui.screens.TradeManagerScreen;
import dev.argus.client.settings.StringSetting;

public class CreateTradePanel extends Panel implements ClientAccess {
   private final StringSetting nameSetting = new StringSetting("Name", "");
   private final TradeManagerScreen managerScreen;

   public CreateTradePanel(TradeManagerScreen screen) {
      super("Trade");
      this.setX(this.getX() + this.getWidth() + 4);
      this.managerScreen = screen;
      this.registerChild(new StringSettingComponent(this, CloseState.ALWAYS_OPEN, this.nameSetting));
      this.registerChild(new ActionButtonComponent(this, "Create", this::createTrade));
   }

   private void createTrade() {
      if (!this.nameSetting.getValue().isBlank()) {
         TradeEditorScreen editorScreen = new TradeEditorScreen(this.nameSetting.getValue());
         editorScreen.setParentScreen(this.managerScreen);
         ClientAccess.argusclientMinecraft.setScreen(editorScreen);
      }
   }
}

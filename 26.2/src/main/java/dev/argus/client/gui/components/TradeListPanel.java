package dev.argus.client.gui.components;

import dev.argus.client.ClientContext;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.TradeManagerScreen;
import dev.argus.client.render.TradePanelRequest;
import dev.argus.client.utils.inventory.TradeDefinition;
import java.util.List;

public class TradeListPanel extends Panel {
   private final TradeManagerScreen managerScreen;

   public TradeListPanel(TradeManagerScreen screen) {
      super("List");
      this.managerScreen = screen;
      this.refreshTrades();
   }

   @Override
   public void init() {
      super.init();
      this.refreshTrades();
   }

   public void refreshTrades() {
      this.getChildren().clear();

      for (Object registeredEntry : (List)ClientContext.tradeRegistry.getRegistry()) {
         TradeDefinition registeredTrade = (TradeDefinition)registeredEntry;
         this.registerChild(new ActionButtonComponent(this, registeredTrade.name(), () -> this.selectTrade(registeredTrade)));
      }

      this.updateLayout();
   }

   private void selectTrade(TradeDefinition selectedTrade) {
      this.managerScreen.requestPanelChange(TradePanelRequest.forEdit(selectedTrade));
   }
}

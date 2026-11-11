package dev.argus.client.gui.components;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.TradeEditorScreen;
import dev.argus.client.gui.screens.TradeManagerScreen;
import dev.argus.client.render.TradePanelRequest;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.TradeDefinition;

public class TradeActionsPanel extends Panel implements ClientAccess {
   private final ElapsedTimer deleteConfirmationTimer = new ElapsedTimer();
   private final TradeManagerScreen managerScreen;
   private final TradeDefinition trade;

   public TradeActionsPanel(TradeManagerScreen screen, TradeDefinition selectedTrade) {
      super("Trade");
      this.setX(this.getX() + this.getWidth() + 4);
      this.managerScreen = screen;
      this.trade = selectedTrade;
      this.registerChild(new ButtonComponent(this, "Trade: " + selectedTrade.getName()));
      this.registerChild(new ActionButtonComponent(this, "Edit", this::openEditor));
      this.registerChild(new ActionButtonComponent(this, this::getDeleteButtonLabel, this::deleteIfConfirmed));
      this.registerChild(new ActionButtonComponent(this, "Cancel", this::showCreatePanel));
   }

   private void openEditor() {
      TradeDefinition registeredTrade = (TradeDefinition)ClientContext.tradeRegistry
         .findFirst(candidate -> candidate.getName().equals(this.trade.getName()))
         .orElse(null);
      TradeEditorScreen editorScreen = new TradeEditorScreen(registeredTrade);
      editorScreen.setParentScreen(this.managerScreen);
      ClientAccess.argusclientMinecraft.gui.setScreen(editorScreen);
   }

   private void deleteIfConfirmed() {
      if (!this.deleteConfirmationTimer.hasElapsedAndReset(1000L)) {
         ClientContext.tradeRegistry.reregisterByName(this.trade.name());
         this.showCreatePanel();
      }
   }

   private void showCreatePanel() {
      this.managerScreen.requestPanelChange(TradePanelRequest.forCreate());
   }

   private String getDeleteButtonLabel() {
      return this.deleteConfirmationTimer.hasElapsed(1000L) ? "Delete" : "Are you sure?";
   }
}

package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.components.CreateTradePanel;
import dev.argus.client.gui.components.TradeActionsPanel;
import dev.argus.client.gui.components.TradeListPanel;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.TradePanelMode;
import dev.argus.client.render.TradePanelRequest;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TradeManagerScreen extends ClientScreen {
   private final TradeListPanel tradeListPanel = new TradeListPanel(this);
   private Panel actionPanel = new CreateTradePanel(this);
   private TradePanelRequest pendingPanelRequest;

   public TradeManagerScreen() {
      this.tradeListPanel.updateLayout();
      this.getPanels().add(this.tradeListPanel);
      this.getPanels().add(this.actionPanel);
   }

   @Override
   public void renderContents(GuiGraphicsExtractor graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
      if (this.pendingPanelRequest != null) {
         this.tradeListPanel.refreshTrades();
         this.getPanels().remove(this.actionPanel);
         if (this.pendingPanelRequest.mode() == TradePanelMode.CREATE) {
            this.actionPanel = new CreateTradePanel(this);
         } else {
            this.actionPanel = new TradeActionsPanel(this, this.pendingPanelRequest.trade());
         }

         this.getPanels().add(this.actionPanel);
         this.pendingPanelRequest = null;
      }
   }

   public void requestPanelChange(TradePanelRequest request) {
      this.pendingPanelRequest = request;
   }
}

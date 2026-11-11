package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.settings.StringSetting;

public abstract class TradeSelectorPanel<T> extends Panel implements ClientAccess {
   protected final TradeItemsComponent tradeItems;
   protected final StringSettingComponent searchInput;
   private final boolean fromSide;

   public TradeSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(fromSide ? "From" : "To");
      StringSetting searchSetting = new StringSetting("Search", "");
      this.fromSide = fromSide;
      this.tradeItems = tradeItems;
      this.searchInput = new StringSettingComponent(this, CloseState.ALWAYS_OPEN, searchSetting);
      this.children.add(this.searchInput);
      this.disableHeightLimit();
   }

   protected abstract void applySelection(T var1);

   protected abstract T getSelection();

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      ClientScreen screen = (ClientScreen)ClientAccess.argusclientMinecraft.gui.screen();
      if (!this.tradeItems.isSelectorOpen()) {
         ClientContext.taskScheduler.scheduleRotationTask(() -> screen.getPanels().remove(this), 0);
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      T selection = this.getSelection();
      if (selection != null) {
         ClientScreen screen = (ClientScreen)ClientAccess.argusclientMinecraft.gui.screen();
         ClientContext.taskScheduler.scheduleRotationTask(() -> {
            screen.getPanels().remove(this);
            this.applySelection(selection);
            this.tradeItems.closeSelector();
         }, 0);
      }
   }

   public boolean isFromSide() {
      return this.fromSide;
   }
}

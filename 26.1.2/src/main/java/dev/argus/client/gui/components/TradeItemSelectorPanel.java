package dev.argus.client.gui.components;

import dev.argus.client.ClientAccess;
import dev.argus.client.render.TextUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class TradeItemSelectorPanel extends TradeSelectorPanel<Item> implements ClientAccess {
   private final ItemListComponent itemList = new ItemListComponent(
      this, item -> TextUtils.containsIgnoreCase(item.getName(item.getDefaultInstance()).getString(), this.searchInput.getText())
   );

   public TradeItemSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(tradeItems, fromSide);
      this.children.add(this.itemList);
   }

   protected void applySelection(Item item) {
      if (this.isFromSide()) {
         this.tradeItems.setToStack(Items.EMERALD.getDefaultInstance());
         this.tradeItems.setFromStack(item.getDefaultInstance());
      } else {
         this.tradeItems.setFromStack(Items.EMERALD.getDefaultInstance());
         this.tradeItems.setToStack(item.getDefaultInstance());
      }
   }

   protected Item getSelection() {
      return this.itemList.getSelectedEntry();
   }
}

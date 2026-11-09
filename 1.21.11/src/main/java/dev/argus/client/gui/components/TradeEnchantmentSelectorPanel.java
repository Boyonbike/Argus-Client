package dev.argus.client.gui.components;

import dev.argus.client.render.TextUtils;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public class TradeEnchantmentSelectorPanel extends TradeSelectorPanel<EnchantmentListEntry> {
   private final EnchantmentListComponent enchantmentList = new EnchantmentListComponent(this, this::matchesEnchantment);

   public TradeEnchantmentSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
      super(tradeItems, fromSide);
      this.children.add(this.enchantmentList);
   }

   protected void applySelection(EnchantmentListEntry entry) {
      ItemStack stack = this.isFromSide() ? this.tradeItems.getFromStack() : this.tradeItems.getToStack();
      stack.enchant(EnchantmentUtils.getHolder(entry.enchantmentKey()), entry.level());
   }

   protected EnchantmentListEntry getSelection() {
      return this.enchantmentList.getSelectedEntry();
   }

   @Override
   public int getWidth() {
      return super.getWidth() + 48;
   }

   private boolean matchesEnchantment(EnchantmentListEntry entry) {
      ItemStack stack = this.isFromSide() ? this.tradeItems.getFromStack() : this.tradeItems.getToStack();
      boolean supported = ((Enchantment)EnchantmentUtils.getHolder(entry.enchantmentKey()).value()).isSupportedItem(stack);
      if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) {
         supported = true;
      }

      return supported && TextUtils.containsIgnoreCase(entry.name(), this.searchInput.getText());
   }
}

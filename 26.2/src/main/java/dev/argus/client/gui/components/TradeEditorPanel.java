package dev.argus.client.gui.components;

import com.google.common.base.Predicates;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.gui.screens.TradeEditorScreen;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.TradeDefinition;
import dev.argus.client.utils.inventory.TradeEnchantmentEntry;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class TradeEditorPanel extends Panel implements ClientAccess {
   private final NumberSetting<Integer> maxPriceSetting = new NumberSetting<>("MaxPrice", 32, 1, 64);
   private final BooleanSetting clearEnchantsSetting;
   private final TradeItemsComponent tradeItems;
   private final TradeEditorScreen screen;
   private final String tradeName;

   public TradeEditorPanel(TradeEditorScreen screen, TradeDefinition trade, String actionLabel) {
      super("Trade Editor");
      this.screen = screen;
      this.tradeName = trade.name();
      this.maxPriceSetting.setValue(trade.maxPrice());
      this.clearEnchantsSetting = new BooleanSetting("ClearEnchants", true, unusedValue -> this.hasAnyEnchantedStack());
      this.tradeItems = new TradeItemsComponent(this, trade);
      this.children.add(this.tradeItems);
      this.children.add(new NumericSettingComponent<>(this, CloseState.ALWAYS_OPEN, this.maxPriceSetting));
      this.children.add(new BooleanSettingComponent(this, CloseState.ALWAYS_OPEN, this.clearEnchantsSetting));
      this.children.add(new ActionButtonComponent(this, actionLabel, this::saveTrade));
      this.clearEnchantsSetting.setChangeCallback(this::clearEnchantments);
      this.setX((ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() - this.getWidth()) / 2);
      this.setY(64);
      this.disableHeightLimit();
   }

   @Override
   public void mouseScrolled(double mouseX, double mouseY, double amount) {
      super.mouseScrolled(mouseX, mouseY, amount);
      Objects.requireNonNull(this.screen);
   }

   private void saveTrade() {
      ArrayList<TradeEnchantmentEntry> enchantments = new ArrayList<>();
      int maxPrice = this.maxPriceSetting.getValue();
      this.getFirstEnchantedStack().ifPresent(enchantedStack -> {
         Map<ResourceKey<Enchantment>, Integer> levels = EnchantmentUtils.getLevels(enchantedStack);
         levels.forEach((enchantmentKey, level) -> enchantments.add(new TradeEnchantmentEntry(enchantmentKey.identifier().getPath(), level)));
      });
      TradeDefinition trade = new TradeDefinition(
         this.tradeName, this.tradeItems.getFromStack().getItem(), this.tradeItems.getToStack().getItem(), maxPrice, enchantments
      );
      ClientContext.tradeRegistry.reregisterByName(this.tradeName);
      ClientContext.tradeRegistry.registerTrade(trade);
      ((ClientScreen)ClientAccess.argusclientMinecraft.screen).closeWithTransition();
   }

   private boolean hasAnyEnchantedStack() {
      return hasEnchantments(this.tradeItems.getFromStack()) || hasEnchantments(this.tradeItems.getToStack());
   }

   private void clearEnchantments() {
      if (!this.clearEnchantsSetting.getValue()) {
         EnchantmentHelper.updateEnchantments(this.tradeItems.getFromStack(), mutableEnchantments -> mutableEnchantments.removeIf(Predicates.alwaysTrue()));
         EnchantmentHelper.updateEnchantments(this.tradeItems.getToStack(), mutableEnchantments -> mutableEnchantments.removeIf(Predicates.alwaysTrue()));
         this.clearEnchantsSetting.setValue(true);
      }
   }

   private Optional<ItemStack> getFirstEnchantedStack() {
      if (hasEnchantments(this.tradeItems.getFromStack())) {
         return Optional.of(this.tradeItems.getFromStack());
      } else {
         return hasEnchantments(this.tradeItems.getToStack()) ? Optional.of(this.tradeItems.getToStack()) : Optional.empty();
      }
   }

   private static boolean hasEnchantments(ItemStack stack) {
      return stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS);
   }

   public TradeEditorScreen getScreen() {
      return this.screen;
   }
}

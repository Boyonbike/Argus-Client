package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.inventory.InventorySortStrategy;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ComparatorInventorySortStrategy implements InventorySortStrategy {
   public static final InventorySortStrategy BY_TYPE = comparingStackProperty(ComparatorInventorySortStrategy::getCreativeTabIndex);
   public static final InventorySortStrategy BY_NAME = comparingStackProperty(stack -> stack.getHoverName().getString());
   private final Comparator<Integer> primaryComparator;

   public ComparatorInventorySortStrategy(Comparator<Integer> primaryComparator) {
      this.primaryComparator = primaryComparator;
   }

   private Comparator<Integer> getSlotComparator() {
      return this.primaryComparator.thenComparingInt(slotValue -> {
         int slot = slotValue;
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return stack.getDamageValue();
      }).thenComparingInt(slotValue -> {
         int slot = slotValue;
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return -stack.getCount();
      });
   }

   @Override
   public Integer[] getSourceSlots(InventorySorterScopeChoice scope) {
      int startInclusive = scope.getStartInclusive();
      int endExclusive = scope.getEndExclusive();
      Stream<Integer> occupiedSlots = IntStream.range(startInclusive, endExclusive).filter(slot -> {
         ItemStack stack = InventorySortStrategy.getInventoryStack(slot);
         return !stack.isEmpty();
      }).boxed();
      return occupiedSlots.sorted(this.getSlotComparator()).toArray(Integer[]::new);
   }

   private static int getCreativeTabIndex(ItemStack stack) {
      List<CreativeModeTab> tabs = CreativeModeTabs.allTabs();
      CreativeModeTab firstTab = tabs.getFirst();
      if (!firstTab.hasAnyItems()) {
         ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
         FeatureFlagSet enabledFeatures = connection.enabledFeatures();
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         Provider registries = level.registryAccess();
         CreativeModeTabs.tryRebuildTabContents(enabledFeatures, true, registries);
      }

      for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
         CreativeModeTab tab = tabs.get(tabIndex);
         Item item = stack.getItem();
         ItemStack defaultStack = item.getDefaultInstance();
         if (tab.contains(defaultStack)) {
            return tabIndex;
         }
      }

      return tabs.size();
   }

   private static InventorySortStrategy comparingStackProperty(Function<ItemStack, ? extends Comparable> property) {
      Comparator<Integer> comparator = Comparator.comparing(slotValue -> {
         int inventorySlot = slotValue;
         return (Comparable)property.apply(InventorySortStrategy.getInventoryStack(inventorySlot));
      });
      return new ComparatorInventorySortStrategy(comparator);
   }
}

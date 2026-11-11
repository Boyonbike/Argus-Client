package dev.argus.client.utils.inventory;

import com.google.gson.annotations.SerializedName;
import dev.argus.client.settings.NamedEntry;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TradeDefinition implements NamedEntry {
   @SerializedName("name")
   private final String name;
   @SerializedName("from")
   private final Item from;
   @SerializedName("to")
   private final Item to;
   @SerializedName("maxPrice")
   private final int maxPrice;
   @SerializedName("enchants")
   private final List<TradeEnchantmentEntry> enchants;

   public TradeDefinition(String name, Item from, Item to, int maxPrice, List<TradeEnchantmentEntry> enchants) {
      this.name = name;
      this.from = from;
      this.to = to;
      this.maxPrice = maxPrice;
      this.enchants = enchants;
   }

   public String getStoredName() {
      return this.name;
   }

   public Item getFromItem() {
      return this.from;
   }

   public Item getToItem() {
      return this.to;
   }

   public int getMaxPrice() {
      return this.maxPrice;
   }

   public List getEnchants() {
      return this.enchants;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public boolean matches(TradeOfferSnapshot offer) {
      ItemStack matchedStack = offer.firstCost();
      Item fromItem = this.from;
      if (matchedStack.is(fromItem)) {
         matchedStack = offer.result();
         Item toItem = this.to;
         if (matchedStack.is(toItem)) {
            if (offer.firstCost().getCount() > this.maxPrice) {
               return false;
            }

            for (TradeEnchantmentEntry enchantment : this.enchants) {
               String enchantmentName = enchantment.getName();
               ResourceKey enchantmentKey = EnchantmentUtils.getKey(enchantmentName);
               ItemStack enchantedResult = offer.result();
               int actualLevel = EnchantmentUtils.getLevel(enchantmentKey, enchantedResult);
               if ((actualLevel <= 0 || enchantment.getLevel() >= 0) && actualLevel != enchantment.getLevel()) {
                  return false;
               }
            }

            return true;
         }
      }

      return false;
   }

   @Override
   public final String toString() {
      return "TradeDefinition[name="
         + this.name
         + ", from="
         + this.from
         + ", to="
         + this.to
         + ", maxPrice="
         + this.maxPrice
         + ", enchants="
         + this.enchants
         + "]";
   }

   @Override
   public final int hashCode() {
      int hash = Objects.hashCode(this.name);
      hash = 31 * hash + Objects.hashCode(this.from);
      hash = 31 * hash + Objects.hashCode(this.to);
      hash = 31 * hash + this.maxPrice;
      return 31 * hash + Objects.hashCode(this.enchants);
   }

   @Override
   public final boolean equals(Object other) {
      if (this == other) {
         return true;
      } else {
         return !(other instanceof TradeDefinition trade)
            ? false
            : Objects.equals(this.enchants, trade.enchants)
               && this.maxPrice == trade.maxPrice
               && Objects.equals(this.to, trade.to)
               && Objects.equals(this.from, trade.from)
               && Objects.equals(this.name, trade.name);
      }
   }

   @SerializedName("name")
   public String name() {
      return this.name;
   }

   @SerializedName("from")
   public Item from() {
      return this.from;
   }

   @SerializedName("to")
   public Item to() {
      return this.to;
   }

   @SerializedName("maxPrice")
   public int maxPrice() {
      return this.maxPrice;
   }

   @SerializedName("enchants")
   public List<TradeEnchantmentEntry> enchants() {
      return this.enchants;
   }
}

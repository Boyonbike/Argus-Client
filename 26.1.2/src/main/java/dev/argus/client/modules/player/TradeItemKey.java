package dev.argus.client.modules.player;

import dev.argus.client.utils.inventory.TradeOfferSnapshot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;

public record TradeItemKey(Item costItem, Item resultItem) {
   public static TradeItemKey fromOffer(MerchantOffer offer) {
      Item costItem = offer.getCostA().getItem();
      Item resultItem = offer.getResult().getItem();
      return new TradeItemKey(costItem, resultItem);
   }

   public static TradeItemKey fromCachedOffer(TradeOfferSnapshot offer) {
      return new TradeItemKey(offer.firstCost().getItem(), offer.result().getItem());
   }
}

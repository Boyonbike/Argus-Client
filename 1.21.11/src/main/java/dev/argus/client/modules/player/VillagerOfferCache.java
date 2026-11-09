package dev.argus.client.modules.player;

import dev.argus.client.utils.inventory.TradeOfferSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VillagerOfferCache {
   private final Map<Integer, VillagerOfferCache.Entry> entries = new HashMap<>();

   public long getLastUpdatedMillis(int entityId) {
      VillagerOfferCache.Entry entry = this.entries.get(entityId);
      return entry == null ? 0L : entry.lastUpdatedMillis;
   }

   public synchronized void updateOffers(AbstractVillager villager, Iterable<MerchantOffer> offers) {
      if (villager != null) {
         Map<Integer, VillagerOfferCache.Entry> capturedEntries = this.entries;
         int entityId = villager.getId();
         VillagerOfferCache.Entry entry = capturedEntries.computeIfAbsent(entityId, ignoredEntityId -> new VillagerOfferCache.Entry());
         entry.lastUpdatedMillis = System.currentTimeMillis();
         entry.offers.clear();

         for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock()) {
               entry.offers.add(TradeOfferSnapshot.OUT_OF_STOCK);
            } else {
               TradeOfferSnapshot snapshot = TradeOfferSnapshot.fromOffer(offer);
               entry.offers.add(snapshot);
            }
         }
      }
   }

   public List<TradeOfferSnapshot> getOffers(int entityId) {
      VillagerOfferCache.Entry entry = this.entries.get(entityId);
      return entry == null ? null : entry.offers;
   }

   private static class Entry {
      private final List<TradeOfferSnapshot> offers = new ArrayList<>(10);
      private long lastUpdatedMillis;
   }
}

package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.elytratrip.RestockPlan.ItemSummary;
import dev.argus.client.modules.movement.elytratrip.RestockPlan.Kind;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Reads what the player carries into the shapes the pure trip rules use, and finds what to equip. */
public final class TripInventory {
   /** Inventory-menu slots: armour head..feet are 5..8, the offhand is 45. */
   public static final int HEAD_SLOT = 5;
   public static final int CHEST_SLOT = 6;
   public static final int OFFHAND_SLOT = 45;
   private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   /** Foods that hurt, or teleport you off the route. */
   private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
      Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW);

   private TripInventory() {
   }

   public static Kind kind(ItemStack stack) {
      if (stack.isEmpty()) return Kind.OTHER;
      if (stack.is(Items.ELYTRA)) return Kind.ELYTRA;
      if (stack.is(Items.TOTEM_OF_UNDYING)) return Kind.TOTEM;
      if (isGapple(stack)) return Kind.GAPPLE;
      if (armourSlot(stack) != null) return Kind.ARMOUR;
      if (isFood(stack)) return Kind.FOOD;
      return Kind.OTHER;
   }

   public static boolean isGapple(ItemStack stack) {
      return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
   }

   public static boolean isFood(ItemStack stack) {
      return stack.get(DataComponents.FOOD) != null && !isGapple(stack) && !BAD_FOOD.contains(stack.getItem());
   }

   public static int nutrition(ItemStack stack) {
      FoodProperties food = stack.get(DataComponents.FOOD);
      return food == null ? 0 : food.nutrition();
   }

   /** The armour slot this stack goes in, or null when it isn't armour. Elytras aren't armour here. */
   public static EquipmentSlot armourSlot(ItemStack stack) {
      if (stack.isEmpty() || stack.is(Items.ELYTRA)) return null;
      for (EquipmentSlot slot : ARMOUR) {
         if (InventoryUtils.canEquip(slot, stack)) return slot;
      }
      return null;
   }

   public static int durability(ItemStack stack) {
      return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
   }

   public static float percent(ItemStack stack) {
      return stack.isDamageableItem() ? durability(stack) / (float)stack.getMaxDamage() : 1.0F;
   }

   public static SupplyCounts counts(Player player) {
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      boolean wearingElytra = chest.is(Items.ELYTRA);
      List<Integer> spares = new ArrayList<>();
      int elytraMax = Items.ELYTRA.getDefaultInstance().getMaxDamage();
      int totems = 0, gapples = 0, food = 0, free = 0;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.isEmpty()) {
            free++;
            continue;
         }
         switch (kind(stack)) {
            case ELYTRA -> spares.add(durability(stack));
            case TOTEM -> totems += stack.getCount();
            case GAPPLE -> gapples += stack.getCount();
            case FOOD -> food += stack.getCount();
            default -> {
            }
         }
      }
      ItemStack offhand = player.getOffhandItem();
      if (offhand.is(Items.TOTEM_OF_UNDYING)) totems += offhand.getCount();
      List<Float> armour = new ArrayList<>();
      for (EquipmentSlot slot : ARMOUR) {
         ItemStack worn = player.getItemBySlot(slot);
         armour.add(worn.isEmpty() || worn.is(Items.ELYTRA) ? -1.0F : percent(worn));
      }
      return new SupplyCounts(wearingElytra ? durability(chest) : -1, elytraMax, spares, elytraMax, totems, gapples, food, armour, free);
   }

   /** Item id per main-inventory slot 0..35, "" for empty. */
   public static List<String> slotIds(Player player) {
      List<String> ids = new ArrayList<>(36);
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         ids.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      }
      return ids;
   }

   /** What the player carries now, as a loadout to restock to. */
   public static TripLoadout loadout(Player player) {
      SupplyCounts counts = counts(player);
      int elytras = counts.spareElytraDurabilities().size() + (counts.wearingElytra() ? 1 : 0);
      return new TripLoadout(slotIds(player), elytras, counts.totems(), counts.gapples(), counts.food());
   }

   /** Every non-empty stack in the main inventory, by inventory index 0..35. */
   public static List<ItemSummary> summaries(Player player) {
      List<ItemSummary> list = new ArrayList<>();
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.isEmpty()) continue;
         list.add(new ItemSummary(i, kind(stack), durability(stack), stack.getMaxDamage(), percent(stack)));
      }
      return list;
   }

   /** Inventory-menu slot of the spare elytra with the most durability, or -1. */
   public static int bestElytraMenuSlot(Player player) {
      int best = -1;
      int bestDurability = SupplyLimits.SPARE_ABOVE;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(Items.ELYTRA) && durability(stack) > bestDurability) {
            bestDurability = durability(stack);
            best = InventoryUtils.toMenuSlot(i);
         }
      }
      return best;
   }

   /** Inventory-menu slot of the best piece for this armour slot that beats what is worn, or -1. */
   public static int betterArmourMenuSlot(Player player, EquipmentSlot slot, SupplyLimits limits) {
      ItemStack worn = player.getItemBySlot(slot);
      boolean wornOk = !worn.isEmpty() && percent(worn) >= limits.armourMinPercent();
      int best = -1;
      float bestScore = wornOk ? score(worn) : -1.0F;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (armourSlot(stack) != slot || percent(stack) < limits.armourMinPercent()) continue;
         float score = score(stack);
         if (score > bestScore) {
            bestScore = score;
            best = InventoryUtils.toMenuSlot(i);
         }
      }
      return best;
   }

   /** Armour value first, then durability left. */
   private static float score(ItemStack stack) {
      return stack.getMaxDamage() * 10.0F + percent(stack);
   }

   public static int totemMenuSlot(Player player) {
      for (int i = 0; i < 36; i++) {
         if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return InventoryUtils.toMenuSlot(i);
      }
      return -1;
   }

   public static boolean hasFreeSlot(Player player) {
      for (int i = 0; i < 36; i++) {
         if (player.getInventory().getItem(i).isEmpty()) return true;
      }
      return false;
   }
}

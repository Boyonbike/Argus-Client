package dev.argus.client.modules.basehunting;

import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item checks shared by the ESPs that look for gear that came from players. */
final class HuntItems {
   static final String[] PLAYER_GEAR = {
      "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "netherite_helmet", "netherite_chestplate", "netherite_leggings",
      "netherite_boots", "elytra", "mace", "trident", "diamond_sword", "diamond_axe", "diamond_pickaxe", "diamond_shovel", "diamond_hoe",
      "netherite_sword", "netherite_axe", "netherite_pickaxe", "netherite_shovel", "netherite_hoe", "diamond_spear", "netherite_spear",
      "enchanted_golden_apple", "end_crystal", "ender_chest", "totem_of_undying", "experience_bottle", "shulker_box", "white_shulker_box",
      "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box",
      "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box",
      "green_shulker_box", "red_shulker_box", "black_shulker_box"
   };
   static final EquipmentSlot[] SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
   };

   private HuntItems() {
   }

   /**
    * Whether a stack looks like it came from a player: on the list, and when {@code needEnchants} is on, enchanted if
    * it is gear that can be (mobs spawn with plain diamond gear now and then, but not enchanted netherite).
    */
   static boolean isPlayerItem(ItemStack stack, Set<Item> items, boolean needEnchants) {
      if (stack.isEmpty() || !items.contains(stack.getItem())) {
         return false;
      }

      return !needEnchants || !stack.isDamageableItem() || stack.isEnchanted();
   }

   /**
    * Whether a stack carries at least one of the named enchantments, given as ids such as "mending" or
    * "minecraft:sharpness" separated by commas or spaces. An empty list matches anything.
    */
   static boolean hasAnyEnchant(ItemStack stack, String wanted) {
      String list = wanted == null ? "" : wanted.trim().toLowerCase();
      if (list.isEmpty()) {
         return true;
      }

      for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment : stack.getEnchantments().keySet()) {
         String id = enchantment.getRegisteredName();
         String path = id.substring(id.indexOf(':') + 1);
         for (String name : list.split("[,\\s]+")) {
            if (!name.isEmpty() && (name.equals(id) || name.equals(path))) {
               return true;
            }
         }
      }

      return false;
   }

   /** Every player item a mob is wearing or holding, which may be empty. */
   static java.util.List<ItemStack> allPlayerGear(LivingEntity entity, Set<Item> items, boolean needEnchants, String enchants) {
      java.util.List<ItemStack> gear = new java.util.ArrayList<>();
      for (EquipmentSlot slot : SLOTS) {
         ItemStack stack = entity.getItemBySlot(slot);
         if (isPlayerItem(stack, items, needEnchants) && hasAnyEnchant(stack, enchants)) {
            gear.add(stack);
         }
      }

      return gear;
   }

   /** The first player item a mob is wearing or holding, or null. */
   static ItemStack playerGear(LivingEntity entity, Set<Item> items, boolean needEnchants) {
      for (EquipmentSlot slot : SLOTS) {
         ItemStack stack = entity.getItemBySlot(slot);
         if (isPlayerItem(stack, items, needEnchants)) {
            return stack;
         }
      }

      return null;
   }
}

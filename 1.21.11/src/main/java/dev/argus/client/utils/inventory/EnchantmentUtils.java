package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class EnchantmentUtils implements ClientAccess {
   private EnchantmentUtils() {
      throw new IllegalArgumentException("Illegal constructor call");
   }

   public static int getLevel(ResourceKey enchantment, ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      }

      for (Entry entry : getEntries(stack)) {
         if (((Holder)entry.getKey()).is(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   public static int getLevel(ResourceKey enchantment, EquipmentSlot slot, LivingEntity entity) {
      ItemStack stack = entity.getItemBySlot(slot);
      return getLevel(enchantment, stack);
   }

   public static int getLevel(ResourceKey enchantment, EquipmentSlot slot) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return getLevel(enchantment, slot, player);
   }

   public static boolean hasEnchantment(ResourceKey enchantment, ItemStack stack) {
      return getLevel(enchantment, stack) > 0;
   }

   public static boolean hasEnchantment(ResourceKey enchantment, EquipmentSlot slot, LivingEntity entity) {
      return getLevel(enchantment, slot, entity) > 0;
   }

   public static boolean hasEnchantment(ResourceKey enchantment, EquipmentSlot slot) {
      return getLevel(enchantment, slot) > 0;
   }

   public static Map getLevels(ItemStack stack) {
      if (stack.isEmpty()) {
         return Collections.emptyMap();
      }

      HashMap levels = new HashMap();

      for (Entry entry : getEntries(stack)) {
         Optional key = ((Holder)entry.getKey()).unwrapKey();
         if (!key.isEmpty()) {
            ResourceKey enchantmentKey = (ResourceKey)key.get();
            int level = entry.getIntValue();
            levels.put(enchantmentKey, level);
         }
      }

      return levels;
   }

   private static Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<net.minecraft.world.item.enchantment.Enchantment>>> getEntries(ItemStack stack) {
      Item enchantedBook = Items.ENCHANTED_BOOK;
      return stack.is(enchantedBook) && stack.has(DataComponents.STORED_ENCHANTMENTS)
         ? stack.get(DataComponents.STORED_ENCHANTMENTS).entrySet()
         : stack.getEnchantments().entrySet();
   }

   public static Holder getHolder(ResourceKey enchantment) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment.identifier()).orElse(null);
   }

   public static Holder getHolder(String name) {
      ResourceKey key = getKey(name);
      return getHolder(key);
   }

   public static ResourceKey getKey(String name) {
      ResourceKey registryKey = Registries.ENCHANTMENT;
      Identifier identifier = Identifier.withDefaultNamespace(name);
      return ResourceKey.create(registryKey, identifier);
   }
}

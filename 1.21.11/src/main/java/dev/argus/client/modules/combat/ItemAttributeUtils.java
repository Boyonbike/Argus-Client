package dev.argus.client.modules.combat;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;

public final class ItemAttributeUtils {
   private ItemAttributeUtils() {
   }

   public static Entry findModifier(Holder attribute, ItemAttributeModifiers modifiers) {
      List entries = modifiers.modifiers();
      return findModifier(attribute, entries);
   }

   public static Entry findModifier(Holder attribute, List modifiers) {
      for (Object value : modifiers) {
         Entry entry = (Entry)value;
         if (entry.attribute() == attribute) {
            return entry;
         }
      }

      return null;
   }

   public static double applyModifiers(Holder attribute, double baseValue, ItemAttributeModifiers modifiers) {
      List entries = modifiers.modifiers();
      return applyModifiers(attribute, baseValue, entries);
   }

   public static double applyModifiers(Holder attribute, double baseValue, List modifiers) {
      double value = baseValue;

      for (Object candidate : modifiers) {
         Entry entry = (Entry)candidate;
         if (entry.attribute() == attribute) {
            AttributeModifier modifier = entry.modifier();
            value = applyModifier(baseValue, value, modifier);
         }
      }

      return value;
   }

   private static double applyModifier(double baseValue, double value, AttributeModifier modifier) {
      Operation operation = modifier.operation();

      double amount = switch (operation) {
         case ADD_VALUE -> modifier.amount();
         case ADD_MULTIPLIED_BASE -> modifier.amount() * baseValue;
         case ADD_MULTIPLIED_TOTAL -> modifier.amount() * value;
      };
      return value + amount;
   }
}

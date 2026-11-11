package dev.argus.client.modules.player.rekit;

import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Vanilla player inventory indices: 0–35 carried, 36–39 armour, 40 offhand. */
public final class RekitInventorySnapshot {
    private RekitInventorySnapshot() {}
    public static EquipmentSlot armourSlot(int index) {
        return switch (index) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            default -> null;
        };
    }
    public static JsonObject capture(IntFunction<ItemStack> inventory, DynamicOps<JsonElement> ops) {
        JsonObject slots = new JsonObject();
        for (int i = 0; i <= 40; i++) {
            ItemStack stack = inventory.apply(i);
            if (!stack.isEmpty()) slots.add(Integer.toString(i), ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow());
        }
        return slots;
    }
    public static Map<Integer, ItemStack> decode(JsonObject slots, DynamicOps<JsonElement> ops) {
        Map<Integer, ItemStack> result = new TreeMap<>();
        if (slots == null) return result;
        for (var entry : slots.entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                if (slot < 0 || slot > 40) continue;
                ItemStack stack = ItemStack.CODEC.parse(ops, entry.getValue()).result().orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) result.put(slot, stack);
            } catch (RuntimeException ignored) { /* Keep valid entries if an item was removed or a slot is malformed. */ }
        }
        return result;
    }
}

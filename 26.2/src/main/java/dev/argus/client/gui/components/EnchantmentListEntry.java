package dev.argus.client.gui.components;

import net.minecraft.resources.ResourceKey;

public record EnchantmentListEntry(ResourceKey enchantmentKey, int level, String name) {
}

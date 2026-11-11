package dev.argus.client.modules.player;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;

record StackGroupKey(Item item, DataComponentMap components) {
}

package dev.argus.client.utils.combat;

import net.minecraft.world.level.block.Block;

public record PendingPlacement(long createdAtMillis, Block block) {
}

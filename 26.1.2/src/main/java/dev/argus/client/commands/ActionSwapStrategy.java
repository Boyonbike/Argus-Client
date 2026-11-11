package dev.argus.client.commands;

import dev.argus.client.ClientAccess;
import net.minecraft.world.item.Item;

public interface ActionSwapStrategy extends ClientAccess {
   SwapSlots findSlots(Item[] var1);

   void apply(SwapSlots var1);

   void undo(SwapSlots var1);
}

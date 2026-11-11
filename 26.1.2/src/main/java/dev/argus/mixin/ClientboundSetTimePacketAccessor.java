package dev.argus.mixin;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetTimePacket.class)
public interface ClientboundSetTimePacketAccessor {
   @Mutable
   @Accessor("gameTime")
   void setTime(long var1);

   @Mutable
   @Accessor("dayTime")
   void setTimeOfDay(long var1);
}

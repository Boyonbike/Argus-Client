package dev.argus.mixin;

import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPongResponsePacket.class)
public interface ClientboundPongResponsePacketAccessor {
   @Mutable
   @Accessor("time")
   long argusclient$startTime();

   @Mutable
   @Accessor("time")
   void argusclient$startTime(long var1);
}

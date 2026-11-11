package dev.argus.mixin;

import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPingRequestPacket.class)
public interface ServerboundPingRequestPacketAccessor {
   @Accessor("time")
   @Mutable
   void argusclient$startTime(long var1);

   @Accessor("time")
   @Mutable
   long argusclient$startTime();
}

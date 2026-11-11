package dev.argus.mixin;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.core.PositionAndRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMoveVehiclePacket.class)
public interface ServerboundMoveVehiclePacketAccessor {
   @Accessor("movingTo")
   @Mutable
   void argusclient$setMovingTo(PositionAndRotation var1);
}

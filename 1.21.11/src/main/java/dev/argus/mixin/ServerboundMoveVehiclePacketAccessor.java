package dev.argus.mixin;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMoveVehiclePacket.class)
public interface ServerboundMoveVehiclePacketAccessor {
   @Accessor("position")
   @Mutable
   void argusclient$setPos(Vec3 var1);
}

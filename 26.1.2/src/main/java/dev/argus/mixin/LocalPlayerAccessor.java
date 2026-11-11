package dev.argus.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
   @Accessor("lastOnGround")
   boolean lastOnGround();

   @Accessor("yRotLast")
   float lastYaw();

   @Invoker("modifyInput")
   Vec2 argusclient$applyMovementSpeedFactors(Vec2 var1);

   @Accessor("positionReminder")
   void argusclient$setTicksSinceLastPositionPacketSent(int var1);

   @Accessor("positionReminder")
   int argusclient$getTicksSinceLastPositionPacketSent();

   @Accessor("lastSentInput")
   void argusclient$setLastPlayerInput(Input var1);

   @Accessor("wasSprinting")
   void argusclient$setLastSprinting(boolean var1);
}

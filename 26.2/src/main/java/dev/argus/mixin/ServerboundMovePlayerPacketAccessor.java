package dev.argus.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
   @Mutable
   @Accessor("onGround")
   void setOnGround(boolean var1);

   @Accessor("hasPos")
   boolean isChangePosition();

   @Mutable
   @Accessor("y")
   void setY(double var1);

   @Mutable
   @Accessor("x")
   void setX(double var1);

   @Mutable
   @Accessor("z")
   void setZ(double var1);

   @Mutable
   @Accessor("yRot")
   void setYaw(float var1);

   @Mutable
   @Accessor("xRot")
   void setPitch(float var1);
}

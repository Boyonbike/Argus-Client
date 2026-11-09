package dev.argus.mixin;

import net.minecraft.world.entity.PositionMoveRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PositionMoveRotation.class)
public interface PositionMoveRotationAccessor {
   @Mutable
   @Accessor("yRot")
   void argusclient$setYaw(float var1);

   @Mutable
   @Accessor("xRot")
   void argusclient$setPitch(float var1);
}

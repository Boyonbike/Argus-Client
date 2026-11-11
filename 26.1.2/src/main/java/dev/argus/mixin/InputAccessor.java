package dev.argus.mixin;

import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
   @Mutable
   @Accessor("shift")
   void argusclient$setSneak(boolean var1);

   @Mutable
   @Accessor("sprint")
   void argusclient$setSprint(boolean var1);

   @Mutable
   @Accessor("jump")
   void argusclient$setJump(boolean var1);

   @Mutable
   @Accessor("forward")
   void argusclient$setForward(boolean var1);

   @Mutable
   @Accessor("backward")
   void argusclient$setBack(boolean var1);

   @Mutable
   @Accessor("left")
   void argusclient$setLeft(boolean var1);

   @Mutable
   @Accessor("right")
   void argusclient$setRight(boolean var1);
}

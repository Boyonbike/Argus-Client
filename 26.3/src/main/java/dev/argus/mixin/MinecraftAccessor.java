package dev.argus.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
   @Invoker("startAttack")
   boolean attack();

   @Invoker("startUseItem")
   void interact();

   @Accessor("rightClickDelay")
   void setItemUseCooldown(int var1);

   @Accessor("rightClickDelay")
   int getItemUseCooldown();

}

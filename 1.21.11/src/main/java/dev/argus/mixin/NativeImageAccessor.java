package dev.argus.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.jtracy.MemoryPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NativeImage.class)
public interface NativeImageAccessor {
   @Accessor("MEMORY_POOL")
   static MemoryPool argusclient$getPool() {
      return null;
   }

   @Accessor("pixels")
   long argusclient$getPointer();

   @Accessor("size")
   long argusclient$getSize();
}

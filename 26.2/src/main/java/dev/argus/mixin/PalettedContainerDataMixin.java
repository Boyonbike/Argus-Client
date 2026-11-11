package dev.argus.mixin;

import dev.argus.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Data.class)
public class PalettedContainerDataMixin implements PalettedContainerDataAccess {
   @Shadow
   @Final
   BitStorage storage;
   @Shadow
   @Final
   Palette palette;

   @Override
   public BitStorage argusclient$storage() {
      return this.storage;
   }

   @Override
   public Palette argusclient$palette() {
      return this.palette;
   }
}

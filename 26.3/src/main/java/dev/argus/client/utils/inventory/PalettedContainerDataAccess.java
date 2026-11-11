package dev.argus.client.utils.inventory;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface PalettedContainerDataAccess {
   static BitStorage getStorage(PalettedContainer container) {
      PalettedContainerDataAccess data = ((PalettedContainerAccess)container).argusclient$data();
      return data.argusclient$storage();
   }

   static Palette getPalette(PalettedContainer container) {
      PalettedContainerDataAccess data = ((PalettedContainerAccess)container).argusclient$data();
      return data.argusclient$palette();
   }

   BitStorage argusclient$storage();

   Palette argusclient$palette();
}

package dev.argus.client.utils;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;

public class SoftwareKeyMapping extends KeyMapping {
   private boolean down;

   public SoftwareKeyMapping() {
      super("argusclient-wrapper-keybind", 0, Category.MISC);
      ALL.remove("argusclient-wrapper-keybind");
   }

   @Override
   public boolean isDown() {
      return this.down;
   }

   @Override
   public void setDown(boolean down) {
      this.down = down;
   }
}

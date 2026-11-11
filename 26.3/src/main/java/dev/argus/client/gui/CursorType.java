package dev.argus.client.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;

public enum CursorType {
   STANDARD(CursorTypes.ARROW),
   POINTER(CursorTypes.POINTING_HAND),
   INPUT(CursorTypes.IBEAM);

   private static CursorType currentCursor = STANDARD;
   private final com.mojang.blaze3d.platform.cursor.CursorType cursor;

   CursorType(com.mojang.blaze3d.platform.cursor.CursorType cursor) {
      this.cursor = cursor;
   }

   public void apply() {
      if (this != currentCursor) {
         Minecraft minecraft = Minecraft.getInstance();
         Window window = minecraft.getWindow();
         window.selectCursor(this.cursor);
         currentCursor = this;
      }
   }
}

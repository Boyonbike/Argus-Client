package dev.argus.client.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public enum CursorType {
   STANDARD(() -> GLFW.glfwCreateStandardCursor(221185)),
   POINTER(() -> GLFW.glfwCreateStandardCursor(221188)),
   INPUT(() -> GLFW.glfwCreateStandardCursor(221186));

   private static CursorType currentCursor = STANDARD;
   private final Supplier cursorSupplier;
   private long cursorHandle = -1L;

   public long getHandle() {
      long cachedHandle = this.cursorHandle;
      if (cachedHandle == -1L) {
         Long createdHandle = (Long)this.cursorSupplier.get();
         this.cursorHandle = createdHandle;
      }

      return this.cursorHandle;
   }

   public void apply() {
      if (this != currentCursor) {
         Minecraft minecraft = Minecraft.getInstance();
         Window window = minecraft.getWindow();
         long windowHandle = window.handle();
         long newCursorHandle = this.getHandle();
         long targetWindowHandle = windowHandle;
         GLFW.glfwSetCursor(targetWindowHandle, newCursorHandle);
         currentCursor = this;
      }
   }

   CursorType(Supplier cursorSupplier) {
      this.cursorSupplier = cursorSupplier;
   }
}

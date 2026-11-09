package dev.argus.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
   @Inject(
      method = "<init>",
      at = @At(value = "INVOKE_ASSIGN", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", shift = Shift.BEFORE, remap = false)
   )
   private void configureWindowHints(
      WindowEventHandler eventHandler,
      ScreenManager screenManager,
      DisplayData displayData,
      String preferredFullscreenMode,
      String title,
      CallbackInfo callback
   ) {
      GLFW.glfwWindowHint(139270, 0);
   }
}

package dev.argus.mixin;

import com.mojang.blaze3d.platform.DisplayData;
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
   // 26.1.2 creates the GLFW window in the static createGlfwWindow helper (called from the constructor).
   @Inject(
      method = "createGlfwWindow",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false)
   )
   private static void configureWindowHints(
      int width, int height, String title, long monitor, com.mojang.blaze3d.systems.GpuBackend backend,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Long> callback
   ) {
      GLFW.glfwWindowHint(139270, 0);
   }
}

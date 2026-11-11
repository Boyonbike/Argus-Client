package dev.argus.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.3 creates the window through SDL, so the old GLFW forward-compatibility
 * hint no longer has a target. The mixin now exposes the close flag so screens
 * can cancel a pending quit.
 */
@Mixin(Window.class)
public interface WindowMixin {
   @Accessor("shouldClose")
   void argusclient$setShouldClose(boolean value);
}

package dev.argus.client.managers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import dev.argus.client.ClientAccess;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Off-screen targets for the client's shader effects.
 *
 * <p>Targets start dormant at 1x1 and only grow to window size the first time something draws into them
 * (see {@link #activate}). Every effect target used to be allocated at full resolution with a depth buffer
 * at startup, which cost hundreds of MB of video memory at high resolutions even with every effect off;
 * on integrated graphics that memory is shared with the system.
 */
public final class RenderTargetRegistry implements ClientAccess {
   private static final Map<RenderTarget, ScaledTarget> renderTargets = new IdentityHashMap<>();
   private static int windowWidth = -1;
   private static int windowHeight = -1;

   private RenderTargetRegistry() {
   }

   public static RenderTarget createRenderTarget(String name) {
      return createRenderTarget(name, 1);
   }

   public static RenderTarget createRenderTarget(String name, int scale) {
      TextureTarget target = new TextureTarget(
         name, 1, 1, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
      renderTargets.put(target, new ScaledTarget(target, scale));
      return target;
   }

   public static Supplier createLazyRenderTarget(String name) {
      RenderTarget[] cachedTarget = new RenderTarget[1];
      return () -> {
         if (cachedTarget[0] == null) {
            cachedTarget[0] = createRenderTarget(name);
         }

         return cachedTarget[0];
      };
   }

   /** Sizes a dormant target to the window before its first use; a no-op for active or foreign targets. */
   public static void activate(RenderTarget target) {
      ScaledTarget entry = renderTargets.get(target);
      if (entry != null && !entry.active) {
         entry.active = true;
         Window window = ClientAccess.argusclientMinecraft.getWindow();
         int width = windowWidth > 0 ? windowWidth : window.getWidth();
         int height = windowHeight > 0 ? windowHeight : window.getHeight();
         entry.target.resize(Math.max(1, width / entry.scale), Math.max(1, height / entry.scale));
      }
   }

   public static void resizeRenderTargets(int width, int height) {
      windowWidth = width;
      windowHeight = height;
      for (ScaledTarget entry : renderTargets.values()) {
         if (entry.active) {
            entry.target.resize(Math.max(1, width / entry.scale), Math.max(1, height / entry.scale));
         }
      }
   }

   private static final class ScaledTarget {
      private final RenderTarget target;
      private final int scale;
      private boolean active;

      private ScaledTarget(RenderTarget target, int scale) {
         this.target = target;
         this.scale = scale;
      }
   }
}

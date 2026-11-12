package dev.argus.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

/**
 * A FeatureRenderDispatcher private to Argus. 26.2's dispatcher has a single
 * PreparedFrame, and vanilla keeps it open for the whole level render, which is
 * exactly when Argus's world-render events submit entities and items. Using a
 * separate dispatcher (with its own staged vertex buffer) lets those immediate
 * draws run instead of being rejected as "PreparedFrame already in use".
 */
public final class ArgusFeatureDispatcher {
   private static final ThreadLocal<StagedVertexBuffer> CONSTRUCTING = new ThreadLocal<>();
   private static FeatureRenderDispatcher dispatcher;
   private static StagedVertexBuffer buffer;

   private ArgusFeatureDispatcher() {
   }

   public static FeatureRenderDispatcher get() {
      if (dispatcher == null) {
         Minecraft minecraft = Minecraft.getInstance();
         StagedVertexBuffer staged = new StagedVertexBuffer(() -> "Argus Immediate Buffer", 1 << 20);
         CONSTRUCTING.set(staged);
         try {
            // RenderBuffers is only read for its staged buffer, which FeatureRenderDispatcherMixin replaces.
            dispatcher = new FeatureRenderDispatcher(
               null, minecraft.getModelManager(), minecraft.getAtlasManager(), minecraft.font, minecraft.gameRenderer.gameRenderState());
         } finally {
            CONSTRUCTING.remove();
         }
         buffer = staged;
      }
      return dispatcher;
   }

   /** Recycles the staged buffer's GPU memory once per frame, like RenderBuffers.endFrame does for vanilla. */
   public static void endFrame() {
      if (buffer != null) {
         buffer.endFrame();
      }
   }

   /** Frees the dispatcher and its staged buffer; called when vanilla closes its own in GameRenderer.close. */
   public static void close() {
      if (dispatcher != null) {
         dispatcher.close();
         dispatcher = null;
      }
      if (buffer != null) {
         buffer.close();
         buffer = null;
      }
   }

   /** Set only while {@link #get()} constructs the dispatcher. */
   public static StagedVertexBuffer constructingBuffer() {
      return CONSTRUCTING.get();
   }
}

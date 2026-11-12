package dev.argus.client.render;

import dev.argus.client.ClientAccess;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;

/** Renders a temporary submit storage immediately, for entity/item draws outside the feature pass. */
public final class ImmediateSubmitRenderer implements ClientAccess {
   private static boolean flushing;
   private static boolean warnedNestedFlush;

   private ImmediateSubmitRenderer() {
   }

   public static void flush(SubmitNodeStorage storage) {
      // Argus's dispatcher has one PreparedFrame; a flush started from inside another flush can't use it.
      if (flushing) {
         if (!warnedNestedFlush) {
            warnedNestedFlush = true;
            dev.argus.client.ArgusClient.LOGGER.warn("Nested Argus immediate submit flush skipped", new IllegalStateException());
         }
         return;
      }

      flushing = true;
      try {
         // Argus's own dispatcher: vanilla's single PreparedFrame is held open for the whole level render.
         FeatureRenderDispatcher dispatcher = ArgusFeatureDispatcher.get();
         PreparedFrame frame;
         try {
            frame = dispatcher.prepareFrame(storage);
         } catch (RuntimeException | Error failure) {
            // prepareFrame marks the frame in use before preparing; close it or every later flush is rejected.
            try {
               dispatcher.preparedFrame.close();
            } catch (RuntimeException closeFailure) {
               failure.addSuppressed(closeFailure);
            }
            throw failure;
         }
         try {
            frame.executeSolid();
            frame.executeTranslucent();
            frame.executeOutline();
            frame.executeTranslucentAfterTerrain();
            frame.executeAlwaysOnTop();
         } finally {
            frame.close();
         }
      } finally {
         flushing = false;
      }
   }
}

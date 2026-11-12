package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.argus.client.ClientAccess;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
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
         render(storage);
      } finally {
         flushing = false;
      }
   }

   private static void render(SubmitNodeStorage storage) {
      Minecraft minecraft = ClientAccess.argusclientMinecraft;
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
      RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      try (RenderPass pass = target.hasDepth()
            ? encoder.createRenderPass(
               () -> "argus-immediate", target.getColorTextureView(), Optional.empty(),
               target.getDepthTextureView(), OptionalDouble.empty())
            : encoder.createRenderPass(() -> "argus-immediate", target.getColorTextureView(), Optional.empty())) {
         frame.executeSolid(pass);
         frame.executeTranslucent(pass);
         frame.executeOutline(pass);
         frame.executeTranslucentAfterTerrain(pass);
         frame.executeAlwaysOnTop(pass);
      } finally {
         frame.close();
      }
      // No submit(): it is the per-frame boundary (Minecraft calls it once); mid-frame it recycles transient uniform memory.
   }
}

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
   private ImmediateSubmitRenderer() {
   }

   public static void flush(SubmitNodeStorage storage) {
      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.featureRenderDispatcher();
      PreparedFrame frame;
      try {
         frame = dispatcher.prepareFrame(storage);
      } catch (IllegalStateException frameInUse) {
         // The main feature frame is already being prepared this tick; its own pass covers the geometry.
         return;
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
      encoder.submit();
   }
}

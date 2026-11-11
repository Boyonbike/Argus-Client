package dev.argus.client.render;

import dev.argus.client.ClientAccess;
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
      PreparedFrame frame = dispatcher.prepareFrame(storage);
      try {
         frame.executeSolid();
         frame.executeTranslucent();
         frame.executeOutline();
         frame.executeTranslucentAfterTerrain();
         frame.executeAlwaysOnTop();
      } finally {
         frame.close();
      }
   }
}

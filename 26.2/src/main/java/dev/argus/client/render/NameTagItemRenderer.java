package dev.argus.client.render;

import net.minecraft.client.renderer.SubmitNodeStorage;

/** Submit storage for nametag item rendering. */
public final class NameTagItemRenderer {
   private static final SubmitNodeStorage submitNodes = new SubmitNodeStorage();

   private NameTagItemRenderer() {
   }

   public static SubmitNodeStorage getSubmitNodes() {
      return submitNodes;
   }

   public static void flush() {
      ImmediateSubmitRenderer.flush(submitNodes);
      submitNodes.getSubmitsPerOrder().clear();
   }
}

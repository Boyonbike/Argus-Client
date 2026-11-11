package dev.argus.client.managers;

import dev.argus.client.render.RenderState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Legacy global render-state hooks. 26.3 RenderPearl pipelines own depth,
 * cull and polygon-offset state, so these hooks no longer touch the backend;
 * blend/depth behaviour is expressed by the RenderPipeline used for the draw.
 */
public final class RenderStateManager {
   private static final List<RenderState> registeredStates = new ObjectArrayList();
   public static final RenderState BLENDED_SMOOTH_LINES = entering -> {
   };
   public static final RenderState THROUGH_WALLS = entering -> {
   };
   public static final RenderState POLYGON_OFFSET = entering -> {
   };
   public static final RenderState POLYGON_OFFSET_THROUGH_WALLS = POLYGON_OFFSET.then(THROUGH_WALLS);
   private static boolean active;

   public static void reset() {
      registeredStates.clear();
      active = false;
   }

   public static void runWithStates(Runnable draw, RenderState... states) {
      addStates(states);
      draw.run();
      reset();
   }

   public static void addStates(RenderState... states) {
      registeredStates.addAll(Arrays.asList(states));
      active = true;
   }

   public static void applyRegistered(boolean entering) {
      if (active) {
         for (RenderState state : registeredStates) {
            state.apply(entering);
         }
      }
   }

   public static boolean isActive() {
      return active;
   }
}

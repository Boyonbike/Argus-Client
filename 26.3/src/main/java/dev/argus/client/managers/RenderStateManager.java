package dev.argus.client.managers;

import dev.argus.client.render.RenderState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Render-state requests for Argus's immediate draws. 26.3 RenderPearl pipelines
 * own depth, cull and polygon-offset state, so these states no longer touch the
 * backend directly: while THROUGH_WALLS (alone or combined) is registered,
 * MeshRenderer draws with a ThroughWallsPipelines copy (no depth test, no cull).
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

   /** Whether the registered states ask for drawing through walls (depth test off). */
   public static boolean ignoresDepth() {
      if (!active) {
         return false;
      }
      for (RenderState state : registeredStates) {
         if (state == THROUGH_WALLS || state == POLYGON_OFFSET_THROUGH_WALLS) {
            return true;
         }
      }
      return false;
   }
}

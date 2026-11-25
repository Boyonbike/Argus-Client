package dev.argus.client.modules.movement;

import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.render.LineMeshRenderer;
import java.awt.Color;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Draws Baritone's elytra route as a line through the middle of each route block, for BaritoneElytra and ElytraTrip. */
public final class ElytraRouteRenderer {
   /** Segments with both ends further than this from the camera aren't drawn; the route can run for thousands of blocks. */
   private static final double MAX_DISTANCE = 512.0;
   private static final float LINE_WIDTH = 2.0F;

   private ElytraRouteRenderer() {
   }

   public static void render(WorldRenderEvent event, List<? extends BlockPos> route, Vec3 camera, Color color) {
      if (route.size() < 2) {
         return;
      }
      int argb = color.hashCode();
      double max = MAX_DISTANCE * MAX_DISTANCE;
      LineMeshRenderer.begin(event, LINE_WIDTH);
      Vec3 previous = Vec3.atCenterOf(route.get(0));
      for (int i = 1; i < route.size(); i++) {
         Vec3 next = Vec3.atCenterOf(route.get(i));
         if (previous.distanceToSqr(camera) <= max || next.distanceToSqr(camera) <= max) {
            LineMeshRenderer.addVertex(previous.x, previous.y, previous.z, argb, true, false);
            LineMeshRenderer.addVertex(next.x, next.y, next.z, argb, true, false);
         }
         previous = next;
      }
      LineMeshRenderer.draw();
   }
}

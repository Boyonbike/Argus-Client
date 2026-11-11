package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum SpeedMineRenderModeChoice implements NamedEntry {
   PLAIN("Plain") {
      @Override
      public AABB getRenderBounds(AABB bounds, float progress) {
         return bounds;
      }
   },
   IN("In") {
      @Override
      public AABB getRenderBounds(AABB bounds, float progress) {
         float remainingProgress = 1.0F - progress;
         double width = bounds.getXsize();
         double absoluteWidth = Math.abs(width);
         double height = bounds.getYsize();
         double absoluteHeight = Math.abs(height);
         double depth = bounds.getZsize();
         double absoluteDepth = Math.abs(depth);
         Vec3 center = bounds.getCenter();
         return new AABB(
            center.subtract(remainingProgress * absoluteWidth / 2.0, remainingProgress * absoluteHeight / 2.0, remainingProgress * absoluteDepth / 2.0),
            center.add(remainingProgress * absoluteWidth / 2.0, remainingProgress * absoluteHeight / 2.0, remainingProgress * absoluteDepth / 2.0)
         );
      }
   },
   OUT("Out") {
      @Override
      public AABB getRenderBounds(AABB bounds, float progress) {
         double width = bounds.getXsize();
         double absoluteWidth = Math.abs(width);
         double height = bounds.getYsize();
         double absoluteHeight = Math.abs(height);
         double depth = bounds.getZsize();
         double absoluteDepth = Math.abs(depth);
         Vec3 center = bounds.getCenter();
         return new AABB(
            center.subtract(progress * absoluteWidth / 2.0, progress * absoluteHeight / 2.0, progress * absoluteDepth / 2.0),
            center.add(progress * absoluteWidth / 2.0, progress * absoluteHeight / 2.0, progress * absoluteDepth / 2.0)
         );
      }
   },
   IN_OUT("InOut") {
      @Override
      public AABB getRenderBounds(AABB bounds, float progress) {
         float clamped = Math.max(0.0F, Math.min(1.0F, progress));
         return OUT.getRenderBounds(bounds, Math.abs(2.0F * clamped - 1.0F));
      }
   };

   private final String name;

   @Override
   public String getName() {
      return this.name;
   }

   SpeedMineRenderModeChoice(String name) {
      this.name = name;
   }

   public abstract AABB getRenderBounds(AABB var1, float var2);
}

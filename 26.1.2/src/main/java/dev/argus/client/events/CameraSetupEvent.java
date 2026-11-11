package dev.argus.client.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CameraSetupEvent extends CancellableEvent {
   private final Entity entity;
   private final float partialTick;
   private double x;
   private double y;
   private double z;

   public Entity getEntity() {
      return this.entity;
   }

   public Vec3 getPosition() {
      return new Vec3(this.x, this.y, this.z);
   }

   public float getPartialTick() {
      return this.partialTick;
   }

   public CameraSetupEvent(Entity entity, float partialTick, double x, double y, double z) {
      this.entity = entity;
      this.partialTick = partialTick;
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public void setPosition(Vec3 position) {
      this.x = position.x;
      this.y = position.y;
      this.z = position.z;
   }
}

package dev.argus.client.events;

import dev.argus.client.utils.math.Vec3Utils;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public class LocalPlayerMoveEvent extends CancellableEvent {
   private final Vec3 originalVelocity;
   private final Vec3 velocity;
   private MoverType moverType;

   public void setVelocity(Vec3 velocity) {
      Vec3Utils.setComponents(this.velocity, velocity.x, velocity.y, velocity.z);
   }

   public MoverType getMoverType() {
      return this.moverType;
   }

   public Vec3 getVelocity() {
      return this.velocity;
   }

   public LocalPlayerMoveEvent(Vec3 velocity, MoverType moverType) {
      this.originalVelocity = new Vec3(velocity.x, velocity.y, velocity.z);
      this.velocity = velocity;
      this.moverType = moverType;
   }

   public void setY(double y) {
      this.setVelocity(new Vec3(this.getX(), y, this.getZ()));
   }

   public double getZ() {
      return this.velocity.z;
   }

   public double getX() {
      return this.velocity.x;
   }

   public double getY() {
      return this.velocity.y;
   }

   public void setMoverType(MoverType moverType) {
      this.moverType = moverType;
   }

   public void setHorizontalVelocity(double x, double z) {
      this.setVelocity(new Vec3(x, this.getY(), z));
   }

   public Vec3 getOriginalVelocity() {
      return this.originalVelocity;
   }
}

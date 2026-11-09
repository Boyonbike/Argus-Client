package dev.argus.client.events;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;

public final class ClientPacketListenerHandleExplosionEvent extends CancellableEvent {
   private final double centerX;
   private final double centerY;
   private final double centerZ;
   private final float radius;
   private float velocityX;
   private float velocityY;
   private float velocityZ;

   public ClientPacketListenerHandleExplosionEvent(ClientboundExplodePacket packet) {
      this.centerX = packet.center().x;
      this.centerY = packet.center().y;
      this.centerZ = packet.center().z;
      this.radius = 6.0F;
      packet.playerKnockback().ifPresent(knockback -> {
         this.velocityX = (float)knockback.x;
         this.velocityY = (float)knockback.y;
         this.velocityZ = (float)knockback.z;
      });
   }

   public ClientPacketListenerHandleExplosionEvent(
      double centerX, double centerY, double centerZ, float radius, float velocityX, float velocityY, float velocityZ
   ) {
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.velocityZ = velocityZ;
   }

   public double getCenterX() {
      return this.centerX;
   }

   public double getCenterY() {
      return this.centerY;
   }

   public double getCenterZ() {
      return this.centerZ;
   }

   public float getRadius() {
      return this.radius;
   }

   public float getVelocityX() {
      return this.velocityX;
   }

   public void setVelocityX(float velocityX) {
      this.velocityX = velocityX;
   }

   public float getVelocityY() {
      return this.velocityY;
   }

   public void setVelocityY(float velocityY) {
      this.velocityY = velocityY;
   }

   public float getVelocityZ() {
      return this.velocityZ;
   }

   public void setVelocityZ(float velocityZ) {
      this.velocityZ = velocityZ;
   }
}

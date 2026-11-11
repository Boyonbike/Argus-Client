package dev.argus.client.events;

public final class LocalPlayerSendPositionEvent extends CancellableEvent {
   private final EventStage stage;
   private final double originalX;
   private final double originalY;
   private final double originalZ;
   private final float originalYaw;
   private final float originalPitch;
   private final boolean originalOnGround;
   private double x;
   private double y;
   private double z;
   private float yaw;
   private float pitch;
   private boolean onGround;
   private boolean modified;

   public EventStage getStage() {
      return this.stage;
   }

   public void setOnGround(boolean onGround) {
      this.modified = true;
      this.onGround = onGround;
   }

   public float getOriginalYaw() {
      return this.originalYaw;
   }

   public float getCurrentPitch() {
      return this.pitch;
   }

   public double getOriginalY() {
      return this.originalY;
   }

   public double getOriginalX() {
      return this.originalX;
   }

   public float getOriginalPitch() {
      return this.originalPitch;
   }

   public boolean isOnGround() {
      return this.onGround;
   }

   public float getCurrentYaw() {
      return this.yaw;
   }

   public boolean wasOnGround() {
      return this.originalOnGround;
   }

   public LocalPlayerSendPositionEvent() {
      this(EventStage.Pre, 0.0, 0.0, 0.0, 0.0F, 0.0F, false);
   }

   public LocalPlayerSendPositionEvent(EventStage stage, double x, double y, double z, float yaw, float pitch, boolean onGround) {
      this.stage = stage;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
      this.onGround = onGround;
      this.originalX = x;
      this.originalY = y;
      this.originalZ = z;
      this.originalYaw = yaw;
      this.originalPitch = pitch;
      this.originalOnGround = onGround;
   }

   public LocalPlayerSendPositionEvent(EventStage stage, LocalPlayerSendPositionEvent event) {
      this(stage, event.x, event.y, event.z, event.yaw, event.pitch, event.onGround);
   }

   public void setX(double x) {
      this.modified = true;
      this.x = x;
   }

   public void setY(double y) {
      this.modified = true;
      this.y = y;
   }

   public void setZ(double z) {
      this.modified = true;
      this.z = z;
   }

   public float getPitch() {
      return this.pitch;
   }

   public float getYaw() {
      return this.yaw;
   }

   public double getZ() {
      return this.z;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public void setPitch(float pitch) {
      this.modified = true;
      this.pitch = pitch;
   }

   public void setYaw(float yaw) {
      this.modified = true;
      this.yaw = yaw;
   }

   public boolean isModified() {
      return this.modified;
   }

   public double getOriginalZ() {
      return this.originalZ;
   }

   public void setRotation(float[] angles) {
      if (angles != null && angles.length == 2) {
         this.setYaw(angles[0]);
         this.setPitch(angles[1]);
      }
   }
}

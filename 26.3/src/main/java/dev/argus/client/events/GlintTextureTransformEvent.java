package dev.argus.client.events;

public final class GlintTextureTransformEvent extends CancellableEvent {
   private static final GlintTextureTransformEvent INSTANCE = new GlintTextureTransformEvent();
   private float scale = 1.0F;

   public void setScale(float scale) {
      this.scale = scale;
   }

   public static GlintTextureTransformEvent resetAndGet() {
      INSTANCE.setScale(1.0F);
      return INSTANCE;
   }

   public float getScale() {
      return this.scale;
   }
}

package dev.argus.client.events;

public final class StepHeightEvent {
   private final EventStage stage;
   private float height;
   private boolean modified;

   public EventStage getStage() {
      return this.stage;
   }

   public StepHeightEvent(EventStage stage, float height) {
      this.stage = stage;
      this.height = height;
   }

   public float getHeight() {
      return this.height;
   }

   public boolean isModified() {
      return this.modified;
   }

   public void setHeight(float height) {
      if (this.height != height) {
         this.height = height;
         this.modified = true;
      }
   }
}

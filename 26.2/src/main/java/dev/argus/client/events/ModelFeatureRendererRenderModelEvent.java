package dev.argus.client.events;

import net.minecraft.client.model.EntityModel;

public final class ModelFeatureRendererRenderModelEvent extends CancellableEvent {
   private EntityModel model;
   private EventStage stage;

   public EventStage getStage() {
      return this.stage;
   }

   public EntityModel getModel() {
      return this.model;
   }

   public ModelFeatureRendererRenderModelEvent(EntityModel model, EventStage stage) {
      this.model = model;
      this.stage = stage;
   }

   public void setStage(EventStage stage) {
      this.stage = stage;
   }

   public void setModel(EntityModel model) {
      this.model = model;
   }
}

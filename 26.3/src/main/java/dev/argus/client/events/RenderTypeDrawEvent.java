package dev.argus.client.events;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public final class RenderTypeDrawEvent extends CancellableEvent {
   private final EventStage stage;
   private GpuTextureView override;

   public EventStage getStage() {
      return this.stage;
   }

   public void setOverride(GpuTextureView override) {
      this.override = override;
   }

   public RenderTypeDrawEvent(EventStage stage) {
      this.stage = stage;
   }

   public GpuTextureView getOverride() {
      return this.override;
   }
}

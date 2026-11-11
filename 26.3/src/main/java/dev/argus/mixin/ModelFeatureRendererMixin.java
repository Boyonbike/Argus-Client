package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.ModelFeatureRendererRenderModelEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
   @Inject(method = "prepareModel", at = @At("HEAD"))
   private void postBeforeEntityModelRender(ModelFeatureRenderer.Submit<?> submission, CallbackInfo callback) {
      post(submission, EventStage.Pre);
   }

   @Inject(method = "prepareModel", at = @At("RETURN"))
   private void postAfterEntityModelRender(ModelFeatureRenderer.Submit<?> submission, CallbackInfo callback) {
      post(submission, EventStage.Post);
   }

   private static void post(ModelFeatureRenderer.Submit<?> submission, EventStage stage) {
      if (submission.model() instanceof EntityModel entityModel) {
         ClientAccess.eventBus.post(new ModelFeatureRendererRenderModelEvent(entityModel, stage));
      }
   }
}

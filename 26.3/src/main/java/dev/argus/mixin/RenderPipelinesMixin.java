package dev.argus.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.argus.client.render.effect.EffectPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Argus's custom pipelines to the game's pipeline compile list. */
@Mixin(RenderPipelines.class)
public class RenderPipelinesMixin {
   @Inject(method = "requiredPipelines", at = @At("RETURN"), cancellable = true)
   private static void argusclient$addCustomPipelines(CallbackInfoReturnable<List<RenderPipeline>> callback) {
      List<RenderPipeline> pipelines = new ArrayList<>(callback.getReturnValue());
      for (RenderPipeline pipeline : EffectPipeline.registered()) {
         if (!pipelines.contains(pipeline)) {
            pipelines.add(pipeline);
         }
      }
      callback.setReturnValue(pipelines);
   }
}

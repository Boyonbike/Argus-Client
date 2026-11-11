package dev.argus.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Compiles and caches Argus's custom RenderPipelines off the render thread. */
public final class CompiledPipelines {
   private static final Map<RenderPipeline, CompletableFuture<CompiledRenderPipeline>> CACHE = new HashMap<>();

   public static CompiledRenderPipeline get(RenderPipeline pipeline) {
      CompletableFuture<CompiledRenderPipeline> future = CACHE.computeIfAbsent(pipeline, key -> {
         ResourceShaderSource source = new ResourceShaderSource(Minecraft.getInstance().getResourceManager());
         return RenderSystem.getDevice()
            .compilePipeline(key, source, net.minecraft.util.Util.backgroundExecutor())
            .thenApplyAsync(
               pending -> pending.finishCompile(),
               command -> Minecraft.getInstance().execute(command));
      });
      if (!future.isDone()) {
         return null;
      }
      try {
         return future.getNow(null);
      } catch (RuntimeException failure) {
         return null;
      }
   }

   private CompiledPipelines() {
   }
}

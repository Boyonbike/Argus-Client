package dev.argus.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Compiles and caches Argus's custom RenderPipelines. Minecraft 26.3 made the
 * device pipeline compiler asynchronous, so each pipeline is compiled once and
 * reused through its {@link CompiledRenderPipeline}.
 */
public final class CompiledPipelines {
   private static final Map<RenderPipeline, CompiledRenderPipeline> CACHE = new HashMap<>();

   public static CompiledRenderPipeline get(RenderPipeline pipeline) {
      return CACHE.computeIfAbsent(pipeline, key -> {
         try (ResourceShaderSource source = new ResourceShaderSource(Minecraft.getInstance().getResourceManager())) {
            return RenderSystem.getDevice()
               .compilePipeline(key, source, Runnable::run)
               .join()
               .finishCompile();
         }
      });
   }

   private CompiledPipelines() {
   }
}

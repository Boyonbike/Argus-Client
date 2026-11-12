package dev.argus.client.render;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Derives "through walls" copies of render pipelines: same shaders, bindings and
 * blending, but no depth test, no depth write and no culling. 26.3 has no global
 * depth/cull state to toggle, so RenderStateManager.THROUGH_WALLS is applied by
 * drawing with one of these copies instead.
 */
public final class ThroughWallsPipelines {
   private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
   private static final Map<RenderPipeline, RenderPipeline> CACHE = new IdentityHashMap<>();

   private ThroughWallsPipelines() {
   }

   /** The through-walls copy of a pipeline, or the pipeline itself when it does not depth test. */
   public static synchronized RenderPipeline of(RenderPipeline pipeline) {
      DepthStencilState depth = pipeline.getDepthStencilState();
      if (depth == null || depth.depthTest() == CompareOp.ALWAYS_PASS && !depth.writeDepth() && !pipeline.isCull()) {
         return pipeline;
      }
      return CACHE.computeIfAbsent(pipeline, ThroughWallsPipelines::derive);
   }

   private static RenderPipeline derive(RenderPipeline pipeline) {
      List<ColorTargetState> colorTargets = pipeline.getColorTargetStates();
      ColorTargetState[] colorTargetArray = new ColorTargetState[8];
      for (int index = 0; index < colorTargets.size(); index++) {
         colorTargetArray[index] = colorTargets.get(index);
      }
      List<VertexFormat> vertexFormats = pipeline.getVertexFormatBindings();
      VertexFormat[] vertexFormatArray = new VertexFormat[16];
      for (int index = 0; index < vertexFormats.size(); index++) {
         vertexFormatArray[index] = vertexFormats.get(index);
      }
      RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
         pipeline.getShaders(),
         Optional.of(pipeline.getShaderDefines()),
         Optional.of(pipeline.getBindGroupLayouts()),
         colorTargetArray,
         colorTargets.size(),
         Optional.of(NO_DEPTH),
         Optional.of(pipeline.getPolygonMode()),
         Optional.of(false),
         vertexFormatArray,
         Optional.of(pipeline.getPrimitiveTopology()),
         pipeline.pushConstantSize()
      );
      Identifier location = pipeline.getLocation();
      return RenderPipeline.builder(snippet)
         .withLocation(Identifier.fromNamespaceAndPath("argusclient", "through_walls/" + location.getNamespace() + "/" + location.getPath()))
         .build();
   }
}

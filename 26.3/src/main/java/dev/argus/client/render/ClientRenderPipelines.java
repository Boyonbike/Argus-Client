package dev.argus.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

public final class ClientRenderPipelines {
   private static final DepthStencilState DEPTH_WRITE = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
   private static final DepthStencilState DEPTH_NO_WRITE = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);
   private static final DepthStencilState DEPTH_NONE = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
   private static final DepthStencilState DEPTH_NONE_WRITE = new DepthStencilState(CompareOp.ALWAYS_PASS, true);
   private static final DepthStencilState DEPTH_EQUAL_NO_WRITE = new DepthStencilState(CompareOp.EQUAL, false);
   private static final ColorTargetState NO_BLEND = ColorTargetState.DEFAULT;
   private static ColorTargetState blend(BlendFunction function) { return new ColorTargetState(function); }
   public static final Snippet POSITION_TEX_COLOR_SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
      .withVertexShader("core/argusclient_pos_tex_color")
      .withFragmentShader("core/argusclient_pos_tex_color")
      .buildSnippet();
   public static final Snippet GUI_LINES_SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH).withPrimitiveTopology(PrimitiveTopology.LINES)
      .buildSnippet();
   public static final Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
      .withVertexShader("core/argusclient_lines_smooth")
      .withFragmentShader("core/argusclient_lines_smooth")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH).withPrimitiveTopology(PrimitiveTopology.LINES)
      .withCull(false)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLES_SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLE_STRIP_SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
      .buildSnippet();
   public static final RenderPipeline POSITION_TEX_COLOR = RenderPipeline.builder(POSITION_TEX_COLOR_SNIPPET)
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withDepthStencilState(DEPTH_WRITE)
      .withLocation("pipeline/argusclient_pos_tex_color")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .build();
   public static final RenderPipeline ENTITY_SOLID_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
      .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withLocation("pipeline/entity_solid_transparent")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
      .build();
   public static final RenderPipeline ENTITY_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
      .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withLocation("pipeline/entity_cutout_no_cull_transparent")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
      .withCull(false)
      .build();
   public static final RenderPipeline ARMOR_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
      .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withLocation("pipeline/armor_cutout_no_cull_transparent")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
      .withCull(false)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .build();
   public static final RenderPipeline GUI_LINES = RenderPipeline.builder(GUI_LINES_SNIPPET).withLocation("pipeline/gui_lines").build();
   public static final RenderPipeline GUI_TRIANGLES = RenderPipeline.builder(GUI_TRIANGLES_SNIPPET).withLocation("pipeline/gui_trigs").build();
   public static final RenderPipeline GUI_TRIANGLE_STRIP = RenderPipeline.builder(GUI_TRIANGLE_STRIP_SNIPPET).withLocation("pipeline/gui_trig_strip").build();
   public static final RenderPipeline WORLD_FILL = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
      .withLocation("pipeline/world_fill_pipeline")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE_WRITE)
      .build();
   public static final RenderPipeline WORLD_FILL_LIGHTNING = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
      .withLocation("pipeline/world_fill_light_pipeline")
      .withColorTargetState(blend(BlendFunction.LIGHTNING))
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE_WRITE)
      .build();
   public static final RenderPipeline WORLD_FILL_DEPTH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
      .withLocation("pipeline/world_fill_depth_pipeline")
      .withDepthStencilState(DEPTH_WRITE)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(DEPTH_WRITE)
      .build();
   public static final RenderPipeline WORLD_LINES = RenderPipeline.builder(WORLD_LINES_SNIPPET)
      .withLocation("pipeline/world_lines_pipeline")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE_WRITE)
      .build();
   public static final RenderPipeline WORLD_LINES_LIGHTNING = RenderPipeline.builder(WORLD_LINES_SNIPPET)
      .withLocation("pipeline/world_lines_light_pipeline")
      .withColorTargetState(blend(BlendFunction.LIGHTNING))
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE_WRITE)
      .build();
   public static final RenderPipeline WORLD_LINES_DEPTH = RenderPipeline.builder(WORLD_LINES_SNIPPET)
      .withLocation("pipeline/world_lines_pipeline")
      .withDepthStencilState(DEPTH_WRITE)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(DEPTH_WRITE)
      .build();
   public static final RenderPipeline WORLD_LINE_STRIP = RenderPipeline.builder(WORLD_LINES_SNIPPET)
      .withVertexShader("core/position_color")
      .withFragmentShader("core/position_color")
      .withLocation("pipeline/world_lines_pipeline")
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.DEBUG_LINE_STRIP)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE_WRITE)
      .build();
   public static final RenderPipeline ENTITY_SHADER = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
      .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withLocation("pipeline/entity_shader_pipeline")
      .withVertexShader("core/position")
      .withFragmentShader("core/position")
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(DEPTH_NONE)
      .withCull(false)
      .build();
   public static final RenderPipeline BLIT_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withDepthStencilState(DEPTH_NONE)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .build();
   public static final RenderPipeline BLIT_LIGHTNING = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withDepthStencilState(DEPTH_NONE)
      .withColorTargetState(blend(BlendFunction.LIGHTNING))
      .build();
   public static final RenderPipeline BLIT_PREMULTIPLIED_ALPHA = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withDepthStencilState(DEPTH_NONE)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
      .build();
   public static final RenderPipeline BLIT_NO_BLEND = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withDepthStencilState(DEPTH_NONE)
      .withColorTargetState(NO_BLEND)
      .build();
   // Mirrors vanilla RenderPipelines.LIGHTMAP: a vertex-less fullscreen triangle drawn by Lightmap.render.
   public static final RenderPipeline AMBIENCE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
      .withLocation("pipeline/argusclient_ambience")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/argusclient_lightmap")
      .withBindGroupLayout(BindGroupLayouts.LIGHTMAP_INFO)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .build();
   // Mirrors vanilla RenderPipelines.LIGHTMAP: a vertex-less fullscreen triangle drawn by Lightmap.render.
   public static final RenderPipeline OCCLUSION = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
      .withLocation("pipeline/argusclient_occlusion")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/argusclient_occlusion")
      .withBindGroupLayout(BindGroupLayouts.LIGHTMAP_INFO)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .build();
   public static final RenderPipeline SHINE_GLINT = RenderPipeline.builder()
         .withBindGroupLayout(BindGroupLayouts.PROJECTION)
         .withBindGroupLayout(BindGroupLayouts.FOG)
         .withBindGroupLayout(BindGroupLayouts.GLOBALS)
      .withLocation("pipeline/argusclient_shine_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withCull(false)
      .withDepthStencilState(DEPTH_NONE)
      .withColorTargetState(blend(BlendFunction.GLINT))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .build();
   public static final RenderPipeline NAMETAG_ITEM = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
      .withLocation("pipeline/argusclient_nametag_item")
      .withVertexShader("core/rendertype_item_entity_translucent_cull")
      .withFragmentShader("core/rendertype_item_entity_translucent_cull")
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, 1.0F, -500000.0F))
      .build();
   public static final RenderPipeline NAMETAG_ITEM_ENTITY = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
      .withLocation("pipeline/argusclient_nametag_item_entity")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
      .withColorTargetState(blend(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, 1.0F, -500000.0F))
      .build();
   public static final RenderPipeline NAMETAG_ITEM_GLINT = RenderPipeline.builder()
         .withBindGroupLayout(BindGroupLayouts.PROJECTION)
         .withBindGroupLayout(BindGroupLayouts.FOG)
         .withBindGroupLayout(BindGroupLayouts.GLOBALS)
      .withLocation("pipeline/argusclient_nametag_item_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withDepthStencilState(DEPTH_NO_WRITE)
      .withCull(false)
      .withColorTargetState(blend(BlendFunction.GLINT))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false, 1.0F, -500000.0F))
      .build();

   public static boolean isGlint(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.GLINT || pipeline == SHINE_GLINT || pipeline == NAMETAG_ITEM_GLINT;
   }

   public static RenderPipeline getNameTagItemPipeline(RenderPipeline pipeline) {
      if (pipeline == RenderPipelines.ITEM_TRANSLUCENT) {
         return NAMETAG_ITEM;
      } else if (pipeline == RenderPipelines.GLINT) {
         return NAMETAG_ITEM_GLINT;
      } else {
         return pipeline != RenderPipelines.ENTITY_SOLID
               && pipeline != RenderPipelines.ENTITY_CUTOUT
               && pipeline != RenderPipelines.ENTITY_CUTOUT
               && pipeline != RenderPipelines.ENTITY_CUTOUT_Z_OFFSET
               && pipeline != RenderPipelines.ENTITY_CUTOUT
               && pipeline != RenderPipelines.ENTITY_TRANSLUCENT
            ? null
            : NAMETAG_ITEM_ENTITY;
      }
   }
}

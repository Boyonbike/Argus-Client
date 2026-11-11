package dev.argus.client.render.effect;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Matrix4f;

/** std140 uniform block and texture bindings for one effect shader. */
public final class EffectUniforms {
   public static final String BLOCK_NAME = "EffectUniforms";
   private static final Pattern BLOCK = Pattern.compile(
      "layout\\s*\\(\\s*std140\\s*\\)\\s*uniform\\s+EffectUniforms\\s*\\{(.*?)\\}\\s*;",
      Pattern.DOTALL);
   private static final Pattern FIELD = Pattern.compile(
      "\\b(float|int|bool|vec2|vec3|vec4|mat4)\\s+([A-Za-z_]\\w*)\\s*;");

   private final List<Field> fields;
   private final Map<String, Field> byName;
   private final ByteBuffer data;
   private final Map<String, TextureBinding> textures = new HashMap<>();

   private EffectUniforms(List<Field> fields, Map<String, Field> byName, ByteBuffer data) {
      this.fields = fields;
      this.byName = byName;
      this.data = data;
   }

   public static EffectUniforms fromSource(String fragmentSource) {
      Matcher block = BLOCK.matcher(fragmentSource);
      if (!block.find()) {
         return new EffectUniforms(List.of(), Map.of(), ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()));
      }
      List<Field> fields = new ArrayList<>();
      Map<String, Field> byName = new HashMap<>();
      int offset = 0;
      Matcher fieldMatcher = FIELD.matcher(block.group(1));
      while (fieldMatcher.find()) {
         String type = fieldMatcher.group(1);
         String name = fieldMatcher.group(2);
         int alignment = alignment(type);
         int size = size(type);
         offset = align(offset, alignment);
         Field field = new Field(name, type, offset);
         fields.add(field);
         byName.put(name, field);
         offset += size;
      }
      int total = align(offset, 16);
      ByteBuffer data = ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder());
      return new EffectUniforms(fields, byName, data);
   }

   public boolean isEmpty() {
      return this.fields.isEmpty();
   }

   public void set(String name, float value) {
      Field field = this.byName.get(name);
      if (field != null) {
         this.data.putFloat(field.offset, value);
      }
   }

   public void set(String name, int value) {
      Field field = this.byName.get(name);
      if (field != null) {
         this.data.putInt(field.offset, value);
      }
   }

   public void set(String name, boolean value) {
      this.set(name, value ? 1 : 0);
   }

   public void set(String name, float x, float y) {
      Field field = this.byName.get(name);
      if (field != null) {
         this.data.putFloat(field.offset, x);
         this.data.putFloat(field.offset + 4, y);
      }
   }

   public void set(String name, float x, float y, float z, float w) {
      Field field = this.byName.get(name);
      if (field != null) {
         this.data.putFloat(field.offset, x);
         this.data.putFloat(field.offset + 4, y);
         this.data.putFloat(field.offset + 8, z);
         this.data.putFloat(field.offset + 12, w);
      }
   }

   public void set(String name, Color color) {
      this.set(name, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
   }

   public void set(String name, Matrix4f matrix) {
      Field field = this.byName.get(name);
      if (field != null) {
         matrix.get(field.offset / 4, this.data);
      }
   }

   public void setTexture(String name, GpuTextureView texture, GpuSampler sampler) {
      this.textures.put(name, new TextureBinding(texture, sampler));
   }

   public void bind(RenderPass pass, CommandEncoder encoder) {
      if (!this.fields.isEmpty()) {
         GpuBufferSlice slice = encoder.transientMemory().uploadStaging(
            this.data.duplicate(), 16L, GpuBuffer.USAGE_UNIFORM);
         pass.setUniform(BLOCK_NAME, slice);
      }
      for (Map.Entry<String, TextureBinding> entry : this.textures.entrySet()) {
         TextureBinding binding = entry.getValue();
         pass.setUniform(entry.getKey(), binding.texture, binding.sampler);
      }
   }

   private static int alignment(String type) {
      return switch (type) {
         case "vec2" -> 8;
         case "vec3", "vec4", "mat4" -> 16;
         default -> 4;
      };
   }

   private static int size(String type) {
      return switch (type) {
         case "vec2" -> 8;
         case "vec3" -> 12;
         case "vec4" -> 16;
         case "mat4" -> 64;
         default -> 4;
      };
   }

   private static int align(int value, int alignment) {
      return (value + alignment - 1) / alignment * alignment;
   }

   private record Field(String name, String type, int offset) {
   }

   private record TextureBinding(GpuTextureView texture, GpuSampler sampler) {
   }
}

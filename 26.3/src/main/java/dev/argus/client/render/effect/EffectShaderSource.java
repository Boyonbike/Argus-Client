package dev.argus.client.render.effect;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;

/** Shader sources for Argus's RenderPearl effect pipelines. */
public final class EffectShaderSource implements ShaderSource {
   private static final String ROOT = "/assets/argusclient/shaders/";
   private final Map<Identifier, String> sources;
   private final String fragmentSource;
   private final String vertexSource;

   private EffectShaderSource(Map<Identifier, String> sources, String vertexSource, String fragmentSource) {
      this.sources = sources;
      this.vertexSource = vertexSource;
      this.fragmentSource = fragmentSource;
   }

   public static EffectShaderSource fromResources(String vertexResource, String fragmentResource) {
      String vertexName = normalize(vertexResource);
      String fragmentName = normalize(fragmentResource);
      String vertex = toStd140(readResource(vertexName));
      String fragment = toStd140(readResource(fragmentName));
      Map<Identifier, String> map = new HashMap<>();
      map.put(pipelineShaderId(vertexName), vertex);
      map.put(pipelineShaderId(fragmentName), fragment);
      return new EffectShaderSource(map, vertex, fragment);
   }

   public static EffectShaderSource fromSources(String name, String vertex, String fragment) {
      vertex = toStd140(vertex);
      fragment = toStd140(fragment);
      String utility = readResource("util/util.frag");
      if (fragment.contains("u_Overlay")) {
         utility = utility + "\n" + readResource("util/blur_util.frag");
      }
      fragment = insertBeforeMain(fragment, utility);
      Map<Identifier, String> map = new HashMap<>();
      map.put(shaderId(name + ".vsh"), vertex);
      map.put(shaderId(name + ".fsh"), fragment);
      return new EffectShaderSource(map, vertex, fragment);
   }

   /** Converts loose non-sampler uniforms (e.g. a user sky shader) to a std140 block. */
   public static String toStd140(String source) {
      if (source.contains("uniform EffectUniforms")) {
         return source;
      }
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
         "^\\s*uniform\\s+(?!sampler)([A-Za-z0-9_]+)\\s+([A-Za-z_]\\w*)\\s*;\\s*(//.*)?$");
      java.util.List<String> kept = new java.util.ArrayList<>();
      java.util.List<String> declarations = new java.util.ArrayList<>();
      for (String line : source.split("\n", -1)) {
         java.util.regex.Matcher matcher = pattern.matcher(line);
         if (matcher.matches()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            if (!name.equals("u_Size")) {
               declarations.add("    " + type + " " + name + ";");
            }
         } else {
            kept.add(line);
         }
      }
      if (declarations.isEmpty()) {
         return source;
      }
      declarations.add(0, "    vec2 u_Size;");
      int headerEnd = 0;
      for (int i = 0; i < kept.size(); i++) {
         String trimmed = kept.get(i).trim();
         if (trimmed.startsWith("#version") || trimmed.startsWith("#extension")
            || trimmed.startsWith("#moj_import") || trimmed.startsWith("//") || trimmed.isEmpty()) {
            headerEnd = i + 1;
         } else {
            break;
         }
      }
      java.util.List<String> block = new java.util.ArrayList<>();
      block.add("layout(std140) uniform EffectUniforms {");
      block.addAll(declarations);
      block.add("};");
      block.add("");
      kept.addAll(headerEnd, block);
      return String.join("\n", kept);
   }

   public static Identifier shaderId(String resource) {
      return Identifier.fromNamespaceAndPath("argusclient", resource);
   }

   /** Pipeline shader ids have no file extension; RenderPearl adds .vsh/.fsh. */
   public static Identifier pipelineShaderId(String resource) {
      String name = normalize(resource);
      if (name.endsWith(".vsh") || name.endsWith(".fsh")) {
         name = name.substring(0, name.length() - 4);
      }
      return Identifier.fromNamespaceAndPath("argusclient", name);
   }

   private static String normalize(String resource) {
      if (resource.endsWith(".vert")) {
         return resource.substring(0, resource.length() - 5) + ".vsh";
      }
      if (resource.endsWith(".frag")) {
         return resource.substring(0, resource.length() - 5) + ".fsh";
      }
      return resource;
   }

   public String vertexSource() {
      return this.vertexSource;
   }

   public String fragmentSource() {
      return this.fragmentSource;
   }

   @Override
   public String getShader(Identifier id, ShaderType type) {
      String source = this.sources.get(id);
      if (source == null) {
         throw new IllegalStateException("No shader source registered for " + id + " (" + type + ")");
      }
      return source;
   }

   @Override
   public CachedIncludeSource getInclude(Identifier id) {
      String path = id.getPath();
      try (InputStream stream = EffectShaderSource.class.getResourceAsStream(ROOT + path)) {
         if (stream == null) {
            return CachedIncludeSource.createError("Missing shader include " + id);
         }
         return CachedIncludeSource.create(id, IOUtils.toString(stream, StandardCharsets.UTF_8));
      } catch (IOException failure) {
         return CachedIncludeSource.createError(failure.toString());
      }
   }

   @Override
   public void close() {
   }

   private static String insertBeforeMain(String source, String utility) {
      int main = source.indexOf("void main(");
      if (main < 0) {
         main = source.indexOf("void main (");
      }
      if (main < 0) {
         return source;
      }
      return source.substring(0, main) + utility + "\n" + source.substring(main);
   }

   private static String readResource(String resource) {
      try (InputStream stream = EffectShaderSource.class.getResourceAsStream(ROOT + resource)) {
         if (stream == null) {
            throw new IllegalStateException("Missing shader resource " + resource);
         }
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         throw new IllegalStateException("Could not read shader resource " + resource, failure);
      }
   }
}

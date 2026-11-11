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
      String vertex = readResource(vertexResource);
      String fragment = readResource(fragmentResource);
      String utility = readResource("util/util.frag");
      if (fragment.contains("u_Overlay")) {
         utility = utility + "\n" + readResource("util/blur_util.frag");
      }
      fragment = insertBeforeMain(fragment, utility);
      Map<Identifier, String> map = new HashMap<>();
      map.put(shaderId(vertexResource), vertex);
      map.put(shaderId(fragmentResource), fragment);
      return new EffectShaderSource(map, vertex, fragment);
   }

   public static EffectShaderSource fromSources(String name, String vertex, String fragment) {
      Map<Identifier, String> map = new HashMap<>();
      map.put(shaderId(name + ".vsh"), vertex);
      map.put(shaderId(name + ".fsh"), fragment);
      return new EffectShaderSource(map, vertex, fragment);
   }

   public static Identifier shaderId(String resource) {
      return Identifier.fromNamespaceAndPath("argusclient", resource);
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

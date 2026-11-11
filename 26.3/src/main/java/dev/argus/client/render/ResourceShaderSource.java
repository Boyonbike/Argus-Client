package dev.argus.client.render;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Supplies RenderPearl shader sources from the client resource packs. */
public final class ResourceShaderSource implements ShaderSource {
   private final ResourceManager resources;

   public ResourceShaderSource(ResourceManager resources) {
      this.resources = resources;
   }

   @Override
   public String getShader(Identifier id, ShaderType type) {
      Identifier file = type.idConverter().idToFile(id);
      Resource resource = this.resources.getResource(file)
         .orElseThrow(() -> new IllegalStateException("Missing shader source " + file));
      try {
         return resource.readAllAsString();
      } catch (IOException failure) {
         throw new IllegalStateException("Could not read shader source " + file, failure);
      }
   }

   @Override
   public CachedIncludeSource getInclude(Identifier id) {
      Identifier file = ShaderManager.SHADER_INCLUDE_CONVERTER.idToFile(id);
      return this.resources.getResource(file).map(resource -> {
         try {
            return CachedIncludeSource.create(id, resource.readAllAsString());
         } catch (IOException failure) {
            return CachedIncludeSource.createError(failure.toString());
         }
      }).orElseGet(() -> CachedIncludeSource.createError("Missing shader include " + file));
   }

   @Override
   public void close() {
   }
}

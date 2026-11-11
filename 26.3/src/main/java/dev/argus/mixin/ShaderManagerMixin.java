package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ArgusClient;
import dev.argus.client.render.BuiltinResourcePack;
import java.io.InputStream;
import java.util.Map;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
   @ModifyExpressionValue(
      method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/client/renderer/ShaderManager$Configs;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;"
      )
   )
   private Map includeBuiltinShaderResources(Map resources) {
      if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("argusclient_lightmap"))) {
         addBuiltinShaderFiles(resources, "shaders/core/argusclient_lightmap");
      }

      if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("argusclient_occlusion"))) {
         addBuiltinShaderFiles(resources, "shaders/core/argusclient_occlusion");
      }

      if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("argusclient_pos_tex_color"))) {
         addBuiltinShaderFiles(resources, "shaders/core/argusclient_pos_tex_color");
      }

      if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("argusclient_lines_smooth"))) {
         addBuiltinShaderFiles(resources, "shaders/core/argusclient_lines_smooth");
      }

      return resources;
   }

   @Unique
   private static void addBuiltinShaderFiles(Map resources, String shaderBasePath) {
      String[] extensions = new String[]{"fsh", "vsh", "json"};

      for (String extension : extensions) {
         InputStream resourceStream = ArgusClient.class.getResourceAsStream("/assets/minecraft/%s.%s".formatted(shaderBasePath, extension));
         if (resourceStream != null) {
            try {
               resourceStream.close();
            } catch (Exception var9) {
            }

            putBuiltinShaderResource(resources, "%s.%s".formatted(shaderBasePath, extension));
         }
      }
   }

   @Unique
   private static void putBuiltinShaderResource(Map resources, String shaderPath) {
      Resource resource = new Resource(new BuiltinResourcePack(), () -> ArgusClient.class.getResourceAsStream("/assets/minecraft/" + shaderPath));
      resources.put(Identifier.fromNamespaceAndPath("minecraft", shaderPath), resource);
   }
}

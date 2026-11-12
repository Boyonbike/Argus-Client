package dev.argus.client.render.effect;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-backend shader rules that Vulkan tolerates but OpenGL enforces (or that 26.3 changed).
 * Each one broke the 26.3 port before it was caught at runtime.
 */
class ShaderInterfaceRulesTest {
   /** 26.3 dynamictransforms.glsl order (TextureMat moved before ColorModulator). */
   private static final List<String> DYNAMIC_TRANSFORMS = List.of("ModelViewMat", "TextureMat", "ColorModulator", "ModelOffset");
   private static final List<String> LIGHTMAP_INFO_PREFIX = List.of(
      "SkyFactor", "BlockFactor", "NightVisionFactor", "DarknessScale", "BossOverlayWorldDarkeningFactor",
      "BrightnessFactor", "BlockLightTint", "SkyLightColor", "AmbientColor", "NightVisionColor");
   private static final Pattern MEMBER = Pattern.compile("^\\s*\\w+\\s+(\\w+)\\s*;", Pattern.MULTILINE);
   private static final Pattern LOOSE_IO = Pattern.compile("^\\s*(flat\\s+)?(in|out)\\s+\\w+\\s+\\w+\\s*;", Pattern.MULTILINE);

   @Test
   void effectVertexShaderDeclaresNoUniformBlock() throws IOException {
      // OpenGL fails to link when stages declare the same uniform block with different members.
      assertFalse(read("/assets/argusclient/shaders/base.vsh").contains("uniform EffectUniforms"));
   }

   @Test
   void dynamicTransformsUse26Point3Order() throws Exception {
      for (Path shader : shaders()) {
         List<String> members = block(Files.readString(shader), "DynamicTransforms");
         if (members != null) {
            assertEquals(DYNAMIC_TRANSFORMS, members, shader.toString());
         }
      }
   }

   @Test
   void lightmapInfoStartsWithThe26Point3Layout() throws Exception {
      for (Path shader : shaders()) {
         List<String> members = block(Files.readString(shader), "LightmapInfo");
         if (members != null) {
            assertEquals(LIGHTMAP_INFO_PREFIX, members.subList(0, Math.min(members.size(), LIGHTMAP_INFO_PREFIX.size())), shader.toString());
         }
      }
   }

   @Test
   void stageInputsAndOutputsHaveExplicitLocations() throws Exception {
      for (Path shader : shaders()) {
         String name = shader.getFileName().toString();
         if (name.endsWith(".glsl") || shader.toString().contains("/util/") || shader.toString().contains("/custom/")) {
            continue; // include snippets, not stages
         }
         String source = Files.readString(shader);
         assertTrue(source.contains("GL_ARB_separate_shader_objects"), shader + " lacks the 26.3 shader header");
         assertFalse(LOOSE_IO.matcher(source).find(), shader + " has an in/out without layout(location)");
      }
   }

   private static List<String> block(String source, String name) {
      Matcher start = Pattern.compile("uniform\\s+" + name + "\\s*\\{([^}]*)\\}").matcher(source);
      if (!start.find()) {
         return null;
      }
      List<String> members = new ArrayList<>();
      Matcher member = MEMBER.matcher(start.group(1));
      while (member.find()) {
         members.add(member.group(1));
      }
      return members;
   }

   private static List<Path> shaders() throws IOException, URISyntaxException {
      Path assets = Path.of(ShaderInterfaceRulesTest.class.getResource("/assets").toURI());
      try (Stream<Path> files = Files.walk(assets)) {
         return files.filter(path -> path.toString().matches(".*\\.(vsh|fsh|vert|frag|glsl)$")).toList();
      }
   }

   private static String read(String resource) throws IOException {
      try (InputStream in = ShaderInterfaceRulesTest.class.getResourceAsStream(resource)) {
         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}

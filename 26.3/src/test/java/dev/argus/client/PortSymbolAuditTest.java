package dev.argus.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards against leaving a removed 26.2 symbol in the production source. */
class PortSymbolAuditTest {
   private static final List<String> BANNED = List.of(
      "AxeItem", "BedItem", "SignItem", "EnderMan",
      "ServerboundSwingPacket", "net.minecraft.client.renderer.DynamicUniforms",
      "com.mojang.blaze3d.GpuFormat", "com.mojang.blaze3d.IndexType",
      "com.mojang.blaze3d.PrimitiveTopology");

   @Test
   void mainSourcesUseOnly26_3Symbols() throws Exception {
      try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            String text = Files.readString(file);
            for (String token : BANNED) {
               assertFalse(text.contains(token), file + " still uses " + token);
            }
         }
      }
   }
}

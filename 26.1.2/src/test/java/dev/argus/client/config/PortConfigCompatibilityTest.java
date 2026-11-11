package dev.argus.client.config;

import dev.argus.client.config.importing.ExternalConfigReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** A 1.21.11 Argus JSON config must still load with the same modules, toggles and settings. */
class PortConfigCompatibilityTest {
   @TempDir
   Path temp;

   @Test void loadsArgusOneTwentyOneElevenConfigFixture() throws Exception {
      Path file = temp.resolve("config.json");
      Files.writeString(file, """
         {"client":"argusclient","category":"all",
          "Sprint":{"enabled":true,"settings":{"Mode":"Rage"}},
          "ESP":{"enabled":false,"settings":{"Mode":"Meteor"}}}
         """);

      var source = ExternalConfigReader.read(file);

      assertEquals(2, source.modules().size());
      assertTrue(source.modules().get(0).enabled());
      assertEquals("Rage", source.modules().get(0).settings().get("Mode").getAsString());
      assertFalse(source.modules().get(1).enabled());
   }
}

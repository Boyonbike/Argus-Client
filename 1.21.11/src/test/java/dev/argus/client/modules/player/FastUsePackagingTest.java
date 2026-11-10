package dev.argus.client.modules.player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FastUsePackagingTest {
    @Test void fastUseMixinIsPackagedAndRegistered() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            assertNotNull(jar.getEntry("dev/argus/mixin/MinecraftFastUseMixin.class"));
            String mixins = new String(
                jar.getInputStream(jar.getEntry("argusclient.mixins.json")).readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(mixins.contains("\"MinecraftFastUseMixin\""));
        }
    }
}

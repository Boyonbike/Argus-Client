package dev.argus.client.gui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClientNamespaceTest {
    @Test
    void baseUsesOneConsistentArgusClientNamespace() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of("libs/client-base.jar").toFile())) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                assertFalse(name.contains("randomstuff") || name.contains("eggclient") || name.contains("shuclient"),
                        "Old namespace in archive path: " + entry.getName());
                if (name.endsWith(".class") || name.endsWith(".json") || name.endsWith(".ini")
                        || name.endsWith(".accesswidener")) {
                    String data = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1)
                            .toLowerCase(Locale.ROOT);
                    assertFalse(data.contains("randomstuff") || data.contains("eggclient") || data.contains("shuclient"),
                            "Old namespace in " + entry.getName());
                }
            }
            assertNotNull(jar.getEntry("dev/argus/client/ArgusClient.class"));
            assertNotNull(jar.getEntry("assets/argusclient/textures/argusclient.png"));
        }
    }
}

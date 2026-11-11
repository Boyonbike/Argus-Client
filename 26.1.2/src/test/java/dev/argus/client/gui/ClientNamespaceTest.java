package dev.argus.client.gui;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClientNamespaceTest {
    private static final Pattern LEGACY_BRANDING = Pattern.compile(
            "random[\\s_-]*stuff|(?:egg|shu|vortex)[\\s_-]*client|\\b(?:zio|mio)\\b|(?:zio|mio)client|spectrumdevs|cypphi|ᴍɪᴏ",
            Pattern.CASE_INSENSITIVE);

    @Test
    void bundlesFontLicenseOutsideTheMinecraftResourceNamespace() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            assertNotNull(jar.getEntry("licenses/OFL-Lexend-Deca.txt"));
            assertNull(jar.getEntry("assets/argusclient/font/OFL-Lexend-Deca.txt"));
        }
    }

    @Test
    void releaseUsesOneConsistentArgusClientNamespace() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                assertFalse(LEGACY_BRANDING.matcher(name).find(),
                        "Old namespace in archive path: " + entry.getName());
                if (name.endsWith(".class") && (entry.getName().startsWith("dev/argus/client/config/ClientDataDirectory")
                        || entry.getName().startsWith("dev/argus/client/config/LegacyConfigRenamer"))) continue;
                if (name.endsWith(".class") || name.endsWith(".json") || name.endsWith(".ini")
                        || name.endsWith(".accesswidener") || name.endsWith(".tsv") || name.endsWith(".mcmeta")) {
                    String data = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    assertFalse(LEGACY_BRANDING.matcher(data).find(), "Old branding in " + entry.getName());
                }
            }
            String metadata = new String(jar.getInputStream(jar.getEntry("fabric.mod.json")).readAllBytes(),
                    StandardCharsets.UTF_8);
            var mod = JsonParser.parseString(metadata).getAsJsonObject();
            assertEquals("Argus Client", mod.get("name").getAsString());
            assertEquals("argusclient", mod.get("id").getAsString());
            assertFalse(metadata.toLowerCase(Locale.ROOT).contains("discord.com/invite"), "Old project in fabric.mod.json");
            assertNotNull(jar.getEntry("dev/argus/client/ArgusClient.class"));
            assertNotNull(jar.getEntry("assets/argusclient/textures/argusclient.png"));
        }
    }
}

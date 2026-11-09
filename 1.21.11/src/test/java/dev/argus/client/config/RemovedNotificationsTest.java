package dev.argus.client.config;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Notification removal must include its registrations, sender and compatibility aliases. */
final class RemovedNotificationsTest {
    @Test void baseAndReleaseJarsContainNoNotificationImplementationOrReferences() throws Exception {
        List<Path> jars = new ArrayList<>(List.of(Path.of(System.getProperty("argus.release.jar"))));
        try (var releases = Files.newDirectoryStream(Path.of("."), "*.jar")) {
            releases.forEach(jars::add);
        }
        for (Path path : jars) {
            try (ZipFile jar = new ZipFile(path.toFile())) {
                for (var entry : Collections.list(jar.entries())) {
                    String name = entry.getName();
                    assertFalse(removed(name), path + " still contains " + name);
                    if (name.endsWith(".class") || name.endsWith(".ini") || name.endsWith(".json")) {
                        String content = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
                        assertFalse(removed(content), path + " still references removed notifications in " + name);
                    }
                }
            }
        }
    }

    @Test void unrelatedLegacyModuleNamesStillResolve() {
        assertEquals("Avoid", LegacyBranding.canonicalModuleName("AntiVoid"));
        assertEquals("AutoTemplate", LegacyBranding.canonicalModuleName("AUTOUPGRADE"));
        assertEquals("ChestStealer", LegacyBranding.canonicalModuleName("ChestStealer"));
        JsonObject config = new JsonObject();
        JsonObject original = new JsonObject();
        config.add("AntiVoid", original);
        assertSame(original, LegacyBranding.findModuleConfig(config, "Avoid"));
        assertNull(LegacyBranding.findModuleConfig(config, "Missing"));
    }

    @Test void editedStartupAndMigrationClassesHaveValidBytecode() throws Exception {
        try (ZipFile jar = new ZipFile(System.getProperty("argus.release.jar"))) {
            for (String name : List.of("ClientContext", "bootstrap/ClientInitializer", "managers/CommandRegistry",
                    "managers/ConfigurableRegistry", "managers/ModuleRegistry", "config/LegacyBranding")) {
                ClassNode type = new ClassNode();
                new ClassReader(jar.getInputStream(jar.getEntry("dev/argus/client/" + name + ".class"))).accept(type, 0);
                for (var method : type.methods) {
                    if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                        new Analyzer<>(new BasicVerifier()).analyze(type.name, method);
                }
            }
        }
    }

    @Test void unrelatedSettingMigrationsStillPreserveTheInput() {
        JsonObject input = new JsonObject();
        JsonObject settings = new JsonObject();
        settings.addProperty("BPT", 5);
        input.add("settings", settings);
        JsonObject migrated = LegacyBranding.migrateModule("AutoFarm", input).getAsJsonObject();
        assertEquals(5, migrated.getAsJsonObject("settings").get("Frequency").getAsInt());
        assertFalse(migrated.getAsJsonObject("settings").has("BPT"));
        assertTrue(settings.has("BPT"));
        assertFalse(settings.has("Frequency"));
        assertSame(input, LegacyBranding.migrateModule("ChestStealer", input));
    }

    /** These cases lost their old-branding rewrites; they must keep settings, not throw and abort config loading. */
    @Test void rebrandedModulesKeepTheirSettings() {
        for (String module : List.of("Watermark", "Welcomer", "AutoSign")) {
            JsonObject input = new JsonObject();
            JsonObject settings = new JsonObject();
            settings.addProperty("Text", "custom");
            input.add("settings", settings);
            JsonObject migrated = LegacyBranding.migrateModule(module, input).getAsJsonObject();
            assertEquals("custom", migrated.getAsJsonObject("settings").get("Text").getAsString(), module);
        }
    }

    private static boolean removed(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("webhook") || lower.contains("telegram") || lower.contains("discordnotifs");
    }
}

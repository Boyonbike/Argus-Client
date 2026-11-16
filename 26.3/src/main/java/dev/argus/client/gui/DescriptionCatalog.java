package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Curated hover text for settings and modules. The client's base language file only
 * describes a handful of settings, so this table fills every remaining popup with a
 * short explanation instead of leaving the description area blank.
 */
final class DescriptionCatalog {
    private static final String RESOURCE = "/assets/argusclient/descriptions.tsv";
    private static final Map<String, String> SETTINGS = new HashMap<>();
    private static final Map<String, String> MODULES = new HashMap<>();
    private static boolean loaded;
    private static boolean modulesApplied;

    private DescriptionCatalog() {
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream stream = DescriptionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] parts = line.split("\t", 3);
                    if (parts.length < 3) continue;
                    String module = parts[0].trim();
                    String setting = parts[1].trim();
                    String description = parts[2].trim().replace("\\n", "\n");
                    if (module.isEmpty() || description.isEmpty()) continue;
                    if (setting.isEmpty()) MODULES.put(module, description);
                    else SETTINGS.put(module + "\t" + setting, description);
                }
            }
        } catch (Exception ignored) {
            // A missing or malformed resource must not break the client; the fallback below still applies.
        }
    }

    /** Fills module descriptions that the module itself did not supply. */
    static void ensureApplied() {
        if (modulesApplied) return;
        load();
        if (ClientContext.moduleRegistry == null) return;
        Collection<?> modules = ClientContext.moduleRegistry.getRegistry();
        if (modules == null) return;
        for (Object value : modules) {
            if (!(value instanceof Module module)) continue;
            if (module.isDescriptionPresent()) continue;
            String description = MODULES.get(module.getConfigName());
            if (description == null) description = "";
            module.setDescription(description);
        }
        modulesApplied = true;
    }

    /**
     * Returns the curated description for a setting, or an empty string when there isn't one: a description
     * generated from the label would only repeat it.
     */
    static String setting(Module module, Setting<?> setting) {
        load();
        String description = SETTINGS.get(module.getConfigName() + "\t" + setting.getConfigName());
        return description != null ? description : "";
    }
}

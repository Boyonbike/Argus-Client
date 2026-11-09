package dev.argus.client.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Updates names left in saved configs by the clients Argus Client grew out of (Egg Client, Random
 * Stuff Client, Mio, Zio) to Argus Client. It runs over every .json and .properties file in the data
 * folder, presets included, and only rewrites files that actually change.
 */
public final class LegacyConfigRenamer {
    static final String PROJECT_URL = "https://github.com/Boyonbike/Argus-Client";

    /** Replaced wherever they appear in a key or string value, in this order. Case-sensitive. */
    private static final String[][] PARTS = {
            {"Random Stuff Client", "Argus Client"},
            {"RandomStuffClient", "Argus Client"},
            {"randomstuffclient", "argusclient"},
            {"Egg Client", "Argus Client"},
            {"EggClient", "Argus Client"},
            {"eggclient", "argusclient"},
            {"Welcome to ArgusClient.", "Welcome to Argus Client."},
            {"get ArgusClient", "get Argus Client"},
            {"get mioclient", "get Argus Client"},
            {"https://github.com/spectrumdevs/zio", PROJECT_URL},
            {"https://github.com/Cypphi/open-zio", PROJECT_URL},
    };

    /** Replaced only when they are the whole value (old watermark and welcome defaults). */
    private static final Map<String, String> WHOLE = Map.of(
            "Mio", "Argus Client",
            "ArgusClient", "Argus Client",
            "Welcome to Mio.", "Welcome to Argus Client.");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacyConfigRenamer() {}

    /** Renames inside every config file under {@code root}; a file that can't be read or parsed is left alone. */
    public static int renameAll(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (attrs.isRegularFile() && (name.endsWith(".json") || name.endsWith(".properties"))) files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            System.err.println("[Argus Client] Could not scan " + root + " for old client names: " + failure);
            return 0;
        }
        int changed = 0;
        for (Path file : files) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String updated = file.getFileName().toString().endsWith(".json") ? renameJson(text) : renameText(text);
                if (updated != null && !updated.equals(text)) {
                    Path temp = file.resolveSibling(file.getFileName() + ".argus-rename");
                    Files.writeString(temp, updated, StandardCharsets.UTF_8);
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    changed++;
                }
            } catch (IOException | RuntimeException failure) {
                System.err.println("[Argus Client] Could not update old client names in " + file + ": " + failure);
            }
        }
        return changed;
    }

    /** Returns the renamed JSON, the original text when nothing changed, or null when it isn't JSON. */
    static String renameJson(String text) {
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (JsonParseException notJson) {
            return null;
        }
        JsonElement updated = rename(root);
        return updated.equals(root) ? text : GSON.toJson(updated) + (text.endsWith("\n") ? "\n" : "");
    }

    static String renameText(String text) {
        return text.lines().map(LegacyConfigRenamer::renameString)
                .collect(Collectors.joining("\n", "", text.endsWith("\n") ? "\n" : ""));
    }

    static String renameString(String value) {
        String whole = WHOLE.get(value);
        if (whole != null) return whole;
        for (String[] part : PARTS) value = value.replace(part[0], part[1]);
        return value;
    }

    private static JsonElement rename(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
            return new JsonPrimitive(renameString(element.getAsString()));
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) copy.add(rename(item));
            return copy;
        }
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = renameString(entry.getKey());
                // If both the old and the Argus name are present, the Argus one is the current setting.
                if (!key.equals(entry.getKey()) && element.getAsJsonObject().has(key)) continue;
                copy.add(key, rename(entry.getValue()));
            }
            updateSign(copy);
            return copy;
        }
        return element;
    }

    /** Rewrites the old Zio/Mio AutoSign text to the Argus sign: "at github.com/" / "Boyonbike/" / "Argus-Client". */
    private static void updateSign(JsonObject sign) {
        String line3 = string(sign, "Line3"), line4 = string(sign, "Line4");
        boolean zio = "github.com/".equals(line3) && "spectrumdevs/zio".equals(line4);
        boolean mio = "www.mioclient.me".equals(line3);
        if (!zio && !mio) return;
        if ("at".equals(string(sign, "Line2"))) sign.addProperty("Line2", "at github.com/");
        sign.addProperty("Line3", "Boyonbike/");
        if (zio || ":p".equals(line4)) sign.addProperty("Line4", "Argus-Client");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}

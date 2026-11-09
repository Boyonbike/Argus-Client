package dev.argus.client.config.importing;
import com.google.gson.*;
import dev.argus.client.ArgusClient;
import dev.argus.client.config.LegacyBranding;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;

public final class ArgusConfigReader {
    private ArgusConfigReader() {}
    public static SourceConfig read(Path file) throws IOException {
        try {
            JsonObject root=ExternalConfigReader.json(file).getAsJsonObject();
            if(!root.has("client") || !root.get("client").getAsString().equals(ArgusClient.MOD_ID) || !root.has("category")
                    || !Set.of("all","modules").contains(root.get("category").getAsString().toLowerCase(Locale.ROOT))) throw new IOException("Expected an Argus Modules or Everything config");
            List<SourceModule> modules=new ArrayList<>(); int fields=0;
            for(var entry:root.entrySet()) {
                if(Set.of("client","category","argusclientLayout","argusImport").contains(entry.getKey())) continue;
                JsonObject m=LegacyBranding.migrateModule(entry.getKey(),entry.getValue()).getAsJsonObject();
                Map<String,JsonElement> settings=new LinkedHashMap<>();
                if(m.has("settings")) m.getAsJsonObject("settings").entrySet().forEach(e->settings.put(e.getKey(),e.getValue()));
                fields+=settings.size(); ExternalConfigReader.limits(modules.size()+1,fields);
                Optional<SourceBind> bind=Optional.empty();
                if(m.has("key")) bind=Optional.of(new SourceBind(m.has("mouse") && booleanValue(m.get("mouse")),bindCode(m.get("key")),0,m.has("state")?m.get("state").getAsString():"toggle"));
                JsonObject extensions=m.deepCopy(); for(String key:List.of("settings","enabled","key","state","mouse","drawn")) extensions.remove(key);
                modules.add(new SourceModule(entry.getKey(),m.has("enabled") && booleanValue(m.get("enabled")),settings,extensions,bind));
            }
            return new SourceConfig(Format.ARGUS,modules);
        } catch(RuntimeException e) { throw new IOException("Invalid Argus module config: "+e.getMessage(),e); }
    }
    private static int bindCode(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Expected an integer keybind code");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException invalid) {
            throw new IOException("Expected an integer keybind code", invalid);
        }
    }
    private static boolean booleanValue(JsonElement v) throws IOException {
        if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IOException("Expected a boolean module state"); return v.getAsBoolean();
    }
}

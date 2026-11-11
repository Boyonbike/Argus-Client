package dev.argus.client.config.importing;

import com.google.gson.*;
import java.util.*;

public final class ConfigImportModel {
    private ConfigImportModel() {}
    public enum Format { METEOR, ARGUS }
    public enum Severity { INFO, WARNING }
    public record SourceBind(boolean mouse, int code, int modifiers, String mode) {}
    public record SourceModule(String id, boolean enabled, Map<String, JsonElement> settings,
                               JsonObject extensions, Optional<SourceBind> bind) {
        public SourceModule {
            settings = copy(settings); extensions = extensions.deepCopy(); bind = Objects.requireNonNull(bind);
        }
        @Override public Map<String, JsonElement> settings() { return copy(settings); }
        @Override public JsonObject extensions() { return extensions.deepCopy(); }
    }
    public record SourceConfig(Format format, List<SourceModule> modules) {
        public SourceConfig { modules = List.copyOf(modules); }
    }
    public record Issue(String sourceId, String field, Severity severity, String message) {}
    public static Map<String, JsonElement> copy(Map<String, JsonElement> input) {
        Map<String, JsonElement> out = new LinkedHashMap<>(); input.forEach((k,v) -> out.put(k, v.deepCopy()));
        return Collections.unmodifiableMap(out);
    }
}

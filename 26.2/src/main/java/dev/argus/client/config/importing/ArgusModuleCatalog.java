package dev.argus.client.config.importing;

import com.google.gson.*;
import dev.argus.client.ClientContext;
import dev.argus.client.config.ColorJsonConverter;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PermanentModule;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.settings.*;
import dev.argus.client.utils.collections.NamedIdentifier;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Snapshot of declared defaults; never temporarily resets a live setting. */
public final class ArgusModuleCatalog {
    public record Field(JsonElement defaults, Function<JsonElement, JsonElement> codec) {
        public Field { defaults = defaults.deepCopy(); }
        @Override public JsonElement defaults() { return defaults.deepCopy(); }
        public JsonElement validate(JsonElement value) {
            try { return codec.apply(value.deepCopy()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Unsupported value; using Argus default",e); }
        }
    }
    public record Target(String id, Map<String, Field> settings, JsonObject extensionDefaults) {
        public Target { settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings)); extensionDefaults = extensionDefaults.deepCopy(); }
        @Override public JsonObject extensionDefaults() { return extensionDefaults.deepCopy(); }
        public JsonObject defaults() {
            JsonObject json = extensionDefaults(); JsonObject values = new JsonObject();
            settings.forEach((key, field) -> values.add(key, field.defaults()));
            json.add("settings", values); json.addProperty("enabled", false); return json;
        }
    }
    private final Map<String, Target> targets;
    public ArgusModuleCatalog(Map<String, Target> targets) { this.targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets)); }
    public Map<String, Target> targets() { return targets; }
    public static boolean eligible(Module module) {
        return !(module instanceof PermanentModule) && !(module instanceof HudWidgetModule)
                && module.getCategory() != ModuleCategory.CLIENT && module.getCategory() != ModuleCategory.HUD;
    }
    public static ArgusModuleCatalog capture() {
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Object entry : ClientContext.moduleRegistry.getRegistry()) {
            if (!(entry instanceof Module module)) continue;
            if (!eligible(module)) continue;
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Setting<?> setting : module.getSettings()) {
                if (!setting.isExcludedFromConfig() && !setting.isGroupOnly()) fields.put(setting.getConfigName(), describe(setting));
            }
            JsonObject extensions = new JsonObject();
            if (module.getConfigName().equals("AutoRekit")) extensions.add("rekitPresets", new JsonObject());
            targets.put(module.getConfigName(), new Target(module.getConfigName(), fields, extensions));
        }
        return new ArgusModuleCatalog(targets);
    }
    public static Field describe(Setting<?> setting) {
        Object value = setting.getDefaultValue();
        if (setting instanceof ColorSetting) {
            JsonObject defaults = new JsonObject(); defaults.add("rgba", new ColorJsonConverter().toJson(setting.opaqueColor ? new Color(((Color)value).getRGB()) : (Color)value));
            defaults.addProperty("rainbow", false); defaults.addProperty("sync", false);
            return new Field(defaults, input -> {
                JsonObject result = defaults.deepCopy();
                JsonElement rgba = input.isJsonArray() ? input : input.getAsJsonObject().get("rgba");
                require(rgba != null && rgba.isJsonArray() && rgba.getAsJsonArray().size() == 4);
                for (JsonElement c : rgba.getAsJsonArray()) { number(c); require(c.getAsDouble() == c.getAsInt() && c.getAsInt() >= 0 && c.getAsInt() <= 255); }
                result.add("rgba", rgba.deepCopy());
                if (setting.opaqueColor) result.getAsJsonArray("rgba").set(3,new JsonPrimitive(255));
                if (input.isJsonObject()) for (String key : List.of("rainbow", "sync")) {
                    require(input.getAsJsonObject().has(key)); bool(input.getAsJsonObject().get(key)); result.add(key, input.getAsJsonObject().get(key).deepCopy());
                }
                return result;
            });
        }
        if (setting instanceof SetSetting<?> set) return collection(set);
        if (value instanceof Boolean b) return new Field(new JsonPrimitive(b), input -> { bool(input); return input; });
        if (value instanceof Number declared) { Number n = clamped(setting, declared); return new Field(new JsonPrimitive(n), input -> {
            number(input); Number normalized;
            BigDecimal decimal=new BigDecimal(input.getAsString());
            if (n instanceof Integer) normalized=decimal.intValueExact();
            else if (n instanceof Long) normalized=decimal.longValueExact();
            else if (n instanceof Float) { float f=input.getAsFloat(); require(Float.isFinite(f)); normalized=f; }
            else normalized=input.getAsDouble();
            double x=normalized.doubleValue();
            if (setting.isNumericBoundsEnabled()) require(x >= ((Number)setting.getMinimumValue()).doubleValue() && x <= ((Number)setting.getMaximumValue()).doubleValue());
            return new JsonPrimitive(normalized);
        }); }
        if (value instanceof Enum<?> e && value instanceof NamedEntry named) {
            Map<String,String> names = new HashMap<>();
            for (Object entry : e.getDeclaringClass().getEnumConstants()) names.put(((NamedEntry)entry).getName().toLowerCase(Locale.ROOT), ((NamedEntry)entry).getName());
            return new Field(new JsonPrimitive(named.getName()), input -> {
                string(input); String name = names.get(input.getAsString().toLowerCase(Locale.ROOT)); require(name != null); return new JsonPrimitive(name);
            });
        }
        if (value instanceof String text) return new Field(new JsonPrimitive(text), input -> { string(input); return input; });
        if (setting instanceof IdentifierSetting) {
            NamedIdentifier id = (NamedIdentifier)value;
            JsonObject defaults = new JsonObject(); defaults.addProperty("name", id.getName()); defaults.addProperty("category", id.getCategory());
            return new Field(defaults, input -> {
                JsonObject o = input.getAsJsonObject(); string(o.get("name")); string(o.get("category"));
                new NamedIdentifier(o.get("category").getAsString(), o.get("name").getAsString()); return o;
            });
        }
        throw new IllegalArgumentException("No default codec for " + setting.getConfigName() + " (" + setting.getClass().getSimpleName() + ")");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Field collection(SetSetting set) {
        JsonArray defaults = new JsonArray();
        for (Object entry : (Set<?>)set.getDefaultValue()) defaults.add(set.formatEntry(entry));
        return new Field(defaults, input -> {
            require(input.isJsonArray()); JsonArray out = new JsonArray();
            for (JsonElement entry : input.getAsJsonArray()) {
                string(entry); Object parsed = set.parseEntry(entry.getAsString()); require(parsed != null); out.add(set.formatEntry(parsed));
            }
            TreeSet<String> unique=new TreeSet<>();for(JsonElement entry:out)unique.add(entry.getAsString());
            JsonArray canonical=new JsonArray();unique.forEach(canonical::add);return canonical;
        });
    }
    // The compiled base exposes only effective/theme-adjusted colour getters. Read its own
    // stored fields through read-only handles so capture/verification never samples animation.
    private static final class StoredColor {
        static final java.lang.invoke.VarHandle VALUE, RAINBOW, SYNC;
        static {
            try {
                VALUE=java.lang.invoke.MethodHandles.privateLookupIn(Setting.class,java.lang.invoke.MethodHandles.lookup()).findVarHandle(Setting.class,"value",Object.class);
                var lookup=java.lang.invoke.MethodHandles.privateLookupIn(ColorSetting.class,java.lang.invoke.MethodHandles.lookup());
                RAINBOW=lookup.findVarHandle(ColorSetting.class,"rainbow",boolean.class);SYNC=lookup.findVarHandle(ColorSetting.class,"synced",boolean.class);
            }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
        }
    }
    /** Same clamping as the base Setting.setValue (float comparison, bound object substituted). */
    static Number clamped(Setting<?> setting, Number value) {
        if (!setting.isNumericBoundsEnabled()) return value;
        Number min = (Number)setting.getMinimumValue(), max = (Number)setting.getMaximumValue();
        if (min != null && value.floatValue() < min.floatValue()) return min;
        if (max != null && value.floatValue() > max.floatValue()) return max;
        return value;
    }
    public static JsonElement current(Setting<?> setting) {
        // Constructors store declared defaults unclamped; report the value a native reload would hold.
        if (setting instanceof NumberSetting<?> && setting.getValue() instanceof Number n) return new JsonPrimitive(clamped(setting, n));
        if (!(setting instanceof ColorSetting)) return setting.toJson().deepCopy();
        JsonObject json=new JsonObject();json.add("rgba",new ColorJsonConverter().toJson((Color)StoredColor.VALUE.get(setting)));
        json.addProperty("rainbow",(boolean)StoredColor.RAINBOW.get(setting));json.addProperty("sync",(boolean)StoredColor.SYNC.get(setting));return json;
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Unsupported value; using Argus default"); }
    private static void bool(JsonElement v) { require(v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()); }
    private static void string(JsonElement v) { require(v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()); }
    private static void number(JsonElement v) { require(v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && Double.isFinite(v.getAsDouble())); }
}

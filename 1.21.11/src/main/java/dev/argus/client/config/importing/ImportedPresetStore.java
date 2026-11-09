package dev.argus.client.config.importing;
import com.google.gson.*;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.Keybind;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.PresetCategory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;
import static dev.argus.client.config.importing.ConfigImportConverter.*;

public final class ImportedPresetStore {
    private ImportedPresetStore() {}
    public record ImportLoadResult(boolean success,String backupName,List<Issue> issues) { public ImportLoadResult { issues=List.copyOf(issues); } }
    private static volatile ImportLoadResult lastResult;
    public static ImportLoadResult lastResult() { return lastResult; }
    public static boolean validName(String name) { return name!=null && name.matches("[A-Za-z0-9 _.\\-]{1,32}") && !name.isBlank() && !name.startsWith(".") && !name.endsWith("."); }
    public static boolean isImported(String name) {
        var preset=ClientContext.configurationManager.getPresetRegistry(PresetCategory.MODULES).getPreset(name);
        return preset!=null && isImported(preset.contents());
    }
    public static boolean isImported(JsonObject json) { return json.has("argusImport"); }
    public static void save(String name,ImportPreview preview) throws IOException {
        var registry=ClientContext.configurationManager.getPresetRegistry(PresetCategory.MODULES);
        if(registry.getPreset(name)!=null)throw new IOException("A preset with that name already exists");
        writeNew(PresetCategory.MODULES.getDirectory(),name,preview.preset()); registry.loadPresets();
        if(registry.getPreset(name)==null)throw new IOException("Preset was written but could not be registered");
    }
    public static boolean isRecovery(JsonObject json) {
        return json.has("argusImport") && json.getAsJsonObject("argusImport").has("recovery") && json.getAsJsonObject("argusImport").get("recovery").getAsBoolean();
    }
    static JsonObject recoverySnapshot(JsonObject snapshot) {
        JsonObject full=snapshot.deepCopy();full.addProperty("client","argusclient");full.addProperty("category","modules");
        JsonObject meta=new JsonObject();meta.addProperty("version",1);meta.addProperty("source","ARGUS");meta.addProperty("recovery",true);full.add("argusImport",meta);return full;
    }
    public static void saveCurrent(String name) throws IOException {
        writeNew(PresetCategory.MODULES.getDirectory(),name,recoverySnapshot(new LiveBackend().snapshot()));
        ClientContext.configurationManager.getPresetRegistry(PresetCategory.MODULES).loadPresets();
    }
    public static void writeNew(Path directory,String name,JsonObject contents) throws IOException {
        if(!validName(name))throw new IOException("Use 1–32 letters, numbers, spaces, dots, dashes or underscores");
        Files.createDirectories(directory);
        try(var files=Files.list(directory)) {
            if(files.anyMatch(p->p.getFileName().toString().equalsIgnoreCase(name+".json")))throw new IOException("A preset with that name already exists");
        }
        Path staged=Files.createTempFile(directory,".argus-import-",".tmp");
        try { Files.writeString(staged,new GsonBuilder().setPrettyPrinting().create().toJson(contents),StandardCharsets.UTF_8);Files.move(staged,directory.resolve(name+".json")); }
        finally { Files.deleteIfExists(staged); }
    }
    public static ImportLoadResult load(String name) {
        try {
            var preset=ClientContext.configurationManager.getPresetRegistry(PresetCategory.MODULES).getPreset(name);
            if(preset==null || !isImported(preset.contents()))throw new IllegalArgumentException("Imported preset not found");
            var catalog=ArgusModuleCatalog.capture();JsonObject desired=validatePreset(preset.contents(),catalog);
            lastResult=CheckedPresetLoader.load(desired,new LiveBackend());
        } catch(Exception e) { lastResult=new ImportLoadResult(false,null,List.of(new Issue(name,"load",Severity.WARNING,"Couldn’t load config: "+e.getMessage()))); }
        return lastResult;
    }
    /** Validate all persisted fields before mutating anything; edited/corrupt imported presets fail closed. */
    static JsonObject validatePreset(JsonObject json,ArgusModuleCatalog catalog) {
        JsonObject meta=json.getAsJsonObject("argusImport");
        if(meta==null || !meta.has("version") || meta.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported imported preset version");
        JsonObject desired=new JsonObject();
        for(var target:catalog.targets().values()) {
            if(!json.has(target.id()))throw new IllegalArgumentException("Module catalog changed; re-import this config");
            JsonObject module=json.getAsJsonObject(target.id());JsonObject out=target.defaults();
            if(!module.has("enabled") || !module.get("enabled").isJsonPrimitive() || !module.get("enabled").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid module state: "+target.id());
            out.add("enabled",module.get("enabled").deepCopy());JsonObject settings=module.getAsJsonObject("settings");
            for(var field:target.settings().entrySet()) {
                if(settings==null || !settings.has(field.getKey()))throw new IllegalArgumentException("Settings changed; re-import this config");
                try { out.getAsJsonObject("settings").add(field.getKey(),field.getValue().validate(settings.get(field.getKey()))); }
                catch(IllegalArgumentException e) { throw new IllegalArgumentException("Invalid saved value for "+target.id()+"/"+field.getKey()+"; re-import this config",e); }
            }
            if(module.has("key")) {
                int code=module.get("key").getAsInt();boolean mouse=module.get("mouse").getAsBoolean();String mode=module.get("state").getAsString();
                if(code < -1 || code>(mouse?7:348) || !Set.of("toggle","hold","hold_reverse").contains(mode))throw new IllegalArgumentException("Invalid keybind");
                out.addProperty("key",code);out.addProperty("mouse",mouse);out.addProperty("state",mode);
            }
            if(module.has("drawn"))out.add("drawn",module.get("drawn").deepCopy());
            if(target.id().equals("AutoRekit")) out.add("rekitPresets",validateRekit(module.get("rekitPresets")));
            desired.add(target.id(),out);
        }
        return desired;
    }
    private static List<Module> modules() {
        List<Module> modules=new ArrayList<>();for(Object entry:ClientContext.moduleRegistry.getRegistry())if(entry instanceof Module m && ArgusModuleCatalog.eligible(m))modules.add(m);return modules;
    }
    private static final class LiveBackend implements CheckedPresetLoader.Backend {
        public JsonObject snapshot() {
            JsonObject snapshot=new JsonObject();snapshot.addProperty("client","argusclient");snapshot.addProperty("category","modules");
            for(Module m:modules()) {
                JsonObject data=m.toJson().getAsJsonObject().deepCopy();JsonObject values=data.getAsJsonObject("settings");
                for(Setting<?> setting:m.getSettings())if(values!=null && values.has(setting.getConfigName()))values.add(setting.getConfigName(),ArgusModuleCatalog.current(setting));
                snapshot.add(m.getConfigName(),data);
            }return snapshot;
        }
        public String backup(JsonObject json) throws IOException {
            String base="Recovery "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));String name=base;
            Path dir=PresetCategory.MODULES.getDirectory();var registry=ClientContext.configurationManager.getPresetRegistry(PresetCategory.MODULES);
            int suffix=2;while(registry.getPreset(name)!=null || Files.exists(dir.resolve(name+".json")))name=base+" "+suffix++;
            writeNew(dir,name,recoverySnapshot(json));registry.loadPresets();return name;
        }
        public void settings(JsonObject desired) {
            List<Module> modules=modules();
            for(Module m:modules)if(desired.has(m.getConfigName()) && m.isToggled())m.disable();
            for(Module m:modules) {
                if(!desired.has(m.getConfigName()))continue;JsonObject data=desired.getAsJsonObject(m.getConfigName());JsonObject values=data.getAsJsonObject("settings");
                for(Setting<?> s:m.getSettings()) {
                    if(s.isExcludedFromConfig() || s.isGroupOnly() || values==null || !values.has(s.getConfigName()))continue;
                    JsonElement expected=values.get(s.getConfigName());s.fromJson(expected.deepCopy());
                    JsonElement actual=ArgusModuleCatalog.describe(s).validate(ArgusModuleCatalog.current(s));
                    if(!actual.equals(ArgusModuleCatalog.describe(s).validate(expected)))throw new IllegalStateException("Setting did not apply: "+m.getConfigName()+"/"+s.getConfigName());
                }
                if(data.has("rekitPresets")) {
                    JsonObject extension=new JsonObject();extension.add("rekitPresets",data.get("rekitPresets").deepCopy());m.fromJson(extension);
                    if(!m.toJson().getAsJsonObject().get("rekitPresets").equals(data.get("rekitPresets")))throw new IllegalStateException("Inventory presets did not apply");
                }
                if(data.has("key"))m.setKeybind(new Keybind(data.get("key").getAsInt(),switch(data.get("state").getAsString()){case "hold"->Keybind.Mode.HOLD;case "hold_reverse"->Keybind.Mode.HOLD_REVERSE;default->Keybind.Mode.TOGGLE;},data.get("mouse").getAsBoolean()));
                if(data.has("drawn"))m.setDrawn(data.get("drawn").getAsBoolean());
            }
        }
        public void enabled(JsonObject desired) {
            for(Module m:modules())if(desired.has(m.getConfigName())) {
                boolean on=desired.getAsJsonObject(m.getConfigName()).get("enabled").getAsBoolean();
                if(on && !m.isToggled())m.enable();else if(!on && m.isToggled())m.disable();
            }
        }
        public void persist() throws IOException { ClientContext.configurationManager.saveChecked(ClientContext.moduleRegistry); }
    }
}

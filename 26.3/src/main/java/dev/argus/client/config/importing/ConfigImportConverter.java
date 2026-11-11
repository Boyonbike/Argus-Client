package dev.argus.client.config.importing;
import com.google.gson.*;
import java.util.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;

/** Pure conversion: declared defaults plus explicitly validated source fields. */
public final class ConfigImportConverter {
    private ConfigImportConverter() {}
    public record ImportPreview(JsonObject preset,List<Issue> issues,Set<String> enabledTargets,int copiedSettings,int defaultedSettings) {
        public ImportPreview { preset=preset.deepCopy();issues=List.copyOf(issues);enabledTargets=Set.copyOf(enabledTargets); }
        @Override public JsonObject preset() { return preset.deepCopy(); }
    }
    public static ImportPreview convert(SourceConfig source,ArgusModuleCatalog catalog,boolean importBinds) {
        JsonObject root=new JsonObject();root.addProperty("client","argusclient");root.addProperty("category","modules");
        catalog.targets().forEach((id,t)->root.add(id,t.defaults()));
        Map<String,MeteorMappings.ModuleMapping> mappings=new HashMap<>();MeteorMappings.modules().forEach(m->mappings.put(m.source(),m));
        List<Issue> issues=new ArrayList<>(); Set<String> enabled=new TreeSet<>(), mapped=new HashSet<>();
        Map<String,JsonElement> assigned=new HashMap<>(); Set<String> conflicted=new HashSet<>(); int total=0;
        for(var target:catalog.targets().values()) total+=target.settings().size();
        for(SourceModule module:source.modules()) {
            var mapping=mappings.get(module.id()); String targetId=source.format()==Format.ARGUS?module.id():mapping==null?null:mapping.target();
            var target=catalog.targets().get(targetId);
            if(target==null) {
                issues.add(new Issue(module.id(),"module",module.enabled()?Severity.WARNING:Severity.INFO,module.enabled()?"Enabled source module has no supported gameplay counterpart":"Module skipped")); continue;
            }
            mapped.add(targetId); JsonObject out=root.getAsJsonObject(targetId);
            if(module.enabled()) { enabled.add(targetId);out.addProperty("enabled",true); }
            Set<String> used=new HashSet<>();
            if(source.format()==Format.ARGUS) {
                module.settings().forEach((key,value)->assign(module.id(),target,key,key,value,out,assigned,conflicted,issues)); used.addAll(module.settings().keySet());
                for(var extension:module.extensions().entrySet()) {
                    if(targetId.equals("AutoRekit") && extension.getKey().equals("rekitPresets")) {
                        try { out.add("rekitPresets",validateRekit(extension.getValue())); }
                        catch(RuntimeException e) { issues.add(new Issue(module.id(),"rekitPresets",Severity.WARNING,"Invalid inventory presets; using empty Argus defaults")); }
                    } else issues.add(new Issue(module.id(),extension.getKey(),Severity.WARNING,"Unsupported module data skipped"));
                }
            } else {
                // A source value split across several targets (e.g. seconds -> Minutes + Delay) is
                // all-or-nothing: copying one half would silently change the unit's meaning.
                Map<String,List<MeteorMappings.SettingMapping>> bySource=new LinkedHashMap<>();
                for(var field:mapping.settings()) bySource.computeIfAbsent(field.sourcePath(),k->new ArrayList<>()).add(field);
                for(var group:bySource.entrySet()) {
                    JsonElement value=module.settings().get(group.getKey());if(value==null)continue;used.add(group.getKey());
                    Map<String,JsonElement> translated=new LinkedHashMap<>();
                    try {
                        for(var field:group.getValue()) {
                            var setting=target.settings().get(field.target());
                            if(setting==null) throw new IllegalArgumentException("Unknown target setting");
                            translated.put(field.target(),setting.validate(field.transform().apply(value)));
                        }
                    } catch(RuntimeException e) {
                        issues.add(new Issue(module.id(),group.getKey(),Severity.WARNING,"Value cannot be translated; using Argus default"));continue;
                    }
                    translated.forEach((key,valid)->assign(module.id(),target,key,group.getKey(),valid,out,assigned,conflicted,issues));
                }
            }
            for(String key:module.settings().keySet()) if(!used.contains(key)) issues.add(new Issue(module.id(),key,Severity.INFO,"No exact mapping; using Argus defaults"));
            if(importBinds && module.bind().isPresent()) {
                SourceBind bind=module.bind().get();
                if(bind.modifiers()!=0 || !Set.of("toggle","hold","hold_reverse").contains(bind.mode()) || bind.code() < -1 || (bind.mouse()?bind.code()>7:bind.code()>348)) {
                    issues.add(new Issue(module.id(),"bind",Severity.WARNING,"Unsupported bind; keeping current Argus bind"));
                } else {
                    JsonObject value=new JsonObject();value.addProperty("key",bind.code());value.addProperty("mouse",bind.mouse());value.addProperty("state",bind.mode());
                    String key=targetId+"/@bind";
                    if(conflicted.contains(key) || assigned.containsKey(key) && !assigned.get(key).equals(value)) {
                        conflicted.add(key);assigned.remove(key);for(String k:List.of("key","mouse","state"))out.remove(k);
                        issues.add(new Issue(module.id(),"bind",Severity.WARNING,"Conflicting binds; keeping current Argus bind"));
                    } else { assigned.put(key,value);value.entrySet().forEach(e->out.add(e.getKey(),e.getValue())); }
                }
            }
        }
        for(String id:mapped) issues.add(new Issue(id,"summary",Severity.INFO,enabled.contains(id)?"Module will be enabled; unmatched settings use Argus defaults":"Module will be disabled; unmatched settings use Argus defaults"));
        issues.sort(Comparator.comparing((Issue i)->i.severity()!=Severity.WARNING).thenComparing(Issue::sourceId).thenComparing(Issue::field));
        int copied=(int)assigned.keySet().stream().filter(k->!k.endsWith("/@bind")).count();
        JsonObject meta=new JsonObject();meta.addProperty("version",1);meta.addProperty("source",source.format().name());meta.addProperty("copiedSettings",copied);meta.addProperty("defaultedSettings",total-copied);root.add("argusImport",meta);
        return new ImportPreview(root,issues,enabled,copied,total-copied);
    }
    private static void assign(String source,ArgusModuleCatalog.Target target,String key,String sourcePath,JsonElement value,JsonObject out,Map<String,JsonElement> assigned,Set<String> conflicted,List<Issue> issues) {
        var field=target.settings().get(key);String identity=target.id()+"/"+key;
        if(field==null) { issues.add(new Issue(source,sourcePath,Severity.INFO,"Unknown setting skipped"));return; }
        try {
            JsonElement valid=field.validate(value);
            if(conflicted.contains(identity)) return;
            if(assigned.containsKey(identity) && !assigned.get(identity).equals(valid)) {
                assigned.remove(identity);conflicted.add(identity);out.getAsJsonObject("settings").add(key,field.defaults());
                issues.add(new Issue(source,sourcePath,Severity.WARNING,"Conflicting values; using Argus default"));
            } else { assigned.put(identity,valid);out.getAsJsonObject("settings").add(key,valid); }
        } catch(RuntimeException e) { issues.add(new Issue(source,sourcePath,Severity.WARNING,"Unsupported value; using Argus default")); }
    }
    static JsonObject validateRekit(JsonElement value) {
        JsonObject presets=value.getAsJsonObject();if(presets.size()>256)throw new IllegalArgumentException("Too many inventory presets");
        for(var entry:presets.entrySet()) {
            if(entry.getKey().isBlank() || entry.getKey().length()>32)throw new IllegalArgumentException("Invalid inventory preset name");
            for(var slot:entry.getValue().getAsJsonObject().entrySet()) {
                int index=Integer.parseInt(slot.getKey());if(index<0 || index>40 || !slot.getValue().isJsonObject())throw new IllegalArgumentException("Invalid inventory slot");
                // ItemStack.CODEC payloads remain registry-aware; decode is deferred until a world is available.
                JsonObject stack=slot.getValue().getAsJsonObject();if(!stack.has("id") || !stack.get("id").isJsonPrimitive())throw new IllegalArgumentException("Invalid item stack");
                net.minecraft.resources.Identifier.parse(stack.get("id").getAsString());
                if(stack.has("count") && (stack.get("count").getAsInt()<1 || stack.get("count").getAsInt()>99))throw new IllegalArgumentException("Invalid item count");
            }
        }
        return presets.deepCopy();
    }
}

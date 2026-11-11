package dev.argus.client.config.importing;
import com.google.gson.*;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static dev.argus.client.config.importing.ConfigImportModel.*;

public final class MeteorConfigReader {
    private MeteorConfigReader() {}
    /** Raw or gzip NBT under the shared file/read/depth budgets. */
    static CompoundTag readNbt(Path file) throws IOException {
        ExternalConfigReader.checkFile(file);
        try(var raw=new BufferedInputStream(ExternalConfigReader.bounded(Files.newInputStream(file),ExternalConfigReader.MAX_FILE))) {
            raw.mark(2); int first=raw.read(),second=raw.read(); raw.reset();
            InputStream decoded=first==31 && second==139 ? new GZIPInputStream(raw):raw;
            return NbtIo.read(new DataInputStream(ExternalConfigReader.bounded(decoded,ExternalConfigReader.MAX_READ)),new NbtAccounter(ExternalConfigReader.MAX_READ,64));
        } catch(RuntimeException e) { throw new IOException("Invalid Meteor NBT: "+e.getMessage(),e); }
    }
    public static SourceConfig read(Path file) throws IOException {
        CompoundTag root=readNbt(file);
        try {
            if(!(root.get("modules") instanceof ListTag modules)) throw new IOException("Expected Meteor modules.nbt");
            List<SourceModule> result=new ArrayList<>(); Set<String> ids=new HashSet<>(); int fields=0;
            for(Tag tag:modules) {
                if(!(tag instanceof CompoundTag m)) throw new IOException("Invalid Meteor module entry");
                String id=m.getString("name").orElseThrow(()->new IOException("Module name missing"));
                if(id.isBlank() || !ids.add(id)) throw new IOException("Blank or duplicate module ID: "+id);
                booleanField(m,"active");booleanField(m,"toggleOnKeyRelease");
                if(m.contains("settings") && !(m.get("settings") instanceof CompoundTag))throw new IOException("Invalid Meteor module settings");
                if(m.contains("keybind") && !(m.get("keybind") instanceof CompoundTag))throw new IOException("Invalid Meteor keybind");
                Map<String,JsonElement> settings=new LinkedHashMap<>();
                CompoundTag settingsTag=m.getCompoundOrEmpty("settings");
                if(settingsTag.contains("groups") && !(settingsTag.get("groups") instanceof ListTag)) throw new IOException("Invalid Meteor setting groups");
                for(Tag groupTag:settingsTag.getListOrEmpty("groups")) {
                    if(!(groupTag instanceof CompoundTag group)) throw new IOException("Invalid setting group");
                    if(group.contains("settings") && !(group.get("settings") instanceof ListTag))throw new IOException("Invalid Meteor group settings");
                    String name=group.getString("name").orElseThrow(()->new IOException("Group name missing"));
                    for(Tag fieldTag:group.getListOrEmpty("settings")) {
                        if(!(fieldTag instanceof CompoundTag field)) throw new IOException("Invalid setting entry");
                        String key=name+"/"+field.getString("name").orElseThrow(()->new IOException("Setting name missing"));
                        Tag value=field.get("value"); if(value==null || settings.putIfAbsent(key,json(value))!=null) throw new IOException("Missing or duplicate setting: "+key);
                        ExternalConfigReader.limits(result.size()+1,++fields);
                    }
                }
                Optional<SourceBind> bind=Optional.empty();
                if(m.get("keybind") instanceof CompoundTag key) {
                    booleanField(key,"isKey");
                    for(String k:List.of("value","modifiers"))if(key.contains(k) && !(key.get(k) instanceof IntTag))throw new IOException("Invalid keybind field: "+k);
                    bind=Optional.of(new SourceBind(!key.getBooleanOr("isKey",true),key.getIntOr("value",-1),key.getIntOr("modifiers",0),m.getBooleanOr("toggleOnKeyRelease",false)?"release":"toggle"));
                }
                result.add(new SourceModule(id,m.getBooleanOr("active",false),settings,new JsonObject(),bind)); ExternalConfigReader.limits(result.size(),fields);
            }
            return new SourceConfig(Format.METEOR,result);
        } catch(RuntimeException e) { throw new IOException("Invalid Meteor NBT: "+e.getMessage(),e); }
    }
    private static void booleanField(CompoundTag tag,String key) throws IOException {
        if(tag.contains(key) && (!(tag.get(key) instanceof ByteTag value) || (value.byteValue()!=0 && value.byteValue()!=1)))throw new IOException("Invalid Meteor boolean: "+key);
    }
    private static JsonElement json(Tag tag) throws IOException {
        if(tag instanceof StringTag text) return new JsonPrimitive(text.asString().orElseThrow());
        if(tag instanceof NumericTag) return new JsonPrimitive(tag.asNumber().orElseThrow());
        if(tag instanceof CompoundTag compound) { JsonObject out=new JsonObject(); for(var entry:compound.entrySet()) out.add(entry.getKey(),json(entry.getValue())); return out; }
        if(tag instanceof ListTag list) { JsonArray out=new JsonArray(); for(Tag t:list) out.add(json(t)); return out; }
        if(tag instanceof ByteArrayTag bytes) { JsonArray out=new JsonArray(); for(byte x:bytes.getAsByteArray()) out.add(x); return out; }
        if(tag instanceof IntArrayTag ints) { JsonArray out=new JsonArray(); for(int x:ints.getAsIntArray()) out.add(x); return out; }
        if(tag instanceof LongArrayTag longs) { JsonArray out=new JsonArray(); for(long x:longs.getAsLongArray()) out.add(x); return out; }
        throw new IOException("Unsupported NBT value");
    }
}

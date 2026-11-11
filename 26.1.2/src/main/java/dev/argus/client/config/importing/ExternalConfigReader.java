package dev.argus.client.config.importing;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;

public final class ExternalConfigReader {
    static final long MAX_FILE = 8L * 1024 * 1024, MAX_READ = 32L * 1024 * 1024;
    private ExternalConfigReader() {}
    public static List<Path> candidates(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String name : List.of("modules.nbt", "modules.json")) if (Files.isRegularFile(directory.resolve(name))) files.add(directory.resolve(name));
        if (!files.isEmpty()) return List.copyOf(files);
        Path profiles=directory.resolve("profiles");
        if (Files.isDirectory(profiles)) try(var dirs=Files.list(profiles)) {
            for (Path dir : dirs.sorted().limit(4097).toList()) if (Files.isRegularFile(dir.resolve("modules.nbt"))) files.add(dir.resolve("modules.nbt"));
        }
        if(files.size()>4096) throw new IOException("Too many profiles"); return List.copyOf(files);
    }
    public static SourceConfig read(Path source) throws IOException {
        if (Files.isDirectory(source)) {
            List<Path> files=candidates(source);
            if(files.size()!=1) throw new IOException(files.isEmpty()?"No module config found in this folder":"Choose a specific file or profile");
            source=files.getFirst();
        }
        checkFile(source);
        try (var in=Files.newInputStream(source)) {
            int first; do { first=in.read(); } while(first>=0 && Character.isWhitespace(first));
            return first=='{' ? ArgusConfigReader.read(source) : MeteorConfigReader.read(source);
        } catch (RuntimeException e) { throw new IOException("Invalid config: "+e.getMessage(),e); }
    }
    static void checkFile(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path)>MAX_FILE) throw new IOException("Choose a regular config file no larger than 8 MiB");
    }
    static InputStream bounded(InputStream in,long limit) {
        return new FilterInputStream(in) {
            long remaining=limit;
            private void used(int n) throws IOException { if(n>0 && (remaining-=n)<0) throw new IOException("Config exceeds read budget"); }
            @Override public int read() throws IOException { int x=in.read(); used(x<0?0:1); return x; }
            @Override public int read(byte[] b,int o,int l) throws IOException { int n=in.read(b,o,(int)Math.min(l,Math.max(1,remaining+1))); used(n); return n; }
        };
    }
    static JsonElement json(Path file) throws IOException {
        checkFile(file);
        try(var stream=bounded(Files.newInputStream(file),MAX_FILE); var reader=new JsonReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT); JsonElement result=jsonValue(reader,0,new int[]{0});
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Trailing config content"); return result;
        } catch(RuntimeException e) { throw new IOException("Invalid JSON: "+e.getMessage(),e); }
    }
    private static JsonElement jsonValue(JsonReader r,int depth,int[] count) throws IOException {
        if(depth>64 || ++count[0]>262144) throw new IOException("Config structure is too large or deeply nested");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {
                r.beginObject(); JsonObject o=new JsonObject();
                while(r.hasNext()) { String k=r.nextName(); if(o.has(k)) throw new IOException("Duplicate config key: "+k); o.add(k,jsonValue(r,depth+1,count)); } r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> { r.beginArray(); JsonArray a=new JsonArray(); while(r.hasNext()) a.add(jsonValue(r,depth+1,count)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unexpected JSON token");
        };
    }
    static void limits(int modules,int settings) throws IOException {
        if(modules>4096 || settings>65536) throw new IOException("Config exceeds module or setting limit");
    }
}

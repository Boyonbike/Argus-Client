package dev.argus.client.config.importing;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ExternalConfigReaderTest {
    @TempDir Path temp;
    @Test void readsRawAndGzipMeteorWithSparseSettingsAndByteValues() throws Exception {
        CompoundTag root = new CompoundTag(), module = new CompoundTag(), settings = new CompoundTag(), group = new CompoundTag(), field = new CompoundTag();
        module.putString("name", "sprint"); module.putBoolean("active", true);
        field.putString("name", "test"); field.putByte("value", (byte)2); ListTag fields = new ListTag(); fields.add(field);
        group.putString("name", "General"); group.put("settings",fields); ListTag groups = new ListTag(); groups.add(group); settings.put("groups", groups); module.put("settings", settings);
        ListTag modules = new ListTag(); modules.add(module); root.put("modules", modules);
        for (boolean gzip : new boolean[]{false,true}) {
            Path file = temp.resolve(gzip ? "gzip.nbt" : "raw.nbt");
            try (var stream = Files.newOutputStream(file); var out = new DataOutputStream(gzip ? new GZIPOutputStream(stream) : stream)) { NbtIo.write(root,out); }
            var source = ExternalConfigReader.read(file);
            assertTrue(source.modules().getFirst().enabled());
            assertEquals(2, source.modules().getFirst().settings().get("General/test").getAsInt());
        }
    }
    @Test void rejectsPresentMalformedMeteorFieldsInsteadOfInventingDefaults() throws Exception {
        for(String field:java.util.List.of("active","settings")) {
            CompoundTag root=new CompoundTag(),module=new CompoundTag();module.putString("name","sprint");module.putString(field,"broken");ListTag list=new ListTag();list.add(module);root.put("modules",list);
            Path file=temp.resolve("bad.nbt");try(var out=new DataOutputStream(Files.newOutputStream(file))){NbtIo.write(root,out);}
            assertThrows(IOException.class,()->ExternalConfigReader.read(file));
        }
    }
    @Test void detectsDeletedAndReplacedPresetFilesEvenWithUnchangedTimestamps() throws Exception {
        Path file=temp.resolve("preset.json");Files.writeString(file,"{\"value\":1}");var time=Files.getLastModifiedTime(file);String before=PresetFileVersion.capture(file);
        Files.writeString(file,"{\"value\":2}");Files.setLastModifiedTime(file,time);assertNotEquals(before,PresetFileVersion.capture(file));
        Files.delete(file);assertNull(PresetFileVersion.capture(file));
    }
    @Test void readsArgusModuleStateAndRejectsUnknownFormatsAndCategories() throws Exception {
        Path file = temp.resolve("config.json");
        Files.writeString(file,"{\"client\":\"argusclient\",\"category\":\"all\",\"Sprint\":{\"enabled\":true,\"settings\":{\"Mode\":\"Rage\"}}}");
        assertEquals("Rage",ExternalConfigReader.read(file).modules().getFirst().settings().get("Mode").getAsString());
        for (String bad : new String[]{"{}", "{\"client\":\"other\"}", "{\"client\":\"argusclient\",\"category\":\"hud\"}", "{", "{\"client\":\"argusclient\",\"client\":\"other\"}"}) {
            Files.writeString(file,bad); assertThrows(IOException.class, () -> ExternalConfigReader.read(file));
        }
    }
    @Test void requiresChoiceForAmbiguousFoldersAndDiscoversProfiles() throws Exception {
        Path profiles=Files.createDirectories(temp.resolve("profiles/Normal")); Files.write(profiles.resolve("modules.nbt"),new byte[]{1});
        assertEquals(java.util.List.of(profiles.resolve("modules.nbt")),ExternalConfigReader.candidates(temp));
        Files.write(temp.resolve("modules.json"), new byte[]{1}); Files.write(temp.resolve("modules.nbt"), new byte[]{1});
        assertThrows(IOException.class, () -> ExternalConfigReader.read(temp));
    }
    @Test void normalizesLegacyBrandingInImportedModuleSettings() throws Exception {
        String original = """
                {
                  "client": "argusclient", "category": "modules",
                  "BetterChat": {"settings": {"Suffix": "⋆ ᴍɪᴏ", "Prefix": "Team chat"}},
                  "AutoSign": {"settings": {"Line1": "get Zio", "Line2": "at",
                                            "Line3": "github.com/", "Line4": "spectrumdevs/zio"}}
                }
                """;
        Path file = Files.writeString(temp.resolve("legacy.json"), original);

        var modules = ExternalConfigReader.read(file).modules();
        assertEquals("⋆ Argus Client", modules.getFirst().settings().get("Suffix").getAsString());
        assertEquals("Team chat", modules.getFirst().settings().get("Prefix").getAsString());
        var sign = modules.get(1).settings();
        assertEquals("get Argus Client", sign.get("Line1").getAsString());
        assertEquals("at github.com/", sign.get("Line2").getAsString());
        assertEquals("Boyonbike/", sign.get("Line3").getAsString());
        assertEquals("Argus-Client", sign.get("Line4").getAsString());
        assertEquals(original, Files.readString(file));
    }

    @Test void rejectsFractionalAndOverflowingArgusBindCodes() throws Exception {
        Path file = temp.resolve("bad-bind.json");
        for (String key : new String[]{"65.5", "4294967361"}) {
            Files.writeString(file, """
                    {"client":"argusclient","category":"modules","Sprint":{"key":%s}}
                    """.formatted(key));
            assertThrows(IOException.class, () -> ExternalConfigReader.read(file));
        }
    }
    @Test void rejectsDepthLimitsDuplicateModulesAndTruncatedNbt() throws Exception {
        Path file=temp.resolve("config.json");
        Files.writeString(file,"[".repeat(66)+"0"+"]".repeat(66)); assertThrows(IOException.class,()->ExternalConfigReader.read(file));
        Files.writeString(file,"{\"client\":\"argusclient\",\"category\":\"modules\",\"Sprint\":{},\"Sprint\":{}}"); assertThrows(IOException.class,()->ExternalConfigReader.read(file));
        Files.write(file,new byte[]{31,(byte)139,8}); assertThrows(IOException.class,()->ExternalConfigReader.read(file));
        try (var out = new RandomAccessFile(file.toFile(),"rw")) { out.setLength(8*1024*1024+1); }
        assertThrows(IOException.class,()->ExternalConfigReader.read(file));
    }
}

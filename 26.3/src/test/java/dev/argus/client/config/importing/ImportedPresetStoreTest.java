package dev.argus.client.config.importing;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ImportedPresetStoreTest {
    @TempDir Path temp;
    @Test void refusesDuplicateAndUnsafeNamesWithoutChangingSavedConfig() throws Exception {
        ImportedPresetStore.writeNew(temp,"Normal",JsonParser.parseString("{\"test\":1}").getAsJsonObject());
        assertThrows(IOException.class,()->ImportedPresetStore.writeNew(temp,"normal",new JsonObject()));
        assertThrows(IOException.class,()->ImportedPresetStore.writeNew(temp,"../escape",new JsonObject()));
        assertEquals(1,JsonParser.parseString(Files.readString(temp.resolve("Normal.json"))).getAsJsonObject().get("test").getAsInt());
    }
    @Test void recoverySnapshotsRemainFullAndUseTheCheckedLoader() {
        var catalog=new ArgusModuleCatalog(Map.of("Sprint",new ArgusModuleCatalog.Target("Sprint",Map.of("Rate",ArgusModuleCatalog.describe(new dev.argus.client.settings.NumberSetting<>("Rate",.1f,0f,1f))),new JsonObject())));
        JsonObject snapshot=JsonParser.parseString("{\"Sprint\":{\"enabled\":true,\"key\":80,\"mouse\":false,\"state\":\"hold_reverse\",\"drawn\":false,\"settings\":{\"Rate\":0.3}}}").getAsJsonObject();
        JsonObject recovery=ImportedPresetStore.recoverySnapshot(snapshot);
        assertTrue(ImportedPresetStore.isImported(recovery));
        JsonObject restored=ImportedPresetStore.validatePreset(recovery,catalog).getAsJsonObject("Sprint");
        assertEquals(80,restored.get("key").getAsInt());assertEquals("hold_reverse",restored.get("state").getAsString());assertFalse(restored.get("drawn").getAsBoolean());assertEquals(new JsonPrimitive(.3f),restored.getAsJsonObject("settings").get("Rate"));
    }
    @Test void invalidStoredSettingNamesTheModuleAndSetting() {
        var catalog=new ArgusModuleCatalog(Map.of("AutoNameTag",new ArgusModuleCatalog.Target("AutoNameTag",Map.of("Delay",ArgusModuleCatalog.describe(new dev.argus.client.settings.NumberSetting<>("Delay",1f,.1f,5f))),new JsonObject())));
        JsonObject preset=ImportedPresetStore.recoverySnapshot(JsonParser.parseString("{\"AutoNameTag\":{\"enabled\":false,\"settings\":{\"Delay\":0.0}}}").getAsJsonObject());
        var error=assertThrows(IllegalArgumentException.class,()->ImportedPresetStore.validatePreset(preset,catalog));
        assertTrue(error.getMessage().contains("AutoNameTag/Delay"),error.getMessage());
    }
    static class State implements CheckedPresetLoader.Backend {
        final List<String> calls=new ArrayList<>();JsonObject current=JsonParser.parseString("{\"value\":1}").getAsJsonObject();boolean failBackup,failSettings;
        public JsonObject snapshot(){calls.add("snapshot");return current.deepCopy();}
        public String backup(JsonObject state)throws IOException{calls.add("backup");if(failBackup)throw new IOException("disk full");return "Recovery";}
        public void settings(JsonObject value){calls.add("settings:"+value.get("value"));if(failSettings && value.get("value").getAsInt()==2)throw new IllegalStateException("setting failed");current=value.deepCopy();}
        public void enabled(JsonObject value){calls.add("enabled:"+value.get("value"));}
        public void persist(){calls.add("persist");}
    }
    @Test void backsUpBeforeApplyingAllSettingsThenEnabledStates() {
        State s=new State();var result=CheckedPresetLoader.load(JsonParser.parseString("{\"value\":2}").getAsJsonObject(),s);
        assertTrue(result.success());assertEquals(List.of("snapshot","backup","settings:2","enabled:2","persist"),s.calls);
    }
    @Test void backupFailurePreventsMutationAndApplicationFailureRestoresPreviousState() {
        JsonObject target=JsonParser.parseString("{\"value\":2}").getAsJsonObject();State s=new State();s.failBackup=true;
        assertFalse(CheckedPresetLoader.load(target,s).success());assertEquals(List.of("snapshot","backup"),s.calls);assertEquals(1,s.current.get("value").getAsInt());
        State failure=new State();failure.failSettings=true;var result=CheckedPresetLoader.load(target,failure);
        assertFalse(result.success());assertEquals(1,failure.current.get("value").getAsInt());assertTrue(failure.calls.contains("enabled:1"));assertEquals("Recovery",result.backupName());
    }
}

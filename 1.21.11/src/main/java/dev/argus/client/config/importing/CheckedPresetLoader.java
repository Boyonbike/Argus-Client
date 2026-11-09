package dev.argus.client.config.importing;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import static dev.argus.client.config.importing.ConfigImportModel.*;

/** Applies a fully validated snapshot as one client-thread operation, with best-effort rollback. */
public final class CheckedPresetLoader {
    private CheckedPresetLoader() {}
    public interface Backend {
        JsonObject snapshot();
        String backup(JsonObject snapshot) throws IOException;
        void settings(JsonObject desired);
        void enabled(JsonObject desired);
        void persist() throws IOException;
    }
    public static ImportedPresetStore.ImportLoadResult load(JsonObject desired,Backend backend) {
        JsonObject previous; String backup;
        try { previous=backend.snapshot();backup=backend.backup(previous.deepCopy()); }
        catch(Exception e) { return failure(null,"Couldn’t save recovery backup. Nothing has been loaded. "+e.getMessage()); }
        try { backend.settings(desired.deepCopy());backend.enabled(desired.deepCopy());backend.persist();return new ImportedPresetStore.ImportLoadResult(true,backup,List.of()); }
        catch(Exception e) {
            String message="Couldn’t load config: "+e.getMessage();
            try { backend.settings(previous);backend.enabled(previous);backend.persist();message+=" Previous config restored."; }
            catch(Exception restore) { message+=" Restore failed: "+restore.getMessage()+". Load recovery preset “"+backup+"”."; }
            return failure(backup,message);
        }
    }
    private static ImportedPresetStore.ImportLoadResult failure(String backup,String message) {
        return new ImportedPresetStore.ImportLoadResult(false,backup,List.of(new Issue("load","",Severity.WARNING,message)));
    }
}

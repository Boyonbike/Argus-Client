package dev.argus.client.gui;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PresetLoadControllerTest {
    static class Actions implements PresetLoadController.Actions {
        List<String> calls=new ArrayList<>();boolean failure,changed,exists;
        public boolean unchanged(){return !changed;}
        public boolean exists(String name){return exists;}
        public void save(String name)throws IOException {calls.add("save:"+name);if(failure)throw new IOException("disk full");}
        public String load(){calls.add("load");return "Loaded Normal.";}
    }
    @Test void requestAndCancellationNeverSaveOrLoad() {
        Actions actions=new Actions();var c=new PresetLoadController(actions);c.cancel();c.confirmLoadWithoutNamedSave();assertTrue(actions.calls.isEmpty());
    }
    @Test void savesBeforeLoadingAndPreventsDoubleSubmission() {
        Actions actions=new Actions();var c=new PresetLoadController(actions);c.chooseSaveCurrent();assertTrue(actions.calls.isEmpty());
        c.confirmSaveAndLoad("Before Normal");c.confirmSaveAndLoad("Before Normal");assertEquals(List.of("save:Before Normal","load"),actions.calls);
    }
    @Test void invalidDuplicateAndFailedSavesNeverLoad() {
        Actions actions=new Actions();var c=new PresetLoadController(actions);c.chooseSaveCurrent();c.confirmSaveAndLoad("../bad");assertTrue(actions.calls.isEmpty());
        actions.exists=true;c.confirmSaveAndLoad("Good");assertTrue(actions.calls.isEmpty());
        actions.exists=false;actions.failure=true;c.confirmSaveAndLoad("Good");assertEquals(List.of("save:Good"),actions.calls);assertTrue(c.message().contains("Nothing has been loaded"));
    }
    @Test void changedPresetInvalidatesConfirmationAndLoadWithoutSavingDoesNotNameSave() {
        Actions actions=new Actions();actions.changed=true;var c=new PresetLoadController(actions);c.confirmLoadWithoutNamedSave();assertTrue(actions.calls.isEmpty());
        Actions direct=new Actions();new PresetLoadController(direct).confirmLoadWithoutNamedSave();assertEquals(List.of("load"),direct.calls);
    }
}

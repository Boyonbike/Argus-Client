package dev.argus.client.config.importing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImportPreviewStateTest {
    @Test void sourceChangesAndCloseInvalidatePendingResults() {
        var state=new ImportPreviewState();long first=state.begin();state.invalidate();assertFalse(state.accepts(first));
        long second=state.begin();assertTrue(state.accepts(second));state.close();assertFalse(state.accepts(second));
    }
}

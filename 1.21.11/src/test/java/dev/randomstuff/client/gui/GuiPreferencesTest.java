package dev.randomstuff.client.gui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
final class GuiPreferencesTest {
    @TempDir Path temp;
    @Test void persistsThemeAccentFavouritesAndWindowPositions() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        p.mode=GuiPreferences.Mode.LIGHT; p.accent=GuiPreferences.Accent.GREEN;
        p.favourites.add("AutoCrystal");p.windows.put("Combat",new GuiPreferences.Window(24,80,true,12));p.save(path);
        var loaded=GuiPreferences.load(path);
        assertEquals(GuiPreferences.Mode.LIGHT,loaded.mode);assertEquals(GuiPreferences.Accent.GREEN,loaded.accent);
        assertTrue(loaded.favourites.contains("AutoCrystal"));assertEquals(24,loaded.windows.get("Combat").x());
    }
    @Test void invalidPreferencesFallBackToDarkAndLavender() throws Exception {
        var path=temp.resolve("gui.properties");Files.writeString(path,"mode=invalid\naccent=invalid\nwindow.Combat.x=bad\n");
        var p=GuiPreferences.load(path);assertEquals(GuiPreferences.Mode.DARK,p.mode);
        assertEquals(GuiPreferences.Accent.LAVENDER,p.accent);assertTrue(p.windows.isEmpty());
    }
}

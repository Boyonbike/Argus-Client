package dev.argus.client.gui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
final class GuiPreferencesTest {
    @TempDir Path temp;
    @Test void persistsThemeAccentFavouritesAndWindowPositions() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        p.mode=GuiPreferences.Mode.LIGHT; p.accent=GuiPreferences.Accent.GREEN;p.uiScale=.8;
        p.favourites.add("AutoCrystal");p.windows.put("Combat",new GuiPreferences.Window(24,80,true,12));p.save(path);
        var loaded=GuiPreferences.load(path);
        assertEquals(.8,loaded.uiScale,.001);assertEquals(GuiPreferences.Mode.LIGHT,loaded.mode);assertEquals(GuiPreferences.Accent.GREEN,loaded.accent);
        assertTrue(loaded.favourites.contains("AutoCrystal"));assertEquals(24,loaded.windows.get("Combat").x());
    }
    @Test void barTabsDefaultToTheMainPagesAndPersistInOrder() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertEquals(GuiPreferences.DEFAULT_BAR_TABS,p.barTabs);
        p.barTabs.clear();p.barTabs.add("Presets");p.barTabs.add("tab:Modules");p.barTabs.add("Discord");p.save(path);
        assertEquals(java.util.List.of("Presets","tab:Modules","Discord"),GuiPreferences.load(path).barTabs);
        p.barTabs.clear();p.save(path);
        assertTrue(GuiPreferences.load(path).barTabs.isEmpty(),"Removing every tab must survive a reload");
    }
    @Test void olderBarTabsMigrateToTheirNewHomes() throws Exception {
        var path=temp.resolve("gui.properties");Files.writeString(path,"barTabs=tab\\:Modules,tab\\:Friends,UI,Fonts,Colors,Discord\ntabStyle=ICONS\n");
        var p=GuiPreferences.load(path);
        // UI, Fonts and Colors all moved into Appearance, so they collapse into one tab.
        assertEquals(java.util.List.of("tab:Modules","Friends","Appearance","Discord"),p.barTabs);
        assertEquals(GuiPreferences.TabStyle.ICONS,p.tabStyle);
    }
    @Test void gridAndGapSettingsPersistAndClampTheGap() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertTrue(p.showGrid); assertEquals(0,p.gap);
        p.showGrid=false;p.gap=12;p.save(path);
        var loaded=GuiPreferences.load(path); assertFalse(loaded.showGrid); assertEquals(12,loaded.gap);
        Files.writeString(path,"gap=999\n"); assertEquals(GuiPreferences.MAX_GAP,GuiPreferences.load(path).gap);
    }
    @Test void savedColoursKeepAlphaAndOrder() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        p.savedColours.add(0x80FF0000);p.savedColours.add(0xFF00C0FF);p.save(path);
        assertEquals(java.util.List.of(0x80FF0000,0xFF00C0FF),GuiPreferences.load(path).savedColours);
    }
    @Test void navigationBarStateIsRemembered() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertFalse(p.barOpen,"The bar starts closed");
        p.barOpen=true;p.save(path); assertTrue(GuiPreferences.load(path).barOpen);
        p=GuiPreferences.load(path);p.barOpen=false;p.save(path);
        assertFalse(GuiPreferences.load(path).barOpen,"Closing the bar must replace the saved open state");
    }
    @Test void invalidPreferencesFallBackToDarkAndLavender() throws Exception {
        var path=temp.resolve("gui.properties");Files.writeString(path,"mode=invalid\naccent=invalid\nwindow.Combat.x=bad\n");
        var p=GuiPreferences.load(path);assertEquals(GuiPreferences.Mode.DARK,p.mode);
        assertEquals(GuiPreferences.Accent.LAVENDER,p.accent);assertTrue(p.windows.isEmpty());
    }
}

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
        assertEquals(java.util.List.of("tab:Modules","tab:Social","Appearance","Discord"),p.barTabs);
        assertEquals(GuiPreferences.TabStyle.ICONS,p.tabStyle);
    }
    @Test void friendsChatAndDiscordSyncTabsBecomeSocialAndDiscord() throws Exception {
        var path=temp.resolve("gui.properties");Files.writeString(path,"barTabs=Friends,tab\\:Chat,DiscordSync,Discord,tab\\:HUD\n");
        // Friends and the Chat page are both the Social tab now, and Discord Sync shares the Discord page.
        assertEquals(java.util.List.of("tab:Social","Discord","tab:HUD"),GuiPreferences.load(path).barTabs);
    }
    @Test void socialTabChoicePersists() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertEquals(GuiPreferences.SocialTab.MENU,p.socialTab,"The Social tab opens the menu by default");
        p.socialTab=GuiPreferences.SocialTab.SETTINGS;p.save(path);
        assertEquals(GuiPreferences.SocialTab.SETTINGS,GuiPreferences.load(path).socialTab);
    }
    @Test void gridAndGapSettingsPersistAndClampTheGap() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertTrue(p.showGrid); assertEquals(0,p.gap);
        p.showGrid=false;p.gap=12;p.save(path);
        var loaded=GuiPreferences.load(path); assertFalse(loaded.showGrid); assertEquals(12,loaded.gap);
        Files.writeString(path,"gap=999\n"); assertEquals(GuiPreferences.MAX_GAP,GuiPreferences.load(path).gap);
    }
    @Test void hudGapIsSeparateFromWindowGapAndDefaultsOff() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertEquals(0,p.hudGap);
        p.gap=10;p.save(path);
        assertEquals(0,GuiPreferences.load(path).hudGap);
        p.hudGap=8;p.save(path);
        var loaded=GuiPreferences.load(path); assertEquals(10,loaded.gap); assertEquals(8,loaded.hudGap);
        Files.writeString(path,"hudGap=999\n"); assertEquals(GuiPreferences.MAX_GAP,GuiPreferences.load(path).hudGap);
        // The HUD gap moves in whole grid steps; an older in-between value rounds to the nearest one.
        Files.writeString(path,"hudGap=6\n"); assertEquals(8,GuiPreferences.load(path).hudGap);
        Files.writeString(path,"hudGap=5\n"); assertEquals(4,GuiPreferences.load(path).hudGap);
    }
    @Test void fitHudToGridDefaultsOnAndPersists() throws Exception {
        var path=temp.resolve("gui.properties"); var p=GuiPreferences.load(path);
        assertTrue(p.hudFitGrid);
        p.hudFitGrid=false;p.save(path);
        assertFalse(GuiPreferences.load(path).hudFitGrid);
    }
    @Test void hudSizesRoundUpToWholeGridSteps() {
        assertEquals(7f,HudGrid.fit(5.2f,3.5f),1e-4);
        assertEquals(7f,HudGrid.fit(7f,3.5f),1e-4);
        assertEquals(10.5f,HudGrid.fit(7.01f,3.5f),1e-4);
        assertEquals(0f,HudGrid.fit(0f,3.5f));
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

    @Test
    void compactMovesAreSavedApartFromTheFullSizeLayout(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var path = temp.resolve("gui.properties");
        var p = new GuiPreferences();
        p.windows.put("Combat", new GuiPreferences.Window(500, 80, false, 0, 1000, 30, 140, 420));
        p.windows.put("Render", new GuiPreferences.Window(10, 64, false, 0, WindowFit.AUTO));
        p.save(path);
        var loaded = GuiPreferences.load(path);
        assertEquals(new GuiPreferences.Window(500, 80, false, 0, 1000, 30, 140, 420), loaded.windows.get("Combat"));
        assertEquals(WindowFit.AUTO, loaded.windows.get("Render").layoutWidth());
        assertEquals(0, loaded.windows.get("Render").compactWidth());
    }
}

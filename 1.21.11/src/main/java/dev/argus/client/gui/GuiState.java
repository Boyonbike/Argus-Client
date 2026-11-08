package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

final class GuiState {
    private static GuiPreferences preferences;
    static String saveError = "";
    private GuiState() {}
    static Path path() { return FabricLoader.getInstance().getGameDir().resolve("argusclient/gui.properties"); }
    static GuiPreferences preferences() {
        if (preferences == null) preferences = GuiPreferences.load(path());
        return preferences;
    }
    private static UiTheme shownTheme, targetTheme;
    private static long themeChanged;
    /** The palette, cross-faded for a quarter second whenever mode or accent changes. */
    static UiTheme theme() {
        UiTheme target = UiTheme.of(preferences());
        if (targetTheme == null) { targetTheme = shownTheme = target; }
        if (!target.equals(targetTheme)) { shownTheme = blendedTheme(); targetTheme = target; themeChanged = System.nanoTime(); }
        return blendedTheme();
    }
    private static UiTheme blendedTheme() {
        float t = UiAnim.easeOutCubic((System.nanoTime() - themeChanged) / 2.5e8f);
        return t >= 1 ? targetTheme : UiTheme.blend(shownTheme, targetTheme, t);
    }
    static void save() {
        try { preferences().save(path()); saveError = ""; }
        catch (IOException failure) { saveError = "Could not save GUI preferences."; }
    }
    static List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        if (ClientContext.moduleRegistry != null)
            for (Object value : ClientContext.moduleRegistry.getRegistry()) if (value instanceof Module module) modules.add(module);
        modules.sort(Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER));
        return modules;
    }
    static Module module(String name) { return modules().stream().filter(module -> module.getName().equals(name)).findFirst().orElse(null); }
    static String title(String name) { String text=name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_',' '); return text.isEmpty()?text:text.substring(0,1).toUpperCase(Locale.ROOT)+text.substring(1); }
}

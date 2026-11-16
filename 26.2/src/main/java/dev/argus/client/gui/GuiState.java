package dev.argus.client.gui;

import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.modules.client.DiscordModule;
import dev.argus.client.modules.client.DiscordSyncModule;

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
        if (targetTheme == null) { targetTheme = shownTheme = target; syncHudColours(); }
        if (!target.equals(targetTheme)) { shownTheme = blendedTheme(); targetTheme = target; themeChanged = System.nanoTime(); syncHudColours(); }
        return blendedTheme();
    }

    /**
     * While "HUD uses accent" is on, copies the menu accent (for the current Light/Dark mode) into the colour
     * settings the HUD draws with: the HUD text colour and its fade partner, the client theme colour that synced
     * colour settings follow, and the HUD editor's selection colour. Called whenever mode or accent change.
     */
    static void syncHudColours() {
        GuiPreferences p = preferences();
        if (!p.hudFollowsAccent) return;
        int on = UiTheme.of(p).on();
        java.awt.Color accent = new java.awt.Color(on, false);
        java.awt.Color partner = new java.awt.Color(UiDraw.mix(on, 0xff000000, .45f), false);
        var hud = ClientContext.getModule(dev.argus.client.modules.client.HUDModule.class);
        if (hud != null) { hud.colorSetting.setValue(accent); hud.secondColorSetting.setValue(partner); }
        var colours = ClientContext.getModule(dev.argus.client.modules.client.ColorsModule.class);
        if (colours != null) colours.themeColorSetting.setValue(accent);
        var ui = dev.argus.client.modules.client.UIModule.INSTANCE;
        if (ui != null) ui.colorSetting.setValue(accent);
    }
    private static UiTheme blendedTheme() {
        float t = UiAnim.eased(themeChanged, .25f);
        return t >= 1 ? targetTheme : UiTheme.blend(shownTheme, targetTheme, t);
    }
    static void save() {
        try { preferences().save(path()); saveError = ""; }
        catch (IOException failure) { saveError = "Could not save GUI preferences."; }
    }
    // The module set is fixed after startup, but menus ask for it (and look modules up by name) while rendering,
    // so the sorted list and name index are rebuilt only when the registry's contents change.
    private static List<Module> sortedModules = List.of();
    private static Map<String, Module> modulesByName = Map.of();
    private static Object cachedRegistry;
    private static int cachedRegistrySize = -1;
    private static List<Module> sortedModules() {
        var registry = ClientContext.moduleRegistry;
        if (registry == null) return List.of();
        Collection<?> entries = registry.getRegistry();
        if (registry != cachedRegistry || entries.size() != cachedRegistrySize) {
            List<Module> modules = new ArrayList<>();
            for (Object value : entries) if (value instanceof Module module && !hidden(module)) modules.add(module);
            modules.sort(Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER));
            Map<String, Module> byName = new HashMap<>();
            for (Module module : modules) byName.putIfAbsent(module.getName(), module);
            sortedModules = List.copyOf(modules); modulesByName = byName;
            cachedRegistry = registry; cachedRegistrySize = entries.size();
        }
        return sortedModules;
    }
    static List<Module> modules() { return new ArrayList<>(sortedModules()); }
    /**
     * BetterChat runs inside Chat Tools: its switch follows Chat Tools and its settings are on the Social page. Discord
     * Sync shares the Discord page with Rich Presence, so it has no section of its own.
     */
    static boolean hidden(Module module) {
        if (module instanceof BetterChatModule) return ClientContext.getModule(ChatToolsModule.class) != null;
        if (module instanceof DiscordSyncModule) return ClientContext.getModule(DiscordModule.class) != null;
        return false;
    }
    static Module module(String name) { sortedModules(); return modulesByName.get(name); }
    /** Chat Tools is called Social everywhere in the menu; its saved name stays ChatTools so configs keep working. */
    // Titles are formatted for every visible row each frame; the regex is compiled once and results are memoised.
    private static final java.util.regex.Pattern CAMEL_BOUNDARY = java.util.regex.Pattern.compile("([a-z0-9])([A-Z])");
    private static final Map<String, String> titles = new java.util.concurrent.ConcurrentHashMap<>();
    static String title(String name) {
        String cached = titles.get(name);
        if (cached != null) return cached;
        String result = formatTitle(name);
        if (titles.size() >= 4096) titles.clear();
        titles.put(name, result);
        return result;
    }
    private static String formatTitle(String name) { if ("ChatTools".equals(name)) return "Social"; String text=CAMEL_BOUNDARY.matcher(name).replaceAll("$1 $2").replace('_',' '); return text.isEmpty()?text:text.substring(0,1).toUpperCase(Locale.ROOT)+text.substring(1); }
}

package dev.argus.client.gui;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/** User-facing labels deliberately use Light/Dark and accent colours, not palette flavour names. */
public final class GuiPreferences {
    public enum Mode { DARK, LIGHT }
    public enum Accent { LAVENDER, BLUE, GREEN, PEACH, PINK, RED }
    /**
     * A saved window. {@code layoutWidth} is the screen width it was arranged on, so a smaller
     * screen can show it squeezed without changing it ({@link WindowFit}); -1 follows the
     * default layout and 0 means unknown (saved by an older version). {@code compactX/Y} is where
     * it was moved to in the compact view, kept apart from the full-size arrangement;
     * {@code compactWidth} is the screen width of that move, 0 when it was never moved there.
     */
    public record Window(int x, int y, boolean collapsed, int scroll, int layoutWidth, int compactX, int compactY, int compactWidth) {
        public Window(int x, int y, boolean collapsed, int scroll) { this(x, y, collapsed, scroll, WindowFit.UNKNOWN); }
        public Window(int x, int y, boolean collapsed, int scroll, int layoutWidth) { this(x, y, collapsed, scroll, layoutWidth, 0, 0, 0); }
    }
    public Mode mode = Mode.DARK;
    public Accent accent = Accent.LAVENDER;
    public boolean snap = true;
    /** Whether the navigation bar was left open; the menu reopens it (with its animation) next time. */
    public boolean barOpen = false;
    /** Draw the snap grid behind windows while one is being dragged. */
    public boolean showGrid = true;
    /** Minimum space (GUI units) kept between windows while dragging; 0 turns it off. */
    public int gap = 0;
    public static final int MAX_GAP = 24;
    /** Colours saved from the colour picker (ARGB), newest last. */
    public final List<Integer> savedColours = new ArrayList<>();
    public static final int MAX_SAVED_COLOURS = 18;
    public double uiScale = 1;
    public final Set<String> favourites = new HashSet<>();
    /** Default tabs between the face and settings circles: the Modules page, the Social menu, and the HUD editor page. */
    public static final List<String> DEFAULT_BAR_TABS = List.of("tab:Modules", "tab:Social", "tab:HUD");
    /** How bar tabs are labelled. Names fall back to icons when they don't fit the screen. */
    public enum TabStyle { NAMES, ICONS }
    public TabStyle tabStyle = TabStyle.NAMES;
    /** What the HUD tab in the top bar opens: the on-screen editor or the HUD settings page. */
    public enum HudTab { EDITOR, SETTINGS }
    public HudTab hudTab = HudTab.EDITOR;
    /** What the Social tab in the top bar opens: the Social menu window or the Social settings page. */
    public enum SocialTab { MENU, SETTINGS }
    public SocialTab socialTab = SocialTab.MENU;
    /**
     * The tabs between the face and settings circles, in order: fixed pages as {@code tab:<name>},
     * Settings sections (Appearance, Presets or a client module name) by name.
     */
    public final List<String> barTabs = new ArrayList<>(DEFAULT_BAR_TABS);
    public final Map<String, Window> windows = new HashMap<>();

    public static GuiPreferences load(Path path) {
        GuiPreferences result = new GuiPreferences(); Properties values = new Properties();
        if (!Files.isRegularFile(path)) return result;
        try (Reader reader = Files.newBufferedReader(path)) { values.load(reader); }
        catch (IOException | IllegalArgumentException failure) { return result; }
        try { result.mode = Mode.valueOf(values.getProperty("mode", "DARK")); } catch (IllegalArgumentException ignored) {}
        try { result.accent = Accent.valueOf(values.getProperty("accent", "LAVENDER")); } catch (IllegalArgumentException ignored) {}
        try { result.tabStyle = TabStyle.valueOf(values.getProperty("tabStyle", "NAMES")); } catch (IllegalArgumentException ignored) {}
        try { result.hudTab = HudTab.valueOf(values.getProperty("hudTab", "EDITOR")); } catch (IllegalArgumentException ignored) {}
        try { result.socialTab = SocialTab.valueOf(values.getProperty("socialTab", "MENU")); } catch (IllegalArgumentException ignored) {}
        result.snap = Boolean.parseBoolean(values.getProperty("snap", "true"));
        result.barOpen = Boolean.parseBoolean(values.getProperty("barOpen", "false"));
        result.showGrid = Boolean.parseBoolean(values.getProperty("showGrid", "true"));
        for (String hex : values.getProperty("savedColours", "").split(",")) {
            if (hex.isBlank() || result.savedColours.size() >= MAX_SAVED_COLOURS) continue;
            try { result.savedColours.add((int) Long.parseLong(hex.trim(), 16)); } catch (NumberFormatException ignored) {}
        }
        try { result.gap = Math.clamp(Integer.parseInt(values.getProperty("gap", "0")), 0, MAX_GAP); } catch (IllegalArgumentException ignored) {}
        try { double scale=Double.parseDouble(values.getProperty("uiScale","1")); if(Double.isFinite(scale))result.uiScale=Math.clamp(scale,.65,1.4); } catch(IllegalArgumentException ignored) {}
        if (values.containsKey("barTabs")) {
            result.barTabs.clear();
            for (String raw : values.getProperty("barTabs").split(",")) {
                // Friends and the Chat Tools page became the Social tab; the UI, Colors and Fonts modules now live under
                // Appearance; Discord Sync shares the Discord page.
                String tab = switch (raw.trim()) {
                    case "tab:Friends", "Friends", "tab:Chat" -> "tab:Social";
                    case "UI", "Colors", "Fonts" -> "Appearance";
                    case "DiscordSync" -> "Discord";
                    default -> raw.trim();
                };
                if (!tab.isBlank() && !result.barTabs.contains(tab)) result.barTabs.add(tab);
            }
        }
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("favourite.") && Boolean.parseBoolean(values.getProperty(key))) result.favourites.add(key.substring(10));
            if (key.startsWith("window.") && key.endsWith(".x")) {
                String name = key.substring(7, key.length() - 2), prefix = "window." + name;
                try {
                    result.windows.put(name, new Window(Integer.parseInt(values.getProperty(key)),
                            Integer.parseInt(values.getProperty(prefix + ".y")),
                            Boolean.parseBoolean(values.getProperty(prefix + ".collapsed", "false")),
                            Math.max(0, Integer.parseInt(values.getProperty(prefix + ".scroll", "0"))),
                            Math.max(WindowFit.AUTO, Integer.parseInt(values.getProperty(prefix + ".layoutWidth", "0"))),
                            Integer.parseInt(values.getProperty(prefix + ".compactX", "0")),
                            Integer.parseInt(values.getProperty(prefix + ".compactY", "0")),
                            Math.max(0, Integer.parseInt(values.getProperty(prefix + ".compactWidth", "0")))));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath(); Files.createDirectories(target.getParent());
        Properties values = new Properties();
        values.setProperty("uiScale",Double.toString(uiScale));
        values.setProperty("mode", mode.name()); values.setProperty("accent", accent.name()); values.setProperty("snap", Boolean.toString(snap)); values.setProperty("barOpen", Boolean.toString(barOpen));
        values.setProperty("showGrid", Boolean.toString(showGrid));
        values.setProperty("savedColours", String.join(",", savedColours.stream().map(c -> String.format("%08X", c)).toList())); values.setProperty("gap", Integer.toString(gap));
        for (String module : favourites) values.setProperty("favourite." + module, "true");
        values.setProperty("barTabs", String.join(",", barTabs));
        values.setProperty("tabStyle", tabStyle.name());
        values.setProperty("hudTab", hudTab.name());
        values.setProperty("socialTab", socialTab.name());
        windows.forEach((name, window) -> {
            String prefix = "window." + name;
            values.setProperty(prefix + ".x", Integer.toString(window.x)); values.setProperty(prefix + ".y", Integer.toString(window.y));
            values.setProperty(prefix + ".collapsed", Boolean.toString(window.collapsed)); values.setProperty(prefix + ".scroll", Integer.toString(window.scroll));
            values.setProperty(prefix + ".layoutWidth", Integer.toString(window.layoutWidth));
            if (window.compactWidth > 0) {
                values.setProperty(prefix + ".compactX", Integer.toString(window.compactX)); values.setProperty(prefix + ".compactY", Integer.toString(window.compactY));
                values.setProperty(prefix + ".compactWidth", Integer.toString(window.compactWidth));
            }
        });
        Path staged = Files.createTempFile(target.getParent(), "gui-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(staged)) { values.store(writer, "Argus Client GUI"); }
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException failure) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staged); }
    }
}

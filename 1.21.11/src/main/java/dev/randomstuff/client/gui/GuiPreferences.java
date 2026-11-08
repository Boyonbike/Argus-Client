package dev.randomstuff.client.gui;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/** User-facing labels deliberately use Light/Dark and accent colours, not palette flavour names. */
public final class GuiPreferences {
    public enum Mode { DARK, LIGHT }
    public enum Accent { LAVENDER, BLUE, GREEN, PEACH, PINK, RED }
    public record Window(int x, int y, boolean collapsed, int scroll) {}
    public Mode mode = Mode.DARK;
    public Accent accent = Accent.LAVENDER;
    public boolean snap = true;
    public double uiScale = 1;
    public final Set<String> favourites = new HashSet<>();
    public final Map<String, Window> windows = new HashMap<>();

    public static GuiPreferences load(Path path) {
        GuiPreferences result = new GuiPreferences(); Properties values = new Properties();
        if (!Files.isRegularFile(path)) return result;
        try (Reader reader = Files.newBufferedReader(path)) { values.load(reader); }
        catch (IOException | IllegalArgumentException failure) { return result; }
        try { result.mode = Mode.valueOf(values.getProperty("mode", "DARK")); } catch (IllegalArgumentException ignored) {}
        try { result.accent = Accent.valueOf(values.getProperty("accent", "LAVENDER")); } catch (IllegalArgumentException ignored) {}
        result.snap = Boolean.parseBoolean(values.getProperty("snap", "true"));
        try { double scale=Double.parseDouble(values.getProperty("uiScale","1")); if(Double.isFinite(scale))result.uiScale=Math.clamp(scale,.65,1.4); } catch(IllegalArgumentException ignored) {}
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("favourite.") && Boolean.parseBoolean(values.getProperty(key))) result.favourites.add(key.substring(10));
            if (key.startsWith("window.") && key.endsWith(".x")) {
                String name = key.substring(7, key.length() - 2), prefix = "window." + name;
                try {
                    result.windows.put(name, new Window(Integer.parseInt(values.getProperty(key)),
                            Integer.parseInt(values.getProperty(prefix + ".y")),
                            Boolean.parseBoolean(values.getProperty(prefix + ".collapsed", "false")),
                            Math.max(0, Integer.parseInt(values.getProperty(prefix + ".scroll", "0")))));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath(); Files.createDirectories(target.getParent());
        Properties values = new Properties();
        values.setProperty("uiScale",Double.toString(uiScale));
        values.setProperty("mode", mode.name()); values.setProperty("accent", accent.name()); values.setProperty("snap", Boolean.toString(snap));
        for (String module : favourites) values.setProperty("favourite." + module, "true");
        windows.forEach((name, window) -> {
            String prefix = "window." + name;
            values.setProperty(prefix + ".x", Integer.toString(window.x)); values.setProperty(prefix + ".y", Integer.toString(window.y));
            values.setProperty(prefix + ".collapsed", Boolean.toString(window.collapsed)); values.setProperty(prefix + ".scroll", Integer.toString(window.scroll));
        });
        Path staged = Files.createTempFile(target.getParent(), "gui-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(staged)) { values.store(writer, "Random Stuff Client GUI"); }
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException failure) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staged); }
    }
}

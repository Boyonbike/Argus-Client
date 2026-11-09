package dev.argus.client.gui;

import dev.argus.client.config.importing.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Files are read in the background; saving this preview never loads it. */
final class ConfigImportScreen extends UiScreen {
    private enum Kind { HEADER, ENABLED, WARNING, INFO, CHOICE }
    private record Row(String text, Kind kind, Path choice) {}
    private record ReadResult(List<Path> candidates, ConfigImportModel.SourceConfig source) {}
    private static final int ROW = 18, CARD_Y = 34, CARD_H = 92;
    private final Consumer<String> saved;
    private final ImportPreviewState session = new ImportPreviewState();
    private final List<Row> rows = new ArrayList<>();
    private UiInput pathInput, nameInput;
    private UiButton save, previewButton;
    private String path = "", name = "Imported config", message = "Choose a Meteor NBT file, profile folder or Argus JSON config.";
    private boolean importBinds, busy, savedAlready, failed;
    private ConfigImportConverter.ImportPreview preview;
    private int left, top, panelWidth, panelHeight, listTop, listHeight, scroll;
    private float shownScroll;

    ConfigImportScreen(Screen parent, Consumer<String> saved) { super("Import config", parent, "Settings"); this.saved = saved; }

    @Override protected void buildContent() {
        panelWidth = Math.min(520, width - 24); left = (width - panelWidth) / 2; top = 44; panelHeight = height - top - 24;
        int cx = left + 22, cw = panelWidth - 44, cardY = top + CARD_Y;
        pathInput = new UiInput(cx, cardY + 40, cw - 96, 12, "Paste a file or folder path, or drop a file here").withIcon("search");
        pathInput.place(cx, cardY + 40, cw - 96);
        pathInput.setMaxLength(4096); pathInput.setValue(path);
        pathInput.setResponder(value -> { path = value; invalidate(); }); addRenderableWidget(pathInput);
        previewButton = addRenderableWidget(new UiButton(cx + cw - 88, cardY + 35, 88, 22, "Preview", true, "search", this::read));
        // Whole Meteor folders (modules, friends, enemies, waypoints, macros) from another launcher instance.
        addRenderableWidget(new UiButton(cx + cw - 112, cardY + 6, 112, 20, "From an instance", false, "compass",
                () -> hostOpen(new MeteorInstanceImportScreen(this, saved))));
        nameInput = new UiInput(cx, cardY + 68, cw - 128, 12, "Name for the imported preset");
        nameInput.place(cx, cardY + 68, cw - 128);
        nameInput.setMaxLength(32); nameInput.setValue(name); nameInput.setResponder(value -> name = value); addRenderableWidget(nameInput);
        int bottom = top + panelHeight - 32, bw = 84;
        save = addRenderableWidget(new UiButton(left + panelWidth - 12 - bw, bottom, bw, 22, "Save preset", true, "star", this::save));
        addRenderableWidget(new UiButton(left + panelWidth - 18 - bw * 2, bottom, bw, 22, "Cancel", this::onClose));
        listTop = cardY + CARD_H + 26; listHeight = Math.max(ROW, bottom - 8 - listTop);
        setInitialFocus(pathInput); updateButtons();
    }

    private float bindsX() { return left + panelWidth - 22 - 112; }
    private float bindsY() { return top + CARD_Y + 66; }

    private void invalidate() { session.invalidate(); preview = null; rows.clear(); scroll = 0; busy = false; savedAlready = false; failed = false; message = "Preview again after changing the source or bind option."; updateButtons(); }
    private void updateButtons() { if (save != null) save.active = preview != null && !busy && !savedAlready; if (previewButton != null) previewButton.active = !busy; }

    private void read() {
        if (busy) return; Path source;
        try { source = Path.of(path.trim()); if (path.isBlank()) throw new IllegalArgumentException("Enter a file or folder path."); }
        catch (RuntimeException e) { fail(e.getMessage()); return; }
        ArgusModuleCatalog catalog;
        try { catalog = ArgusModuleCatalog.capture(); }
        catch (Exception e) { fail("Couldn’t read Argus defaults: " + e.getMessage()); return; }
        long token = session.begin(); preview = null; rows.clear(); scroll = 0; busy = true; failed = false; message = "Reading config…"; updateButtons();
        CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.isDirectory(source)) { List<Path> candidates = ExternalConfigReader.candidates(source); if (candidates.size() != 1) return new ReadResult(candidates, null); }
                return new ReadResult(List.of(), ExternalConfigReader.read(source));
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!session.accepts(token) || minecraft.screen != this) return; busy = false;
            if (error != null) { fail("Couldn’t read config: " + cause(error).getMessage()); updateButtons(); return; }
            if (result.source() == null) {
                if (!result.candidates().isEmpty()) rows.add(new Row("Configs in this folder (" + result.candidates().size() + ")", Kind.HEADER, null));
                for (Path candidate : result.candidates()) rows.add(new Row(candidate.getParent().getFileName() + " / " + candidate.getFileName(), Kind.CHOICE, candidate));
                if (rows.isEmpty()) fail("No module config found in this folder."); else message = "Choose a file or profile below to preview it.";
                updateButtons(); return;
            }
            try {
                preview = ConfigImportConverter.convert(result.source(), catalog, importBinds);
                long warnings = preview.issues().stream().filter(i -> i.severity() == ConfigImportModel.Severity.WARNING).count();
                if (warnings > 0) {
                    rows.add(new Row("Needs attention (" + warnings + ")", Kind.HEADER, null));
                    for (var issue : preview.issues()) if (issue.severity() == ConfigImportModel.Severity.WARNING) rows.add(new Row(issue.sourceId() + " · " + issue.field() + " · " + issue.message(), Kind.WARNING, null));
                }
                rows.add(new Row("Modules to enable (" + preview.enabledTargets().size() + ")", Kind.HEADER, null));
                preview.enabledTargets().stream().sorted().forEach(id -> rows.add(new Row(id, Kind.ENABLED, null)));
                if (preview.issues().size() > warnings) {
                    rows.add(new Row("Settings and compatibility (" + (preview.issues().size() - warnings) + ")", Kind.HEADER, null));
                    for (var issue : preview.issues()) if (issue.severity() != ConfigImportModel.Severity.WARNING) rows.add(new Row(issue.sourceId() + " · " + issue.field() + " · " + issue.message(), Kind.INFO, null));
                }
                message = "Saves to Modules presets only · HUD and appearance stay as they are.";
                if (name.equals("Imported config")) {
                    String n = source.getFileName().toString().replaceFirst("\\.[^.]+$", ""); if (n.equals("modules")) n = source.getParent().getFileName().toString();
                    n = n.replaceAll("[^A-Za-z0-9 _.\\-]", " ").trim(); if (n.length() > 32) n = n.substring(0, 32);
                    if (ImportedPresetStore.validName(n)) { name = n; nameInput.setValue(n); }
                }
            } catch (Exception e) { preview = null; fail("Couldn’t convert config: " + e.getMessage()); }
            updateButtons();
        }));
    }
    private void fail(String text) { message = text; failed = true; }
    private static Throwable cause(Throwable e) { while (e.getCause() != null) e = e.getCause(); return e; }
    private void save() {
        if (preview == null || busy || savedAlready) return;
        try { ImportedPresetStore.save(name.trim(), preview); savedAlready = true; saved.accept(name.trim()); minecraft.setScreen(parent); }
        catch (Exception e) { fail("Couldn’t save preset: " + e.getMessage()); updateButtons(); }
    }

    private int maxScroll() { return Math.max(0, rows.size() * ROW - listHeight); }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // Header.
        float bh = UiAnim.smooth("imp.back", UiDraw.over(mx, my, left + 10, top + 10, 18, 18) ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.bold(g, "Import config", left + 36, top + 19, UiDraw.TITLE, t.text());
        float right = left + panelWidth - 12;
        right -= ProfileManagerScreen.chip(g, "Argus JSON", right - UiDraw.width("Argus JSON", 5.5f) - 10, top + 19, t.accent()) + 4;
        ProfileManagerScreen.chip(g, "Meteor", right - UiDraw.width("Meteor", 5.5f) - 10, top + 19, t.blue());
        // Source card: path + preview, then name + binds.
        float cardY = top + CARD_Y;
        UiDraw.card(g, left + 12, cardY, panelWidth - 24, CARD_H, "misc", "Source", "Preview first · nothing changes until you load the saved preset", 124, t);
        float bx = bindsX(), by = bindsY();
        boolean bindHover = UiDraw.over(mx, my, bx, by - 2, 112, 20);
        UiDraw.rounded(g, bx, by - 2, 112, 20, 7, UiDraw.mix(t.crust(), t.mantle(), bindHover ? .6f : 0));
        UiDraw.outline(g, bx, by - 2, 112, 20, 7, .7f, UiDraw.alpha(t.overlay(), .7f));
        UiDraw.text(g, "Import binds", bx + 8, by + 8, UiDraw.SMALL, importBinds ? t.text() : t.muted());
        UiDraw.toggle(g, bx + 112 - 26, by + 3, UiAnim.smooth("imp.binds", importBinds ? 1 : 0, 16), bindHover, t);
        if (bindHover) tooltip("imp.binds", importBinds ? "Importing compatible binds" : "Keeping your current binds", "Turn on to copy keybinds for modules Argus also has");
        // Preview list.
        float headY = listTop - 12;
        UiDraw.bold(g, "Preview", left + 14, headY, UiDraw.TEXT, t.text());
        if (preview != null) UiDraw.right(g, preview.enabledTargets().size() + " modules on · " + preview.copiedSettings() + " settings copied · " + preview.defaultedSettings() + " at defaults",
                left + panelWidth - 14, headY, UiDraw.SMALL, t.accent());
        UiDraw.rect(g, left + 12, listTop - 4, panelWidth - 24, .6f, UiDraw.alpha(t.overlay(), .6f));
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        g.enableScissor(left + 4, listTop, left + panelWidth - 4, listTop + listHeight);
        for (int i = 0; i < rows.size(); i++) {
            float y = listTop + i * ROW - shownScroll; if (y + ROW < listTop || y > listTop + listHeight) continue;
            Row row = rows.get(i);
            float cy = y + ROW / 2f, tx = left + 30;
            boolean hover = UiDraw.over(mx, my, left + 12, y, panelWidth - 24, ROW) && my >= listTop && my < listTop + listHeight;
            switch (row.kind()) {
                case HEADER -> { UiDraw.bold(g, UiDraw.fit(row.text(), panelWidth - 32, UiDraw.SMALL), left + 16, cy + 2, UiDraw.SMALL, t.muted()); continue; }
                case CHOICE -> {
                    float hv = UiAnim.smooth("imp.row." + i, hover ? 1 : 0, 20);
                    UiDraw.rounded(g, left + 12, y + 1, panelWidth - 24, ROW - 2, 5, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .8f));
                    UiDraw.icon(g, "chevron_right", left + 17, cy - 4, 8, UiDraw.mix(t.muted(), t.accent(), hv));
                }
                case ENABLED -> UiDraw.icon(g, "check", left + 17, cy - 4, 8, t.green());
                case WARNING -> { UiDraw.circle(g, left + 21, cy, 4.5f, UiDraw.alpha(t.yellow(), .2f)); UiDraw.center(g, "!", left + 16.5f, cy, 9, 6f, t.yellow()); }
                case INFO -> UiDraw.circle(g, left + 21, cy, 1.6f, UiDraw.alpha(t.muted(), .7f));
            }
            int colour = row.kind() == Kind.WARNING ? t.yellow() : row.kind() == Kind.INFO ? t.muted() : t.text();
            String shown = UiDraw.fit(row.text(), panelWidth - 50, UiDraw.SMALL);
            UiDraw.text(g, shown, tx, cy, UiDraw.SMALL, colour);
            if (hover && !shown.equals(row.text())) tooltip("import.row." + i, row.text());
        }
        if (rows.isEmpty()) {
            float cy = listTop + Math.min(listHeight / 2f - 16, 24);
            UiDraw.icon(g, busy ? "reset" : "star", left + panelWidth / 2f - 7, cy - 6, 14, UiDraw.alpha(failed ? t.red() : t.muted(), .45f));
            UiDraw.center(g, busy ? "Reading config…" : failed ? "Nothing to preview" : "Nothing previewed yet", left, cy + 18, panelWidth, t.muted());
            UiDraw.center(g, busy ? "This only reads the file." : "Choose a source above, then press Preview.", left, cy + 30, panelWidth, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        }
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, left + 4, listTop, panelWidth - 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, left + 4, listTop + listHeight - 8, panelWidth - 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base());
        // Footer: status on the left, actions on the right.
        float fy = top + panelHeight - 21, room = panelWidth - 28 - 180;
        String shown = UiDraw.fit(message, room, UiDraw.SMALL);
        UiDraw.text(g, shown, left + 14, fy, UiDraw.SMALL, failed ? t.red() : savedAlready || preview != null ? t.green() : t.muted());
        if (!shown.equals(message) && UiDraw.over(mx, my, left + 10, fy - 8, room, 16)) tooltip("import.status", message);
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();
        if (UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { onClose(); return true; }
        if (UiDraw.over(mx, my, bindsX(), bindsY() - 2, 112, 20)) { importBinds = !importBinds; invalidate(); return true; }
        if (UiDraw.over(mx, my, left + 12, listTop, panelWidth - 24, listHeight)) {
            int index = (int) ((my - listTop + shownScroll) / ROW);
            if (index >= 0 && index < rows.size() && rows.get(index).choice() != null) { pathInput.setValue(rows.get(index).choice().toString()); read(); }
            return true;
        }
        return false;
    }
    @Override protected boolean mouseScrolledUi(double x, double y, double h, double v) { scroll = Math.clamp(scroll - (int) (v * ROW * 2), 0, maxScroll()); return true; }
    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && pathInput.isFocused()) { read(); return true; }
        if (event.key() == 265 || event.key() == 264) { scroll = Math.clamp(scroll + (event.key() == 265 ? -ROW : ROW), 0, maxScroll()); return true; }
        return super.keyPressed(event);
    }
    @Override public void onFilesDrop(List<Path> files) { if (files.size() == 1) pathInput.setValue(files.getFirst().toString()); }
    @Override public void removed() { session.close(); super.removed(); }
}

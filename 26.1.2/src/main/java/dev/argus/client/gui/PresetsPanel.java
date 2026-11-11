package dev.argus.client.gui;

import dev.argus.client.settings.options.PresetCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Presets section of the Settings tab: save the current config under a name, load it, and bind
 * it to any number of servers or singleplayer worlds so it loads automatically when you join.
 */
final class PresetsPanel {
    private static final int ROW = 32, EXPANDED = 28;
    private static PresetCategory category = PresetCategory.ALL;

    private record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(double mx, double my) { return UiDraw.over(mx, my, x, y, w, h); }
    }

    private final UiScreen host;
    private final UiInput nameInput, addressInput;
    private final List<Hit> hits = new ArrayList<>();
    private int x, y, w, h;
    private String selected, confirmDelete, recoveryName, status = "";
    private boolean statusError;
    private long confirmAt, statusAt;
    private int scroll;
    private float shownScroll;
    private float chipIndicator = Float.NaN, chipIndicatorW;

    PresetsPanel(UiScreen host) {
        this.host = host;
        nameInput = new UiInput(0, 0, 100, 12, "Name for the current config…");
        nameInput.setMaxLength(32);
        addressInput = new UiInput(0, 0, 100, 12, "Server address, e.g. play.example.net");
        addressInput.setMaxLength(128);
    }

    void layout(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        nameInput.place(x, y + 122, w - 92);
        host.hostAdd(nameInput);
        if (selected != null) host.hostAdd(addressInput);
    }

    void detach() { host.hostRemove(nameInput); host.hostRemove(addressInput); }

    private static String label(PresetCategory c) { return c == PresetCategory.ALL ? "Everything" : c == PresetCategory.HUD ? "HUD" : c.getName(); }

    private void status(String text, boolean error) { status = text; statusError = error; statusAt = System.nanoTime(); }

    private int listTop() { return y + 194; }
    private int listHeight() { return Math.max(30, h - 194 - 30); }

    // ---- actions ----------------------------------------------------------------------------

    private void saveCurrent() {
        String name = nameInput.getValue().trim();
        if (!Presets.validName(name)) { status("Use 1–32 letters, numbers, spaces, dots, dashes or underscores.", true); return; }
        boolean replacing = Presets.exists(category, name);
        try { Presets.save(category, name); }
        catch (Exception failure) { status("Couldn't save the preset: " + failure.getMessage(), true); return; }
        nameInput.setValue(""); selected = name;
        status((replacing ? "Updated " : "Saved ") + name + ".", false);
    }

    private void load(String name) {
        boolean imported = category == PresetCategory.MODULES && dev.argus.client.config.importing.ImportedPresetStore.isImported(name);
        host.hostOpen(new PresetLoadConfirmationScreen(host, category, name, message -> {
            if (imported) {
                var result = dev.argus.client.config.importing.ImportedPresetStore.lastResult();
                if (result != null) recoveryName = result.backupName();
            }
            status(message, false);
        }));
    }

    private void importConfig() {
        host.hostOpen(new ConfigImportScreen(host, name -> {
            category = PresetCategory.MODULES; selected = name; scroll = 0;
            status("Imported " + name + ". Choose Load when you’re ready.", false);
        }));
    }

    private void overwrite(String name) {
        try { Presets.save(category, name); status("Saved the current config over " + name + ".", false); }
        catch (Exception failure) { status("Couldn't save the preset: " + failure.getMessage(), true); }
    }

    private void delete(String name) {
        if (!name.equals(confirmDelete) || System.nanoTime() - confirmAt > 3e9) { confirmDelete = name; confirmAt = System.nanoTime(); return; }
        Presets.delete(category, name);
        if (name.equals(selected)) select(null);
        confirmDelete = null;
        status("Deleted " + name + ".", false);
    }

    private void bindTyped(String preset) {
        String address = addressInput.getValue().trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty() || address.contains(" ")) { status("Enter a server address with no spaces.", true); return; }
        Presets.bind(address, category, preset);
        addressInput.setValue("");
        status(preset + " will load when you join " + address + ".", false);
    }

    private void select(String name) {
        if (name != null && name.equals(selected)) name = null;
        if (selected != null && name == null) host.hostRemove(addressInput);
        if (selected == null && name != null) host.hostAdd(addressInput);
        selected = name;
        addressInput.setValue("");
    }

    // ---- rendering --------------------------------------------------------------------------

    /** Small rounded text button; registers its hit box for this frame. Returns its width. */
    private float pill(GuiGraphics g, String label, float right, float cy, int mx, int my, int tint, boolean filled, Runnable action) {
        float pw = UiDraw.width(label, UiDraw.SMALL) + 14, px = right - pw;
        boolean hover = UiDraw.over(mx, my, px, cy - 7, pw, 14);
        float hv = UiAnim.smooth("pp." + label + "." + Math.round(cy) + "." + Math.round(right), hover ? 1 : 0, 20);
        UiTheme t = GuiState.theme();
        UiDraw.rounded(g, px, cy - 7, pw, 14, 7, filled ? UiDraw.alpha(tint, .22f + hv * .14f) : UiDraw.mix(t.surface(), t.overlay(), hv * .8f));
        UiDraw.center(g, label, px, cy, pw, UiDraw.SMALL, filled ? tint : UiDraw.mix(t.text(), tint, hv));
        hits.add(new Hit(px, cy - 7, pw, 14, action));
        return pw;
    }

    /** Delete pill: eases wider and red while armed, crossfades its label and drains a timer line. */
    private float confirmPill(GuiGraphics g, String key, String label, String armedLabel, boolean armed, long armedAt, long window,
                              float right, float cy, int mx, int my, int tint, Runnable action) {
        UiTheme t = GuiState.theme();
        String shown = armed ? armedLabel : label;
        float target = UiDraw.width(shown, UiDraw.SMALL) + 14;
        float pw = UiAnim.smooth(key + ".w", target, target, 20), px = right - pw;
        boolean hover = UiDraw.over(mx, my, px, cy - 7, pw, 14);
        float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20), on = UiAnim.smooth(key + ".arm", armed ? 1 : 0, 18);
        UiDraw.rounded(g, px, cy - 7, pw, 14, 7, UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), hv * .8f), UiDraw.alpha(tint, .22f + hv * .14f), on));
        int colour = UiDraw.mix(UiDraw.mix(t.text(), tint, hv), tint, on);
        UiMotion.crossfade(g, key + ".label", shown, l -> UiDraw.center(g, l, px, cy, pw, UiDraw.SMALL, colour));
        if (armed) UiMotion.drain(g, px + 5, cy + 4.6f, pw - 10, armedAt, window, UiDraw.alpha(tint, .8f));
        hits.add(new Hit(px, cy - 7, pw, 14, action));
        return pw;
    }

    void render(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        hits.clear();
        UiDraw.icon(g, "star", x, y + 13.5f, 9, t.accent());
        UiDraw.bold(g, "Presets", x + 13, y + 18, UiDraw.TITLE, t.text());
        float importWidth = pill(g, "Import config", x + w, y + 14, mx, my, t.accent(), true, this::importConfig);
        if (recoveryName != null && Presets.exists(PresetCategory.MODULES, recoveryName))
            pill(g, "Restore backup", x + w - importWidth - 5, y + 14, mx, my, t.green(), false, () -> {
                PresetCategory previous = category; category = PresetCategory.MODULES;
                load(recoveryName); category = previous;
            });
        UiDraw.text(g, "Save configs and bind them to servers or worlds to load them automatically", x, y + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, y + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));
        UiDraw.card(g, x - 6, y + 46, w + 12, 112, "star", "Save current config", "Pick what the preset covers, name it and save", 0, t);
        UiDraw.card(g, x - 6, y + 164, w + 12, listHeight() + 34, "misc", "Saved presets",
                Presets.names(category).size() + " saved · click a name to bind it to servers", 0, t);
        // Category chips with a sliding highlight.
        float cx = x;
        for (PresetCategory c : Presets.categories()) {
            String label = label(c);
            float cw = UiDraw.width(label, UiDraw.SMALL) + 16;
            boolean active = c == category, hover = UiDraw.over(mx, my, cx, y + 78, cw, 15);
            if (active) {
                if (Float.isNaN(chipIndicator)) { chipIndicator = cx; chipIndicatorW = cw; }
                chipIndicator = UiAnim.approach(chipIndicator, cx, 18); chipIndicatorW = UiAnim.approach(chipIndicatorW, cw, 18);
            }
            float hv = UiAnim.smooth("pp.cat." + c.name(), hover && !active ? 1 : 0, 20);
            if (hv > 0) UiDraw.rounded(g, cx, y + 78, cw, 15, 7.5f, UiDraw.alpha(t.surface(), hv * .8f));
            cx += cw + 4;
        }
        UiDraw.rounded(g, chipIndicator, y + 78, chipIndicatorW, 15, 7.5f, UiDraw.alpha(t.accent(), .22f));
        cx = x;
        for (PresetCategory c : Presets.categories()) {
            String label = label(c);
            float cw = UiDraw.width(label, UiDraw.SMALL) + 16;
            UiDraw.center(g, label, cx, y + 85.5f, cw, UiDraw.SMALL, c == category ? t.accent() : t.muted());
            PresetCategory chosen = c;
            hits.add(new Hit(cx, y + 78, cw, 15, () -> { if (category != chosen) { category = chosen; select(null); scroll = 0; } }));
            cx += cw + 4;
        }
        // Where you are now.
        String target = Presets.currentTarget();
        String where = target == null ? "Join a server or world to bind presets to it"
                : (Presets.isWorld(target) ? "In world: " : "On server: ") + Presets.label(target);
        UiDraw.icon(g, target == null ? "misc" : Presets.isWorld(target) ? "player" : "people", x, y + 98, 8, target == null ? t.muted() : t.green());
        UiDraw.text(g, UiDraw.fit(where, w - 14), x + 12, y + 102, UiDraw.SMALL, target == null ? t.muted() : t.text());
        // Save the current config.
        nameInput.render(g, mx, my, delta);
        pill(g, "Save current", x + w, y + 128, mx, my, t.accent(), true, this::saveCurrent);
        // Preset list.
        List<String> names = Presets.names(category);
        var bindings = Presets.bindings(category);
        int top = listTop(), view = listHeight();
        int total = 0;
        for (String name : names) total += ROW + (name.equals(selected) ? EXPANDED : 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, total - view));
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        g.enableScissor(x - 4, top, x + w + 4, top + view);
        // Layout position of each row; the drawn position glides to it, so rows slide up when one is deleted.
        float flowY = 0;
        boolean addressShown = false;
        for (String name : names) {
            boolean open = name.equals(selected);
            String key = "pp.row." + category.name() + "." + name;
            float expand = UiAnim.smooth(key + ".x", open ? 1 : 0, 16);
            float rowH = ROW + EXPANDED * expand;
            float rowY = top - shownScroll + UiMotion.rowY("pp." + category.name(), name, flowY);
            float rowBefore = UiRender.opacity;
            UiRender.opacity = rowBefore * UiMotion.rowAppear("pp." + category.name(), name);
            if (rowY + rowH >= top && rowY <= top + view) {
                boolean hover = UiDraw.over(mx, my, x - 4, rowY, w + 8, rowH) && my >= top && my < top + view;
                float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20);
                UiDraw.rounded(g, x - 4, rowY + 1, w + 8, rowH - 2, 6, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), Math.max(hv * .7f, expand)));
                if (expand > 0) UiDraw.outline(g, x - 4, rowY + 1, w + 8, rowH - 2, 6, .6f, UiDraw.alpha(t.accent(), .35f * expand));
                // The chevron turns with the row's expansion instead of swapping.
                if (expand < .99f) UiDraw.icon(g, "chevron_right", x + 1 + expand * 1.5f, rowY + 7, 8, UiDraw.alpha(t.muted(), .7f * (1 - expand)));
                if (expand > .01f) UiDraw.icon(g, "chevron_down", x + 1, rowY + 7 - (1 - expand) * 1.5f, 8, UiDraw.alpha(t.muted(), .7f * expand));
                String shownName = UiDraw.fit(name, w * .34f);
                UiDraw.bold(g, shownName, x + 13, rowY + 11, UiDraw.TEXT, t.text());
                if (Presets.hasLayout(category, name)) {
                    float lx = x + 13 + UiDraw.width(shownName) + 6, lw = UiDraw.width("Layout", 5.5f) + 10;
                    UiDraw.rounded(g, lx, rowY + 6.5f, lw, 9, 4.5f, UiDraw.alpha(t.blue(), .16f));
                    UiDraw.center(g, "Layout", lx, rowY + 11, lw, 5.5f, t.blue());
                    if (UiDraw.over(mx, my, lx, rowY + 6, lw, 10)) host.hostTooltip(key + ".layout", "Includes window layout", "Loading this preset also moves the module windows");
                }
                String selectedName = name;
                hits.add(new Hit(x - 4, rowY, w * .45f, 20, () -> select(selectedName)));
                // Bound servers/worlds as removable chips.
                float chipX = x + 13, chipY = rowY + 23;
                List<String> targets = new ArrayList<>();
                bindings.forEach((where2, preset) -> { if (preset.equalsIgnoreCase(name)) targets.add(where2); });
                if (targets.isEmpty()) UiDraw.text(g, "Not bound · loads only when you choose it", chipX, chipY, 6f, UiDraw.alpha(t.muted(), .7f));
                for (String bound : targets) {
                    String label = Presets.label(bound);
                    float cw = UiDraw.width(label, 6f) + 22;
                    if (chipX + cw > x + w * .55f) { UiDraw.text(g, "+" + (targets.size() - targets.indexOf(bound)) + " more", chipX, chipY, 6f, t.muted()); break; }
                    // Chips fade in when bound and slide along when a neighbour is unbound.
                    float chipDrawX = UiAnim.smooth(key + ".chipDrawX." + bound, chipX, chipX, 18), chipBefore = UiRender.opacity;
                    UiRender.opacity = chipBefore * UiMotion.rowAppear(key + ".chip", bound);
                    boolean chipHover = UiDraw.over(mx, my, chipDrawX + cw - 11, chipY - 5, 10, 10);
                    int chipColour = Presets.isWorld(bound) ? t.green() : t.blue();
                    UiDraw.rounded(g, chipDrawX, chipY - 5, cw, 10, 5, UiDraw.alpha(chipColour, .16f));
                    UiDraw.icon(g, Presets.isWorld(bound) ? "player" : "people", chipDrawX + 3, chipY - 3, 6, chipColour);
                    UiDraw.text(g, label, chipDrawX + 11, chipY, 6f, chipColour);
                    UiDraw.icon(g, "close", chipDrawX + cw - 9, chipY - 3, 6, UiDraw.mix(UiDraw.alpha(chipColour, .7f), t.red(), UiAnim.smooth(key + ".cxh." + bound, chipHover ? 1 : 0, 20)));
                    UiRender.opacity = chipBefore;
                    String unbindTarget = bound;
                    hits.add(new Hit(chipDrawX + cw - 11, chipY - 5, 10, 10, () -> { Presets.unbind(unbindTarget, category); status("Unbound " + Presets.label(unbindTarget) + ".", false); }));
                    if (chipHover) host.hostTooltip("pp.unbind." + bound, "Stop loading " + name + " on " + Presets.label(bound));
                    chipX += cw + 4;
                }
                // Actions, right to left.
                float right = x + w, cy = rowY + 16;
                boolean confirming = name.equals(confirmDelete) && System.nanoTime() - confirmAt < 3e9;
                right -= confirmPill(g, "pp.del." + name, "Delete", "Really delete?", confirming, confirmAt, 3_000_000_000L, right, cy, mx, my, t.red(), () -> delete(selectedName)) + 4;
                right -= pill(g, "Save over", right, cy, mx, my, t.accent(), false, () -> overwrite(selectedName)) + 4;
                if (target != null) {
                    boolean boundHere = name.equalsIgnoreCase(bindings.getOrDefault(target, ""));
                    String here = target;
                    right -= pill(g, boundHere ? "Unbind here" : (Presets.isWorld(target) ? "Bind to world" : "Bind to server"), right, cy, mx, my, t.green(), boundHere, () -> {
                        if (boundHere) { Presets.unbind(here, category); status(selectedName + " no longer loads here.", false); }
                        else { Presets.bind(here, category, selectedName); status(selectedName + " will load whenever you join " + Presets.label(here) + ".", false); }
                    }) + 4;
                }
                pill(g, "Load", right, cy, mx, my, t.accent(), true, () -> load(selectedName));
                // Expanded: bind to any server by address.
                if (open && expand > .6f) {
                    float before = UiRender.opacity;
                    UiRender.opacity = before * (expand - .6f) / .4f;
                    addressInput.place(x + 13, Math.round(rowY + 40), Math.round(w - 13 - 70));
                    addressInput.visible = rowY + 34 >= top && rowY + 54 <= top + view;
                    addressShown = addressInput.visible;
                    addressInput.render(g, mx, my, delta);
                    pill(g, "Add server", x + w, rowY + 46, mx, my, t.blue(), true, () -> bindTyped(selectedName));
                    UiRender.opacity = before;
                }
            }
            UiRender.opacity = rowBefore;
            flowY += rowH;
        }
        if (selected != null) addressInput.visible = addressShown;
        if (names.isEmpty()) {
            UiDraw.icon(g, "star", x + w / 2 - 7, top + 14, 14, UiDraw.alpha(t.muted(), .45f));
            UiDraw.center(g, "No " + (category == PresetCategory.ALL ? "" : (category == PresetCategory.HUD ? "HUD" : category.getName().toLowerCase(Locale.ROOT)) + " ") + "presets yet", x, top + 40, w, t.muted());
            UiDraw.center(g, "Name your current config above and press Save current.", x, top + 52, w, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        }
        g.disableScissor();
        UiMotion.faded(UiMotion.edge(shownScroll), () -> UiDraw.gradient(g, x - 4, top, w + 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge((Math.max(0, total - view)) - shownScroll), () -> UiDraw.gradient(g, x - 4, top + view - 8, w + 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base()));
        // Footer.
        boolean showStatus = !status.isEmpty() && (System.nanoTime() - statusAt) / 1e9f < 4.25f;
        var line = showStatus ? new UiMotion.Line(UiDraw.fit(status, w), statusError ? 1 : 2)
                : new UiMotion.Line(UiDraw.fit("Each server or world loads one preset per type · Click a name to bind it to more servers", w), 0);
        float fy = y + h - 11;
        UiMotion.crossfade(g, "presets.footer", line, l -> UiDraw.text(g, l.text(), x, fy, UiDraw.SMALL, UiMotion.footerColour(l.kind(), t)));
    }

    // ---- input ------------------------------------------------------------------------------

    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (nameInput.isMouseOver(mx, my)) { host.hostFocus(nameInput); return nameInput.mouseClicked(event, doubled); }
        if (selected != null && addressInput.visible && addressInput.isMouseOver(mx, my)) { host.hostFocus(addressInput); return addressInput.mouseClicked(event, doubled); }
        if (event.button() != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            boolean inList = hit.y >= listTop() - 1;
            if (hit.contains(mx, my) && (!inList || (my >= listTop() && my < listTop() + listHeight()))) { hit.action.run(); return true; }
        }
        return UiDraw.over(mx, my, x, y, w, h);
    }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!UiDraw.over(mx, my, x - 4, listTop(), w + 8, listHeight())) return false;
        scroll = Math.max(0, scroll - (int) (vertical * ROW));
        return true;
    }

    boolean keyPressed(KeyEvent event) {
        if (event.key() != 257 && event.key() != 335) return false;
        if (nameInput.isFocused()) { saveCurrent(); return true; }
        if (selected != null && addressInput.isFocused()) { bindTyped(selected); return true; }
        return false;
    }
}

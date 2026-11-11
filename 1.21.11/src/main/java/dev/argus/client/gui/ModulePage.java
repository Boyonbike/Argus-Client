package dev.argus.client.gui;

import dev.argus.client.config.ModuleClipboard;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The one layout every module's settings use, wherever they are shown.
 *
 * <pre>
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │ (icon) Module name                ☆        Enabled  [●──]   │  hero: identity and the main switch
 *  │        Description, up to two lines                         │
 *  │ [⌨ R] [Toggle|Hold|Release] [HUD list]   [⧉][📋] [↺ Reset]   │  controls: keybind, mode, extras, copy/paste config
 *  ├─────────────────────────────────────────────────────────────┤
 *  │ ┌ Module tools (presets, combos…) ─────────────────────┐    │  tool cards sit by the settings they use
 *  │ └──────────────────────────────────────────────────────┘    │
 *  │ ┌ General ─────────────────────────────────────────── ⌄ ┐   │  one foldable card per group
 *  │ │ rows…                                                │    │
 *  │ └──────────────────────────────────────────────────────┘    │
 *  ├─────────────────────────────────────────────────────────────┤
 *  │ hint or error                                               │  footer
 *  └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Hosts give it a rectangle and forward input; modules add their own tools with {@link #inline}.
 */
final class ModulePage {
    private static final float CHIP = 16, FOOTER = 22;
    private static final Keybind.Mode[] MODES = {Keybind.Mode.TOGGLE, Keybind.Mode.HOLD, Keybind.Mode.HOLD_REVERSE};
    private static final String[] MODE_LABELS = {"Toggle", "Hold", "Hold off"};
    private static final String[] MODE_HINTS = {"Press to switch on, press again to switch off",
            "On only while the key is held", "Off only while the key is held"};

    /** A clickable region recorded while drawing, so clicks always match what is on screen. */
    private record Hit(float x, float y, float w, float h, int button, Runnable action) {}

    private final UiScreen host;
    private final Module module;
    private final SettingsCards cards;
    private final boolean favouritable;
    private final List<Hit> hits = new ArrayList<>();
    private final String key;
    private String icon;

    private float x, y, w, h;
    private float scroll, shownScroll;
    private boolean binding, scrolledToFocus;
    /** Reset all asks for a second click within a few seconds. */
    private long resetArmedAt;
    private String notice = "";
    private boolean noticeError;
    private long noticeAt;

    /**
     * @param focusSetting a setting to scroll to and highlight, or null
     * @param favouritable whether the star (Favourites window) is offered — not for client modules
     */
    ModulePage(UiScreen host, Module module, String focusSetting, boolean favouritable) {
        this.host = host;
        this.module = module;
        this.favouritable = favouritable;
        this.key = "mp." + module.getName() + ".";
        this.icon = module.getCategory().name().toLowerCase(Locale.ROOT);
        cards = new SettingsCards(host, module, focusSetting);
        scrolledToFocus = focusSetting == null;
    }

    Module module() { return module; }

    /** Replaces the category icon in the badge. */
    ModulePage icon(String name) { icon = name; return this; }

    /**
     * Adds a card of the module's own tools (presets, combos, shortcuts).
     */
    ModulePage inline(InlineCard card, Placement placement) { cards.addInline(card, placement); return this; }

    /** Places the page. */
    void layout(float x, float y, float w, float h, float panelLeft, float panelRight) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        cards.panelEdges(panelLeft, panelRight);
        cards.reattach();
    }

    // ---- geometry ----------------------------------------------------------------------------

    private List<String> descriptionLines() {
        String description = UiText.stripFormatting(module.getDescription());
        if (description == null || description.isBlank()) description = "No description";
        List<String> lines = UiDraw.wrap(description, w - 150, UiDraw.SMALL);
        if (lines.size() > 2) lines = List.of(lines.get(0), UiDraw.fit(lines.get(1) + "…", w - 150, UiDraw.SMALL));
        return lines;
    }
    private float controlsY() { return y + 26 + descriptionLines().size() * 9 + 4; }
    private float bodyTop() { return controlsY() + CHIP + 12; }
    private float bodyBottom() { return y + h - FOOTER; }
    private float cardsX() { return x; }
    private float cardsW() { return w; }
    private float maxScroll() { return Math.max(0, cards.height() - (bodyBottom() - bodyTop())); }
    private String bindLabel() { return binding ? "Press a key…" : module.getKeybind().isUnbound() ? "Not bound" : module.getKeybind().getDisplayName(); }

    private void notice(String text) { notice = text; noticeError = false; noticeAt = System.nanoTime(); }
    private void error(String text) { notice = text; noticeError = true; noticeAt = System.nanoTime(); }
    private boolean resetArmed() { return resetArmedAt != 0 && System.nanoTime() - resetArmedAt < 3_000_000_000L; }

    // ---- drawing -----------------------------------------------------------------------------

    void render(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        hits.clear();
        float base = UiRender.opacity;
        stage(g, 0, base, () -> hero(g, mx, my, t));
        stage(g, 1, base, () -> controls(g, mx, my, t));
        UiDraw.rect(g, x, bodyTop() - 6, w, .6f, UiDraw.alpha(t.overlay(), .6f));
        body(g, mx, my, delta, t);
        footer(g, t);
    }

    /** Staggered entrance for one band of the page. */
    private void stage(GuiGraphics g, int index, float base, Runnable draw) {
        float appear = host.appear(index);
        UiRender.opacity = base * appear;
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - appear) * 6);
        try { draw.run(); } finally { g.pose().popMatrix(); UiRender.opacity = base; }
    }

    private void hero(GuiGraphics g, int mx, int my, UiTheme t) {
        float on = UiAnim.smooth(key + "on", module.isToggled() ? 1 : 0, 14);
        // Icon badge: glows with the accent while enabled, and sends out a ring when switched.
        float bx = x + 11, by = y + 13;
        float ring = UiAnim.smooth(key + "ring", 0, 5);
        if (ring > .01f) UiDraw.outline(g, bx - 11 - (1 - ring) * 7, by - 11 - (1 - ring) * 7, 22 + (1 - ring) * 14, 22 + (1 - ring) * 14,
                11 + (1 - ring) * 7, 1, UiDraw.alpha(t.accent(), ring * .7f));
        if (on > .01f) UiDraw.shadow(g, bx - 11, by - 11, 22, 22, 11, 6, UiDraw.alpha(t.accent(), .22f * on));
        UiDraw.circle(g, bx, by, 11, UiDraw.mix(UiDraw.alpha(t.surface(), .9f), UiDraw.alpha(t.accent(), .24f), on));
        UiDraw.icon(g, icon, bx - 5, by - 5, 10, UiDraw.mix(t.muted(), t.accent(), .45f + on * .55f));

        float textX = x + 30, right = x + w;
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), w - 170), textX, y + 8, UiDraw.TITLE, t.text());
        float titleEnd = textX + UiDraw.width(UiDraw.fit(GuiState.title(module.getName()), w - 170), UiDraw.TITLE);
        String category = GuiState.title(module.getCategory().getName());
        float tagW = UiDraw.width(category, 6f) + 10;
        UiDraw.rounded(g, titleEnd + 6, y + 3, tagW, 10, 5, UiDraw.alpha(t.surface(), .9f));
        UiDraw.text(g, category, titleEnd + 11, y + 8, 6f, t.muted());
        float lineY = y + 21;
        for (String line : descriptionLines()) { UiDraw.text(g, line, textX, lineY, UiDraw.SMALL, t.muted()); lineY += 9; }

        // Favourite star.
        if (favouritable) {
            boolean favourite = GuiState.preferences().favourites.contains(module.getName());
            float sx = titleEnd + tagW + 12, sy = y + 1;
            boolean hover = UiDraw.over(mx, my, sx, sy, 14, 14);
            float hv = UiAnim.smooth(key + "fav.h", hover ? 1 : 0, 20), fv = UiAnim.smooth(key + "fav", favourite ? 1 : 0, 14);
            float pop = UiAnim.smooth(key + "fav.pop", 0, 9);
            if (hv > 0) UiDraw.circle(g, sx + 7, sy + 7, 7, UiDraw.alpha(t.surface(), hv));
            float size = 8 + pop * 3;
            UiDraw.icon(g, "star", sx + 7 - size / 2, sy + 7 - size / 2, size, UiDraw.mix(UiDraw.mix(t.muted(), t.text(), hv), t.yellow(), fv));
            hits.add(new Hit(sx, sy, 14, 14, 0, () -> {
                var favourites = GuiState.preferences().favourites;
                boolean added = favourites.add(module.getName());
                if (!added) favourites.remove(module.getName());
                GuiState.save();
                if (added) UiAnim.set(key + "fav.pop", 1);
            }));
            if (hover) host.tooltip(key + "fav", favourite ? "Remove from Favourites" : "Add to Favourites");
        }

        // Main switch: a wide pill with the state written in it.
        float pw = 72, ph = 18, px = right - pw, py = y;
        boolean hover = UiDraw.over(mx, my, px, py, pw, ph);
        float hv = UiAnim.smooth(key + "sw.h", hover ? 1 : 0, 18);
        UiDraw.rounded(g, px, py, pw, ph, 9, UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), hv * .6f), UiDraw.alpha(t.accent(), .2f + hv * .06f), on));
        UiDraw.outline(g, px, py, pw, ph, 9, .6f, UiDraw.mix(UiDraw.alpha(t.overlay(), .6f), UiDraw.alpha(t.accent(), .55f), on));
        // The HUD module's switch shows or hides the whole HUD, so say that rather than "Enabled".
        boolean hudSwitch = module instanceof dev.argus.client.modules.client.HUDModule;
        String state = hudSwitch ? (module.isToggled() ? "HUD on" : "HUD off") : module.isToggled() ? "Enabled" : "Disabled";
        UiDraw.text(g, state, px + 9, py + ph / 2, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
        if (hover && hudSwitch) host.tooltip(key + "sw.hud", module.isToggled() ? "HUD is on" : "HUD is off", "Shows or hides every HUD element in game");
        UiDraw.toggle(g, px + pw - 25, py + 4, on, hover, t);
        hits.add(new Hit(px, py, pw, ph, 0, () -> { module.toggle(); UiAnim.set(key + "ring", 1); }));
    }

    private void controls(GuiGraphics g, int mx, int my, UiTheme t) {
        float cy = controlsY(), cx = x, right = x + w;
        // Keybind chip.
        String label = bindLabel();
        float kw = UiDraw.width(label, UiDraw.SMALL) + 26;
        float kwShown = UiAnim.smooth(key + "kw", kw, 18);
        boolean kHover = UiDraw.over(mx, my, cx, cy, kwShown, CHIP);
        float kh = UiAnim.smooth(key + "kh", kHover || binding ? 1 : 0, 18);
        float pulse = binding ? .5f + .5f * (float) Math.sin(System.nanoTime() / 1.5e8) : 0;
        UiDraw.rounded(g, cx, cy, kwShown, CHIP, 8, binding ? UiDraw.alpha(t.accent(), .16f + pulse * .12f) : UiDraw.mix(t.surface(), t.overlay(), kh * .7f));
        UiDraw.outline(g, cx, cy, kwShown, CHIP, 8, .6f, binding ? UiDraw.alpha(t.accent(), .5f + pulse * .5f) : UiDraw.alpha(t.overlay(), .5f + kh * .3f));
        UiDraw.icon(g, "keyboard", cx + 7, cy + 4, 8, binding ? t.accent() : UiDraw.mix(t.muted(), t.text(), kh));
        UiDraw.text(g, UiDraw.fit(label, kwShown - 22, UiDraw.SMALL), cx + 19, cy + CHIP / 2, UiDraw.SMALL, binding ? t.accent() : t.text());
        hits.add(new Hit(cx, cy, kwShown, CHIP, 0, () -> { cards.stopEditing(); binding = true; }));
        hits.add(new Hit(cx, cy, kwShown, CHIP, 1, () -> { module.setKeybind(Keybind.UNBOUND); binding = false; }));
        if (kHover && !binding) host.tooltip(key + "bind", "Keybind", "Click to set · Right-click to clear");
        cx += kwShown + 6;

        // Bind mode, only meaningful once a key is bound.
        float modeShow = UiAnim.smooth(key + "mode", module.getKeybind().isUnbound() ? 0 : 1, 14);
        if (modeShow > .01f) {
            float seg = 44, mw = seg * MODES.length;
            float before = UiRender.opacity;
            UiRender.opacity = before * modeShow;
            int chosen = 0;
            for (int i = 0; i < MODES.length; i++) if (MODES[i] == module.getKeybind().mode()) chosen = i;
            UiDraw.rounded(g, cx, cy, mw, CHIP, 8, t.crust());
            float thumb = UiAnim.smooth(key + "mode.t", chosen, 16);
            UiDraw.rounded(g, cx + 2 + thumb * seg, cy + 2, seg - 4, CHIP - 4, 6, UiDraw.alpha(t.accent(), .22f));
            for (int i = 0; i < MODES.length; i++) {
                boolean hover = UiDraw.over(mx, my, cx + i * seg, cy, seg, CHIP);
                int colour = i == chosen ? t.accent() : UiDraw.mix(t.muted(), t.text(), UiAnim.smooth(key + "mode.h" + i, hover ? 1 : 0, 18));
                UiDraw.center(g, MODE_LABELS[i], cx + i * seg, cy + CHIP / 2, seg, 6f, colour);
                Keybind.Mode mode = MODES[i];
                if (modeShow > .5f) hits.add(new Hit(cx + i * seg, cy, seg, CHIP, 0, () -> module.setKeybind(module.getKeybind().withMode(mode))));
                if (hover && modeShow > .5f) host.tooltip(key + "mode" + i, MODE_LABELS[i], MODE_HINTS[i]);
            }
            UiRender.opacity = before;
            cx += (mw + 6) * modeShow;
        }

        // HUD list visibility.
        float drawn = UiAnim.smooth(key + "drawn", module.isDrawn() ? 1 : 0, 16);
        String dl = "HUD list";
        float dw = UiDraw.width(dl, UiDraw.SMALL) + 34;
        if (cx + dw < right - 130) {
            boolean hover = UiDraw.over(mx, my, cx, cy, dw, CHIP);
            float hv = UiAnim.smooth(key + "drawn.h", hover ? 1 : 0, 18);
            UiDraw.rounded(g, cx, cy, dw, CHIP, 8, UiDraw.mix(t.surface(), t.overlay(), hv * .7f));
            UiDraw.text(g, dl, cx + 8, cy + CHIP / 2, UiDraw.SMALL, UiDraw.mix(t.muted(), t.text(), Math.max(hv, drawn * .7f)));
            UiDraw.toggle(g, cx + dw - 24, cy + 3, drawn, hover, t);
            hits.add(new Hit(cx, cy, dw, CHIP, 0, () -> module.setDrawn(!module.isDrawn())));
            if (hover) host.tooltip(key + "drawn", "HUD list", "Show this module in the on-screen module list while it is on");
        }

        // Right side: reset all.
        float rx = right;
        int changed = cards.changed();
        boolean armed = resetArmed();
        String rl = armed ? "Reset " + changed + "?" : "Reset";
        float rw = UiDraw.width(rl, UiDraw.SMALL) + 26;
        float rwShown = UiAnim.smooth(key + "rw", rw, 18);
        rx -= rwShown;
        boolean rHover = UiDraw.over(mx, my, rx, cy, rwShown, CHIP);
        float rh = UiAnim.smooth(key + "rh", rHover ? 1 : 0, 18), ra = UiAnim.smooth(key + "ra", armed ? 1 : 0, 16);
        float usable = changed > 0 ? 1 : .45f;
        int rFill = UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), rh * .7f * usable), UiDraw.alpha(t.red(), .2f), ra);
        UiDraw.rounded(g, rx, cy, rwShown, CHIP, 8, rFill);
        if (ra > .01f) UiDraw.outline(g, rx, cy, rwShown, CHIP, 8, .6f, UiDraw.alpha(t.red(), .6f * ra));
        int rColour = UiDraw.mix(UiDraw.alpha(UiDraw.mix(t.muted(), t.text(), rh), usable), t.red(), ra);
        UiDraw.icon(g, "reset", rx + 7, cy + 4, 8, rColour);
        UiDraw.text(g, UiDraw.fit(rl, rwShown - 22, UiDraw.SMALL), rx + 19, cy + CHIP / 2, UiDraw.SMALL, rColour);
        hits.add(new Hit(rx, cy, rwShown, CHIP, 0, () -> {
            if (cards.changed() == 0) { notice("Everything is already at its default"); return; }
            if (!resetArmed()) { resetArmedAt = System.nanoTime(); return; }
            resetArmedAt = 0;
            int count = cards.resetAll();
            notice("Restored " + count + (count == 1 ? " setting" : " settings") + " to default");
        }));
        if (rHover) host.tooltip(key + "reset", "Reset all", changed == 0 ? "Nothing changed from the defaults"
                : changed + (changed == 1 ? " setting differs" : " settings differ") + " from default · click twice to restore");

        // Copy and paste this module's settings, to share them with another client.
        rx -= 6 + CHIP;
        iconChip(g, mx, my, t, "paste", rx, cy, "Paste config", "Apply settings copied from this module in another client (Ctrl+V)", this::pasteConfig);
        rx -= 4 + CHIP;
        iconChip(g, mx, my, t, "copy", rx, cy, "Copy config", "Copy this module's settings to the clipboard to paste into another client (Ctrl+C)", this::copyConfig);
    }

    private void iconChip(GuiGraphics g, int mx, int my, UiTheme t, String icon, float cx, float cy, String title, String hint, Runnable action) {
        boolean hover = UiDraw.over(mx, my, cx, cy, CHIP, CHIP);
        float hv = UiAnim.smooth(key + icon + ".h", hover ? 1 : 0, 18);
        float flash = UiAnim.smooth(key + icon + ".done", 0, 6);
        UiDraw.rounded(g, cx, cy, CHIP, CHIP, 8, UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), hv * .7f), UiDraw.alpha(t.accent(), .25f), flash));
        UiDraw.icon(g, icon, cx + 4, cy + 4, 8, UiDraw.mix(UiDraw.mix(t.muted(), t.text(), hv), t.accent(), flash));
        hits.add(new Hit(cx, cy, CHIP, CHIP, 0, () -> { action.run(); UiAnim.set(key + icon + ".done", 1); }));
        if (hover) host.tooltip(key + icon, title, hint);
    }

    private void copyConfig() {
        cards.stopEditing();
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(ModuleClipboard.copy(module));
        notice("Copied " + GuiState.title(module.getName()) + " config · paste it with Ctrl+V on this module in another client");
    }

    private void pasteConfig() {
        cards.stopEditing();
        ModuleClipboard.Result result = ModuleClipboard.paste(module, net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
        if (!result.ok()) { error(result.problem()); return; }
        cards.reattach();
        notice("Pasted " + result.applied() + (result.applied() == 1 ? " setting" : " settings") + " into " + GuiState.title(module.getName()));
    }

    private void body(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        float top = bodyTop(), bottom = bodyBottom(), cx = cardsX(), cw = cardsW();
        float view = bottom - top, total = cards.height();
        if (!scrolledToFocus) { scroll = cards.focusOffset(); shownScroll = scroll; scrolledToFocus = true; }
        scroll = Math.clamp(scroll, 0, Math.max(0, total - view));
        shownScroll = UiAnim.approach(shownScroll, scroll, 18);
        g.enableScissor(Math.round(cx - 3), Math.round(top), Math.round(cx + cw + 3), Math.round(bottom));
        if (cards.isEmpty()) empty(g, cx, top, cw, t);
        else cards.render(g, mx, my, delta, t, cx, top - shownScroll, cw, top, bottom);
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, cx - 3, top, cw + 6, 12, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < Math.max(0, total - view) - 1) UiDraw.gradient(g, cx - 3, bottom - 12, cw + 6, 12, 0, UiDraw.alpha(t.base(), 0), t.base());
        if (total > view) {
            float track = Math.max(16, view * view / total), offset = (view - track) * shownScroll / Math.max(1, total - view);
            float shown = UiAnim.smooth(key + "bar", UiDraw.over(mx, my, cx, top, cw + 8, view) ? 1 : .5f, 10);
            UiDraw.rounded(g, cx + cw + 3, top + offset, 2, track, 1, UiDraw.alpha(t.overlay(), shown));
        }
        cards.popup(g, t, host.width, host.height);
    }

    private void empty(GuiGraphics g, float cx, float top, float cw, UiTheme t) {
        float cy = top + 40;
        UiDraw.circle(g, cx + cw / 2, cy, 14, UiDraw.alpha(t.surface(), .8f));
        UiDraw.icon(g, "sliders", cx + cw / 2 - 6, cy - 6, 12, t.muted());
        UiDraw.center(g, "Nothing to configure", cx, cy + 24, cw, t.text());
        UiDraw.center(g, "This module only needs switching on or off.", cx, cy + 35, cw, UiDraw.SMALL, t.muted());
    }

    private void footer(GuiGraphics g, UiTheme t) {
        String error = cards.message();
        boolean fresh = !notice.isEmpty() && System.nanoTime() - noticeAt < 3_500_000_000L;
        String text; int colour;
        if (!error.isEmpty()) { text = error; colour = t.red(); }
        else if (binding) { text = "Press a key or mouse button · Esc cancels · Backspace clears"; colour = t.accent(); }
        else if (!cards.notice().isEmpty() && !fresh) { text = cards.notice(); colour = t.green(); }
        else if (resetArmed()) { text = "Click Reset again to restore " + cards.changed() + " settings to default"; colour = t.red(); }
        else if (fresh) { text = notice; colour = noticeError ? t.red() : t.green(); }
        else { text = "Click a card's title to fold it · hover a setting for details · the reset icon restores one setting"; colour = UiDraw.alpha(t.muted(), .8f); }
        UiDraw.text(g, UiDraw.fit(text, w), x, y + h - 9, UiDraw.SMALL, colour);
    }

    // ---- input -------------------------------------------------------------------------------

    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (binding) { module.setKeybind(new Keybind(event.button(), module.getKeybind().mode(), true)); binding = false; return true; }
        double mx = event.x(), my = event.y();
        // Later hits are drawn on top, so they win.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.button() == event.button() && UiDraw.over(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return true;
            }
        }
        if (resetArmed() && event.button() == 0) resetArmedAt = 0;
        return cards.mouseClicked(event, doubled, bodyTop(), bodyBottom());
    }

    boolean mouseDragged(MouseButtonEvent event) { return cards.mouseDragged(event); }
    boolean mouseReleased() { return cards.mouseReleased(); }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!UiDraw.over(mx, my, cardsX(), bodyTop(), cardsW() + 8, bodyBottom() - bodyTop())) return false;
        scroll = Math.max(0, scroll - (float) vertical * 40);
        return true;
    }

    boolean keyPressed(KeyEvent event) {
        if (binding) {
            if (event.key() != 256) module.setKeybind(event.key() == 259 || event.key() == 261 ? Keybind.UNBOUND : new Keybind(event.key(), module.getKeybind().mode(), false));
            binding = false;
            return true;
        }
        if (cards.keyPressed(event)) return true;
        if (event.key() == 256 && resetArmed()) { resetArmedAt = 0; return true; }
        // Ctrl+C / Ctrl+V copy and paste the module's config, unless a text box has the keyboard.
        if ((event.modifiers() & 2) != 0 && !(host.getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            if (event.key() == 67) { copyConfig(); UiAnim.set(key + "copy.done", 1); return true; }
            if (event.key() == 86) { pasteConfig(); UiAnim.set(key + "paste.done", 1); return true; }
        }
        // Page Up / Page Down / Home / End move through the cards.
        float page = bodyBottom() - bodyTop() - 30;
        switch (event.key()) {
            case 266 -> { scroll = Math.max(0, scroll - page); return true; }
            case 267 -> { scroll += page; return true; }
            case 268 -> { scroll = 0; return true; }
            case 269 -> { scroll = maxScroll(); return true; }
            default -> { return false; }
        }
    }

    void stopEditing() { cards.stopEditing(); binding = false; }
    void reattach() { cards.reattach(); }
}

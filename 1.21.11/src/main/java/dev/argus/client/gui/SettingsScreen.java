package dev.argus.client.gui;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.client.UIModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The Settings page (opened by the gear circle): Appearance (theme, top bar, window placement,
 * and the UI, Colours and Fonts modules), Presets, Friends, and every other Client module.
 */
final class SettingsScreen extends UiScreen {
    static final String APPEARANCE = "Appearance", PRESETS = "Presets", FRIENDS = "Friends";
    private static final int SIDE = 128, ITEM = 20, FIXED = 3; // Appearance, Presets, Friends; client modules follow
    private static final double MIN_SCALE = .65, MAX_SCALE = 1.4;
    /** Last section shown, so Settings reopens where you left it. */
    private static String remembered = APPEARANCE;
    private static float indicatorY = Float.NaN;

    private final String focusSetting;
    private String section;
    /** Module asked for when opening (e.g. from search), so Appearance can scroll to its card. */
    private String focusModule;
    private List<Module> modules = List.of();
    /** Settings of the selected client module section, as cards. */
    private SettingsCards cards;
    private float modScroll, shownModScroll;
    private boolean scrollToFocus;
    /** The modules folded into Appearance, shown as cards. */
    private final List<SettingsList> appearanceLists = new ArrayList<>();
    private PresetsPanel presets;
    private FriendsPanel friends;
    private int left, top, panelWidth, panelHeight;
    private long sectionChanged;

    /** @param section a section name or client module name; null reopens the last section. */
    SettingsScreen(Screen parent, String section, String focusSetting) {
        super("Settings", parent, "Settings");
        DescriptionCatalog.ensureApplied();
        String requested = section == null ? remembered : section;
        if (isAppearanceModule(requested)) { focusModule = requested; requested = APPEARANCE; }
        this.section = requested;
        this.focusSetting = focusSetting;
    }

    // ---- sections ---------------------------------------------------------------------------

    /** The UI, Colours and Fonts modules shape how things look, so they live on the Appearance page. */
    private static boolean appearanceModule(Module module) { return module instanceof UIModule || module instanceof ColorsModule || module instanceof FontsModule; }
    private static boolean isAppearanceModule(String name) { return appearanceModules().stream().anyMatch(m -> m.getName().equals(name)); }
    private static List<Module> appearanceModules() {
        List<Module> result = new ArrayList<>();
        for (Class<?> type : List.of(UIModule.class, ColorsModule.class, FontsModule.class))
            for (Module module : GuiState.modules()) if (type.isInstance(module)) result.add(module);
        return result;
    }

    /** Client modules with their own section. */
    static List<Module> clientModules() {
        List<Module> result = new ArrayList<>();
        for (Module module : GuiState.modules()) if (module.getCategory() == ModuleCategory.CLIENT && !appearanceModule(module)) result.add(module);
        return result;
    }

    static boolean isSection(String name) {
        return APPEARANCE.equals(name) || PRESETS.equals(name) || FRIENDS.equals(name) || clientModules().stream().anyMatch(m -> m.getName().equals(name));
    }
    static String sectionLabel(String name) { return APPEARANCE.equals(name) || PRESETS.equals(name) || FRIENDS.equals(name) ? name : GuiState.title(name); }

    /** One icon per section, used in the sidebar, the top-bar chips and icon-style tabs. */
    static String icon(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "appearance" -> "palette";
            case "presets" -> "star";
            case "friends" -> "people";
            case "anticheat" -> "shield";
            case "baritone" -> "compass";
            case "colors" -> "drop";
            case "discord" -> "chat";
            case "fonts" -> "font";
            case "hud" -> "render";
            case "notifications" -> "bell";
            case "settings", "ui" -> "sliders";
            default -> "client";
        };
    }

    @Override protected String activeSection() { return section; }
    @Override protected void openSection(String name) {
        if (isAppearanceModule(name)) { focusModule = name; name = APPEARANCE; }
        if (!name.equals(section)) select(name, null, true);
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(640, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        modules = clientModules();
        if (!isSection(section)) section = APPEARANCE;
        if (cards == null && appearanceLists.isEmpty() && presets == null && friends == null) select(section, focusSetting, false);
        else layoutSection();
    }

    private void select(String name, String focus, boolean animate) {
        if (cards != null) cards.stopEditing();
        for (SettingsList card : appearanceLists) card.stopEditing();
        if (presets != null) presets.detach();
        if (friends != null) friends.detach();
        section = name; remembered = name;
        cards = null; appearanceLists.clear();
        if (name.equals(APPEARANCE)) {
            for (Module module : appearanceModules())
                appearanceLists.add(new SettingsList(this, module, module.getName().equals(focusModule) ? focus : null));
            pageScroll = 0; shownPageScroll = 0;
        } else {
            Module module = modules.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
            if (module != null) { cards = new SettingsCards(this, module, focus); modScroll = 0; shownModScroll = 0; scrollToFocus = focus != null; }
        }
        if (name.equals(PRESETS) && presets == null) presets = new PresetsPanel(this);
        if (name.equals(FRIENDS) && friends == null) friends = new FriendsPanel(this);
        layoutSection();
        if (animate) sectionChanged = System.nanoTime();
        sliding = null;
    }

    private void layoutSection() {
        int x = Math.round(contentX()), w = Math.round(contentWidth() - 8);
        if (cards != null) { cards.panelEdges(left, left + panelWidth); cards.reattach(); }
        for (SettingsList card : appearanceLists) card.panelEdges(left, left + panelWidth);
        if (section.equals(PRESETS)) presets.layout(x, top, w, panelHeight);
        if (section.equals(FRIENDS)) friends.layout(x, top, w, panelHeight);
    }

    private float contentX() { return left + SIDE + 24; }
    private float contentWidth() { return panelWidth - SIDE - 34; }
    private float itemY(int index) { return top + 32 + index * ITEM; }
    private int sections() { return modules.size() + FIXED; }
    private String sectionName(int index) { return index == 0 ? APPEARANCE : index == 1 ? PRESETS : index == 2 ? FRIENDS : modules.get(index - FIXED).getName(); }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // ---- sidebar
        UiDraw.rounded(g, left + 4, top + 4, SIDE + 8, panelHeight - 8, 5, UiDraw.alpha(t.mantle(), .7f));
        UiDraw.icon(g, "client", left + 12, top + 13, 10, t.accent());
        UiDraw.bold(g, "Settings", left + 27, top + 18, UiDraw.TITLE, t.text());
        int selected = 0;
        for (int i = 0; i < sections(); i++) if (sectionName(i).equals(section)) selected = i;
        float targetY = itemY(selected);
        indicatorY = Float.isNaN(indicatorY) ? targetY : UiAnim.approach(indicatorY, targetY, 18);
        UiDraw.rounded(g, left + 8, indicatorY + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.accent(), .2f));
        UiDraw.rounded(g, left + 8, indicatorY + 5, 2, ITEM - 10, 1, t.accent());
        for (int i = 0; i < sections(); i++) {
            float iy = itemY(i);
            boolean active = i == selected, hover = UiDraw.over(mx, my, left + 8, iy, SIDE, ITEM);
            float hv = UiAnim.smooth("side." + sectionName(i), hover && !active ? 1 : 0, 20);
            if (hv > 0) UiDraw.rounded(g, left + 8, iy + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.surface(), hv * .8f));
            int colour = active ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv);
            UiDraw.icon(g, icon(sectionName(i)), left + 16, iy + ITEM / 2f - 4, 8, colour);
            UiDraw.text(g, UiDraw.fit(sectionLabel(sectionName(i)), SIDE - 36), left + 29, iy + ITEM / 2f, colour);
            if (i >= FIXED && modules.get(i - FIXED).isToggled())
                UiDraw.circle(g, left + SIDE - 4, iy + ITEM / 2f, 2, UiDraw.alpha(t.accent(), active ? 1 : .7f));
        }
        // ---- content, which fades and slides when the section changes
        float change = UiAnim.easeOutCubic((System.nanoTime() - sectionChanged) / 2.2e8f);
        float before = UiRender.opacity;
        UiRender.opacity = before * change;
        g.pose().pushMatrix();
        g.pose().translate((1 - change) * 8, 0);
        try {
            switch (section) {
                case PRESETS -> presets.render(g, mx, my, delta, t);
                case FRIENDS -> friends.render(g, mx, my, delta, t);
                case APPEARANCE -> appearance(g, mx, my, delta, t);
                default -> module(g, mx, my, delta, t);
            }
        } finally { g.pose().popMatrix(); UiRender.opacity = before; }
    }

    // ---- client module section --------------------------------------------------------------

    private void module(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        if (cards == null) return;
        Module module = cards.module();
        float x = contentX(), w = contentWidth();
        UiDraw.icon(g, icon(module.getName()), x, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), w - 124), x + 13, top + 18, UiDraw.TITLE, t.text());
        String description = UiText.stripFormatting(module.getDescription());
        if (description == null || description.isBlank()) description = "Client settings";
        UiDraw.text(g, UiDraw.fit(description, w - 100), x, top + 31, UiDraw.SMALL, t.muted());
        boolean hover = UiDraw.over(mx, my, x + w - 80, top + 10, 80, 18);
        float on = UiAnim.smooth("cs.on." + module.getName(), module.isToggled() ? 1 : 0, 16);
        UiDraw.right(g, module.isToggled() ? "Enabled" : "Disabled", x + w - 26, top + 19, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
        UiDraw.toggle(g, x + w - 20, top + 14, on, hover, t);
        UiDraw.rect(g, x, top + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));
        float view = pageBottom() - pageTop(), total = cards.height();
        if (scrollToFocus) { modScroll = cards.focusOffset(); shownModScroll = modScroll; scrollToFocus = false; }
        modScroll = Math.clamp(modScroll, 0, Math.max(0, total - view));
        shownModScroll = UiAnim.approach(shownModScroll, modScroll, 18);
        g.enableScissor(Math.round(x - 4), Math.round(pageTop()), Math.round(x + w + 4), Math.round(pageBottom()));
        if (cards.isEmpty()) UiDraw.center(g, "This module has no settings.", x, pageTop() + 16, w, t.muted());
        else cards.render(g, mx, my, delta, t, x, pageTop() - shownModScroll, w, pageTop(), pageBottom());
        g.disableScissor();
        if (shownModScroll > 1) UiDraw.gradient(g, x - 4, pageTop(), w + 8, 10, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownModScroll < Math.max(0, total - view) - 1) UiDraw.gradient(g, x - 4, pageBottom() - 10, w + 8, 10, 0, UiDraw.alpha(t.base(), 0), t.base());
        if (total > view) {
            float track = Math.max(16, view * view / total), offset = (view - track) * shownModScroll / Math.max(1, total - view);
            UiDraw.rounded(g, x + w + 3, pageTop() + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
        cards.popup(g, t, width, height);
        String message = cards.message();
        UiDraw.text(g, UiDraw.fit(message.isEmpty() ? "Click a card's title to fold it · hover a setting for details" : message, w), x, top + panelHeight - 11, UiDraw.SMALL,
                message.isEmpty() ? UiDraw.alpha(t.muted(), .8f) : t.red());
    }

    // ---- appearance page: a scrolling stack of cards ------------------------------------------

    private static final float CARD_GAP = 8, CARD_HEAD = 30, PAD = 10, CHIP_H = 16;

    /** A clickable region recorded while drawing, so clicks always match what is on screen. */
    private record Hit(float x, float y, float w, float h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();
    /** Slider being dragged ("scale" or "gap") and the track it uses. */
    private String sliding;
    private float sliderX, sliderW;
    /** Pointer x when a recorded hit runs, so a slider jumps to where it was clicked. */
    private double pointerX;
    private double pendingScale = Double.NaN;
    private float pageScroll, shownPageScroll;

    private float pageTop() { return top + 46; }
    private float pageBottom() { return top + panelHeight - 24; }

    /** Every tab that can sit in the bar, in a fixed order: the two pages, then Settings sections. */
    static List<String> barCandidates() {
        List<String> all = new ArrayList<>();
        for (String page : UiScreen.PAGE_TABS) all.add(UiScreen.PAGE_PREFIX + page);
        all.add(APPEARANCE); all.add(PRESETS); all.add(FRIENDS);
        for (Module module : clientModules()) all.add(module.getName());
        return all;
    }

    static String chipLabel(String entry) {
        if (entry.startsWith(UiScreen.PAGE_PREFIX)) { String page = entry.substring(UiScreen.PAGE_PREFIX.length()); return page.equals("HUD") ? "HUD editor" : page; }
        String label = sectionLabel(entry);
        return label.equalsIgnoreCase("HUD") ? "HUD settings" : label;
    }
    static String tabIcon(String entry) {
        if (entry.startsWith(UiScreen.PAGE_PREFIX)) return entry.endsWith("HUD") ? "hud" : "modules";
        return icon(entry);
    }

    private int accentColour(GuiPreferences.Accent accent, boolean light) {
        var preview = new GuiPreferences(); preview.accent = accent; preview.mode = light ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK;
        return UiTheme.of(preview).accent();
    }

    /** Height of the chip area for a given inner width (chips wrap onto new lines). */
    private float chipsHeight(float width) {
        float x = 0, rows = 1;
        for (String entry : barCandidates()) {
            float w = UiDraw.width(chipLabel(entry), UiDraw.SMALL) + 26;
            if (x + w > width && x > 0) { x = 0; rows++; }
            x += w + 4;
        }
        return rows * (CHIP_H + 4) - 4;
    }

    /** Card frame with an icon badge, title, subtitle and an optional enable switch on the right. */
    private void card(GuiGraphics g, float x, float y, float w, float h, String icon, String title, String subtitle, Module toggle, int mx, int my, UiTheme t) {
        UiDraw.rounded(g, x, y, w, h, 8, UiDraw.alpha(t.mantle(), .6f));
        UiDraw.outline(g, x, y, w, h, 8, .6f, UiDraw.alpha(t.overlay(), .4f));
        UiDraw.circle(g, x + PAD + 7, y + 15, 8, UiDraw.alpha(t.accent(), .16f));
        UiDraw.icon(g, icon, x + PAD + 3, y + 11, 8, t.accent());
        UiDraw.bold(g, title, x + PAD + 20, y + 11, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(subtitle, w - PAD * 2 - 20 - (toggle != null ? 70 : 0)), x + PAD + 20, y + 21, 6f, t.muted());
        if (toggle != null) {
            boolean hover = UiDraw.over(mx, my, x + w - PAD - 70, y + 6, 70, 18);
            float on = UiAnim.smooth("card.on." + toggle.getName(), toggle.isToggled() ? 1 : 0, 16);
            UiDraw.right(g, toggle.isToggled() ? "On" : "Off", x + w - PAD - 24, y + 15, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
            UiDraw.toggle(g, x + w - PAD - 18, y + 10, on, hover, t);
            hits.add(new Hit(x + w - PAD - 70, y + 6, 70, 18, toggle::toggle));
        }
    }

    /** A two-option segmented control with a sliding thumb. */
    private void segmented(GuiGraphics g, String key, String[] labels, int chosen, float x, float y, float w, int mx, int my, UiTheme t, java.util.function.IntConsumer pick) {
        float seg = w / 2;
        UiDraw.rounded(g, x, y, w, 18, 7, t.crust());
        float thumb = UiAnim.smooth(key, chosen, 16);
        UiDraw.rounded(g, x + 2 + thumb * seg, y + 2, seg - 4, 14, 5.5f, UiDraw.alpha(t.accent(), .22f));
        for (int i = 0; i < 2; i++) {
            boolean hover = UiDraw.over(mx, my, x + i * seg, y, seg, 18);
            int colour = i == chosen ? t.accent() : UiDraw.mix(t.muted(), t.text(), UiAnim.smooth(key + ".h" + i, hover ? 1 : 0, 18));
            UiDraw.center(g, labels[i], x + i * seg, y + 9, seg, UiDraw.SMALL, colour);
            int index = i;
            hits.add(new Hit(x + i * seg, y, seg, 18, () -> pick.accept(index)));
        }
    }

    /** A label with a switch at the end of a row. */
    private void toggleRow(GuiGraphics g, String label, String hint, boolean on, String key, float x, float y, float w, int mx, int my, UiTheme t, Runnable flip) {
        boolean hover = UiDraw.over(mx, my, x - 4, y, w + 8, 18);
        float h = UiAnim.smooth(key + ".h", hover ? 1 : 0, 18);
        if (h > 0) UiDraw.rounded(g, x - 4, y, w + 8, 18, 6, UiDraw.alpha(t.surface(), h * .8f));
        UiDraw.text(g, label, x, y + 9, UiDraw.SMALL, t.text());
        UiDraw.toggle(g, x + w - 18, y + 4, UiAnim.smooth(key, on ? 1 : 0, 16), hover, t);
        hits.add(new Hit(x - 4, y, w + 8, 18, flip));
        if (hover) tooltip(key, label, hint);
    }

    private void appearance(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        hits.clear();
        var p = GuiState.preferences();
        boolean light = p.mode == GuiPreferences.Mode.LIGHT;
        float x = contentX(), w = contentWidth(), ix = x + PAD, iw = w - PAD * 2, half = (iw - 16) / 2;
        UiDraw.icon(g, "palette", x, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, APPEARANCE, x + 13, top + 18, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Theme, top bar, window placement, and the menu, colour and font options", x, top + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, top + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));

        // Card heights, so the page knows how far it can scroll.
        float themeH = CARD_HEAD + 52, barH = CARD_HEAD + 26 + chipsHeight(iw) + PAD, windowsH = CARD_HEAD + 46;
        List<Float> listHeights = new ArrayList<>();
        for (SettingsList card : appearanceLists) listHeights.add(CARD_HEAD + card.contentHeight() + 6f);
        float total = themeH + barH + windowsH + CARD_GAP * 3;
        for (float h : listHeights) total += h + CARD_GAP;
        float view = pageBottom() - pageTop();
        if (focusModule != null) {
            // Opened for one of the folded-in modules: scroll its card into view.
            float offset = themeH + barH + windowsH + CARD_GAP * 3;
            for (int i = 0; i < appearanceLists.size(); i++) {
                if (appearanceLists.get(i).module().getName().equals(focusModule)) { pageScroll = offset; break; }
                offset += listHeights.get(i) + CARD_GAP;
            }
            focusModule = null;
        }
        pageScroll = Math.clamp(pageScroll, 0, Math.max(0, total - view));
        shownPageScroll = UiAnim.approach(shownPageScroll, pageScroll, 18);

        g.enableScissor(Math.round(x - 4), Math.round(pageTop()), Math.round(x + w + 4), Math.round(pageBottom()));
        float y = pageTop() - shownPageScroll;

        // Theme: mode, scale and accent.
        card(g, x, y, w, themeH, "palette", "Theme", "Dark or light, accent colour and how large the menu is drawn", null, mx, my, t);
        float row = y + CARD_HEAD + 4;
        UiDraw.text(g, "Mode", ix, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.mode", new String[]{"Dark", "Light"}, light ? 1 : 0, ix + 34, row, half - 34, mx, my, t,
                i -> { p.mode = i == 1 ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK; GuiState.save(); });
        double scale = Double.isNaN(pendingScale) ? p.uiScale : pendingScale;
        float sx = ix + half + 16, scaleTrackX = sx + 34, scaleTrackW = ix + iw - 32 - scaleTrackX;
        UiDraw.text(g, "Scale", sx, row + 9, UiDraw.SMALL, t.muted());
        float scaleValue = (float) ((scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE));
        boolean overScale = UiDraw.over(mx, my, scaleTrackX - 4, row + 1, scaleTrackW + 8, 16);
        UiDraw.slider(g, scaleTrackX, row + 9, scaleTrackW, "scale".equals(sliding) ? scaleValue : UiAnim.smooth("app.scale", scaleValue, 18),
                UiAnim.smooth("app.scale.h", overScale || "scale".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, Math.round(scale * 100) + "%", ix + iw, row + 9, UiDraw.SMALL, "scale".equals(sliding) ? t.accent() : t.text());
        hits.add(new Hit(scaleTrackX - 4, row + 1, scaleTrackW + 8, 16, () -> startSlider("scale", scaleTrackX, scaleTrackW)));
        row += 26;
        UiDraw.text(g, "Accent", ix, row + 9, UiDraw.SMALL, t.muted());
        var accents = GuiPreferences.Accent.values();
        float cell = (iw - 34) / accents.length;
        for (int i = 0; i < accents.length; i++) {
            var accent = accents[i];
            float cx = ix + 34 + i * cell + cell / 2, cy = row + 9;
            boolean chosen = p.accent == accent, hover = UiDraw.over(mx, my, ix + 34 + i * cell, row - 2, cell, 22);
            float hv = UiAnim.smooth("app.acc.h" + i, hover ? 1 : 0, 18), ring = UiAnim.smooth("app.acc." + i, chosen ? 1 : 0, 14);
            int colour = accentColour(accent, light);
            if (ring > 0) UiDraw.outline(g, cx - 9.5f, cy - 9.5f, 19, 19, 9.5f, 1.3f, UiDraw.alpha(colour, ring));
            UiDraw.circle(g, cx, cy, 6.5f + hv * .8f, colour);
            if (ring > .02f) UiDraw.icon(g, "check", cx - 4, cy - 4, 8, UiDraw.alpha(t.crust(), ring));
            if (hover) tooltip("app.acc." + i, GuiState.title(accent.name().toLowerCase(Locale.ROOT)));
            hits.add(new Hit(ix + 34 + i * cell, row - 2, cell, 22, () -> { p.accent = accent; GuiState.save(); }));
        }
        y += themeH + CARD_GAP;

        // Top bar: which tabs sit between the face and the settings circle, and their style.
        card(g, x, y, w, barH, "modules", "Top bar", "Click a tab to add or remove it · drag tabs in the bar to reorder · right-click to remove", null, mx, my, t);
        row = y + CARD_HEAD + 4;
        UiDraw.text(g, "Tab style", ix, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.tabstyle", new String[]{"Names", "Icons"}, p.tabStyle == GuiPreferences.TabStyle.ICONS ? 1 : 0, ix + 50, row, 110, mx, my, t,
                i -> { p.tabStyle = i == 1 ? GuiPreferences.TabStyle.ICONS : GuiPreferences.TabStyle.NAMES; GuiState.save(); layoutTabs(); });
        UiDraw.text(g, "Names switch to icons when they don't fit", ix + 168, row + 9, 6f, UiDraw.alpha(t.muted(), .7f));
        float cx = ix, cy = row + 26;
        for (String entry : barCandidates()) {
            float cw = UiDraw.width(chipLabel(entry), UiDraw.SMALL) + 26;
            if (cx + cw > ix + iw && cx > ix) { cx = ix; cy += CHIP_H + 4; }
            boolean shown = p.barTabs.contains(entry), hover = UiDraw.over(mx, my, cx, cy, cw, CHIP_H);
            float on = UiAnim.smooth("chip.on." + entry, shown ? 1 : 0, 16), hv = UiAnim.smooth("chip.h." + entry, hover ? 1 : 0, 20);
            UiDraw.rounded(g, cx, cy, cw, CHIP_H, CHIP_H / 2, UiDraw.mix(UiDraw.mix(t.crust(), t.surface(), hv), UiDraw.alpha(t.accent(), .22f + hv * .08f), on));
            if (on < 1) UiDraw.outline(g, cx, cy, cw, CHIP_H, CHIP_H / 2, .6f, UiDraw.alpha(t.overlay(), (1 - on) * (.6f + hv * .3f)));
            int colour = UiDraw.mix(UiDraw.mix(t.muted(), t.text(), hv), t.accent(), on);
            UiDraw.icon(g, tabIcon(entry), cx + 7, cy + CHIP_H / 2 - 3.5f, 7, colour);
            UiDraw.text(g, chipLabel(entry), cx + 18, cy + CHIP_H / 2, UiDraw.SMALL, colour);
            if (hover) tooltip("chip." + entry, shown ? "Remove from the top bar" : "Add to the top bar");
            hits.add(new Hit(cx, cy, cw, CHIP_H, () -> {
                if (!p.barTabs.remove(entry)) { p.barTabs.add(entry); showBar(); }
                GuiState.save(); layoutTabs();
            }));
            cx += cw + 4;
        }
        y += barH + CARD_GAP;

        // Windows: snapping, grid, auto gap and reset.
        card(g, x, y, w, windowsH, "misc", "Windows", "How module windows snap and space out while you drag them", null, mx, my, t);
        row = y + CARD_HEAD + 2;
        toggleRow(g, "Snap to grid", "Windows land on a fine grid when you let go", p.snap, "app.snap", ix, row, half, mx, my, t,
                () -> { p.snap = !p.snap; GuiState.save(); });
        toggleRow(g, "Show grid while moving", "Draws the grid behind windows while you drag", p.showGrid, "app.grid", ix + half + 16, row, half, mx, my, t,
                () -> { p.showGrid = !p.showGrid; GuiState.save(); });
        row += 22;
        UiDraw.text(g, "Auto gap", ix, row + 9, UiDraw.SMALL, t.text());
        float gapTrackX = ix + 52, gapTrackW = half - 52 - 26;
        float gapValue = p.gap / (float) GuiPreferences.MAX_GAP;
        boolean overGap = UiDraw.over(mx, my, gapTrackX - 4, row + 1, gapTrackW + 8, 16);
        UiDraw.slider(g, gapTrackX, row + 9, gapTrackW, "gap".equals(sliding) ? gapValue : UiAnim.smooth("app.gap", gapValue, 18),
                UiAnim.smooth("app.gap.h", overGap || "gap".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, p.gap == 0 ? "Off" : Integer.toString(p.gap), ix + half, row + 9, UiDraw.SMALL, p.gap == 0 ? t.muted() : "gap".equals(sliding) ? t.accent() : t.text());
        if (overGap) tooltip("app.gap", "Auto gap", "Keeps dragged windows at least this far from each other");
        hits.add(new Hit(gapTrackX - 4, row + 1, gapTrackW + 8, 16, () -> startSlider("gap", gapTrackX, gapTrackW)));
        String resetLabel = "Reset window positions";
        float rw = UiDraw.width(resetLabel, UiDraw.SMALL) + 24, rx = ix + iw - rw;
        boolean resetHover = UiDraw.over(mx, my, rx, row + 1, rw, 16);
        float rh = UiAnim.smooth("app.resetpos", resetHover ? 1 : 0, 18);
        UiDraw.rounded(g, rx, row + 1, rw, 16, 8, UiDraw.mix(t.surface(), t.overlay(), rh * .8f));
        UiDraw.icon(g, "reset", rx + 7, row + 5, 8, UiDraw.mix(t.muted(), t.accent(), rh));
        UiDraw.text(g, resetLabel, rx + 18, row + 9, UiDraw.SMALL, t.text());
        hits.add(new Hit(rx, row + 1, rw, 16, () -> { p.windows.clear(); GuiState.save(); minecraft.setScreen(new ModulesScreen(rootParent())); }));
        y += windowsH + CARD_GAP;

        // The folded-in modules, each as a card holding its own settings.
        for (int i = 0; i < appearanceLists.size(); i++) {
            SettingsList card = appearanceLists.get(i);
            Module module = card.module();
            boolean ui = module instanceof UIModule;
            String title = ui ? "Menu" : module instanceof ColorsModule ? "Colours" : "Fonts";
            String subtitle = ui ? "Options for this menu and the classic click GUI"
                    : module instanceof ColorsModule ? "The client's colour scheme for HUD, ESP and other visuals"
                    : "The client's custom font renderer";
            float h = listHeights.get(i);
            card(g, x, y, w, h, icon(module.getName()), title, subtitle, ui ? null : module, mx, my, t);
            card.embed(ix - 4, y + CARD_HEAD, iw + 8, pageTop(), pageBottom());
            card.render(g, mx, my, delta, t);
            y += h + CARD_GAP;
        }
        g.disableScissor();
        // Edge fades and a slim scrollbar hint that the page scrolls.
        if (shownPageScroll > 1) UiDraw.gradient(g, x - 4, pageTop(), w + 8, 10, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownPageScroll < Math.max(0, total - view) - 1) UiDraw.gradient(g, x - 4, pageBottom() - 10, w + 8, 10, 0, UiDraw.alpha(t.base(), 0), t.base());
        if (total > view) {
            float track = Math.max(16, view * view / total), offset = (view - track) * shownPageScroll / Math.max(1, total - view);
            UiDraw.rounded(g, x + w + 3, pageTop() + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
        for (SettingsList card : appearanceLists) card.popup(g, t, width, height);
        String message = appearanceLists.stream().map(SettingsList::message).filter(m -> !m.isEmpty()).findFirst().orElse("");
        if (!message.isEmpty()) UiDraw.text(g, UiDraw.fit(message, w), x, top + panelHeight - 11, UiDraw.SMALL, t.red());
        else UiDraw.text(g, "Changes apply instantly and are saved automatically · scroll for more", x, top + panelHeight - 11, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
    }

    private void startSlider(String which, float trackX, float trackW) {
        sliding = which; sliderX = trackX; sliderW = trackW;
        slideTo(pointerX);
    }

    private void slideTo(double mx) {
        double f = Math.clamp((mx - sliderX) / sliderW, 0, 1);
        if ("scale".equals(sliding)) pendingScale = Math.round((MIN_SCALE + (MAX_SCALE - MIN_SCALE) * f) * 20) / 20.0;
        else if ("gap".equals(sliding)) GuiState.preferences().gap = (int) Math.round(f * GuiPreferences.MAX_GAP / 2) * 2;
    }

    // ---- input ------------------------------------------------------------------------------

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) for (int i = 0; i < sections(); i++)
            if (UiDraw.over(mx, my, left + 8, itemY(i), SIDE, ITEM)) { if (!sectionName(i).equals(section)) select(sectionName(i), null, true); return true; }
        switch (section) {
            case PRESETS -> { if (presets.mouseClicked(event, doubled)) return true; }
            case FRIENDS -> { if (friends.mouseClicked(event, doubled)) return true; }
            case APPEARANCE -> {
                if (my >= pageTop() && my < pageBottom()) {
                    if (event.button() == 0) for (int i = hits.size() - 1; i >= 0; i--) {
                        Hit hit = hits.get(i);
                        if (UiDraw.over(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) { pointerX = mx; hit.action().run(); return true; }
                    }
                    for (SettingsList card : appearanceLists) if (card.mouseClicked(event, doubled)) return true;
                } else for (SettingsList card : appearanceLists) card.stopEditing();
            }
            default -> {
                if (cards != null) {
                    if (event.button() == 0 && UiDraw.over(mx, my, contentX() + contentWidth() - 80, top + 10, 80, 18)) { cards.module().toggle(); return true; }
                    if (cards.mouseClicked(event, doubled, pageTop(), pageBottom())) return true;
                }
            }
        }
        return super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (sliding != null) { slideTo(event.x()); return true; }
        if (cards != null && cards.mouseDragged(event)) return true;
        for (SettingsList card : appearanceLists) if (card.mouseDragged(event)) return true;
        return super.mouseDraggedUi(event, dx, dy);
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) {
        if (sliding != null) {
            String was = sliding; sliding = null;
            var p = GuiState.preferences();
            if ("scale".equals(was) && !Double.isNaN(pendingScale) && pendingScale != p.uiScale) {
                // Apply the new density on release so the page doesn't resize under the pointer.
                p.uiScale = pendingScale; GuiState.save(); rebuildWidgets();
            } else GuiState.save();
            pendingScale = Double.NaN;
            return true;
        }
        if (cards != null && cards.mouseReleased()) return true;
        for (SettingsList card : appearanceLists) if (card.mouseReleased()) return true;
        return super.mouseReleasedUi(event);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (section.equals(PRESETS)) return presets.mouseScrolled(mx, my, vertical);
        if (section.equals(FRIENDS)) return friends.mouseScrolled(mx, my, vertical);
        if (section.equals(APPEARANCE)) {
            if (!UiDraw.over(mx, my, contentX() - 4, pageTop(), contentWidth() + 8, pageBottom() - pageTop())) return false;
            pageScroll = Math.max(0, pageScroll - (float) vertical * 40); return true;
        }
        if (cards != null && UiDraw.over(mx, my, contentX() - 4, pageTop(), contentWidth() + 8, pageBottom() - pageTop())) { modScroll = Math.max(0, modScroll - (float) vertical * 40); return true; }
        return false;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (section.equals(PRESETS) && presets.keyPressed(event)) return true;
        if (section.equals(FRIENDS) && friends.keyPressed(event)) return true;
        if (cards != null && cards.keyPressed(event)) return true;
        for (SettingsList card : appearanceLists) if (card.keyPressed(event)) return true;
        return super.keyPressed(event);
    }
}

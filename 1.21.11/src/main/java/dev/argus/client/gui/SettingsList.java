package dev.argus.client.gui;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.KeyBindSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.SetSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Scrolling editor for one module's settings, shared by the module settings page and the
 * Settings tab. Every row ends in a reset button that restores the setting's default.
 * Uses the actual client Setting API; no shadow copies of module configuration.
 */
final class SettingsList {
    static final int ROW = 22, SLIDER = 96;
    private final UiScreen host;
    private final Module module;
    private final String focusSetting;
    private final long createdAt = System.nanoTime();
    /** Direction of the last click on a choice pill (-1 left, 1 right), so its value slides in from that side. */
    private int cycleDir = 1;
    private int x, y, w, h;
    private int scroll;
    private float shownScroll;
    private List<Setting<?>> visible = List.of();
    private UiInput editor;
    private Setting<?> editing, sliding;
    /** A key setting waiting for its next key or mouse button. */
    private KeyBindSetting capturing;
    /** The list that most recently started capturing a key, so GUI-move can tell a bind is in progress. */
    private static SettingsList captureOwner;

    /** True while any settings list is waiting for a key, so the pressed key mustn't also move or turn the player. */
    static boolean anyCapturing() { return captureOwner != null && captureOwner.capturing != null; }
    private String message = "";
    private long messageAt;
    /** Details popup: the row under the pointer, since when, and where it was drawn. */
    private Setting<?> hovered, popupFor;
    private float hoveredY;
    private long hoverSince;
    private float popupFade;
    private float panelLeft = Float.NaN, panelRight = Float.NaN;
    /** Right edge of the widest visible label and left edge of the leftmost visible control, this frame. */
    private float labelsRight, controlsLeft;

    /** Settings this list shows: the whole module, or one group's members when used as a card. */
    private final List<Setting<?>> source;
    private final boolean hideGroups;

    SettingsList(UiScreen host, Module module, String focusSetting) { this(host, module, focusSetting, null); }

    /** @param members a subset of the module's settings (group headers are left to the card), or null for all. */
    SettingsList(UiScreen host, Module module, String focusSetting, List<Setting<?>> members) {
        this.host = host; this.module = module; this.focusSetting = focusSetting;
        this.source = members; this.hideGroups = members != null;
        if (focusSetting != null) GuiSettings.expandGroups(module.getSettings());
        refresh();
        if (focusSetting != null) for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).getName().equals(focusSetting)) { scroll = i * ROW; break; }
    }

    Module module() { return module; }

    /** Sets the list's box; the first layout after construction centres the focused setting. */
    /** The visible edges of the panel holding this list; the details popup opens outside them when there is room. */
    void panelEdges(float left, float right) { panelLeft = left; panelRight = right; }

    /** Visible vertical range; rows outside it never react to the pointer. */
    private float clipTop = -Float.MAX_VALUE, clipBottom = Float.MAX_VALUE;

    /**
     * Places the list inside a scrolling page: it is drawn at its full height (no own scroll)
     * and only rows between {@code clipTop} and {@code clipBottom} take hover and clicks.
     */
    void embed(float x, float y, float w, float clipTop, float clipBottom) {
        this.x = Math.round(x); this.y = Math.round(y); this.w = Math.round(w);
        this.h = Math.max(ROW, contentHeight());
        this.clipTop = clipTop; this.clipBottom = clipBottom;
        scroll = 0; shownScroll = 0;
    }

    /** Full height of the rows currently shown. */
    int contentHeight() { refresh(); return Math.max(ROW, visible.size() * ROW); }

    private float clipTop() { return Math.max(y, clipTop); }
    private float clipBottom() { return Math.min(y + h, clipBottom); }

    void layout(int x, int y, int w, int h) {
        boolean first = this.h == 0;
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (first && focusSetting != null) { scroll = Math.clamp(scroll - h / 3, 0, maxScroll()); shownScroll = scroll; }
        if (editor != null) host.hostAdd(editor);
    }

    boolean editing() { return editing != null; }
    String hint() {
        if (!message.isEmpty() && (System.nanoTime() - messageAt) / 1e9f < 4.5f) return null;
        if (capturing != null) return "Press a key or mouse button · Esc cancels · Delete restores the default";
        return editing != null ? "Enter or Esc saves · Click away saves" : "Click to change · Drag sliders · The reset icon restores the default";
    }
    String message() { return message.isEmpty() || (System.nanoTime() - messageAt) / 1e9f >= 4.5f ? "" : message; }

    /**
     * Options never hide behind a chevron: groups fold only through their card, and the options that belong to a switch are
     * shown exactly while the switch is on.
     */
    private void syncExpansion() {
        for (Setting<?> setting : module.getSettings()) {
            if (setting.visibilityDependency && !setting.isGroupOnly()) setting.expanded = !(setting.getValue() instanceof Boolean on) || on;
        }
    }

    private void refresh() {
        syncExpansion();
        List<Setting<?>> rows = GuiSettings.rows(source == null ? module.getSettings() : source, focusSetting);
        visible = hideGroups ? rows.stream().filter(setting -> !setting.isGroupOnly()).toList() : rows;
    }

    /** Re-registers an open inline editor after the screen rebuilt its widgets. */
    void reattach() { if (editor != null) host.hostAdd(editor); }

    boolean showsSetting(String name) { refresh(); return visible.stream().anyMatch(setting -> setting.getName().equals(name)); }
    int rowCount() { refresh(); return visible.size(); }
    private int maxScroll() { return Math.max(0, visible.size() * ROW - h); }
    private float right() { return x + w - 30; }
    private float resetX() { return x + w - 22; }
    private float sliderX() { return right() - 34 - SLIDER; }

    static String value(Setting<?> setting) {
        if (setting instanceof KeyBindSetting bind) return bind.displayName();
        // A labelled end of a slider (such as "MAX" or "Unlimited") shows its label instead of the number.
        if (setting.shouldDisplayBoundaryLabel()) return setting.getBoundaryLabel();
        Object value = setting.getValue();
        if (value instanceof NamedEntry named) return named.getName();
        if (value instanceof Color colour) return String.format("#%06X", colour.getRGB() & 0xffffff) + ((colour.getAlpha() < 255) ? String.format(" · %d%%", Math.round(colour.getAlpha() / 2.55)) : "");
        if (setting instanceof SetSetting<?> collection) return collection.getValue().size() + (collection.getValue().size() == 1 ? " entry" : " entries");
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return number == Math.rint(number) && Math.abs(number) < 1e9 ? Long.toString((long) number) : String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (value instanceof Enum<?> choice) return GuiState.title(choice.name().toLowerCase(Locale.ROOT));
        return GuiSettings.editText(setting);
    }

    private static double sliderMaximum(Setting<?> setting) {
        return setting instanceof NumberSetting<?> number ? number.getSliderMaximumValue() : ((Number) setting.getMaximumValue()).doubleValue();
    }
    private static boolean slider(Setting<?> setting) {
        return setting.getValue() instanceof Number && setting.isNumericBoundsEnabled()
                && setting.getMinimumValue() instanceof Number min && setting.getMaximumValue() instanceof Number max
                && sliderMaximum(setting) > min.doubleValue();
    }
    static double fraction(Setting<?> setting) {
        if (setting instanceof NumberSetting<?> number) return number.sliderFraction();
        double min = ((Number) setting.getMinimumValue()).doubleValue(), max = sliderMaximum(setting);
        return Math.clamp((((Number) setting.getValue()).doubleValue() - min) / (max - min), 0, 1);
    }
    private static boolean isDefault(Setting<?> setting) { return Objects.equals(setting.getValue(), setting.getDefaultValue()); }

    void render(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        refresh();
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        g.enableScissor(x - 4, y - 2, x + w + 4, y + h);
        boolean editorShown = false;
        Setting<?> pointed = null;
        float pointedY = 0, labelEdge = x, controlEdge = x + w;
        for (int i = 0; i < visible.size(); i++) {
            Setting<?> setting = visible.get(i);
            String key = "set." + System.identityHashCode(setting);
            // Rows animate relative to the list, so moving the list (page scrolling) never lags.
            float rowY = y + UiAnim.smooth(key + ".y", i * ROW, i * ROW, 18) - shownScroll;
            float appear = UiAnim.smooth(key + ".a", 0, 1, 12);
            if (rowY + ROW < y || rowY > y + h) continue;
            float before = UiRender.opacity;
            UiRender.opacity = before * appear;
            boolean hover = UiDraw.over(mx, my, x, rowY, w, ROW) && my >= clipTop() && my < clipBottom();
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20);
            boolean focus = editing == setting || sliding == setting;
            float highlight = Objects.equals(focusSetting, setting.getName()) ? Math.max(0, 1 - (System.nanoTime() - createdAt) / 1.6e9f) : 0;
            float flash = UiAnim.smooth(key + ".reset", 0, 6);
            if (hv > 0 || focus) UiDraw.rounded(g, x, rowY + 1, w, ROW - 2, 5, UiDraw.alpha(t.surface(), focus ? 1 : hv * .8f));
            if (highlight > 0) UiDraw.rounded(g, x, rowY + 1, w, ROW - 2, 5, UiDraw.alpha(t.accent(), .25f * highlight));
            if (flash > .01f) UiDraw.rounded(g, x, rowY + 1, w, ROW - 2, 5, UiDraw.alpha(t.accent(), .22f * flash));
            float cy = rowY + ROW / 2f;
            float labelX = x + 8;
            int half = (w - 12) / 2;
            String label = UiDraw.fit(GuiState.title(setting.getName()), half - 12);
            if (setting.isGroupOnly()) {
                UiDraw.bold(g, label, labelX, cy, UiDraw.TEXT, t.accent());
                UiRender.opacity = before; continue;
            }
            UiDraw.text(g, label, labelX, cy, setting.isVisible() ? UiDraw.mix(t.text(), 0xffffffff, hv * .1f) : t.muted());
            labelEdge = Math.max(labelEdge, labelX + UiDraw.width(label));
            controlEdge = Math.min(controlEdge, controlLeft(setting));
            // Reset to default: always present, dimmed while the value is already the default.
            boolean atDefault = isDefault(setting);
            boolean resetHover = hover && UiDraw.over(mx, my, resetX() - 3, rowY + 3, 16, ROW - 6);
            if (hover && !resetHover) { pointed = setting; pointedY = rowY; }
            float rh = UiAnim.smooth(key + ".rh", resetHover ? 1 : 0, 20);
            if (rh > 0 && !atDefault) UiDraw.circle(g, resetX() + 5, cy, 7, UiDraw.alpha(t.accent(), .16f * rh));
            int resetColour = atDefault ? UiDraw.alpha(t.muted(), .22f + hv * .13f) : UiDraw.mix(UiDraw.alpha(t.muted(), .55f + hv * .3f), t.accent(), rh);
            UiDraw.icon(g, "reset", resetX(), cy - 5, 10, resetColour);
            if (resetHover) host.hostTooltip(key + ".rt", atDefault ? "Already at default" : "Reset to default", "Default: " + defaultText(setting));
            Object current = setting.getValue();
            float right = right();
            // Switching between the control, the inline editor and "Unavailable" fades the new one in.
            UiRender.opacity *= UiMotion.sinceChange(key + ".state", (setting.isVisible() ? 1 : 0) + (editing == setting ? 2 : 0));
            if (!setting.isVisible()) {
                UiDraw.right(g, "Unavailable", right, cy, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
            } else if (editing == setting && editor != null) {
                editor.place(Math.round(x + w / 2f), Math.round(cy - 6), Math.round(right - (x + w / 2f)));
                editor.visible = rowY >= clipTop() - 2 && rowY + ROW <= clipBottom() + 2;
                editorShown = editor.visible;
                editor.render(g, mx, my, delta);
            } else if (setting instanceof KeyBindSetting bind) {
                boolean listening = capturing == bind;
                String text = listening ? "Press a key…" : UiDraw.fit(bind.displayName(), half - 40);
                float pulse = listening ? UiAnim.pulse(1.5e8) : 0;
                float pw = UiDraw.width(text, UiDraw.SMALL) + 23, px = right - pw;
                UiDraw.rounded(g, px, cy - 7, pw, 14, 7, listening ? UiDraw.alpha(t.accent(), .18f + pulse * .12f) : UiDraw.mix(t.mantle(), t.overlay(), hv * .6f));
                if (listening) UiDraw.outline(g, px, cy - 7, pw, 14, 7, .7f, UiDraw.alpha(t.accent(), .5f + pulse * .5f));
                UiDraw.icon(g, "keyboard", px + 6, cy - 4, 8, listening ? t.accent() : t.muted());
                UiDraw.text(g, text, px + 17, cy, UiDraw.SMALL, listening ? t.accent() : t.text());
            } else if (current instanceof Boolean enabled) {
                UiDraw.toggle(g, right - 18, cy - 5, UiAnim.smooth(key + ".on", enabled ? 1 : 0, 16), hover && !resetHover, t);
            } else if (slider(setting)) {
                float target = (float) fraction(setting);
                float shown = sliding == setting ? target : UiAnim.smooth(key + ".v", target, 18);
                boolean over = hover && UiDraw.over(mx, my, sliderX() - 4, rowY, SLIDER + 8, ROW);
                UiDraw.slider(g, sliderX(), cy, SLIDER, shown, UiAnim.smooth(key + ".sh", over || sliding == setting ? 1 : 0, 18), t);
                UiDraw.right(g, value(setting), right, cy, focus ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv));
            } else if (current instanceof Enum<?> || current instanceof NamedEntry) {
                // The pill eases to the new value's width and the value slides in from the side that was clicked.
                String text = UiDraw.fit(value(setting), half - 40);
                float target = UiDraw.width(text) + 30, pw = UiAnim.smooth(key + ".pw", target, target, 20), px = right - pw;
                UiDraw.rounded(g, px, cy - 7, pw, 14, 7, UiDraw.mix(t.mantle(), t.overlay(), hv * .6f));
                UiDraw.icon(g, "chevron_left", px + 3, cy - 3.5f, 7, UiDraw.alpha(t.muted(), .5f + hv * .5f));
                UiDraw.icon(g, "chevron_right", px + pw - 10, cy - 3.5f, 7, UiDraw.alpha(t.muted(), .5f + hv * .5f));
                g.enableScissor((int) Math.floor(px + 10), (int) Math.floor(cy - 7), (int) Math.ceil(px + pw - 10), (int) Math.ceil(cy + 7));
                try { UiMotion.crossfade(g, key + ".enum", text, cycleDir * 8, 0, v -> UiDraw.center(g, v, px, cy, pw, t.text())); }
                finally { g.disableScissor(); }
            } else if (current instanceof Color colour) {
                String text = value(setting);
                float tw = UiDraw.width(text, UiDraw.SMALL);
                UiDraw.right(g, text, right, cy, UiDraw.SMALL, UiDraw.mix(t.muted(), t.text(), hv));
                float sx = right - tw - 18;
                UiDraw.rounded(g, sx, cy - 6, 12, 12, 3.5f, colour.getRGB() | 0xff000000);
                UiDraw.outline(g, sx, cy - 6, 12, 12, 3.5f, .6f, UiDraw.alpha(t.text(), .25f));
            } else if (setting instanceof SetSetting<?>) {
                // A list is edited on its own screen, so the row is a labelled button rather than a bare chevron.
                String text = UiDraw.fit(value(setting), half - 60) + "  ·  Edit";
                float pw = UiDraw.width(text, UiDraw.SMALL) + 22, px = right - pw;
                UiDraw.rounded(g, px, cy - 7, pw, 14, 7, UiDraw.mix(UiDraw.alpha(t.accent(), .1f), UiDraw.alpha(t.accent(), .22f), hv));
                UiDraw.outline(g, px, cy - 7, pw, 14, 7, .6f, UiDraw.alpha(t.accent(), .3f + hv * .3f));
                UiDraw.text(g, text, px + 8, cy, UiDraw.SMALL, t.accent());
                UiDraw.icon(g, "chevron_right", px + pw - 12, cy - 3.5f, 7, t.accent());
            } else {
                String text = UiDraw.fit(value(setting), half - 20);
                float pw = Math.max(40, UiDraw.width(text) + 14), px = right - pw;
                UiDraw.rounded(g, px, cy - 7, pw, 14, 4, UiDraw.mix(t.mantle(), t.crust(), hv));
                UiDraw.outline(g, px, cy - 7, pw, 14, 4, .6f, UiDraw.alpha(t.overlay(), .4f + hv * .4f));
                UiDraw.text(g, text, px + 7, cy, UiDraw.mix(t.muted(), t.text(), hv));
            }
            UiRender.opacity = before;
        }
        if (editor != null) editor.visible = editorShown;
        if (visible.isEmpty()) UiDraw.center(g, "This module has no settings.", x, y + 14, w, t.muted());
        g.disableScissor();
        if (pointed != hovered) { hovered = pointed; hoverSince = System.nanoTime(); }
        labelsRight = labelEdge; controlsLeft = controlEdge;
        hoveredY = pointedY;
        UiMotion.faded(UiMotion.edge(shownScroll), () -> UiDraw.gradient(g, x - 4, y - 2, w + 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge(maxScroll() - shownScroll), () -> UiDraw.gradient(g, x - 4, y + h - 8, w + 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base()));
        if (maxScroll() > 0) {
            float track = Math.max(14, h * (float) h / (visible.size() * ROW)), offset = (h - track) * shownScroll / maxScroll();
            // The scrollbar rests faint and brightens while the list is hovered or scrolling.
            boolean active = UiDraw.over(mx, my, x - 4, y, w + 10, h) || Math.abs(shownScroll - scroll) > .5f;
            UiDraw.rounded(g, x + w + 2, y + offset, 2, track, 1, UiDraw.alpha(t.overlay(), UiAnim.smooth("sl.bar." + System.identityHashCode(this), active ? .8f : .3f, 10)));
        }
    }

    // ---- details popup ----------------------------------------------------------------------

    private static final float POPUP_DELAY = .45f, POPUP_WIDTH = 176;

    private static String kind(Setting<?> setting) {
        Object value = setting.getValue();
        if (setting instanceof KeyBindSetting) return "Key";
        if (value instanceof Boolean) return "Toggle";
        if (value instanceof Number) return "Number";
        if (value instanceof Enum<?> || value instanceof NamedEntry) return "Choice";
        if (value instanceof Color) return "Colour";
        if (setting instanceof SetSetting<?>) return "List";
        return "Text";
    }

    private static String number(Object value) {
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return number == Math.rint(number) ? Long.toString((long) number) : String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return String.valueOf(value);
    }

    /** Label/value facts shown under the description. */
    private static List<String[]> facts(Setting<?> setting) {
        List<String[]> facts = new ArrayList<>();
        facts.add(new String[]{"Current", setting.getValue() instanceof Boolean on ? (on ? "On" : "Off") : value(setting)});
        facts.add(new String[]{"Default", defaultText(setting)});
        if (setting.getValue() instanceof Number && setting.isNumericBoundsEnabled() && setting.getMinimumValue() != null && setting.getMaximumValue() != null)
            facts.add(new String[]{"Range", number(setting.getMinimumValue()) + " to " + number(sliderMaximum(setting))});
        if (setting.getValue() instanceof Enum<?> choice) {
            List<String> names = new ArrayList<>();
            for (Object option : choice.getDeclaringClass().getEnumConstants())
                names.add(option instanceof NamedEntry named ? named.getName() : GuiState.title(((Enum<?>) option).name().toLowerCase(Locale.ROOT)));
            facts.add(new String[]{"Options", String.join(", ", names)});
        }
        return facts;
    }

    /** Where a row's control starts, mirroring the layout in {@link #render}. */
    private float controlLeft(Setting<?> setting) {
        Object current = setting.getValue();
        float right = right();
        if (editing == setting) return x + w / 2f;
        if (!setting.isVisible()) return right - UiDraw.width("Unavailable", UiDraw.SMALL);
        if (setting instanceof KeyBindSetting bind) return right - UiDraw.width(capturing == bind ? "Press a key…" : bind.displayName(), UiDraw.SMALL) - 23;
        if (current instanceof Boolean) return right - 18;
        if (slider(setting)) return sliderX();
        if (current instanceof Enum<?> || current instanceof NamedEntry) return right - UiDraw.width(UiDraw.fit(value(setting), (w - 12) / 2f - 40)) - 30;
        if (current instanceof Color) return right - UiDraw.width(value(setting), UiDraw.SMALL) - 18;
        if (setting instanceof SetSetting<?>) return right - 10 - UiDraw.width(value(setting));
        return right - Math.max(40, UiDraw.width(UiDraw.fit(value(setting), (w - 12) / 2f - 20)) + 14);
    }

    /**
     * The written description from the client's language table (the same text the classic GUI
     * showed), respecting the UI module's Descriptions option; otherwise the setting's own text
     * or the curated catalog. The toggle hides the whole description body, not just the table.
     */
    private String description(Setting<?> setting) {
        String text = null;
        boolean enabled = true;
        try {
            var ui = dev.argus.client.modules.client.UIModule.INSTANCE;
            enabled = ui == null || !(ui.descriptionsSetting.getValue() instanceof Boolean on) || on;
            if (enabled && dev.argus.client.ClientContext.languageTable != null)
                text = dev.argus.client.ClientContext.languageTable.getTranslation(
                        "descriptions.settings.%s.%s".formatted(module.getConfigName(), setting.getConfigName()));
        } catch (RuntimeException ignored) { }
        if (!enabled) return "";
        if (text != null && text.isBlank()) text = null;
        if (text == null && setting.isDescriptionPresent()) text = setting.getDescription();
        if (text == null) text = DescriptionCatalog.setting(module, setting);
        if (text == null || text.isBlank()) return "";
        text = text.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        return UiText.stripFormatting(text.replace("\\n", "\n").trim());
    }

    /**
     * Draws the hovered setting's details. It only appears after the pointer rests on a row,
     * never takes clicks, hides while dragging or typing, and is placed beside the panel (or
     * under the label column) so it never covers another setting's control.
     */
    void popup(GuiGraphics g, UiTheme t, int screenWidth, int screenHeight) {
        boolean busy = sliding != null || (editing != null && editing == hovered);
        boolean show = hovered != null && !busy && (System.nanoTime() - hoverSince) / 1e9f >= POPUP_DELAY;
        if (show) popupFor = hovered;
        popupFade = UiAnim.approach(popupFade, show ? 1 : 0, show ? 14 : 24);
        if (popupFade <= .01f || popupFor == null) { if (popupFade <= .01f) popupFor = null; return; }
        Setting<?> setting = popupFor;
        float left = Float.isNaN(panelLeft) ? x - 12 : panelLeft, right = Float.isNaN(panelRight) ? x + w + 12 : panelRight;
        float pw = POPUP_WIDTH, px, rowY = hoveredY;
        boolean beside = true;
        float rightSpace = screenWidth - right - 14, leftSpace = left - 14;
        if (rightSpace >= 140) { pw = Math.min(POPUP_WIDTH, rightSpace); px = right + 8; }
        else if (leftSpace >= 140) { pw = Math.min(POPUP_WIDTH, leftSpace); px = left - 8 - pw; }
        else {
            beside = false;
            // No room beside the panel: use the empty gap between the names and the controls,
            // so neither any setting's name nor its control is covered.
            float gapLeft = labelsRight + 12, gapRight = controlsLeft - 10;
            if (gapRight - gapLeft >= 130) { pw = Math.min(POPUP_WIDTH, gapRight - gapLeft); px = gapLeft; }
            else { pw = Math.max(120, Math.min(POPUP_WIDTH, w * .5f - 12)); px = x + 4; }
        }
        float inner = pw - 16;
        List<String> lines = UiDraw.wrap(description(setting), inner, UiDraw.SMALL);
        List<String[]> facts = facts(setting);
        float factLabel = 0;
        for (String[] fact : facts) factLabel = Math.max(factLabel, UiDraw.width(fact[0], UiDraw.SMALL));
        List<String[]> factLines = new ArrayList<>();
        for (String[] fact : facts) {
            List<String> wrapped = UiDraw.wrap(fact[1], inner - factLabel - 8, UiDraw.SMALL);
            for (int i = 0; i < wrapped.size(); i++) factLines.add(new String[]{i == 0 ? fact[0] : "", wrapped.get(i)});
        }
        String status = !setting.isVisible() ? "Unavailable until the option it depends on is turned on." : null;
        List<String> statusLines = status == null ? List.of() : UiDraw.wrap(status, inner, UiDraw.SMALL);
        float lineH = 9.5f;
        float ph = 8 + 12 + (lines.isEmpty() ? 0 : lines.size() * lineH + 3) + 4 + factLines.size() * lineH + (statusLines.isEmpty() ? 0 : 3 + statusLines.size() * lineH) + 6;
        float py;
        if (beside) py = Math.clamp(rowY + ROW / 2f - 14, 46, screenHeight - ph - 26);
        else {
            // Under the label column; flips above the row near the bottom of the list.
            py = rowY + ROW + 2;
            if (py + ph > y + h) py = rowY - ph - 2;
            py = Math.clamp(py, 46, screenHeight - ph - 26);
        }
        float before = UiRender.opacity;
        UiRender.opacity = before * popupFade;
        py += (1 - popupFade) * 3;
        UiDraw.shadow(g, px, py + 2, pw, ph, 7, 10, UiDraw.alpha(0xff000000, .35f));
        UiDraw.rounded(g, px, py, pw, ph, 7, t.mantle());
        UiDraw.outline(g, px, py, pw, ph, 7, .6f, UiDraw.alpha(t.accent(), .45f));
        // A small notch points back at the row when the popup sits beside the panel.
        if (beside) {
            float ny = Math.clamp(rowY + ROW / 2f, py + 8, py + ph - 8);
            float nx = px < x ? px + pw - 1.5f : px - 1.5f;
            UiDraw.rounded(g, nx, ny - 4, 3, 8, 1.5f, UiDraw.alpha(t.accent(), .7f));
        }
        float cy = py + 8 + 5;
        String kind = kind(setting);
        float kw = UiDraw.width(kind, 5.5f) + 10;
        UiDraw.bold(g, UiDraw.fit(GuiState.title(setting.getName()), inner - kw - 6), px + 8, cy, UiDraw.TEXT, t.text());
        UiDraw.rounded(g, px + pw - 8 - kw, cy - 4.5f, kw, 9, 4.5f, UiDraw.alpha(t.accent(), .16f));
        UiDraw.center(g, kind, px + pw - 8 - kw, cy, kw, 5.5f, t.accent());
        cy += 12;
        for (String line : lines) { UiDraw.text(g, line, px + 8, cy, UiDraw.SMALL, t.text()); cy += lineH; }
        if (!lines.isEmpty()) cy += 3;
        UiDraw.rect(g, px + 8, cy - 3, inner, .6f, UiDraw.alpha(t.overlay(), .6f));
        cy += 3;
        for (String[] fact : factLines) {
            if (!fact[0].isEmpty()) UiDraw.text(g, fact[0], px + 8, cy, UiDraw.SMALL, t.muted());
            UiDraw.text(g, fact[1], px + 8 + factLabel + 8, cy, UiDraw.SMALL, fact[0].equals("Current") ? t.accent() : t.text());
            cy += lineH;
        }
        if (!statusLines.isEmpty()) { cy += 3; for (String line : statusLines) { UiDraw.text(g, line, px + 8, cy, UiDraw.SMALL, t.yellow()); cy += lineH; } }
        UiRender.opacity = before;
    }

    private static String defaultText(Setting<?> setting) {
        if (setting instanceof KeyBindSetting bind) return bind.followsName() + " key";
        Object value = setting.getDefaultValue();
        if (value instanceof Boolean b) return b ? "On" : "Off";
        if (value instanceof NamedEntry named) return named.getName();
        if (value instanceof Enum<?> choice) return GuiState.title(choice.name().toLowerCase(Locale.ROOT));
        if (value instanceof Color colour) return String.format("#%06X", colour.getRGB() & 0xffffff);
        if (value instanceof Collection<?> collection) return collection.size() + (collection.size() == 1 ? " entry" : " entries");
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return number == Math.rint(number) ? Long.toString((long) number) : String.format(Locale.ROOT, "%.2f", number);
        }
        return String.valueOf(value);
    }

    private void fail(String text) { message = text; messageAt = System.nanoTime(); }

    @SuppressWarnings({"rawtypes", "unchecked"}) private void change(Setting setting, Object next) { setting.setValue(next); message = ""; }

    /** Ends an inline edit, saving what was typed when it's valid; an invalid value is dropped and its error stays shown. */
    void stopEditing() {
        if (editing != null && editor != null) apply();
        closeEditor();
    }

    private void closeEditor() {
        if (editor != null) host.hostRemove(editor);
        editor = null; editing = null; capturing = null;
    }

    private void startEditing(Setting<?> setting) {
        stopEditing();
        editing = setting;
        editor = new UiInput(0, 0, 100, 12, setting.getValue() instanceof Color ? "#RRGGBB or #AARRGGBB" : "Value");
        editor.setMaxLength(1024);
        Object value = setting.getValue();
        editor.setValue(value instanceof Color colour ? String.format("#%08X", colour.getRGB()) : GuiSettings.editText(setting));
        editor.visible = false;
        host.hostAdd(editor); host.hostFocus(editor);
        editor.moveCursorToEnd(false); editor.setHighlightPos(0);
    }

    private void reset(Setting<?> setting) {
        if (editing == setting) closeEditor();
        setting.reset(); message = "";
        UiAnim.set("set." + System.identityHashCode(setting) + ".reset", 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void slideTo(Setting setting, double mouseX) {
        double f = Math.clamp((mouseX - sliderX()) / SLIDER, 0, 1);
        Object old = setting.getValue();
        Object next = slideValue(setting, f);
        if (!next.equals(old)) change(setting, next);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object slideValue(Setting setting, double f) {
        double minimum = ((Number) setting.getMinimumValue()).doubleValue(), maximum = sliderMaximum(setting);
        if (maximum <= minimum) return setting.getValue();
        double raw = minimum + (maximum - minimum) * Math.clamp(f, 0, 1);
        Object old = setting.getValue();
        Object next;
        if (old instanceof Integer) next = (int) Math.round(raw);
        else if (old instanceof Long) next = Math.round(raw);
        else if (old instanceof Short) next = (short) Math.round(raw);
        else if (old instanceof Byte) next = (byte) Math.round(raw);
        else {
            // Snap to a tidy step: 1/100th of the range rounded to 1, 2 or 5 × 10^n.
            double step = (maximum - minimum) / 100, magnitude = Math.pow(10, Math.floor(Math.log10(step))), unit = step / magnitude;
            step = (unit < 1.5 ? 1 : unit < 3.5 ? 2 : 5) * magnitude;
            double snapped = Math.clamp(Math.round(raw / step) * step, minimum, maximum);
            next = old instanceof Float ? (Object) (float) snapped : (Object) snapped;
        }
        return next;
    }

    /** @return true if the click landed in the list (handled or not). */
    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (capturing != null) { capturing.bindMouse(event.button()); capturing = null; return true; }
        if (editor != null && editor.visible && editor.isMouseOver(mx, my)) { host.hostFocus(editor); return editor.mouseClicked(event, doubled); }
        if (!UiDraw.over(mx, my, x, y, w, h) || my < clipTop() || my >= clipBottom()) { if (editing != null) stopEditing(); return false; }
        int i = (int) Math.floor((my - y + shownScroll) / ROW);
        if (i < 0 || i >= visible.size()) { stopEditing(); return true; }
        Setting<?> setting = visible.get(i); Object current = setting.getValue();
        boolean rejected = editing != null && editing != setting && !saveOrDrop();
        if (!rejected) message = "";
        if (setting.isGroupOnly()) return true;
        float rowY = y + i * ROW - shownScroll;
        if (UiDraw.over(mx, my, resetX() - 3, rowY + 3, 16, ROW - 6)) { if (!isDefault(setting)) reset(setting); return true; }
        if (!setting.isVisible()) { fail("Turn on the option this depends on to change it."); return true; }
        if (event.button() == 1) { reset(setting); return true; }
        if (event.button() != 0) return true;
        if (setting instanceof SetSetting<?> collection) host.hostOpen(new CollectionSettingScreen(host, collection));
        else if (setting instanceof KeyBindSetting bind) { stopEditing(); capturing = bind; captureOwner = this; }
        else if (current instanceof Boolean enabled) change(setting, !enabled);
        else if (slider(setting) && mx >= sliderX() - 4 && mx <= sliderX() + SLIDER + 4) { sliding = setting; slideTo(setting, mx); }
        else if (current instanceof Enum<?> choice) {
            Object[] choices = choice.getDeclaringClass().getEnumConstants();
            float pw = UiDraw.width(value(setting)) + 30, px = right() - pw;
            int direction = mx < px + pw / 2 && mx >= px ? -1 : 1;
            cycleDir = direction;
            change(setting, choices[Math.floorMod(choice.ordinal() + direction, choices.length)]);
        }
        else if (setting instanceof ColorSetting colour) {
            stopEditing();
            float left = Float.isNaN(panelLeft) ? x - 12 : panelLeft, right = Float.isNaN(panelRight) ? x + w + 12 : panelRight;
            ColorPicker.show(colour, x, rowY, w, ROW, left, right, host.width, host.height);
        }
        else if (editing != setting) startEditing(setting);
        return true;
    }

    boolean mouseDragged(MouseButtonEvent event) { if (sliding == null) return false; slideTo(sliding, event.x()); return true; }
    boolean mouseReleased() { boolean was = sliding != null; sliding = null; return was; }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!UiDraw.over(mx, my, x, y, w + 6, h)) return false;
        scroll = Math.clamp(scroll - (int) (vertical * ROW * 2), 0, maxScroll()); return true;
    }

    /** Enter applies an inline edit. Esc applies it too, then lets the Esc close the menu; an invalid value keeps the editor open. */
    boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            if (event.key() == 259 || event.key() == 261) capturing.followDefault();
            else if (event.key() != 256) capturing.bindKey(event.key());
            capturing = null;
            return true;
        }
        if (editor == null || editing == null) return false;
        if (event.key() == 257 || event.key() == 335) { apply(); return true; }
        if (event.key() == 256) return !apply();
        return false;
    }

    /** Ends the edit as {@link #stopEditing()} does; false when the typed value was rejected. */
    private boolean saveOrDrop() {
        boolean rejected = editing != null && editor != null && !apply();
        closeEditor();
        return !rejected;
    }

    /** Applies the typed value and closes the editor; on a bad value shows why and keeps it open. */
    private boolean apply() {
        String input = editor.getValue().trim();
        try {
            Object old = editing.getValue();
            if (old instanceof Number) {
                double number = Double.parseDouble(input); if (!Double.isFinite(number)) throw new IllegalArgumentException();
                if (old instanceof Float && !Float.isFinite(Float.parseFloat(input))) throw new IllegalArgumentException();
                if (editing.isNumericBoundsEnabled()) {
                    Object minimum = editing.getMinimumValue(), maximum = editing.getMaximumValue();
                    if (minimum instanceof Number min && number < min.doubleValue() || maximum instanceof Number max && number > max.doubleValue())
                        throw new IllegalArgumentException();
                }
            }
            if (editing instanceof ColorSetting colour) {
                String hex = input.startsWith("#") ? input.substring(1) : input;
                if (hex.length() != 6 && hex.length() != 8) throw new IllegalArgumentException();
                long bits = Long.parseUnsignedLong(hex, 16); colour.setColor(new Color((int) (hex.length() == 6 ? bits | 0xff000000L : bits), true));
            } else editing.parseValue(input);
            closeEditor(); message = "";
            return true;
        } catch (RuntimeException failure) {
            String range = editing.isNumericBoundsEnabled() && editing.getMinimumValue() != null && editing.getMaximumValue() != null
                    ? " Allowed: " + editing.getMinimumValue() + " to " + editing.getMaximumValue() + "." : "";
            fail("That value isn't valid." + range);
            return false;
        }
    }

    /** Draws the hint or the latest error message at the given footer line. */
    void footer(GuiGraphics g, float fx, float cy, float width, UiTheme t, String override) {
        String error = message();
        var line = error.isEmpty() ? new UiMotion.Line(UiDraw.fit(override != null ? override : hint(), width), 0) : new UiMotion.Line(UiDraw.fit(error, width), 1);
        UiMotion.crossfade(g, "sl.footer." + System.identityHashCode(this), line, l -> UiDraw.text(g, l.text(), fx, cy, UiDraw.SMALL, UiMotion.footerColour(l.kind(), t)));
    }
}

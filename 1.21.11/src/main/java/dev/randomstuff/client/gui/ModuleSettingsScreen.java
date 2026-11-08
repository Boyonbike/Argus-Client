package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import dev.randomstuff.client.modules.Keybind;
import dev.randomstuff.client.settings.Setting;
import dev.randomstuff.client.settings.ColorSetting;
import dev.randomstuff.client.settings.SetSetting;
import dev.randomstuff.client.settings.NamedEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.awt.Color;
import java.util.*;

/** Uses the actual client Setting API; no shadow copies of module configuration. */
final class ModuleSettingsScreen extends UiScreen {
    private static final int ROW = 22, HEAD = 64, SLIDER = 96;
    private final Module module;
    private final String initialSetting;
    private int left, top, panelWidth, panelHeight, scroll, viewHeight;
    private float shownScroll;
    private List<Setting<?>> visible = List.of();
    private UiInput editor;
    private Setting<?> editing, sliding;
    private boolean binding, revealedGroups, scrolledToInitial;
    private String message = "";
    private long messageAt, openedAt = System.nanoTime();

    ModuleSettingsScreen(Screen parent, Module module, String setting) { super(module.getName(), parent, "Modules"); this.module = module; initialSetting = setting; }

    @Override protected void buildContent() {
        panelWidth = Math.min(440, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24; viewHeight = Math.max(40, panelHeight - HEAD - 24);
        if (initialSetting != null && !revealedGroups) { GuiSettings.expandGroups(module.getSettings()); revealedGroups = true; }
        refreshRows();
        if (initialSetting != null && !scrolledToInitial) {
            for (int i = 0; i < visible.size(); i++) if (visible.get(i).getName().equals(initialSetting)) { scroll = Math.max(0, i * ROW - viewHeight / 3); break; }
            shownScroll = scroll; scrolledToInitial = true;
        }
        if (editor != null) { addWidget(editor); setFocused(editor); }
    }

    private void refreshRows() { visible = GuiSettings.rows(module.getSettings(), initialSetting); }

    private static String value(Setting<?> setting) {
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

    private static boolean slider(Setting<?> setting) {
        return setting.getValue() instanceof Number && setting.isNumericBoundsEnabled()
                && setting.getMinimumValue() instanceof Number min && setting.getMaximumValue() instanceof Number max
                && max.doubleValue() > min.doubleValue();
    }
    private static double fraction(Setting<?> setting) {
        double min = ((Number) setting.getMinimumValue()).doubleValue(), max = ((Number) setting.getMaximumValue()).doubleValue();
        return Math.clamp((((Number) setting.getValue()).doubleValue() - min) / (max - min), 0, 1);
    }

    private int maxScroll() { return Math.max(0, visible.size() * ROW - viewHeight); }
    private int listTop() { return top + HEAD; }
    private float sliderX() { return left + panelWidth - 14 - 34 - SLIDER; }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        refreshRows();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // ---- header
        boolean backHover = UiDraw.over(mx, my, left + 10, top + 10, 18, 18);
        float bh = UiAnim.smooth("set.back", backHover ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.icon(g, module.getCategory().name().toLowerCase(Locale.ROOT), left + 36, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), panelWidth - 150), left + 49, top + 18, UiDraw.TITLE, t.text());
        String description = module.getDescription() == null || module.getDescription().isBlank() ? "No description" : module.getDescription();
        UiDraw.text(g, UiDraw.fit(description, panelWidth - 140), left + 36, top + 31, UiDraw.SMALL, t.muted());
        // enabled switch
        boolean enabledHover = UiDraw.over(mx, my, left + panelWidth - 80, top + 10, 70, 18);
        float on = UiAnim.smooth("set.enabled." + module.getName(), module.isToggled() ? 1 : 0, 16);
        UiDraw.right(g, module.isToggled() ? "Enabled" : "Disabled", left + panelWidth - 38, top + 19, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
        UiDraw.toggle(g, left + panelWidth - 32, top + 14, on, enabledHover, t);
        // keybind chip
        String bindLabel = binding ? "Press a key…" : module.getKeybind().isUnbound() ? "Not bound" : module.getKeybind().getDisplayName();
        float chipW = UiDraw.width(bindLabel, UiDraw.SMALL) + 24, chipX = left + panelWidth - 14 - chipW;
        boolean chipHover = UiDraw.over(mx, my, chipX, top + 33, chipW, 14);
        float ch = UiAnim.smooth("set.chip", chipHover || binding ? 1 : 0, 18);
        float pulse = binding ? .5f + .5f * (float) Math.sin(System.nanoTime() / 1.5e8) : 0;
        UiDraw.rounded(g, chipX, top + 33, chipW, 14, 7, binding ? UiDraw.alpha(t.accent(), .18f + pulse * .12f) : UiDraw.mix(t.surface(), t.overlay(), ch * .7f));
        if (binding) UiDraw.outline(g, chipX, top + 33, chipW, 14, 7, .7f, UiDraw.alpha(t.accent(), .5f + pulse * .5f));
        UiDraw.icon(g, "keyboard", chipX + 6, top + 36, 8, binding ? t.accent() : t.muted());
        UiDraw.text(g, bindLabel, chipX + 17, top + 40, UiDraw.SMALL, binding ? t.accent() : t.text());
        UiDraw.right(g, "Keybind", chipX - 6, top + 40, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
        UiDraw.rect(g, left + 10, top + HEAD - 7, panelWidth - 20, .6f, UiDraw.alpha(t.overlay(), .6f));
        // ---- rows
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        int start = listTop();
        g.enableScissor(left + 4, start - 2, left + panelWidth - 4, start + viewHeight);
        boolean editorShown = false;
        for (int i = 0; i < visible.size(); i++) {
            Setting<?> setting = visible.get(i);
            String key = "set." + System.identityHashCode(setting);
            float y = UiAnim.smooth(key + ".y", start + i * ROW, start + i * ROW, 18) - shownScroll;
            float appear = UiAnim.smooth(key + ".a", 0, 1, 12);
            if (y + ROW < start || y > start + viewHeight) continue;
            float before = UiRender.opacity;
            UiRender.opacity = before * appear;
            boolean hover = UiDraw.over(mx, my, left + 8, y, panelWidth - 16, ROW) && my >= start && my < start + viewHeight && !binding;
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20);
            boolean focus = editing == setting || sliding == setting;
            float highlight = Objects.equals(initialSetting, setting.getName()) ? Math.max(0, 1 - (System.nanoTime() - openedAt) / 1.6e9f) : 0;
            if (hv > 0 || focus) UiDraw.rounded(g, left + 8, y + 1, panelWidth - 16, ROW - 2, 5, UiDraw.alpha(t.surface(), focus ? 1 : hv * .8f));
            if (highlight > 0) UiDraw.rounded(g, left + 8, y + 1, panelWidth - 16, ROW - 2, 5, UiDraw.alpha(t.accent(), .25f * highlight));
            float cy = y + ROW / 2f;
            float labelX = left + 16;
            boolean expandable = setting.isGroupOnly() || setting.visibilityDependency;
            if (expandable) {
                float open = UiAnim.smooth(key + ".open", setting.isExpanded() ? 1 : 0, 16);
                UiDraw.icon(g, "chevron_down", labelX - 2, cy - 4, 8, UiDraw.alpha(t.muted(), open));
                UiDraw.icon(g, "chevron_right", labelX - 2, cy - 4, 8, UiDraw.alpha(t.muted(), 1 - open));
                labelX += 9;
            }
            int half = (panelWidth - 28) / 2;
            String label = UiDraw.fit(GuiState.title(setting.getName()), half - 12);
            if (setting.isGroupOnly()) {
                UiDraw.bold(g, label, labelX, cy, UiDraw.TEXT, t.accent());
                UiDraw.right(g, setting.isExpanded() ? "Collapse" : "Expand", left + panelWidth - 16, cy, UiDraw.SMALL, UiDraw.alpha(t.muted(), .6f + hv * .4f));
                UiRender.opacity = before; continue;
            }
            UiDraw.text(g, label, labelX, cy, setting.isVisible() ? UiDraw.mix(t.text(), 0xffffffff, hv * .1f) : t.muted());
            Object current = setting.getValue();
            float right = left + panelWidth - 16;
            if (!setting.isVisible()) {
                UiDraw.right(g, "Unavailable", right, cy, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
            } else if (editing == setting && editor != null) {
                editor.place(Math.round(left + panelWidth / 2f), Math.round(cy - 6), Math.round(panelWidth / 2f - 14));
                editor.visible = y >= start - 2 && y + ROW <= start + viewHeight + 2;
                editorShown = editor.visible;
                editor.render(g, mx, my, delta);
            } else if (current instanceof Boolean enabled) {
                UiDraw.toggle(g, right - 18, cy - 5, UiAnim.smooth(key + ".on", enabled ? 1 : 0, 16), hover, t);
            } else if (slider(setting)) {
                float target = (float) fraction(setting);
                float shown = sliding == setting ? target : UiAnim.smooth(key + ".v", target, 18);
                boolean over = UiDraw.over(mx, my, sliderX() - 4, y, SLIDER + 8, ROW) && hover;
                UiDraw.slider(g, sliderX(), cy, SLIDER, shown, UiAnim.smooth(key + ".sh", over || sliding == setting ? 1 : 0, 18), t);
                UiDraw.right(g, value(setting), right, cy, focus ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv));
            } else if (current instanceof Enum<?> || current instanceof NamedEntry) {
                String text = UiDraw.fit(value(setting), half - 40);
                float pw = UiDraw.width(text) + 30, px = right - pw;
                UiDraw.rounded(g, px, cy - 7, pw, 14, 7, UiDraw.mix(t.mantle(), t.overlay(), hv * .6f));
                UiDraw.icon(g, "chevron_left", px + 3, cy - 3.5f, 7, UiDraw.alpha(t.muted(), .5f + hv * .5f));
                UiDraw.icon(g, "chevron_right", px + pw - 10, cy - 3.5f, 7, UiDraw.alpha(t.muted(), .5f + hv * .5f));
                UiDraw.center(g, text, px, cy, pw, t.text());
            } else if (current instanceof Color colour) {
                String text = value(setting);
                float tw = UiDraw.width(text, UiDraw.SMALL);
                UiDraw.right(g, text, right, cy, UiDraw.SMALL, UiDraw.mix(t.muted(), t.text(), hv));
                float sx = right - tw - 18;
                UiDraw.rounded(g, sx, cy - 6, 12, 12, 3.5f, colour.getRGB() | 0xff000000);
                UiDraw.outline(g, sx, cy - 6, 12, 12, 3.5f, .6f, UiDraw.alpha(t.text(), .25f));
            } else if (setting instanceof SetSetting<?>) {
                UiDraw.right(g, value(setting), right - 10, cy, UiDraw.mix(t.muted(), t.text(), hv));
                UiDraw.icon(g, "chevron_right", right - 7, cy - 4, 8, UiDraw.mix(t.muted(), t.accent(), hv));
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
        if (visible.isEmpty()) UiDraw.center(g, "This module has no settings.", left, start + 14, panelWidth, t.muted());
        g.disableScissor();
        // fades at the list edges hint that it scrolls
        if (shownScroll > 1) UiDraw.gradient(g, left + 4, start - 2, panelWidth - 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, left + 4, start + viewHeight - 8, panelWidth - 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base());
        if (maxScroll() > 0) {
            float track = Math.max(14, viewHeight * (float) viewHeight / (visible.size() * ROW)), offset = (viewHeight - track) * shownScroll / maxScroll();
            UiDraw.rounded(g, left + panelWidth - 6, start + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
        // ---- footer
        float messageAlpha = message.isEmpty() ? 0 : Math.clamp(1 - ((System.nanoTime() - messageAt) / 1e9f - 4) / .5f, 0f, 1f);
        if (messageAlpha <= 0) message = "";
        String hint = binding ? "Press a key or mouse button · Esc cancels · Delete clears"
                : editing != null ? "Enter applies · Esc cancels"
                : "Click to change · Drag sliders · Right-click resets to default";
        if (!message.isEmpty()) UiDraw.text(g, UiDraw.fit(message, panelWidth - 24), left + 14, top + panelHeight - 11, UiDraw.SMALL, UiDraw.alpha(t.red(), messageAlpha));
        else UiDraw.text(g, UiDraw.fit(hint, panelWidth - 24), left + 14, top + panelHeight - 11, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
    }

    private void fail(String text) { message = text; messageAt = System.nanoTime(); }

    @SuppressWarnings({"rawtypes", "unchecked"}) private void change(Setting setting, Object next) { setting.setValue(next); message = ""; }

    private void stopEditing() {
        if (editor != null) removeWidget(editor);
        editor = null; editing = null;
    }

    private void startEditing(Setting<?> setting) {
        stopEditing();
        editing = setting;
        editor = new UiInput(0, 0, 100, 12, setting.getValue() instanceof Color ? "#RRGGBB or #AARRGGBB" : "Value");
        editor.setMaxLength(1024);
        Object value = setting.getValue();
        editor.setValue(value instanceof Color colour ? String.format("#%08X", colour.getRGB()) : GuiSettings.editText(setting));
        editor.visible = false;
        addWidget(editor); setFocused(editor);
        editor.moveCursorToEnd(false); editor.setHighlightPos(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void slideTo(Setting setting, double mouseX) {
        double f = Math.clamp((mouseX - sliderX()) / SLIDER, 0, 1);
        double min = ((Number) setting.getMinimumValue()).doubleValue(), max = ((Number) setting.getMaximumValue()).doubleValue();
        double raw = min + (max - min) * f;
        Object old = setting.getValue();
        Object next;
        if (old instanceof Integer) next = (int) Math.round(raw);
        else if (old instanceof Long) next = Math.round(raw);
        else if (old instanceof Short) next = (short) Math.round(raw);
        else if (old instanceof Byte) next = (byte) Math.round(raw);
        else {
            // Snap to a tidy step: 1/100th of the range rounded to 1, 2 or 5 × 10^n.
            double step = (max - min) / 100, magnitude = Math.pow(10, Math.floor(Math.log10(step))), unit = step / magnitude;
            step = (unit < 1.5 ? 1 : unit < 3.5 ? 2 : 5) * magnitude;
            double snapped = Math.clamp(Math.round(raw / step) * step, min, max);
            next = old instanceof Float ? (Object) (float) snapped : (Object) snapped;
        }
        if (!next.equals(old)) change(setting, next);
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (binding) {
            module.setKeybind(new Keybind(event.button(), module.getKeybind().mode(), true)); binding = false; return true;
        }
        if (editor != null && editor.visible && editor.isMouseOver(event.x(), event.y())) { setFocused(editor); return editor.mouseClicked(event, doubled); }
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { onClose(); return true; }
        if (event.button() == 0 && UiDraw.over(mx, my, left + panelWidth - 80, top + 10, 70, 18)) { module.toggle(); return true; }
        String bindLabel = module.getKeybind().isUnbound() ? "Not bound" : module.getKeybind().getDisplayName();
        float chipW = UiDraw.width(bindLabel, UiDraw.SMALL) + 24;
        if (UiDraw.over(mx, my, left + panelWidth - 14 - chipW, top + 33, chipW, 14)) {
            if (event.button() == 1) module.setKeybind(Keybind.UNBOUND); else { stopEditing(); binding = true; }
            return true;
        }
        int start = listTop();
        if (!UiDraw.over(mx, my, left + 8, start, panelWidth - 16, viewHeight)) { if (editing != null) stopEditing(); return super.mouseClickedUi(event, doubled); }
        int i = (int) Math.floor((my - start + shownScroll) / ROW);
        if (i < 0 || i >= visible.size()) { stopEditing(); return true; }
        Setting<?> setting = visible.get(i); Object current = setting.getValue(); message = "";
        if (editing != null && editing != setting) stopEditing();
        if (setting.isGroupOnly()) { setting.expanded = !setting.isExpanded(); return true; }
        if (!setting.isVisible()) { fail("Turn on the option this depends on to change it."); return true; }
        if (setting.visibilityDependency && event.button() == 0 && mx < left + panelWidth / 2.0) { setting.expanded = !setting.isExpanded(); return true; }
        if (event.button() == 1) { setting.reset(); stopEditing(); return true; }
        if (event.button() != 0) return false;
        if (setting instanceof SetSetting<?> collection) minecraft.setScreen(new CollectionSettingScreen(this, collection));
        else if (current instanceof Boolean enabled) change(setting, !enabled);
        else if (slider(setting) && mx >= sliderX() - 4 && mx <= sliderX() + SLIDER + 4) { sliding = setting; slideTo(setting, mx); }
        else if (current instanceof Enum<?> choice) {
            Object[] choices = choice.getDeclaringClass().getEnumConstants();
            String text = value(setting);
            float pw = UiDraw.width(text) + 30, px = left + panelWidth - 16 - pw;
            int direction = mx < px + pw / 2 && mx >= px ? -1 : 1;
            change(setting, choices[Math.floorMod(choice.ordinal() + direction, choices.length)]);
        }
        else if (editing != setting) startEditing(setting);
        return true;
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (sliding != null) { slideTo(sliding, event.x()); return true; }
        return super.mouseDraggedUi(event, dx, dy);
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) { sliding = null; return super.mouseReleasedUi(event); }

    private void apply() {
        if (editing == null || editor == null) return;
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
            stopEditing(); message = "";
        } catch (RuntimeException failure) {
            String range = editing.isNumericBoundsEnabled() && editing.getMinimumValue() != null && editing.getMaximumValue() != null
                    ? " Allowed: " + editing.getMinimumValue() + " to " + editing.getMaximumValue() + "." : "";
            fail("That value isn't valid." + range);
        }
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (UiDraw.over(mx, my, left, listTop(), panelWidth, viewHeight)) { scroll = Math.clamp(scroll - (int) (vertical * ROW * 2), 0, maxScroll()); return true; }
        return false;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (binding) {
            if (event.key() != 256) module.setKeybind(event.key() == 259 || event.key() == 261 ? Keybind.UNBOUND : new Keybind(event.key(), module.getKeybind().mode(), false));
            binding = false; return true;
        }
        if (editor != null && editing != null) {
            if (event.key() == 257 || event.key() == 335) { apply(); return true; }
            if (event.key() == 256) { stopEditing(); return true; }
        }
        return super.keyPressed(event);
    }
}

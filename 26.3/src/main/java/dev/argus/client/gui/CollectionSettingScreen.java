package dev.argus.client.gui;

import dev.argus.client.utils.InputButtons;
import dev.argus.client.utils.InputKeys;
import dev.argus.client.settings.SetSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Entry-level selection for block/item/entity/string sets; never parses a set's display text. */
final class CollectionSettingScreen extends UiScreen {
    private static final int ROW = 20;
    private final SetSetting<?> setting;
    private UiInput query;
    private List<String> entries = List.of();
    private Set<String> selected = Set.of();
    private int left, top, panelWidth, panelHeight, viewHeight, scroll;
    private float shownScroll;
    private String message = "";
    private boolean onlySelected;

    CollectionSettingScreen(Screen parent, SetSetting<?> setting) { super(setting.getName(), parent, "Modules"); this.setting = setting; }

    @Override protected void buildContent() {
        panelWidth = Math.min(440, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24; viewHeight = Math.max(32, panelHeight - 92);
        String text = query == null ? GuiSettings.editText(setting) : query.getValue();
        query = new UiInput(left + 12, top + 42, panelWidth - 120, 12, "Search, or type an entry to add…").withIcon("search");
        query.setValue(text);
        query.setResponder(value -> { scroll = 0; refresh(); }); addRenderableWidget(query); setInitialFocus(query);
        addRenderableWidget(new UiButton(left + panelWidth - 98, top + 37, 86, 22, "Add / remove", false, "plus", this::toggleTyped));
        refresh();
    }

    private void refresh() {
        selected = new HashSet<>(setting.getFormattedValues()); Set<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(setting.getFormattedAvailableEntries()); all.addAll(selected); String filter = query.getValue().toLowerCase(Locale.ROOT);
        entries = all.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(filter)).filter(name -> !onlySelected || selected.contains(name)).toList();
    }

    private void toggle(String entry) {
        try { GuiSettings.toggleCollectionEntry(setting, entry); message = ""; refresh(); }
        catch (RuntimeException failure) { message = "That entry isn't recognised. Pick a name from the list."; }
    }
    private void toggleTyped() { toggle(query.getValue()); }

    private int start() { return top + 72; }
    private float resetX() {
        String filter = onlySelected ? "Showing selected" : "Showing all";
        float fx = left + panelWidth - 12 - (UiDraw.width(filter, UiDraw.SMALL) + 14);
        return fx - 8 - UiDraw.width(selected.size() + " selected", UiDraw.SMALL) - 18;
    }
    private int maxScroll() { return Math.max(0, entries.size() * ROW - viewHeight); }

    @Override protected void renderUi(GuiGraphicsExtractor g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        boolean backHover = UiDraw.over(mx, my, left + 10, top + 10, 18, 18);
        float bh = UiAnim.smooth("col.back", backHover ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.bold(g, UiDraw.fit(GuiState.title(setting.getName()), panelWidth - 170), left + 36, top + 19, UiDraw.TITLE, t.text());
        String filter = onlySelected ? "Showing selected" : "Showing all";
        float fw = UiDraw.width(filter, UiDraw.SMALL) + 14, fx = left + panelWidth - 12 - fw;
        boolean fh = UiDraw.over(mx, my, fx, top + 12, fw, 14);
        UiDraw.rounded(g, fx, top + 12, fw, 14, 7, onlySelected ? UiDraw.alpha(t.accent(), .2f) : UiDraw.mix(t.surface(), t.overlay(), fh ? .7f : 0));
        UiDraw.center(g, filter, fx, top + 19, fw, UiDraw.SMALL, onlySelected ? t.accent() : t.muted());
        String count = selected.size() + " selected";
        UiDraw.right(g, count, fx - 8, top + 19, UiDraw.SMALL, t.accent());
        float rx = resetX();
        boolean atDefault = java.util.Objects.equals(setting.getValue(), setting.getDefaultValue());
        boolean rh = UiDraw.over(mx, my, rx - 3, top + 12, 16, 14);
        float rha = UiAnim.smooth("col.reset", rh ? 1 : 0, 20);
        if (rha > 0 && !atDefault) UiDraw.circle(g, rx + 5, top + 19, 7, UiDraw.alpha(t.accent(), .16f * rha));
        UiDraw.icon(g, "reset", rx, top + 14, 10, atDefault ? UiDraw.alpha(t.muted(), .3f) : UiDraw.mix(UiDraw.alpha(t.muted(), .8f), t.accent(), rha));
        if (rh) tooltip("col.reset", atDefault ? "Already at default" : "Reset to default");
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        int start = start();
        g.enableScissor(left + 4, start, left + panelWidth - 4, start + viewHeight);
        for (int i = 0; i < entries.size(); i++) {
            float y = start + i * ROW - shownScroll; if (y + ROW < start || y > start + viewHeight) continue;
            String entry = entries.get(i);
            boolean on = selected.contains(entry), hover = UiDraw.over(mx, my, left + 8, y, panelWidth - 16, ROW) && my >= start && my < start + viewHeight;
            String key = "col." + setting.getName() + "." + entry;
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20), sel = UiAnim.smooth(key + ".on", on ? 1 : 0, 16);
            if (hv > 0) UiDraw.rounded(g, left + 8, y + 1, panelWidth - 16, ROW - 2, 5, UiDraw.alpha(t.surface(), hv * .8f));
            UiDraw.checkbox(g, left + 15, y + ROW / 2f - 5, 10, sel, t);
            UiDraw.text(g, UiDraw.fit(entry, panelWidth - 52), left + 32, y + ROW / 2f, UiDraw.mix(t.text(), t.accent(), sel));
        }
        if (entries.isEmpty()) UiDraw.center(g, query.getValue().isBlank() ? "Nothing to show." : "No matches. Press Enter to add it as typed.", left, start + 14, panelWidth, t.muted());
        g.disableScissor();
        UiMotion.faded(UiMotion.edge(shownScroll), () -> UiDraw.gradient(g, left + 4, start, panelWidth - 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge(maxScroll() - shownScroll), () -> UiDraw.gradient(g, left + 4, start + viewHeight - 8, panelWidth - 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base()));
        UiDraw.text(g, UiDraw.fit(message.isEmpty() ? "Click an entry to add or remove it · Enter adds what you typed" : message, panelWidth - 24),
                left + 14, top + panelHeight - 11, UiDraw.SMALL, message.isEmpty() ? UiDraw.alpha(t.muted(), .8f) : t.red());
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        double mx = event.x(), my = event.y();
        if (InputButtons.glfw(event) == 0 && UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { onClose(); return true; }
        if (InputButtons.glfw(event) == 0 && UiDraw.over(mx, my, resetX() - 3, top + 12, 16, 14)) { setting.reset(); message = ""; refresh(); return true; }
        String filter = onlySelected ? "Showing selected" : "Showing all";
        float fw = UiDraw.width(filter, UiDraw.SMALL) + 14;
        if (InputButtons.glfw(event) == 0 && UiDraw.over(mx, my, left + panelWidth - 12 - fw, top + 12, fw, 14)) { onlySelected = !onlySelected; scroll = 0; refresh(); return true; }
        if (InputButtons.glfw(event) != 0 || !UiDraw.over(mx, my, left + 8, start(), panelWidth - 16, viewHeight)) return false;
        int row = (int) Math.floor((my - start() + shownScroll) / ROW); if (row >= 0 && row < entries.size()) toggle(entries.get(row)); return true;
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (UiDraw.over(mx, my, left, start(), panelWidth, viewHeight)) { scroll = Math.clamp(scroll - (int) (vertical * ROW * 2), 0, maxScroll()); return true; }
        return false;
    }

    @Override public boolean keyPressed(KeyEvent event) { if (InputKeys.glfw(event) == 257 || InputKeys.glfw(event) == 335) { toggleTyped(); return true; } return super.keyPressed(event); }
}

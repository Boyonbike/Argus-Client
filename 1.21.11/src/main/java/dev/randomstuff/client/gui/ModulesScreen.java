package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import dev.randomstuff.client.modules.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Category, favourites and search panels share the same draggable dropdown-menu canvas. */
public final class ModulesScreen extends UiScreen {
    private static final int WIDTH = 112, HEADER = 20, ROW = 15, SEARCH_ROW = 24, SEARCH_INPUT = 28, TOP = 42;
    private enum Kind { CATEGORY, FAVOURITES, SEARCH }
    private static final class Window {
        final String name;
        final ModuleCategory category;
        final Kind kind;
        final int order;
        int x, y, scroll, width = WIDTH;
        float shownX = Float.NaN, shownY, shownScroll, open, lift, scrollbar;
        boolean collapsed;
        Window(String name, ModuleCategory category, Kind kind, int order) { this.name = name; this.category = category; this.kind = kind; this.order = order; }
    }
    private final List<Window> windows = new ArrayList<>();
    private List<Module> all = List.of();
    private List<GuiSearch.Entry> searchIndex = List.of();
    private List<GuiSearch.Hit> results = List.of();
    private UiInput searchInput;
    private Window searchWindow, dragging;
    private float dragX, dragY, canvasShown;
    private int canvasScroll, selected = -1;
    private long resultsChanged;
    private boolean focusOnOpen;

    public ModulesScreen(Screen parent) { super("Random Stuff Client", parent, "Modules"); }
    ModulesScreen(Screen parent, boolean focusSearch) { this(parent); focusOnOpen = focusSearch; }

    @Override protected void buildContent() {
        String query = searchInput == null ? "" : searchInput.getValue();
        all = GuiState.modules(); windows.clear();
        int order = 0;
        for (ModuleCategory category : ModuleCategory.values()) if (category != ModuleCategory.HUD)
            windows.add(new Window(GuiState.title(category.getName()), category, Kind.CATEGORY, order++));
        windows.add(new Window("Favourites", null, Kind.FAVOURITES, order++));
        searchWindow = new Window("Search", null, Kind.SEARCH, order);
        int categoryArea = width - searchWindow.width - 18;
        int[] heights = new int[windows.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = panelHeight(windows.get(i), 1);
        int[][] positions = CategoryLayout.arrange(categoryArea, heights);
        for (int i = 0; i < windows.size(); i++) restore(windows.get(i), positions[i][0], positions[i][1]);
        restore(searchWindow, width - searchWindow.width - 10, 64); windows.add(searchWindow);
        searchIndex = all.stream().map(module -> new GuiSearch.Entry(module.getName(), module.getCategory().getName(),
                Arrays.asList(module.getAliases()), module.getSettings().stream().filter(setting -> !setting.isGroupOnly())
                .map(setting -> setting.getName()).distinct().toList())).toList();
        searchInput = new UiInput(searchWindow.x + 6, searchWindow.y + HEADER + 8, searchWindow.width - 12, 12, "Search modules or settings…").withIcon("search");
        searchInput.setValue(query); searchInput.setResponder(value -> refreshSearch()); addWidget(searchInput); refreshSearch();
        if (focusOnOpen) { focusSearch(); focusOnOpen = false; }
    }

    private void restore(Window window, int x, int y) {
        var saved = GuiState.preferences().windows.get(window.name);
        window.x = saved == null ? x : Math.clamp(saved.x(), 10, Math.max(10, width - window.width - 10));
        window.y = saved == null ? y : Math.max(48, saved.y()); window.collapsed = saved != null && saved.collapsed(); window.scroll = saved == null ? 0 : saved.scroll();
        window.open = window.collapsed ? 0 : 1; window.shownScroll = window.scroll;
    }

    private List<Module> modules(Window window) {
        return all.stream().filter(module -> window.kind == Kind.FAVOURITES ? GuiState.preferences().favourites.contains(module.getName()) : module.getCategory() == window.category).toList();
    }

    private void refreshSearch() { results = GuiSearch.search(searchIndex, searchInput.getValue()); searchWindow.scroll = 0; selected = results.isEmpty() ? -1 : 0; resultsChanged = System.nanoTime(); }

    private int contentHeight(Window window) { return window.kind == Kind.SEARCH ? results.size() * SEARCH_ROW : modules(window).size() * ROW; }
    private int viewHeight(Window window) {
        if (window.kind == Kind.SEARCH) return Math.min(Math.max(52, results.size() * SEARCH_ROW), Math.max(52, Math.min(264, height - 154)));
        return Math.min(Math.max(ROW + 4, modules(window).size() * ROW), Math.max(48, height - 112));
    }
    private int panelHeight(Window window, float open) { return HEADER + Math.round((viewHeight(window) + (window.kind == Kind.SEARCH ? SEARCH_INPUT : 0) + 6) * open); }
    private float top(Window window) { return window.shownY - canvasShown; }

    private Window at(double mx, double my) {
        if (my < TOP || my >= height - 20) return null;
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window window = windows.get(i);
            if (UiDraw.over(mx, my, window.shownX, top(window), window.width, panelHeight(window, window.open))) return window;
        }
        return null;
    }

    void focusSearch() {
        searchWindow.collapsed = false; canvasScroll = Math.max(0, searchWindow.y - 64);
        searchInput.visible = true; searchInput.active = true; setInitialFocus(searchInput);
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme theme = GuiState.theme();
        int bottom = windows.stream().mapToInt(window -> window.y + panelHeight(window, window.collapsed ? 0 : 1)).max().orElse(height);
        canvasScroll = Math.clamp(canvasScroll, 0, Math.max(0, bottom - height + 28));
        canvasShown = UiAnim.approach(canvasShown, canvasScroll, 18);
        for (Window window : windows) {
            if (Float.isNaN(window.shownX)) { window.shownX = window.x; window.shownY = window.y; }
            if (window != dragging) { window.shownX = UiAnim.approach(window.shownX, window.x, 22); window.shownY = UiAnim.approach(window.shownY, window.y, 22); }
            window.open = UiAnim.approach(window.open, window.collapsed ? 0 : 1, 15);
            window.lift = UiAnim.approach(window.lift, window == dragging ? 1 : 0, 14);
            window.scroll = Math.clamp(window.scroll, 0, Math.max(0, contentHeight(window) - viewHeight(window)));
            window.shownScroll = UiAnim.approach(window.shownScroll, window.scroll, 20);
        }
        Window over = dragging != null ? null : at(mx, my);
        g.enableScissor(0, TOP, width, height - 19);
        searchInput.visible = false;
        float base = UiRender.opacity;
        for (Window window : windows) {
            float appear = appear(window.order);
            UiRender.opacity = base * appear;
            g.pose().pushMatrix();
            g.pose().translate(0, (1 - appear) * 10);
            try { drawWindow(g, window, mx, my, delta, over == window, theme); }
            finally { g.pose().popMatrix(); UiRender.opacity = base; }
        }
        g.disableScissor();
        searchInput.active = searchInput.visible;
        if (!searchInput.visible && getFocused() == searchInput) setFocused(null);
    }

    private void drawWindow(GuiGraphics g, Window window, int mx, int my, float delta, boolean over, UiTheme theme) {
        float x = window.shownX, top = top(window), w = window.width;
        float ph = panelHeight(window, window.open);
        if (top + ph < TOP || top > height - 19) return;
        UiDraw.panel(g, x, top, w, ph, theme, window.lift);
        // Header
        boolean headerHover = over && my < top + HEADER;
        float hh = UiAnim.smooth("hdr." + window.name, headerHover ? 1 : 0, 18);
        UiDraw.rounded(g, x, top, w, HEADER, 7, UiDraw.mix(theme.mantle(), theme.surface(), hh * .5f));
        if (window.open > .01f) UiDraw.rect(g, x, top + HEADER - 7, w, 7, UiDraw.mix(theme.mantle(), theme.surface(), hh * .5f));
        if (window.open > .01f) UiDraw.rect(g, x, top + HEADER - .6f, w, .6f, UiDraw.alpha(theme.overlay(), .6f * window.open));
        String icon = window.kind == Kind.SEARCH ? "search" : window.kind == Kind.FAVOURITES ? "favourites" : window.category.name().toLowerCase(Locale.ROOT);
        UiDraw.icon(g, icon, x + 7, top + (HEADER - 9) / 2f, 9, theme.accent());
        UiDraw.bold(g, UiDraw.fit(window.name, w - 46), x + 21, top + HEADER / 2f, UiDraw.TEXT, theme.text());
        if (window.kind != Kind.SEARCH) {
            List<Module> list = modules(window);
            long enabled = list.stream().filter(Module::isToggled).count();
            String count = enabled > 0 ? enabled + "/" + list.size() : Integer.toString(list.size());
            UiDraw.right(g, count, x + w - 19, top + HEADER / 2f, UiDraw.SMALL, enabled > 0 ? UiDraw.alpha(theme.accent(), .85f) : UiDraw.alpha(theme.muted(), .7f));
        }
        int chevron = UiDraw.mix(theme.muted(), theme.text(), hh);
        UiDraw.icon(g, "chevron_down", x + w - 15, top + (HEADER - 9) / 2f, 9, UiDraw.alpha(chevron, window.open));
        UiDraw.icon(g, "chevron_right", x + w - 15, top + (HEADER - 9) / 2f, 9, UiDraw.alpha(chevron, 1 - window.open));
        if (window.open < .01f) return;
        float clipBottom = Math.min(height - 19, top + ph - 3);
        if (window.kind == Kind.SEARCH) { drawSearch(g, window, mx, my, over, theme, clipBottom); return; }
        List<Module> modules = modules(window);
        int view = viewHeight(window);
        float start = top + HEADER + 2;
        g.enableScissor((int) x + 2, (int) Math.max(TOP, top + HEADER), (int) (x + w - 2), (int) clipBottom);
        Module hovered = null;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float rowY = start + i * ROW - window.shownScroll;
            if (rowY + ROW < start || rowY > clipBottom) continue;
            boolean hover = over && my >= top + HEADER && my < clipBottom && UiDraw.over(mx, my, x + 3, rowY, w - 6, ROW);
            String key = "row." + window.name + "." + module.getName();
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20);
            float on = UiAnim.smooth(key + ".on", module.isToggled() ? 1 : 0, 14);
            if (on > 0) UiDraw.rounded(g, x + 4, rowY + 1, w - 8, ROW - 2, 4, UiDraw.alpha(theme.accent(), .16f * on));
            if (hv > 0) UiDraw.rounded(g, x + 4, rowY + 1, w - 8, ROW - 2, 4, UiDraw.alpha(theme.text(), .07f * hv));
            if (on > 0) UiDraw.rounded(g, x + 6, rowY + ROW / 2f - 3.5f * on, 2, 7 * on, 1, UiDraw.alpha(theme.accent(), on));
            boolean favourite = window.kind != Kind.FAVOURITES && GuiState.preferences().favourites.contains(module.getName());
            float side = favourite ? 22 : 18;
            String name = UiDraw.fit(GuiState.title(module.getName()), w - side * 2);
            UiDraw.center(g, name, x, rowY + ROW / 2f, w, UiDraw.mix(UiDraw.mix(theme.text(), 0xffffffff, hv * .12f), theme.accent(), on));
            if (favourite) UiDraw.icon(g, "star", x + w - 13, rowY + ROW / 2f - 3, 6, UiDraw.alpha(theme.yellow(), .75f));
            if (hover) hovered = module;
        }
        if (modules.isEmpty()) UiDraw.center(g, UiDraw.fit(window.kind == Kind.FAVOURITES ? "Middle-click a module to pin it" : "No modules", w - 12),
                x, start + (ROW + 2) / 2f, w, UiDraw.SMALL, theme.muted());
        g.disableScissor();
        scrollbar(g, window, start, view, contentHeight(window), over, theme);
        if (hovered != null) {
            String description = hovered.getDescription() == null ? "" : hovered.getDescription();
            var bind = hovered.getKeybind();
            tooltip(hovered, GuiState.title(hovered.getName()), description.isBlank() ? "No description" : description,
                    (bind.isUnbound() ? "Not bound" : "Bind: " + bind.getDisplayName()) + "   ·   Left toggle  ·  Right settings  ·  Middle pin");
        }
    }

    private void drawSearch(GuiGraphics g, Window window, int mx, int my, boolean over, UiTheme theme, float clipBottom) {
        float x = window.shownX, top = top(window), w = window.width;
        int inputY = Math.round(top + HEADER + 8);
        searchInput.place(Math.round(x + 6), inputY, Math.round(w - 12));
        boolean inputVisible = window.open > .9f && inputY - 4 >= TOP && inputY + 16 <= height - 19;
        searchInput.visible = inputVisible;
        if (window.open > .3f) {
            float before = UiRender.opacity;
            UiRender.opacity = before * Math.clamp((window.open - .3f) / .7f, 0f, 1f);
            searchInput.render(g, mx, my, 0);
            UiRender.opacity = before;
        }
        float start = top + HEADER + SEARCH_INPUT;
        int view = viewHeight(window);
        if (start >= clipBottom) return;
        g.enableScissor((int) x + 2, (int) Math.max(TOP, start), (int) (x + w - 2), (int) clipBottom);
        float since = (System.nanoTime() - resultsChanged) / 1e9f;
        for (int i = 0; i < results.size(); i++) {
            float rowY = start + i * SEARCH_ROW - window.shownScroll;
            if (rowY + SEARCH_ROW < start || rowY > clipBottom) continue;
            var hit = results.get(i);
            boolean hover = over && my >= start && my < clipBottom && UiDraw.over(mx, my, x + 4, rowY, w - 8, SEARCH_ROW);
            if (hover) selected = i;
            float appear = UiAnim.easeOutCubic((since - Math.min(i, 10) * .022f) / .16f);
            float before = UiRender.opacity;
            UiRender.opacity = before * appear;
            float sel = UiAnim.smooth("sr." + i + "." + hit.module() + hit.setting() + hit.alias(), i == selected ? 1 : 0, 22);
            float ry = rowY + (1 - appear) * 4;
            if (sel > 0) UiDraw.rounded(g, x + 4, ry + 1, w - 8, SEARCH_ROW - 2, 5, UiDraw.alpha(theme.surface(), sel));
            int colour = hit.isSetting() ? theme.blue() : hit.alias() != null ? theme.yellow() : theme.green();
            UiDraw.rounded(g, x + 8, ry + 5, 14, 14, 4, UiDraw.alpha(colour, .18f));
            Module module = GuiState.module(hit.module());
            String icon = hit.isSetting() ? "client" : hit.alias() != null ? "search" : module == null ? "misc" : module.getCategory().name().toLowerCase(Locale.ROOT);
            UiDraw.icon(g, icon, x + 10.5f, ry + 7.5f, 9, colour);
            String title = GuiState.title(hit.isSetting() ? hit.setting() : hit.alias() != null ? hit.alias() : hit.module());
            boolean toggled = !hit.isSetting() && module != null && module.isToggled();
            UiDraw.text(g, UiDraw.fit(title, w - 44), x + 27, ry + 8.5f, toggled ? theme.accent() : theme.text());
            String path = hit.isSetting() ? "Setting in " + GuiState.title(hit.module()) : hit.alias() != null ? "Alias of " + GuiState.title(hit.module()) : GuiState.title(hit.category());
            UiDraw.text(g, UiDraw.fit(path, w - 44), x + 27, ry + 17, UiDraw.SMALL, theme.muted());
            if (toggled) UiDraw.circle(g, x + w - 12, ry + SEARCH_ROW / 2f, 2, theme.accent());
            UiRender.opacity = before;
        }
        if (results.isEmpty()) {
            float cy = start + view / 2f - 6;
            boolean blank = searchInput.getValue().isBlank();
            UiDraw.icon(g, blank ? "search" : "close", x + w / 2 - 6, cy - 14, 12, UiDraw.alpha(theme.muted(), .55f));
            UiDraw.center(g, blank ? "Type to search" : "No matches", x, cy + 6, w, theme.muted());
            UiDraw.center(g, "Modules · Aliases · Settings", x, cy + 17, w, UiDraw.SMALL, UiDraw.alpha(theme.muted(), .7f));
        }
        g.disableScissor();
        scrollbar(g, window, start, view, contentHeight(window), over, theme);
    }

    private void scrollbar(GuiGraphics g, Window window, float start, int view, int total, boolean over, UiTheme theme) {
        boolean scrolling = Math.abs(window.shownScroll - window.scroll) > .5f;
        window.scrollbar = UiAnim.approach(window.scrollbar, total > view && (over || scrolling) ? 1 : 0, 10);
        if (total <= view || window.scrollbar <= 0) return;
        float track = Math.max(10, view * (float) view / total), offset = (view - track) * window.shownScroll / Math.max(1, total - view);
        UiDraw.rounded(g, window.shownX + window.width - 4.5f, start + offset, 2, track * window.open, 1, UiDraw.alpha(theme.overlay(), window.scrollbar));
    }

    private void activate(GuiSearch.Hit hit, int button) {
        Module module = GuiState.module(hit.module());
        if (module == null) return;
        if (button == 1 || hit.isSetting()) minecraft.setScreen(new ModuleSettingsScreen(this, module, hit.setting()));
        else if (button == 0) module.toggle();
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        Window window = at(event.x(), event.y());
        if (window == null) {
            // This manually rendered child must never intercept navigation through a clipped/stale box.
            boolean active = searchInput.active; searchInput.active = false;
            try { return super.mouseClickedUi(event, doubled); } finally { searchInput.active = active; }
        }
        float top = top(window);
        if (window == searchWindow && !window.collapsed && searchInput.visible && searchInput.isMouseOver(event.x(), event.y()) && event.y() >= top + HEADER) {
            setFocused(searchInput); setDragging(true); return searchInput.mouseClicked(event, doubled);
        }
        if (window != searchWindow) setFocused(null);
        if (event.y() < top + HEADER) {
            if (event.button() == 1 || event.x() >= window.shownX + window.width - 18) { window.collapsed = !window.collapsed; if (window == searchWindow && window.collapsed) setFocused(null); }
            else if (event.button() == 0) { dragging = window; dragX = (float) event.x() - window.shownX; dragY = (float) event.y() - top; windows.remove(window); windows.add(window); }
            remember(); return true;
        }
        if (window.collapsed || window.open < .9f) return true;
        if (window.kind == Kind.SEARCH) {
            float start = top + HEADER + SEARCH_INPUT;
            if (event.y() < start) return true;
            int row = (int) Math.floor((event.y() - start + window.shownScroll) / SEARCH_ROW);
            if (row >= 0 && row < results.size()) activate(results.get(row), event.button());
            return true;
        }
        int row = (int) Math.floor((event.y() - top - HEADER - 2 + window.shownScroll) / ROW);
        List<Module> list = modules(window);
        if (row >= 0 && row < list.size()) {
            Module module = list.get(row);
            if (event.button() == 0) module.toggle();
            else if (event.button() == 1) minecraft.setScreen(new ModuleSettingsScreen(this, module, null));
            else if (event.button() == 2) { var favourites = GuiState.preferences().favourites; if (!favourites.add(module.getName())) favourites.remove(module.getName()); GuiState.save(); }
        }
        return true;
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDraggedUi(event, dx, dy);
        float x = (float) event.x() - dragX, top = (float) event.y() - dragY + canvasShown;
        float maxX = Math.max(10, width - dragging.width - 10);
        dragging.shownX = Math.clamp(x, 10, maxX); dragging.shownY = Math.max(48, top);
        int tx = Math.round(dragging.shownX), ty = Math.round(dragging.shownY);
        if (GuiState.preferences().snap) { tx = Math.round(tx / 8f) * 8; ty = Math.round(ty / 8f) * 8; }
        dragging.x = Math.clamp(tx, 10, (int) maxX); dragging.y = Math.max(48, ty);
        remember(); return true;
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) { dragging = null; remember(); GuiState.save(); return super.mouseReleasedUi(event); }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        Window window = at(mx, my);
        if (window != null && !window.collapsed) {
            int total = contentHeight(window);
            int previous = window.scroll; window.scroll = Math.clamp(window.scroll - (int) (vertical * 30), 0, Math.max(0, total - viewHeight(window)));
            if (previous != window.scroll) { remember(); return true; }
        }
        canvasScroll = Math.max(0, canvasScroll - (int) (vertical * 24)); remember(); return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (getFocused() == searchInput && !results.isEmpty()) {
            int key = event.key();
            if (key == 264 || key == 265) { // down / up
                selected = Math.floorMod((selected < 0 ? -1 : selected) + (key == 264 ? 1 : -1), results.size());
                float rowTop = selected * SEARCH_ROW, view = viewHeight(searchWindow);
                if (rowTop < searchWindow.scroll) searchWindow.scroll = (int) rowTop;
                else if (rowTop + SEARCH_ROW > searchWindow.scroll + view) searchWindow.scroll = (int) (rowTop + SEARCH_ROW - view);
                return true;
            }
            if ((key == 257 || key == 335) && selected >= 0 && selected < results.size()) { activate(results.get(selected), (event.modifiers() & 1) != 0 ? 1 : 0); return true; }
        }
        return super.keyPressed(event);
    }

    /** Typing anywhere in the menu starts a search; the first character goes into the box. */
    @Override public boolean charTyped(CharacterEvent event) {
        if (!closing() && getFocused() != searchInput && event.isAllowedChatCharacter() && !Character.isWhitespace(event.codepoint())) {
            focusSearch();
            searchInput.visible = true; searchInput.active = true;
            setFocused(searchInput);
            searchInput.moveCursorToEnd(false);
            return searchInput.charTyped(event);
        }
        return super.charTyped(event);
    }

    private void remember() { for (Window window : windows) GuiState.preferences().windows.put(window.name, new GuiPreferences.Window(window.x, window.y, window.collapsed, window.scroll)); }
    @Override public void removed() { remember(); super.removed(); }
}

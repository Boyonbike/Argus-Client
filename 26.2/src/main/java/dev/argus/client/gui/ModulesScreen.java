package dev.argus.client.gui;

import dev.argus.client.modules.misc.ChatToolsModule;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Category, favourites and search panels share the same draggable dropdown-menu canvas. */
public final class ModulesScreen extends UiScreen {
    private static final int WIDTH = 112, HEADER = 20, ROW = 15, SEARCH_ROW = 24, SEARCH_INPUT = 28, TOP = 0;
    /** Windows may sit anywhere below this, including beside the navigation bar. */
    private static final int MIN_Y = 6;
    /** Snap grid spacing in GUI units. */
    static final int GRID = 4;
    private enum Kind { CATEGORY, FAVOURITES, SEARCH }
    private static final class Window {
        final String name;
        final ModuleCategory category;
        final Kind kind;
        final int order;
        /** Where the window is shown; may be squeezed from its arrangement on a small screen. */
        int x, y, scroll, width = WIDTH;
        /** The arrangement the user made, and the screen width it was made on ({@link WindowFit}). */
        int layoutX, layoutY, layoutWidth = WindowFit.AUTO;
        /** Where it was moved to in the compact view (only used there); compactWidth 0 = never moved there. */
        int compactX, compactY, compactWidth;
        float shownX = Float.NaN, shownY, shownScroll, open, lift, scrollbar;
        /** Seconds left before this window starts gliding to a new place after a resize. */
        float reflowDelay;
        boolean reflowing;
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
    /** Windows picked with the selection box; dragging any of them moves the whole group. */
    private final Set<Window> picked = new LinkedHashSet<>();
    private final Map<Window, int[]> groupStart = new LinkedHashMap<>();
    private Set<Window> pickedBefore = Set.of();
    private boolean boxing;
    /** Selection box corners and the drag origin, in canvas space (screen y + canvas scroll). */
    private float boxX0, boxY0, boxX1, boxY1, pressX, pressY;
    private int canvasScroll, selected = -1;
    private long resultsChanged;
    private boolean focusOnOpen;

    public ModulesScreen(Screen parent) {
        super("Argus Client", parent, "Modules");
        DescriptionCatalog.ensureApplied();
    }
    ModulesScreen(Screen parent, boolean focusSearch) { this(parent); focusOnOpen = focusSearch; }

    @Override protected void buildContent() {
        String query = searchInput == null ? "" : searchInput.getValue();
        // Resizing rebuilds the screen; windows keep where they were drawn so they can glide to their new places.
        Map<String, Window> previous = new HashMap<>();
        for (Window window : windows) previous.put(window.name, window);
        all = GuiState.modules(); windows.clear(); windowModules.clear();
        int order = 0;
        // Client modules live in the Settings tab rather than a dropdown.
        for (ModuleCategory category : ModuleCategory.values()) if (category != ModuleCategory.HUD && category != ModuleCategory.CLIENT)
            windows.add(new Window(GuiState.title(category.getName()), category, Kind.CATEGORY, order++));
        windows.add(new Window("Favourites", null, Kind.FAVOURITES, order++));
        searchWindow = new Window("Search", null, Kind.SEARCH, order);
        int categoryArea = width - searchWindow.width - 18;
        int[] heights = new int[windows.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = panelHeight(windows.get(i), 1);
        int[][] positions = CategoryLayout.arrange(categoryArea, heights);
        for (int i = 0; i < windows.size(); i++) restore(windows.get(i), positions[i][0], positions[i][1]);
        restore(searchWindow, width - searchWindow.width - 10, 64); windows.add(searchWindow);
        fit();
        reflowFrom(previous);
        searchIndex = all.stream().map(module -> new GuiSearch.Entry(module.getName(), module.getCategory().getName(),
                Arrays.asList(module.getAliases()), List.of())).toList();
        searchInput = new UiInput(searchWindow.x + 6, searchWindow.y + HEADER + 8, searchWindow.width - 12, 12, "Search modules…").withIcon("search");
        searchInput.setValue(query); searchInput.setResponder(value -> refreshSearch()); addWidget(searchInput); refreshSearch();
        if (focusOnOpen) { focusSearch(); focusOnOpen = false; }
    }

    private void restore(Window window, int x, int y) {
        var saved = GuiState.preferences().windows.get(window.name);
        boolean auto = saved == null || saved.layoutWidth() == WindowFit.AUTO;
        // Untouched windows follow the default layout for this screen size.
        window.layoutX = auto ? x : saved.x(); window.layoutY = auto ? y : saved.y();
        window.layoutWidth = auto ? WindowFit.AUTO : saved.layoutWidth();
        if (saved != null) { window.compactX = saved.compactX(); window.compactY = saved.compactY(); window.compactWidth = saved.compactWidth(); }
        window.collapsed = saved != null && saved.collapsed(); window.scroll = saved == null ? 0 : saved.scroll();
        window.open = window.collapsed ? 0 : 1; window.shownScroll = window.scroll;
    }

    /**
     * Shows the arrangement on this screen: unchanged if it fits. If it doesn't, windows moved in
     * the compact view go where they were put there and the rest are squeezed around them.
     */
    private void fit() {
        boolean compact = compact();
        List<WindowFit.Box> boxes = new ArrayList<>();
        for (Window window : windows) boxes.add(compact && window.compactWidth > 0
                ? new WindowFit.Box(window.compactX, window.compactY, window.width, panelHeight(window, 1), window.compactWidth, true)
                : new WindowFit.Box(window.layoutX, window.layoutY, window.width, panelHeight(window, 1), window.layoutWidth, false));
        int[][] shown = WindowFit.fit(width, MIN_Y, boxes);
        for (int i = 0; i < windows.size(); i++) { windows.get(i).x = shown[i][0]; windows.get(i).y = shown[i][1]; }
    }

    /** True while at least one hand-placed window is shown squeezed because the screen is too narrow. */
    private boolean compact() {
        for (Window window : windows) if (WindowFit.squeezed(width, window.layoutWidth)) return true;
        return false;
    }

    /** After a resize, windows glide from where they were drawn, one after another left to right. */
    private void reflowFrom(Map<String, Window> previous) {
        if (previous.isEmpty()) return;
        List<Window> moving = new ArrayList<>();
        for (Window window : windows) {
            Window old = previous.get(window.name);
            if (old == null || Float.isNaN(old.shownX)) continue;
            window.shownX = old.shownX; window.shownY = old.shownY; window.open = old.open; window.shownScroll = old.shownScroll;
            if (Math.abs(window.shownX - window.x) > .5f || Math.abs(window.shownY - window.y) > .5f) moving.add(window);
        }
        moving.sort(Comparator.comparingInt((Window window) -> window.x).thenComparingInt(window -> window.y));
        for (int i = 0; i < moving.size(); i++) { moving.get(i).reflowing = true; moving.get(i).reflowDelay = Math.min(i * .028f, .2f); }
    }

    /**
     * A dropped window: in the compact view the move only changes the compact view, so the
     * full-size arrangement is untouched; at full size it becomes part of the arrangement.
     */
    private void place(Window window) {
        if (compact()) { window.compactX = window.x; window.compactY = window.y; window.compactWidth = width; return; }
        window.layoutX = window.x; window.layoutY = window.y; window.layoutWidth = width;
        // Once anything is arranged by hand, the rest stop following the default layout so they can't drift into it.
        for (Window other : windows) if (other.layoutWidth == WindowFit.AUTO) { other.layoutX = other.x; other.layoutY = other.y; other.layoutWidth = width; }
    }

    // A window's modules are needed several times per frame (layout, hit testing, header count, rows), so the
    // filtered list is computed once per frame and reused. Cleared at the start of every frame and on rebuild.
    private final Map<Window, List<Module>> windowModules = new IdentityHashMap<>();

    private List<Module> modules(Window window) {
        List<Module> cached = windowModules.get(window);
        if (cached == null) windowModules.put(window, cached = filterModules(window));
        return cached;
    }

    private List<Module> filterModules(Window window) {
        // Social (Chat Tools) opens from the top bar or its key, so it isn't listed with the other modules.
        return all.stream().filter(module -> !(module instanceof ChatToolsModule)).filter(module -> window.kind == Kind.FAVOURITES ? GuiState.preferences().favourites.contains(module.getName()) : module.getCategory() == window.category).toList();
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

    @Override protected void renderUi(GuiGraphicsExtractor g, int mx, int my, float delta) {
        windowModules.clear();
        UiTheme theme = GuiState.theme();
        int bottom = windows.stream().mapToInt(window -> window.y + panelHeight(window, window.collapsed ? 0 : 1)).max().orElse(height);
        canvasScroll = Math.clamp(canvasScroll, 0, Math.max(0, bottom - height + 28));
        canvasShown = UiAnim.approach(canvasShown, canvasScroll, 18);
        for (Window window : windows) {
            if (Float.isNaN(window.shownX)) { window.shownX = window.x; window.shownY = window.y; }
            if (window.reflowDelay > 0) window.reflowDelay -= UiAnim.delta();
            else if (!groupStart.containsKey(window)) {
                // Resize reflows glide a little slower than drags so the reshuffle reads clearly.
                float speed = window.reflowing ? 11 : 22;
                window.shownX = UiAnim.approach(window.shownX, window.x, speed); window.shownY = UiAnim.approach(window.shownY, window.y, speed);
                if (window.shownX == window.x && window.shownY == window.y) window.reflowing = false;
            }
            window.open = UiAnim.approach(window.open, window.collapsed ? 0 : 1, 15);
            window.lift = UiAnim.approach(window.lift, groupStart.containsKey(window) ? 1 : 0, 14);
            window.scroll = Math.clamp(window.scroll, 0, Math.max(0, contentHeight(window) - viewHeight(window)));
            window.shownScroll = UiAnim.approach(window.shownScroll, window.scroll, 20);
        }
        Window over = dragging != null || boxing ? null : at(mx, my);
        g.enableScissor(0, TOP, width, height - 19);
        drawGrid(g, theme);
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
        if (boxing) {
            // The selection box, drawn in screen space.
            float bx = Math.min(boxX0, boxX1), by = Math.min(boxY0, boxY1) - canvasShown, bw = Math.abs(boxX1 - boxX0), bh = Math.abs(boxY1 - boxY0);
            UiDraw.rounded(g, bx, by, bw, bh, 3, UiDraw.alpha(theme.accent(), .08f));
            UiDraw.outline(g, bx, by, bw, bh, 3, .8f, UiDraw.alpha(theme.accent(), .7f));
        }
        g.disableScissor();
        drawCompactHint(g, theme);
        searchInput.active = searchInput.visible;
        if (!searchInput.visible && getFocused() == searchInput) setFocused(null);
    }

    private float gridFade;

    /** A small pill at the bottom while the arrangement is squeezed, so it's clear nothing was lost. */
    private void drawCompactHint(GuiGraphicsExtractor g, UiTheme theme) {
        float shown = UiAnim.smooth("modules.compact", compact() ? 1 : 0, 7);
        if (shown <= .01f) return;
        String text = UiDraw.fit("Compact view · moves here don't change your full-size layout", Math.max(40, width - 140), UiDraw.SMALL);
        float w = UiDraw.width(text, UiDraw.SMALL) + 22, h = 13, x = (width - w) / 2f, y = height - 16 + (1 - UiAnim.easeOutCubic(shown)) * 6;
        float base = UiRender.opacity;
        UiRender.opacity = base * shown;
        try {
            UiDraw.rounded(g, x, y, w, h, h / 2, UiDraw.alpha(theme.mantle(), .92f));
            UiDraw.outline(g, x, y, w, h, h / 2, .6f, UiDraw.alpha(theme.accent(), .45f));
            UiDraw.circle(g, x + 8, y + h / 2, 1.8f, theme.accent());
            UiDraw.text(g, text, x + 14, y + h / 2, UiDraw.SMALL, theme.muted());
        } finally { UiRender.opacity = base; }
    }

    /** The snap grid, faded in while a window is being dragged (if enabled in Appearance). */
    private void drawGrid(GuiGraphicsExtractor g, UiTheme theme) {
        var p = GuiState.preferences();
        gridFade = UiAnim.approach(gridFade, dragging != null && p.showGrid ? 1 : 0, dragging != null ? 12 : 8);
        if (gridFade <= .01f) return;
        int minor = UiDraw.alpha(theme.overlay(), .16f * gridFade), major = UiDraw.alpha(theme.overlay(), .38f * gridFade);
        for (int x = 0, i = 0; x <= width; x += GRID, i++) UiDraw.rect(g, x, TOP, .5f, height - TOP, i % 4 == 0 ? major : minor);
        float offset = canvasShown % GRID;
        int first = (int) Math.floor(canvasShown / GRID);
        for (float y = -offset, i = first; y <= height; y += GRID, i++) UiDraw.rect(g, 0, y, width, .5f, Math.floorMod((int) i, 4) == 0 ? major : minor);
    }

    private void drawWindow(GuiGraphicsExtractor g, Window window, int mx, int my, float delta, boolean over, UiTheme theme) {
        float x = window.shownX, top = top(window), w = window.width;
        float ph = panelHeight(window, window.open);
        if (top + ph < TOP || top > height - 19) return;
        UiDraw.panel(g, x, top, w, ph, theme, window.lift);
        float pick = UiAnim.smooth("pick." + window.name, picked.contains(window) ? 1 : 0, 18);
        if (pick > 0) UiDraw.outline(g, x - 1.5f, top - 1.5f, w + 3, ph + 3, 8.5f, 1.2f, UiDraw.alpha(theme.accent(), pick));
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
            // The count rolls to its new value and flashes the accent when a module in the window changes.
            float flash = UiMotion.flash("cnt.flash." + window.name, count), countRight = x + w - 19, countY = top + HEADER / 2f;
            int countColour = UiDraw.mix(enabled > 0 ? UiDraw.alpha(theme.accent(), .85f) : UiDraw.alpha(theme.muted(), .7f), theme.accent(), flash);
            UiMotion.text(g, "cnt." + window.name, count, c -> UiDraw.right(g, c, countRight, countY, UiDraw.SMALL, countColour));
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
            // Rows glide when one is pinned or unpinned above them, and fade in when they first appear.
            float rowY = start + UiMotion.rowY("mw." + window.name, module.getName(), i * ROW) - window.shownScroll;
            if (rowY + ROW < start || rowY > clipBottom) continue;
            float rowBefore = UiRender.opacity;
            UiRender.opacity = rowBefore * UiMotion.rowAppear("mw." + window.name, module.getName());
            boolean hover = over && my >= top + HEADER && my < clipBottom && UiDraw.over(mx, my, x + 3, rowY, w - 6, ROW);
            String key = "row." + window.name + "." + module.getName();
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20);
            float on = UiAnim.smooth(key + ".on", module.isToggled() ? 1 : 0, 14);
            if (on > 0) UiDraw.rounded(g, x + 4, rowY + 1, w - 8, ROW - 2, 4, UiDraw.alpha(theme.accent(), .16f * on));
            if (hv > 0) UiDraw.rounded(g, x + 4, rowY + 1, w - 8, ROW - 2, 4, UiDraw.alpha(theme.text(), .07f * hv));
            if (on > 0) UiDraw.rounded(g, x + 6, rowY + ROW / 2f - 3.5f * on, 2, 7 * on, 1, UiDraw.alpha(theme.accent(), on));
            boolean favourite = window.kind != Kind.FAVOURITES && GuiState.preferences().favourites.contains(module.getName());
            // The star pops in with a small overshoot and the name makes room for it gradually.
            float fav = UiAnim.smooth(key + ".fav", favourite ? 1 : 0, 14);
            float side = 18 + 4 * fav;
            String name = UiDraw.fit(GuiState.title(module.getName()), w - side * 2);
            UiDraw.center(g, name, x, rowY + ROW / 2f, w, UiDraw.mix(UiDraw.mix(theme.text(), 0xffffffff, hv * .12f), theme.accent(), on));
            if (fav > .01f) {
                float size = 6 * (.4f + .6f * UiAnim.easeOutBack(fav));
                UiDraw.icon(g, "star", x + w - 10 - size / 2, rowY + ROW / 2f - size / 2, size, UiDraw.alpha(theme.yellow(), .75f * Math.min(1, fav * 1.5f)));
            }
            UiRender.opacity = rowBefore;
            if (hover) hovered = module;
        }
        if (modules.isEmpty()) UiDraw.center(g, UiDraw.fit(window.kind == Kind.FAVOURITES ? "Middle-click a module to pin it" : "No modules", w - 12),
                x, start + (ROW + 2) / 2f, w, UiDraw.SMALL, theme.muted());
        g.disableScissor();
        scrollbar(g, window, start, view, contentHeight(window), over, theme);
        if (hovered != null) {
            String description = UiText.stripFormatting(hovered.getDescription());
            var bind = hovered.getKeybind();
            tooltip(hovered, GuiState.title(hovered.getName()), description.isBlank() ? "No description" : description,
                    (bind.isUnbound() ? "Not bound" : "Bind: " + bind.getDisplayName()) + "   ·   Left toggle  ·  Right settings  ·  Middle pin");
        }
    }

    private void drawSearch(GuiGraphicsExtractor g, Window window, int mx, int my, boolean over, UiTheme theme, float clipBottom) {
        float x = window.shownX, top = top(window), w = window.width;
        int inputY = Math.round(top + HEADER + 8);
        searchInput.place(Math.round(x + 6), inputY, Math.round(w - 12));
        boolean inputVisible = window.open > .9f && inputY - 4 >= TOP && inputY + 16 <= height - 19;
        searchInput.visible = inputVisible;
        if (window.open > .3f) {
            float before = UiRender.opacity;
            UiRender.opacity = before * Math.clamp((window.open - .3f) / .7f, 0f, 1f);
            searchInput.extractRenderState(g, mx, my, 0);
            UiRender.opacity = before;
        }
        float start = top + HEADER + SEARCH_INPUT;
        int view = viewHeight(window);
        if (start >= clipBottom) return;
        g.enableScissor((int) x + 2, (int) Math.max(TOP, start), (int) (x + w - 2), (int) clipBottom);
        float since = UiAnim.elapsed(resultsChanged);
        for (int i = 0; i < results.size(); i++) {
            float rowY = start + i * SEARCH_ROW - window.shownScroll;
            if (rowY + SEARCH_ROW < start || rowY > clipBottom) continue;
            var hit = results.get(i);
            boolean hover = over && my >= start && my < clipBottom && UiDraw.over(mx, my, x + 4, rowY, w - 8, SEARCH_ROW);
            if (hover) selected = i;
            float appear = UiAnim.enabled() ? UiAnim.easeOutCubic((since - Math.min(i, 10) * .022f) / .16f) : 1;
            float before = UiRender.opacity;
            UiRender.opacity = before * appear;
            float sel = UiAnim.smooth("sr." + i + "." + hit.module() + hit.setting() + hit.alias(), i == selected ? 1 : 0, 22);
            float ry = rowY + (1 - appear) * 4;
            if (sel > 0) UiDraw.rounded(g, x + 4, ry + 1, w - 8, SEARCH_ROW - 2, 5, UiDraw.alpha(theme.surface(), sel));
            int colour = hit.isSetting() ? theme.blue() : hit.alias() != null ? theme.yellow() : theme.green();
            UiDraw.rounded(g, x + 8, ry + 5, 14, 14, 4, UiDraw.alpha(colour, .18f));
            Module module = GuiState.module(hit.module());
            String icon = hit.isSetting() ? "client" : module != null ? module.getCategory().name().toLowerCase(Locale.ROOT) : hit.category().toLowerCase(Locale.ROOT);
            UiDraw.icon(g, icon, x + 10.5f, ry + 7.5f, 9, colour);
            String title = GuiState.title(hit.isSetting() ? hit.setting() : hit.alias() != null ? hit.alias() : hit.module());
            boolean toggled = !hit.isSetting() && module != null && module.isToggled();
            float lit = UiAnim.smooth("sr.on." + hit.module() + hit.setting() + hit.alias(), toggled ? 1 : 0, 14);
            UiDraw.text(g, UiDraw.fit(title, w - 44), x + 27, ry + 8.5f, UiDraw.mix(theme.text(), theme.accent(), lit));
            boolean client = module != null && module.getCategory() == ModuleCategory.CLIENT;
            String path = hit.isSetting() ? "Setting in " + GuiState.title(hit.module()) : hit.alias() != null ? "Alias of " + GuiState.title(hit.module()) : client ? "Settings tab" : GuiState.title(hit.category());
            UiDraw.text(g, UiDraw.fit(path, w - 44), x + 27, ry + 17, UiDraw.SMALL, theme.muted());
            if (lit > .01f) UiDraw.circle(g, x + w - 12, ry + SEARCH_ROW / 2f, 2 * (.5f + .5f * UiAnim.easeOutBack(lit)), UiDraw.alpha(theme.accent(), lit));
            UiRender.opacity = before;
        }
        float emptyShown = UiAnim.smooth("search.empty", results.isEmpty() ? 1 : 0, 14);
        if (results.isEmpty()) {
            // The empty state fades in, and switches between "Type to search" and "No matches" with a crossfade.
            float cy = start + view / 2f - 6, before = UiRender.opacity;
            UiRender.opacity = before * emptyShown;
            boolean blank = searchInput.getValue().isBlank();
            UiMotion.crossfade(g, "search.empty.state", blank, b -> {
                UiDraw.icon(g, b ? "search" : "close", x + w / 2 - 6, cy - 14, 12, UiDraw.alpha(theme.muted(), .55f));
                UiDraw.center(g, b ? "Type to search" : "No matches", x, cy + 6, w, theme.muted());
            });
            UiDraw.center(g, "Modules · Aliases", x, cy + 17, w, UiDraw.SMALL, UiDraw.alpha(theme.muted(), .7f));
            UiRender.opacity = before;
        }
        g.disableScissor();
        scrollbar(g, window, start, view, contentHeight(window), over, theme);
    }

    private void scrollbar(GuiGraphicsExtractor g, Window window, float start, int view, int total, boolean over, UiTheme theme) {
        boolean scrolling = Math.abs(window.shownScroll - window.scroll) > .5f;
        window.scrollbar = UiAnim.approach(window.scrollbar, total > view && (over || scrolling) ? 1 : 0, 10);
        if (total <= view || window.scrollbar <= 0) return;
        float track = Math.max(10, view * (float) view / total), offset = (view - track) * window.shownScroll / Math.max(1, total - view);
        UiDraw.rounded(g, window.shownX + window.width - 4.5f, start + offset, 2, track * window.open, 1, UiDraw.alpha(theme.overlay(), window.scrollbar));
    }

    private void activate(GuiSearch.Hit hit, int button) {
        Module module = GuiState.module(hit.module());
        if (module == null) return;
        if (module.getCategory() == ModuleCategory.CLIENT || module instanceof ChatToolsModule) openSettings(module, hit.setting());
        else if (button == 1 || hit.isSetting()) openSettings(module, hit.setting());
        else if (button == 0) module.toggle();
    }

    /** Client-category modules and Social open in the Settings tab; everything else gets its own page. */
    private void openSettings(Module module, String setting) {
        if (module.getCategory() == ModuleCategory.CLIENT || module instanceof ChatToolsModule) minecraft.gui.setScreen(new SettingsScreen(this, module.getName(), setting));
        else minecraft.gui.setScreen(new ModuleSettingsScreen(this, module, setting));
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        Window window = at(event.x(), event.y());
        if (window == null) {
            // This manually rendered child must never intercept navigation through a clipped/stale box.
            boolean active = searchInput.active; searchInput.active = false;
            try { if (super.mouseClickedUi(event, doubled)) return true; } finally { searchInput.active = active; }
            // Empty canvas: start a selection box (Shift adds to the current selection).
            if (event.button() == 0 && event.y() >= TOP && event.y() < height - 19) {
                setFocused(null);
                if ((event.modifiers() & 1) == 0) picked.clear();
                pickedBefore = Set.copyOf(picked);
                boxing = true;
                boxX0 = boxX1 = (float) event.x(); boxY0 = boxY1 = (float) event.y() + canvasShown;
                return true;
            }
            return false;
        }
        float top = top(window);
        if (window == searchWindow && !window.collapsed && searchInput.visible && searchInput.isMouseOver(event.x(), event.y()) && event.y() >= top + HEADER) {
            setFocused(searchInput); setDragging(true); return searchInput.mouseClicked(event, doubled);
        }
        if (window != searchWindow) setFocused(null);
        if (event.y() < top + HEADER) {
            if (event.button() == 1 || event.x() >= window.shownX + window.width - 18) { window.collapsed = !window.collapsed; if (window == searchWindow && window.collapsed) setFocused(null); }
            else if (event.button() == 0) {
                // Dragging a selected window moves the whole selection; any other window moves alone.
                if (!picked.contains(window)) picked.clear();
                List<Window> group = picked.isEmpty() ? List.of(window) : List.copyOf(picked);
                dragging = window; dragX = (float) event.x() - window.shownX; dragY = (float) event.y() - top;
                pressX = (float) event.x(); pressY = (float) event.y() + canvasShown;
                groupStart.clear();
                for (Window member : group) { groupStart.put(member, new int[]{member.x, member.y}); windows.remove(member); windows.add(member); }
            }
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
            else if (event.button() == 1) openSettings(module, null);
            else if (event.button() == 2) { var favourites = GuiState.preferences().favourites; if (!favourites.add(module.getName())) favourites.remove(module.getName()); windowModules.clear(); GuiState.save(); }
        }
        return true;
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (boxing) {
            boxX1 = (float) event.x(); boxY1 = (float) event.y() + canvasShown;
            float left = Math.min(boxX0, boxX1), right = Math.max(boxX0, boxX1), top = Math.min(boxY0, boxY1), bottom = Math.max(boxY0, boxY1);
            picked.clear(); picked.addAll(pickedBefore);
            for (Window window : windows) {
                float wy = window.shownY, wh = panelHeight(window, window.open);
                if (window.shownX < right && window.shownX + window.width > left && wy < bottom && wy + wh > top) picked.add(window);
            }
            return true;
        }
        if (dragging == null) return super.mouseDraggedUi(event, dx, dy);
        // Move the group by the pointer's travel, keeping every member on screen.
        float moveX = (float) event.x() - pressX, moveY = (float) event.y() + canvasShown - pressY;
        float minX = -Float.MAX_VALUE, maxX = Float.MAX_VALUE, minY = -Float.MAX_VALUE;
        for (var entry : groupStart.entrySet()) {
            int[] start = entry.getValue();
            minX = Math.max(minX, 10 - start[0]);
            maxX = Math.min(maxX, Math.max(10, width - entry.getKey().width - 10) - start[0]);
            minY = Math.max(minY, MIN_Y - start[1]);
        }
        if (maxX < minX) maxX = minX;
        moveX = Math.clamp(moveX, minX, maxX); moveY = Math.max(minY, moveY);
        // The grabbed window snaps to the grid; the rest keep their places relative to it.
        int[] lead = groupStart.get(dragging);
        int tx = Math.round(lead[0] + moveX), ty = Math.round(lead[1] + moveY);
        if (GuiState.preferences().snap) { tx = Math.round(tx / (float) GRID) * GRID; ty = Math.round(ty / (float) GRID) * GRID; }
        int snapX = Math.clamp(tx - lead[0], (int) Math.ceil(minX), (int) Math.floor(maxX)), snapY = Math.max((int) Math.ceil(minY), ty - lead[1]);
        int[] pushed = keepGap(snapX, snapY, (int) Math.ceil(minX), (int) Math.floor(maxX), (int) Math.ceil(minY));
        snapX = pushed[0]; snapY = pushed[1];
        for (var entry : groupStart.entrySet()) {
            Window member = entry.getKey(); int[] start = entry.getValue();
            member.shownX = start[0] + moveX; member.shownY = start[1] + moveY;
            member.x = start[0] + snapX; member.y = start[1] + snapY;
            member.reflowing = false; member.reflowDelay = 0;
            place(member);
        }
        remember(); return true;
    }

    /**
     * Auto gap: pushes the dragged group out of any other window's reach, so windows can't be
     * closer than the chosen gap and settle exactly that far apart.
     */
    private int[] keepGap(int dx, int dy, int minX, int maxX, int minY) {
        int gap = GuiState.preferences().gap;
        if (gap <= 0) return new int[]{dx, dy};
        for (int pass = 0; pass < 8; pass++) {
            boolean moved = false;
            for (var entry : groupStart.entrySet()) {
                Window member = entry.getKey(); int[] start = entry.getValue();
                int mx = start[0] + dx, my = start[1] + dy, mw = member.width, mh = panelHeight(member, member.collapsed ? 0 : 1);
                for (Window other : windows) {
                    if (groupStart.containsKey(other)) continue;
                    int ox = other.x - gap, oy = other.y - gap, ow = other.width + gap * 2, oh = panelHeight(other, other.collapsed ? 0 : 1) + gap * 2;
                    if (mx >= ox + ow || mx + mw <= ox || my >= oy + oh || my + mh <= oy) continue;
                    // Smallest push that clears the other window, preferring moves that stay on screen.
                    int left = mx + mw - ox, right = ox + ow - mx, up = my + mh - oy, down = oy + oh - my;
                    int best = Integer.MAX_VALUE, bx = 0, by = 0;
                    if (dx - left >= minX && left < best) { best = left; bx = -left; by = 0; }
                    if (dx + right <= maxX && right < best) { best = right; bx = right; by = 0; }
                    if (dy - up >= minY && up < best) { best = up; bx = 0; by = -up; }
                    if (down < best) { best = down; bx = 0; by = down; }
                    dx += bx; dy += by; moved = true;
                    mx = start[0] + dx; my = start[1] + dy;
                }
            }
            if (!moved) break;
        }
        return new int[]{dx, dy};
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) {
        boxing = false; dragging = null; groupStart.clear();
        remember(); GuiState.save();
        return super.mouseReleasedUi(event);
    }

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
        if (event.key() == 256 && !picked.isEmpty() && getFocused() != searchInput) { picked.clear(); return true; }
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

    private void remember() { for (Window window : windows) GuiState.preferences().windows.put(window.name,
            new GuiPreferences.Window(window.layoutX, window.layoutY, window.collapsed, window.scroll, window.layoutWidth,
                    window.compactX, window.compactY, window.compactWidth)); }
    @Override public void removed() { remember(); super.removed(); }
}

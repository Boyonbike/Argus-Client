package dev.argus.client.gui;

import dev.argus.client.gui.mixin.HudEditorScreenAccessor;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.render.HudAnchorGroup;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The HUD editor in the Argus style. The base editor still does the HUD work (anchors, dragging,
 * snapping, margins); this replaces its legacy module panel with:
 * <pre>
 *            ┌──────────── [▣ HUD editor │ 3 on screen │ Elements │ Mirror │ Done] ────────────┐
 *            │                                                          ┌ Elements ──────────┐ │
 *            │   widgets are dragged on the canvas as before            │ [⌕ Search…       ] │ │
 *            │                                                          │ All · On · Hidden  │ │
 *            │                                                          │ ◉ Armor        [●] │ │
 *            │                                                          │ ◉ Coordinates  [ ] │ │
 *            │        Drag to move · Right-click for settings · …       └────────────────────┘ │
 * </pre>
 * Everything Argus draws is in logical units of its own viewport; the HUD canvas uses Minecraft's
 * GUI coordinates, converted with {@link GuiViewport#toLogical}.
 */
public final class ArgusHudEditorScreen extends HudEditorScreen {
    private static final float BAR_Y = 8, BAR_H = 24, DRAWER_W = 182, DRAWER_TOP = 42, ROW_H = 30, ROW_GAP = 3;
    private static final String[] FILTERS = {"All", "On screen", "Hidden"};

    /** Kept for the session so the drawer stays as the player left it. */
    private static boolean drawerOpen = true;
    private static int filter;

    private GuiViewport viewport = new GuiViewport(320, 240, 1);
    private String query = "";
    private boolean searchFocused;
    private float scroll, scrollTarget;
    private int lastMouseX = -1000, lastMouseY = -1000;

    // Geometry from the last frame, used for hit-testing.
    private float barX, barW, drawerX, listTop, listBottom;
    private final float[] buttonX = new float[3], buttonW = new float[3];
    private final List<HudWidgetModule> shown = new ArrayList<>();

    public ArgusHudEditorScreen() {
        super();
        // The legacy category panel is replaced by the Elements drawer.
        panels.clear();
    }

    private HudEditorScreenAccessor base() { return (HudEditorScreenAccessor) (Object) this; }
    private float lw() { return viewport.width(); }
    private float lh() { return viewport.height(); }
    private float drawerOpenness() { return UiAnim.smooth("hud.drawer", drawerOpen ? 1 : 0, 14); }
    private boolean dragging() { return base().argus$isDraggingWidget(); }

    @Override protected void init() {
        super.init();
        base().argus$setCenterPanelPending(false);
        var window = minecraft.getWindow();
        viewport = GuiViewport.create(window.getWidth(), window.getHeight(), window.getGuiScale(), GuiState.preferences().uiScale);
        DescriptionCatalog.ensureApplied();
    }

    // ---- data ---------------------------------------------------------------------------------

    static List<HudWidgetModule> elements() {
        List<HudWidgetModule> result = new ArrayList<>();
        for (Module module : GuiState.modules()) if (module instanceof HudWidgetModule widget) result.add(widget);
        return result;
    }

    private void refreshShown() {
        shown.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (HudWidgetModule module : elements()) {
            if (filter == 1 && !module.isToggled() || filter == 2 && module.isToggled()) continue;
            if (!needle.isEmpty() && !matches(module, needle)) continue;
            shown.add(module);
        }
    }

    private static boolean matches(Module module, String needle) {
        if (GuiState.title(module.getName()).toLowerCase(Locale.ROOT).contains(needle)) return true;
        for (String alias : module.getAliases()) if (alias.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return false;
    }

    private static String description(Module module) {
        String text = module.getDescription();
        return text == null || text.isBlank() ? "HUD element" : text;
    }

    /** A recognisable icon for each element, falling back to the HUD icon. */
    static String icon(Module module) {
        String name = module.getName().toLowerCase(Locale.ROOT);
        if (name.contains("chat") || name.contains("welcome")) return "chat";
        if (name.contains("armor") || name.contains("armour") || name.contains("totem")) return "shield";
        if (name.contains("direction") || name.contains("position") || name.contains("coord") || name.contains("map")) return "compass";
        if (name.contains("effect") || name.contains("potion")) return "drop";
        if (name.contains("radar") || name.contains("entity")) return "people";
        if (name.contains("speed") || name.contains("volytra")) return "movement";
        if (name.contains("music")) return "bell";
        if (name.contains("module")) return "modules";
        if (name.contains("watermark")) return "star";
        if (name.contains("inventory")) return "player";
        if (name.contains("player")) return "player";
        if (name.contains("graph") || name.contains("metric") || name.contains("lag") || name.contains("crypto")) return "sliders";
        if (name.contains("indicator")) return "bell";
        return "hud";
    }

    // ---- rendering ----------------------------------------------------------------------------

    @Override public void render(GuiGraphics g, int rawX, int rawY, float delta) {
        super.render(g, rawX, rawY, delta);
        UiAnim.frame();
        var window = minecraft.getWindow();
        UiDraw.density = (float) (viewport.renderScale() * window.getGuiScale());
        int mx = (int) viewport.toLogical(rawX), my = (int) viewport.toLogical(rawY);
        lastMouseX = mx; lastMouseY = my;
        UiTheme t = GuiState.theme();
        g.pose().pushMatrix();
        g.pose().scale(viewport.renderScale(), viewport.renderScale());
        float before = UiRender.opacity;
        try {
            UiRender.opacity = getTransitionOpacity();
            hoverOutline(g, rawX, rawY, t);
            toolbar(g, mx, my, t);
            drawer(g, mx, my, t);
            hints(g, t);
        } finally {
            UiRender.opacity = before;
            g.pose().popMatrix();
        }
    }

    /** Accent frame around the element under the pointer (or the one being dragged). */
    private void hoverOutline(GuiGraphics g, int rawX, int rawY, UiTheme t) {
        HudWidgetRenderer target = null;
        for (HudAnchorGroup group : getAnchorGroups())
            for (Object element : group.getWidgets())
                if (element instanceof HudWidgetRenderer widget && widget.isEnabled()
                        && (widget.isSelected() || target == null && !overUi(lastMouseX, lastMouseY) && widget.isMouseOver(rawX, rawY)))
                    target = widget;
        if (target == null) return;
        float[] size = target.getDimensions();
        float x = (float) viewport.toLogical(target.getX()), y = (float) viewport.toLogical(target.getY());
        float w = (float) viewport.toLogical(Math.max(size[0], 4)), h = (float) viewport.toLogical(Math.max(size[1], 4));
        UiDraw.outline(g, x - 2, y - 2, w + 4, h + 4, 3, 1, UiDraw.alpha(t.accent(), target.isSelected() ? .95f : .7f));
    }

    private void toolbar(GuiGraphics g, int mx, int my, UiTheme t) {
        String title = "HUD editor";
        int on = 0, total = 0;
        for (HudWidgetModule module : elements()) { total++; if (module.isToggled()) on++; }
        String count = on + " of " + total + " on screen";
        String[] labels = {"Elements", "Mirror", "Done"};
        String[] icons = {"sliders", "reset", "check"};
        float titleW = 14 + UiDraw.width(title) + 10, countW = UiDraw.width(count, UiDraw.SMALL) + 14;
        float buttons = 0;
        for (int i = 0; i < 3; i++) { buttonW[i] = UiDraw.width(labels[i]) + 26; buttons += buttonW[i] + 3; }
        barW = 8 + titleW + countW + buttons + 5;
        barX = (lw() - barW) / 2;
        float alpha = dragging() ? .35f : 1;
        float prior = UiRender.opacity;
        UiRender.opacity = prior * alpha;
        UiDraw.shadow(g, barX, BAR_Y + 1, barW, BAR_H, BAR_H / 2, 9, UiDraw.alpha(0xff000000, .3f));
        UiDraw.rounded(g, barX, BAR_Y, barW, BAR_H, BAR_H / 2, t.mantle());
        UiDraw.outline(g, barX, BAR_Y, barW, BAR_H, BAR_H / 2, .6f, UiDraw.alpha(t.overlay(), .55f));
        float x = barX + 10, cy = BAR_Y + BAR_H / 2;
        UiDraw.icon(g, "hud", x, cy - 4.5f, 9, t.accent());
        UiDraw.bold(g, title, x + 14, cy, UiDraw.TEXT, t.text());
        x = barX + 8 + titleW;
        UiDraw.rect(g, x - 5, BAR_Y + 7, .6f, BAR_H - 14, UiDraw.alpha(t.overlay(), .7f));
        UiDraw.text(g, count, x + 2, cy, UiDraw.SMALL, t.muted());
        x += countW;
        for (int i = 0; i < 3; i++) {
            float bx = x, by = BAR_Y + 3, bh = BAR_H - 6;
            buttonX[i] = bx;
            boolean hover = !dragging() && UiDraw.over(mx, my, bx, by, buttonW[i], bh);
            boolean selected = i == 0 && drawerOpen || i == 2;
            float h = UiAnim.smooth("hud.btn." + i, hover ? 1 : 0, 18);
            int fill = selected ? UiDraw.alpha(t.accent(), (i == 2 ? .22f : .16f) + h * .1f) : UiDraw.alpha(UiDraw.mix(t.surface(), t.overlay(), h * .7f), h);
            UiDraw.rounded(g, bx, by, buttonW[i], bh, bh / 2, fill);
            int colour = selected ? t.accent() : UiDraw.mix(t.muted(), t.text(), h);
            UiDraw.icon(g, icons[i], bx + 8, cy - 4, 8, colour);
            UiDraw.text(g, labels[i], bx + 19, cy, colour);
            if (hover) switch (i) {
                case 0 -> tooltipHint(g, "Show or hide the list of HUD elements (Ctrl+F to search)", mx, my, t);
                case 1 -> tooltipHint(g, "Swap the left and right sides of the HUD (I)", mx, my, t);
                default -> tooltipHint(g, "Save and leave the editor (Esc)", mx, my, t);
            }
            x += buttonW[i] + 3;
        }
        UiRender.opacity = prior;
    }

    private void drawer(GuiGraphics g, int mx, int my, UiTheme t) {
        float open = drawerOpenness();
        if (open <= .002f) { drawerX = lw(); return; }
        refreshShown();
        float h = lh() - DRAWER_TOP - 32;
        drawerX = lw() - 10 - DRAWER_W + (1 - UiAnim.easeOutCubic(open)) * (DRAWER_W + 16);
        float x = drawerX, y = DRAWER_TOP;
        float prior = UiRender.opacity;
        UiRender.opacity = prior * Math.min(1, open * 1.4f) * (dragging() ? .3f : 1);
        try {
            UiDraw.panel(g, x, y, DRAWER_W, h, t, .4f);
            // Header.
            UiDraw.circle(g, x + 17, y + 17, 8, UiDraw.alpha(t.accent(), .16f));
            UiDraw.icon(g, "plus", x + 13, y + 13, 8, t.accent());
            UiDraw.bold(g, "Elements", x + 30, y + 13, UiDraw.TITLE, t.text());
            UiDraw.text(g, "Switch one on to add it to your screen", x + 30, y + 23, 6f, t.muted());
            // Search.
            float sx = x + 10, sy = y + 36, sw = DRAWER_W - 20, sh = 18;
            boolean searchHover = UiDraw.over(mx, my, sx, sy, sw, sh);
            float sf = UiAnim.smooth("hud.search", searchFocused ? 1 : 0, 16);
            UiDraw.rounded(g, sx, sy, sw, sh, sh / 2, UiDraw.mix(t.mantle(), t.surface(), .55f + (searchHover ? .2f : 0)));
            UiDraw.outline(g, sx, sy, sw, sh, sh / 2, .7f, UiDraw.mix(UiDraw.alpha(t.overlay(), .6f), t.accent(), sf));
            UiDraw.icon(g, "search", sx + 7, sy + 5, 8, UiDraw.mix(t.muted(), t.accent(), sf));
            if (query.isEmpty()) UiDraw.text(g, "Search elements…", sx + 19, sy + sh / 2, UiDraw.alpha(t.muted(), .8f));
            else UiDraw.text(g, UiDraw.fit(query, sw - 36), sx + 19, sy + sh / 2, t.text());
            if (searchFocused && (System.currentTimeMillis() / 530) % 2 == 0) {
                float cx = sx + 19 + (query.isEmpty() ? 0 : UiDraw.width(UiDraw.fit(query, sw - 36)) + .8f);
                UiDraw.rect(g, cx, sy + 5, .8f, sh - 10, t.accent());
            }
            if (!query.isEmpty()) {
                boolean clearHover = UiDraw.over(mx, my, sx + sw - 16, sy + 3, 12, 12);
                UiDraw.icon(g, "close", sx + sw - 14, sy + 5, 8, clearHover ? t.text() : t.muted());
            }
            // Filter segments.
            float fy = y + 60, fw = (DRAWER_W - 20) / 3f, fh = 15;
            UiDraw.rounded(g, x + 10, fy, DRAWER_W - 20, fh, fh / 2, UiDraw.alpha(t.surface(), .55f));
            float indicator = UiAnim.smooth("hud.filter", filter, 18);
            UiDraw.rounded(g, x + 10 + fw * indicator + 1, fy + 1, fw - 2, fh - 2, (fh - 2) / 2, UiDraw.alpha(t.accent(), .22f));
            for (int i = 0; i < FILTERS.length; i++) {
                boolean hover = UiDraw.over(mx, my, x + 10 + fw * i, fy, fw, fh);
                int colour = i == filter ? t.accent() : hover ? t.text() : t.muted();
                UiDraw.center(g, FILTERS[i], x + 10 + fw * i, fy + fh / 2, fw, UiDraw.SMALL, colour);
            }
            // List.
            listTop = y + 82; listBottom = y + h - 8;
            float content = shown.size() * (ROW_H + ROW_GAP) - ROW_GAP;
            float max = Math.max(0, content - (listBottom - listTop));
            scrollTarget = Math.clamp(scrollTarget, 0, max);
            scroll = UiAnim.approach(scroll, scrollTarget, 20);
            if (shown.isEmpty()) {
                String empty = elements().isEmpty() ? "No HUD elements found" : !query.isBlank() ? "Nothing matches “" + UiDraw.fit(query.trim(), 70) + "”"
                        : filter == 1 ? "Nothing on screen yet" : "Everything is on screen";
                UiDraw.icon(g, filter == 1 && query.isBlank() ? "plus" : "search", x + DRAWER_W / 2 - 6, listTop + 18, 12, UiDraw.alpha(t.muted(), .5f));
                UiDraw.center(g, empty, x, listTop + 42, DRAWER_W, UiDraw.SMALL, t.muted());
                if (filter == 1 && query.isBlank()) UiDraw.center(g, "Switch elements on under All", x, listTop + 53, DRAWER_W, 6f, UiDraw.alpha(t.muted(), .7f));
            }
            g.enableScissor(Math.round(x + 2), Math.round(listTop), Math.round(x + DRAWER_W - 2), Math.round(listBottom));
            try {
                for (int i = 0; i < shown.size(); i++) {
                    float ry = listTop + i * (ROW_H + ROW_GAP) - scroll;
                    if (ry + ROW_H < listTop || ry > listBottom) continue;
                    row(g, shown.get(i), x + 8, ry, DRAWER_W - 16, mx, my, t);
                }
            } finally { g.disableScissor(); }
            // Scroll fade and thumb.
            if (scroll > .5f) UiDraw.gradient(g, x + 1, listTop, DRAWER_W - 2, 10, 0, t.base(), UiDraw.alpha(t.base(), 0));
            if (scroll < max - .5f) UiDraw.gradient(g, x + 1, listBottom - 10, DRAWER_W - 2, 10, 0, UiDraw.alpha(t.base(), 0), t.base());
            if (max > 0) {
                float track = listBottom - listTop, thumb = Math.max(16, track * track / (content));
                UiDraw.rounded(g, x + DRAWER_W - 5, listTop + (track - thumb) * (scroll / max), 2, thumb, 1, UiDraw.alpha(t.overlay(), .8f));
            }
        } finally { UiRender.opacity = prior; }
    }

    private void row(GuiGraphics g, HudWidgetModule module, float x, float y, float w, int mx, int my, UiTheme t) {
        boolean inList = my >= listTop && my < listBottom;
        boolean hover = inList && !dragging() && UiDraw.over(mx, my, x, y, w, ROW_H);
        String key = "hud.row." + module.getName();
        float hv = UiAnim.smooth(key, hover ? 1 : 0, 18);
        float on = UiAnim.smooth(key + ".on", module.isToggled() ? 1 : 0, 16);
        UiDraw.rounded(g, x, y, w, ROW_H, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .55f), t.surface(), hv * .75f));
        if (on > .01f) UiDraw.rounded(g, x, y + 7, 2, ROW_H - 14, 1, UiDraw.alpha(t.accent(), on));
        float cx = x + 16, cy = y + ROW_H / 2;
        UiDraw.circle(g, cx, cy, 9, UiDraw.mix(UiDraw.alpha(t.surface(), .9f), UiDraw.alpha(t.accent(), .2f), on));
        UiDraw.icon(g, icon(module), cx - 4.5f, cy - 4.5f, 9, UiDraw.mix(t.muted(), t.accent(), on));
        float textX = x + 31, reserve = 18 + 6 + (hv > .01f ? 14 : 0);
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), w - (textX - x) - reserve), textX, y + 11, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(description(module), w - (textX - x) - reserve, 6f), textX, y + 21, 6f, t.muted());
        float tx = x + w - 24, ty = cy - 5;
        boolean toggleHover = hover && UiDraw.over(mx, my, tx - 2, ty - 3, 22, 16);
        UiDraw.toggle(g, tx, ty, on, toggleHover, t);
        if (hv > .01f) {
            float gx = tx - 15;
            boolean gearHover = hover && UiDraw.over(mx, my, gx - 2, cy - 6, 12, 12);
            float before = UiRender.opacity;
            UiRender.opacity = before * hv;
            if (gearHover) UiDraw.circle(g, gx + 4, cy, 6.5f, t.overlay());
            UiDraw.icon(g, "sliders", gx, cy - 4, 8, gearHover ? t.text() : t.muted());
            UiRender.opacity = before;
        }
    }

    private void hints(GuiGraphics g, UiTheme t) {
        String text = dragging() ? "Release to drop · snaps to edges, centre and other elements"
                : "Drag to move · Right-click for settings · Del to remove · Arrow keys nudge · Drag the screen edges to set margins";
        text = UiDraw.fit(text, lw() - 40, UiDraw.SMALL);
        float w = UiDraw.width(text, UiDraw.SMALL) + 20, h = 16, x = (lw() - w) / 2, y = lh() - h - 8;
        UiDraw.rounded(g, x, y, w, h, h / 2, UiDraw.alpha(t.mantle(), .78f));
        UiDraw.outline(g, x, y, w, h, h / 2, .6f, UiDraw.alpha(t.overlay(), .4f));
        UiDraw.text(g, text, x + 10, y + h / 2, UiDraw.SMALL, t.muted());
    }

    /** A small hint pill under the pointer for toolbar buttons. */
    private void tooltipHint(GuiGraphics g, String text, int mx, int my, UiTheme t) {
        float w = UiDraw.width(text, UiDraw.SMALL) + 14, h = 14;
        float x = Math.clamp(mx - w / 2, 6, lw() - w - 6), y = BAR_Y + BAR_H + 6;
        UiDraw.shadow(g, x, y + 1, w, h, 5, 6, UiDraw.alpha(0xff000000, .3f));
        UiDraw.rounded(g, x, y, w, h, 5, t.mantle());
        UiDraw.outline(g, x, y, w, h, 5, .6f, UiDraw.alpha(t.overlay(), .7f));
        UiDraw.text(g, text, x + 7, y + h / 2, UiDraw.SMALL, t.muted());
    }

    // ---- input --------------------------------------------------------------------------------

    private boolean overToolbar(double x, double y) { return UiDraw.over(x, y, barX, BAR_Y, barW, BAR_H); }
    private boolean overDrawer(double x, double y) { return drawerOpen && x >= drawerX && x < drawerX + DRAWER_W && y >= DRAWER_TOP && y < lh() - 32; }
    private boolean overUi(double x, double y) { return !dragging() && (overToolbar(x, y) || overDrawer(x, y)); }

    private HudWidgetRenderer widgetAt(double rawX, double rawY) {
        HudWidgetRenderer found = null;
        for (HudAnchorGroup group : getAnchorGroups())
            for (Object element : group.getWidgets())
                if (element instanceof HudWidgetRenderer widget && widget.isEnabled() && widget.isMouseOver(rawX, rawY)) found = widget;
        return found;
    }

    private void openSettings(Module module) {
        searchFocused = false;
        minecraft.setScreen(new ModuleSettingsScreen(this, module, null));
    }

    private void mirror() {
        base().argus$swapAnchorContents(HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT);
        base().argus$swapAnchorContents(HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT);
        requestDirectionRefresh();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (isClosing()) return false;
        double x = viewport.toLogical(event.x()), y = viewport.toLogical(event.y());
        int button = event.button();
        if (overUi(x, y)) {
            if (button == 0 || button == 1) handleUiClick(x, y, button);
            return true;
        }
        searchFocused = false;
        if (button == 1) {
            HudWidgetRenderer widget = widgetAt(event.x(), event.y());
            if (widget != null) { openSettings(widget.getWidgetModule()); return true; }
        }
        return super.mouseClicked(event, doubled);
    }

    private void handleUiClick(double mx, double my, int button) {
        if (overToolbar(mx, my)) {
            searchFocused = false;
            if (button != 0) return;
            for (int i = 0; i < 3; i++) if (UiDraw.over(mx, my, buttonX[i], BAR_Y + 3, buttonW[i], BAR_H - 6)) {
                switch (i) {
                    case 0 -> drawerOpen = !drawerOpen;
                    case 1 -> mirror();
                    default -> closeWithTransition();
                }
            }
            return;
        }
        float x = drawerX, y = DRAWER_TOP;
        float sx = x + 10, sy = y + 36, sw = DRAWER_W - 20, sh = 18;
        if (UiDraw.over(mx, my, sx, sy, sw, sh)) {
            if (!query.isEmpty() && UiDraw.over(mx, my, sx + sw - 16, sy + 3, 12, 12)) { query = ""; scrollTarget = 0; }
            searchFocused = true;
            return;
        }
        searchFocused = false;
        float fy = y + 60, fw = (DRAWER_W - 20) / 3f;
        for (int i = 0; i < FILTERS.length; i++)
            if (UiDraw.over(mx, my, x + 10 + fw * i, fy, fw, 15)) { filter = i; scrollTarget = 0; return; }
        if (my < listTop || my >= listBottom) return;
        for (int i = 0; i < shown.size(); i++) {
            float ry = listTop + i * (ROW_H + ROW_GAP) - scroll, rx = x + 8, rw = DRAWER_W - 16;
            if (!UiDraw.over(mx, my, rx, ry, rw, ROW_H)) continue;
            HudWidgetModule module = shown.get(i);
            float gx = rx + rw - 24 - 15, cy = ry + ROW_H / 2;
            if (button == 1 || UiDraw.over(mx, my, gx - 2, cy - 6, 12, 12)) openSettings(module);
            else module.toggle();
            return;
        }
    }

    @Override public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        double x = viewport.toLogical(rawX), y = viewport.toLogical(rawY);
        if (overDrawer(x, y)) { scrollTarget -= (float) vertical * 24; return true; }
        return super.mouseScrolled(rawX, rawY, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean control = (event.modifiers() & 2) != 0;
        if (control && key == 70) { drawerOpen = true; searchFocused = true; return true; }
        if (searchFocused) {
            switch (key) {
                case 256 -> searchFocused = false;
                case 259 -> { if (!query.isEmpty()) query = control ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)); scrollTarget = 0; }
                case 257, 335 -> { refreshShown(); if (!shown.isEmpty()) shown.getFirst().toggle(); }
                default -> { }
            }
            return true;
        }
        // Delete/arrow keys act on the element under the pointer; ignore them over the toolbar or drawer.
        if (overUi(lastMouseX, lastMouseY) && (key == 259 || key == 261 || key >= 262 && key <= 265)) return true;
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (searchFocused) {
            if (event.isAllowedChatCharacter() && query.length() < 48) { query += Character.toString(event.codepoint()); scrollTarget = 0; }
            return true;
        }
        return super.charTyped(event);
    }

    @Override public void removed() {
        searchFocused = false;
        super.removed();
    }
}

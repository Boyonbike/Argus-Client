package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.gui.mixin.HudEditorScreenAccessor;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.modules.hud.HudTextListRenderer;
import dev.argus.client.modules.hud.HudTextRow;
import dev.argus.client.render.HudAnchorGroup;
import dev.argus.client.render.HudHorizontalAlignment;
import dev.argus.client.render.HudVerticalAlignment;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.math.FloatBounds;
import dev.argus.client.gui.HudSafeZones.Side;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The HUD editor in the Argus style. The base editor still owns the HUD itself (anchor groups,
 * resizing, margins, rendering); this screen replaces its legacy module panel and its dragging:
 * <pre>
 *            ┌─ [▣ HUD editor │ 3 of 21 on screen │ HUD ● │ Elements │ Mirror │ Settings │ Done] ─┐
 *            │                                                          ┌ Elements ──────────┐ │
 *            │   elements drag on the Modules page's snap grid,         │ [⌕ Search…       ] │ │
 *            │   keeping its gap and gliding into place on release      │ All · On · Hidden  │ │
 *            │                                                          │ ◉ Armor        [●] │ │
 *            │        Drag to move · Right-click for settings · …       └────────────────────┘ │
 * </pre>
 * The four screen edges are the HUD's safe zones (one per side, {@link HudSafeZones}); drag an
 * edge to change that side. Mirror flips every element, stacked or free, across the screen.
 * Argus chrome is drawn in logical units of its own {@link GuiViewport}, exactly like every
 * {@link UiScreen}, so the grid here is the same grid as on the Modules page. The HUD canvas uses
 * Minecraft's GUI coordinates: {@code logical = raw / renderScale}.
 */
public final class ArgusHudEditorScreen extends HudEditorScreen {
    private static final float BAR_Y = 7, BAR_H = 28, BTN_H = 21, PANEL_W = 182, PANEL_HEAD = 30, ROW_H = 30, ROW_GAP = 3, MIN_Y = 6;
    private static final int GRID = ModulesScreen.GRID;
    /** Saved like a Modules window; {@code collapsed} records whether the panel is hidden. */
    private static final String PANEL_KEY = "HUD Elements";
    private static final String[] FILTERS = {"All", "On screen", "Hidden"};
    private static final String[] BUTTONS = {"HUD", "Elements", "Mirror", "Settings", "Done"};
    private static final String[] BUTTON_ICONS = {"hud", "sliders", "reset", "render", "check"};
    private static final int B_HUD = 0, B_ELEMENTS = 1, B_MIRROR = 2, B_SETTINGS = 3, B_DONE = 4;
    /** How close (logical units) to a safe-zone edge counts as grabbing it. */
    private static final float EDGE_GRAB = 4;
    private static int filter;

    private GuiViewport viewport = new GuiViewport(320, 240, 1);
    private String query = "";
    private boolean searchFocused;
    private float scroll, scrollTarget;
    private int mouseX = -1000, mouseY = -1000;

    // Elements panel: snapped position, eased position, and drag state (logical units).
    private int panelX = Integer.MIN_VALUE, panelY;
    private boolean panelHidden;
    private float panelShownX, panelShownY, panelLift, panelVisible;
    private boolean draggingPanel;
    private float panelGrabX, panelGrabY;

    // Element being dragged on the grid, and one gliding into its snapped place after release.
    private HudWidgetRenderer dragged, settling;
    private float grabX, grabY, dragShownX, dragShownY, settleX, settleY;
    private int targetX, targetY;
    private float gridFade;
    /** Edge alignment a dropped element takes once it has glided into place. */
    private HudHorizontalAlignment settleH = HudHorizontalAlignment.FREE;
    private HudVerticalAlignment settleV = HudVerticalAlignment.FREE;

    // Selection, like the Modules page: a box picks elements, and dragging a picked one moves them all.
    private final Set<HudWidgetRenderer> picked = new LinkedHashSet<>();
    private Set<HudWidgetRenderer> pickedBefore = Set.of();
    private boolean boxing;
    private float boxX0, boxY0, boxX1, boxY1;
    /** The picked elements being dragged together, with where each started (logical units). */
    private final Map<HudWidgetRenderer, float[]> groupStart = new LinkedHashMap<>();
    private HudWidgetRenderer groupLead;
    private float pressX, pressY;

    // One line of a multi-line element being moved on its own.
    private HudTextListRenderer lineOwner;
    private HudTextRow line;
    private float lineGrabX, lineGrabY;

    // Safe-zone edge under the pointer, and the one being dragged.
    private HudSafeZones.Side edgeHover, edgeDrag;

    // Geometry from the last frame, used for hit-testing.
    private float barX, barW, listTop, listBottom;
    private final float[] buttonX = new float[BUTTONS.length], buttonW = new float[BUTTONS.length];
    private final List<HudWidgetModule> shown = new ArrayList<>();

    // Tooltips behave like UiScreen's: they appear after the pointer rests on something.
    private Object tooltipOwner, shownTooltipOwner;
    private String[] tooltipLines;
    private long tooltipSince;

    public ArgusHudEditorScreen() {
        super();
        // The legacy category panel is replaced by the Elements panel.
        panels.clear();
    }

    /** Shows the editor with {@code parent} as the screen Done returns to. */
    void openOver(Screen parent) {
        setParentScreen(parent);
        Minecraft.getInstance().setScreen(this);
    }

    private static boolean shiftDown() { return dev.argus.client.utils.KeyUtils.isPhysicalKeyDown(340) || dev.argus.client.utils.KeyUtils.isPhysicalKeyDown(344); }
    private HudEditorScreenAccessor base() { return (HudEditorScreenAccessor) (Object) this; }
    private float lw() { return viewport.width(); }
    private float lh() { return viewport.height(); }
    private float logical(double raw) { return (float) viewport.toLogical(raw); }
    private float raw(float logical) { return logical * viewport.renderScale(); }
    private boolean busy() { return dragged != null || edgeDrag != null || boxing || lineOwner != null || !groupStart.isEmpty() || base().argus$isDraggingWidget(); }
    private static HUDModule hud() { return ClientContext.getModule(HUDModule.class); }

    @Override protected void init() {
        super.init();
        base().argus$setCenterPanelPending(false);
        var window = minecraft.getWindow();
        viewport = GuiViewport.create(window.getWidth(), window.getHeight(), window.getGuiScale(), GuiState.preferences().uiScale);
        DescriptionCatalog.ensureApplied();
        restorePanel();
    }

    // ---- elements panel position ---------------------------------------------------------------

    private float panelHeight() { return Math.max(140, Math.min(320, lh() - 76)); }
    private int panelMaxX() { return Math.max(10, Math.round(lw() - PANEL_W - 10)); }
    private int panelMaxY() { return Math.max((int) MIN_Y, Math.round(lh() - panelHeight() - 28)); }

    private void restorePanel() {
        var saved = GuiState.preferences().windows.get(PANEL_KEY);
        boolean first = panelX == Integer.MIN_VALUE;
        if (saved != null) { panelX = saved.x(); panelY = saved.y(); panelHidden = saved.collapsed(); }
        else if (first) { panelX = Math.round((lw() - PANEL_W - 10) / GRID) * GRID; panelY = 44; }
        // Only clamp to the screen; a shown/hidden panel never moves.
        panelX = Math.clamp(panelX, 10, panelMaxX());
        panelY = Math.clamp(panelY, (int) MIN_Y, panelMaxY());
        if (first || !draggingPanel) { panelShownX = panelX; panelShownY = panelY; }
        if (first) panelVisible = panelHidden ? 0 : 1;
    }

    private void rememberPanel() {
        GuiState.preferences().windows.put(PANEL_KEY, new GuiPreferences.Window(panelX, panelY, panelHidden, 0));
    }

    private void setPanelHidden(boolean hidden) {
        panelHidden = hidden;
        if (hidden) searchFocused = false;
        rememberPanel(); GuiState.save();
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
        if (name.contains("music") || name.contains("indicator")) return "bell";
        if (name.contains("module")) return "modules";
        if (name.contains("watermark")) return "star";
        if (name.contains("inventory") || name.contains("player")) return "player";
        if (name.contains("graph") || name.contains("metric") || name.contains("lag") || name.contains("crypto")) return "sliders";
        return "hud";
    }

    private List<HudWidgetRenderer> widgets() {
        List<HudWidgetRenderer> result = new ArrayList<>();
        for (HudAnchorGroup group : getAnchorGroups())
            for (Object element : group.getWidgets()) if (element instanceof HudWidgetRenderer widget) result.add(widget);
        return result;
    }

    /** Logical bounds {x, y, w, h} of an element on the canvas. */
    private float[] bounds(HudWidgetRenderer widget) {
        float[] size = widget.getDimensions();
        return new float[]{logical(widget.getX()), logical(widget.getY()), logical(Math.max(size[0], 4)), logical(Math.max(size[1], 4))};
    }

    private HudWidgetRenderer widgetAt(double rawX, double rawY) {
        HudWidgetRenderer found = null;
        for (HudWidgetRenderer widget : widgets()) if (widget.isEnabled() && widget.isMouseOver(rawX, rawY)) found = widget;
        return found;
    }

    // ---- rendering ----------------------------------------------------------------------------

    /** Follows the Appearance animation speed; with animations off the editor closes on the next tick. */
    @Override protected long transitionMillis() { return UiAnim.enabled() ? Math.round(150 / UiAnim.speed()) : 0; }

    /** The editor's fade, eased like the menu pages' open and close (and instant when animations are off). */
    @Override public float getTransitionOpacity() {
        if (!UiAnim.enabled()) return isClosing() ? 0 : 1;
        float t = Math.clamp((System.currentTimeMillis() - transitionStartedAt) / (float) transitionMillis(), 0f, 1f);
        return isClosing() ? 1 - UiAnim.easeInOutSine(t) : UiAnim.easeOutCubic(t);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int rawX, int rawY, float delta) {
        settle();
        syncSelection();
        super.extractRenderState(g, rawX, rawY, delta);
        UiAnim.frame();
        UiDraw.density = (float) (viewport.renderScale() * minecraft.getWindow().getGuiScale());
        mouseX = (int) logical(rawX); mouseY = (int) logical(rawY);
        UiTheme t = GuiState.theme();
        tooltipOwner = null;
        g.pose().pushMatrix();
        g.pose().scale(viewport.renderScale(), viewport.renderScale());
        float before = UiRender.opacity;
        try {
            float appear = getTransitionOpacity();
            UiRender.opacity = appear;
            drawGrid(g, t);
            drawZones(g, t);
            elementFrames(g, rawX, rawY, t);
            // The selection box fades in as it starts.
            boxShown = boxing ? UiAnim.approach(boxShown, 1, 22) : 0;
            if (boxing) {
                float bx = Math.min(boxX0, boxX1), by = Math.min(boxY0, boxY1), bw = Math.abs(boxX1 - boxX0), bh = Math.abs(boxY1 - boxY0);
                UiDraw.rounded(g, bx, by, bw, bh, 3, UiDraw.alpha(t.accent(), .08f * boxShown));
                UiDraw.outline(g, bx, by, bw, bh, 3, .8f, UiDraw.alpha(t.accent(), .7f * boxShown));
            }
            // The toolbar and Elements panel rise in and sink out like the menu pages do.
            g.pose().pushMatrix();
            g.pose().translate(0, (1 - appear) * 6);
            try {
                toolbar(g, mouseX, mouseY, t);
                panel(g, mouseX, mouseY, t);
            } finally { g.pose().popMatrix(); }
            hints(g, t);
            drawTooltip(g, t);
        } finally {
            UiRender.opacity = before;
            g.pose().popMatrix();
        }
    }

    /**
     * The Modules page's snap grid, faded in while something is being dragged (if enabled in
     * Appearance). The game shows through here, so a light scrim goes under it and the lines are
     * drawn a little stronger than on the Modules page's dark backdrop, or they would vanish.
     */
    private void drawGrid(GuiGraphicsExtractor g, UiTheme theme) {
        var p = GuiState.preferences();
        boolean active = dragged != null || draggingPanel || edgeDrag != null || lineOwner != null || !groupStart.isEmpty();
        gridFade = UiAnim.approach(gridFade, active && p.showGrid ? 1 : 0, active ? 12 : 8);
        if (gridFade <= .01f) return;
        UiDraw.rect(g, 0, 0, lw(), lh(), UiDraw.alpha(0xff000000, .28f * gridFade));
        int minor = UiDraw.alpha(theme.text(), .12f * gridFade), major = UiDraw.alpha(theme.text(), .28f * gridFade);
        for (int x = 0, i = 0; x <= lw(); x += GRID, i++) UiDraw.rect(g, x, 0, .5f, lh(), i % 4 == 0 ? major : minor);
        for (int y = 0, i = 0; y <= lh(); y += GRID, i++) UiDraw.rect(g, 0, y, lw(), .5f, i % 4 == 0 ? major : minor);
    }

    // ---- safe zones ---------------------------------------------------------------------------

    private float zone(Side side) { return logical(HudSafeZones.get(side)); }

    /** The safe-zone edge at a logical point, if it is close enough to grab. */
    private Side edgeAt(double x, double y) {
        if (x <= zone(Side.LEFT) + EDGE_GRAB) return Side.LEFT;
        if (x >= lw() - zone(Side.RIGHT) - EDGE_GRAB) return Side.RIGHT;
        if (y <= zone(Side.TOP) + EDGE_GRAB) return Side.TOP;
        if (y >= lh() - zone(Side.BOTTOM) - EDGE_GRAB) return Side.BOTTOM;
        return null;
    }

    private static String sideName(Side side) {
        return switch (side) { case LEFT -> "Left"; case RIGHT -> "Right"; case TOP -> "Top"; case BOTTOM -> "Bottom"; };
    }

    /**
     * Shades the safe zones: the edge under the pointer or being dragged, and all four (dimmer)
     * while an element is moved, so it's clear where elements stop.
     */
    private void drawZones(GuiGraphicsExtractor g, UiTheme t) {
        for (Side side : Side.values()) {
            boolean focus = side == edgeDrag || edgeDrag == null && dragged == null && side == edgeHover;
            float a = UiAnim.smooth("hud.zone." + side, focus ? 1 : dragged != null || edgeDrag != null ? .5f : 0, 14);
            if (a <= .01f) continue;
            float z = zone(side);
            float x = side == Side.RIGHT ? lw() - z : 0, y = side == Side.BOTTOM ? lh() - z : 0;
            float w = side == Side.LEFT || side == Side.RIGHT ? z : lw(), h = side == Side.TOP || side == Side.BOTTOM ? z : lh();
            UiDraw.rect(g, x, y, w, h, UiDraw.alpha(0xff000000, .4f * a));
            float lx = side == Side.LEFT ? z : side == Side.RIGHT ? lw() - z : 0, ly = side == Side.TOP ? z : side == Side.BOTTOM ? lh() - z : 0;
            int edge = UiDraw.alpha(t.accent(), (focus ? .9f : .5f) * a);
            if (side == Side.LEFT || side == Side.RIGHT) UiDraw.rect(g, lx - .5f, 0, 1, lh(), edge);
            else UiDraw.rect(g, 0, ly - .5f, lw(), 1, edge);
            if (!focus) continue;
            String label = sideName(side) + " safe zone · " + HudSafeZones.get(side);
            float pw = UiDraw.width(label, UiDraw.SMALL) + 14, ph = 14;
            float px = side == Side.LEFT ? lx + 6 : side == Side.RIGHT ? lx - pw - 6 : (lw() - pw) / 2;
            float py = side == Side.TOP ? ly + 6 : side == Side.BOTTOM ? ly - ph - 6 : (lh() - ph) / 2;
            float before = UiRender.opacity;
            UiRender.opacity = before * a;
            UiDraw.rounded(g, px, py, pw, ph, ph / 2, UiDraw.alpha(t.mantle(), .92f));
            UiDraw.outline(g, px, py, pw, ph, ph / 2, .6f, UiDraw.alpha(t.accent(), .6f));
            UiDraw.text(g, label, px + 7, py + ph / 2, UiDraw.SMALL, t.text());
            UiRender.opacity = before;
        }
    }

    private void dragEdge(double x, double y) {
        double distance = switch (edgeDrag) { case LEFT -> x; case RIGHT -> lw() - x; case TOP -> y; case BOTTOM -> lh() - y; };
        HudSafeZones.set(edgeDrag, (int) Math.round(raw((float) Math.max(0, distance))));
    }

    /** Accent frame on the hovered element (or line), the picked ones, and the landing spot of the one being dragged. */
    /** Fade of each selection and hover frame, so frames ease in and out instead of popping. */
    private final Map<HudWidgetRenderer, Float> pickFade = new HashMap<>(), hoverFade = new HashMap<>();
    private float boxShown, ghostX, ghostY, ghostShown;
    private boolean ghostPlaced;

    private static void fade(Map<HudWidgetRenderer, Float> fades, Collection<HudWidgetRenderer> on) {
        for (HudWidgetRenderer widget : on) fades.putIfAbsent(widget, 0f);
        fades.replaceAll((widget, a) -> UiAnim.approach(a, on.contains(widget) ? 1 : 0, on.contains(widget) ? 22 : 14));
        fades.values().removeIf(a -> a <= .01f);
    }

    private void elementFrames(GuiGraphicsExtractor g, int rawX, int rawY, UiTheme t) {
        List<HudWidgetRenderer> selection = new ArrayList<>(picked);
        selection.remove(dragged);
        fade(pickFade, selection);
        pickFade.forEach((widget, a) -> {
            float[] b = bounds(widget);
            UiDraw.rounded(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, UiDraw.alpha(t.accent(), .1f * a));
            UiDraw.outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, 1, UiDraw.alpha(t.accent(), a));
        });
        if (dragged == null) { ghostPlaced = false; ghostShown = 0; }
        if (lineOwner != null || !groupStart.isEmpty() || boxing || dragged != null) hoverFade.clear();
        if (lineOwner != null) {
            for (var drawn : lineOwner.lines()) if (drawn.row() == line) {
                UiDraw.outline(g, logical(drawn.x()) - 2, logical(drawn.y()) - 2, logical(drawn.w()) + 4, logical(drawn.h()) + 4, 3, 1, t.accent());
            }
            float[] stack = lineOwner.stackBounds();
            if (stack != null) UiDraw.outline(g, logical(stack[0]) - 2, logical(stack[1]) - 2, logical(stack[2]) + 4, logical(stack[3]) + 4, 3, .6f, UiDraw.alpha(t.accent(), .45f));
            return;
        }
        if (!groupStart.isEmpty() || boxing) return;
        if (dragged != null) {
            float[] b = bounds(dragged);
            // The drop preview glides between snap spots and fades in once the element is off its spot.
            if (!ghostPlaced) { ghostX = targetX; ghostY = targetY; ghostPlaced = true; }
            ghostX = UiAnim.approach(ghostX, targetX, 26); ghostY = UiAnim.approach(ghostY, targetY, 26);
            ghostShown = UiAnim.approach(ghostShown, Math.abs(targetX - b[0]) > .5f || Math.abs(targetY - b[1]) > .5f ? 1 : 0, 18);
            if (ghostShown > .01f) {
                UiDraw.rounded(g, ghostX - 1, ghostY - 1, b[2] + 2, b[3] + 2, 3, UiDraw.alpha(t.accent(), .12f * ghostShown));
                UiDraw.outline(g, ghostX - 1, ghostY - 1, b[2] + 2, b[3] + 2, 3, .8f, UiDraw.alpha(t.accent(), .55f * ghostShown));
            }
            UiDraw.outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, 1, t.accent());
            return;
        }
        edgeHover = null;
        HudWidgetRenderer hovered = null;
        if (!overUi(mouseX, mouseY) && !base().argus$isDraggingWidget() && edgeDrag == null) {
            hovered = widgetAt(rawX, rawY);
            if (hovered == null) edgeHover = edgeAt(mouseX, mouseY);
            else if (!picked.contains(hovered) && hovered instanceof HudTextListRenderer list && list.isSplittable()) {
                // An unpicked multi-line element moves line by line: frame just the line under the pointer.
                var under = list.lineAt(rawX, rawY);
                if (under != null) {
                    float a = UiAnim.smooth("hud.line." + under.x() + "." + under.y(), 0, 1, 22);
                    UiDraw.outline(g, logical(under.x()) - 2, logical(under.y()) - 2, logical(under.w()) + 4, logical(under.h()) + 4, 3, 1, UiDraw.alpha(t.accent(), .7f * a));
                    hovered = null;
                }
            }
        }
        // Hover frames ease in and fade out when the pointer moves on.
        fade(hoverFade, hovered == null ? List.of() : List.of(hovered));
        hoverFade.forEach((widget, a) -> {
            float[] b = bounds(widget);
            UiDraw.outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, 1, UiDraw.alpha(t.accent(), .7f * a));
        });
    }

    /** A pill like the navigation bar: title, live count, then the editor's buttons. */
    private void toolbar(GuiGraphicsExtractor g, int mx, int my, UiTheme t) {
        String title = "HUD editor";
        int on = 0, total = 0;
        for (HudWidgetModule module : elements()) { total++; if (module.isToggled()) on++; }
        String count = on + " of " + total + " on screen";
        float titleW = 16 + UiDraw.width(title) + 12, countW = UiDraw.width(count, UiDraw.SMALL) + 14, buttons = 0;
        for (int i = 0; i < BUTTONS.length; i++) { buttonW[i] = Math.max(34, UiDraw.width(BUTTONS[i]) + 30 + (i == B_HUD ? 20 : 0)); buttons += buttonW[i]; }
        barW = 10 + titleW + countW + buttons + 4;
        barX = (lw() - barW) / 2;
        float prior = UiRender.opacity;
        UiRender.opacity = prior * (busy() ? .35f : 1);
        UiDraw.shadow(g, barX, BAR_Y + 1, barW, BAR_H, BAR_H / 2, 8, UiDraw.alpha(0xff000000, .28f));
        UiDraw.rounded(g, barX, BAR_Y, barW, BAR_H, BAR_H / 2, t.mantle());
        UiDraw.outline(g, barX, BAR_Y, barW, BAR_H, BAR_H / 2, .6f, UiDraw.alpha(t.overlay(), .5f));
        float x = barX + 12, cy = BAR_Y + BAR_H / 2;
        UiDraw.icon(g, "hud", x, cy - 4.5f, 9, t.accent());
        UiDraw.bold(g, title, x + 14, cy, UiDraw.TEXT, t.text());
        x = barX + 10 + titleW;
        UiDraw.rect(g, x - 6, BAR_Y + 8, .6f, BAR_H - 16, UiDraw.alpha(t.overlay(), .7f));
        UiDraw.text(g, count, x, cy, UiDraw.SMALL, t.muted());
        x += countW;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = BAR_Y + (BAR_H - BTN_H) / 2;
            buttonX[i] = x;
            boolean hover = !busy() && UiDraw.over(mx, my, x, by, buttonW[i], BTN_H);
            boolean hudOn = hud().isToggled();
            boolean selected = i == B_ELEMENTS && !panelHidden || i == B_HUD && hudOn;
            float h = UiAnim.smooth("hud.btn." + i, hover ? 1 : 0, 18), s = UiAnim.smooth("hud.btn.on." + i, selected ? 1 : 0, 16);
            if (s > .01f) UiDraw.rounded(g, x, by, buttonW[i], BTN_H, BTN_H / 2, UiDraw.alpha(t.accent(), .2f * s));
            if (h > 0 && s < 1) UiDraw.rounded(g, x, by, buttonW[i], BTN_H, BTN_H / 2, UiDraw.alpha(t.surface(), h * .7f * (1 - s)));
            int colour = UiDraw.mix(UiDraw.mix(t.muted(), t.text(), h), t.accent(), s);
            UiDraw.icon(g, BUTTON_ICONS[i], x + 9, cy - 4, 8, colour);
            UiDraw.text(g, BUTTONS[i], x + 21, cy, colour);
            if (i == B_HUD) UiDraw.toggle(g, x + buttonW[i] - 27, cy - 5, UiAnim.smooth("hud.btn.switch", hudOn ? 1 : 0, 16), hover, t);
            if (hover) switch (i) {
                case B_HUD -> tooltip("hud.btn.hud", hudOn ? "HUD is on" : "HUD is off", hudOn ? "Click to hide every HUD element in game" : "Click to show your HUD in game again");
                case B_ELEMENTS -> tooltip("hud.btn.1", panelHidden ? "Show elements" : "Hide elements", "The list of HUD elements you can add · Ctrl+F searches it");
                case B_MIRROR -> tooltip("hud.btn.2", "Mirror", "Flip every element to the other side of the screen (I)");
                case B_SETTINGS -> tooltip("hud.btn.3", "HUD settings", "Colours, text, safe zones and the rest of the HUD's options");
                default -> tooltip("hud.btn.4", "Done", "Leave the editor (Esc)");
            }
            x += buttonW[i];
        }
        UiRender.opacity = prior;
    }

    /** The Elements panel: a draggable window that keeps its place while hidden. */
    private void panel(GuiGraphicsExtractor g, int mx, int my, UiTheme t) {
        panelVisible = UiAnim.approach(panelVisible, panelHidden ? 0 : 1, panelHidden ? 18 : 14);
        if (!draggingPanel) { panelShownX = UiAnim.approach(panelShownX, panelX, 22); panelShownY = UiAnim.approach(panelShownY, panelY, 22); }
        panelLift = UiAnim.approach(panelLift, draggingPanel ? 1 : 0, 14);
        if (panelVisible <= .002f) return;
        refreshShown();
        float x = panelShownX, y = panelShownY, h = panelHeight();
        float appear = UiAnim.easeOutCubic(panelVisible);
        float prior = UiRender.opacity;
        UiRender.opacity = prior * appear * (busy() ? .3f : 1);
        g.pose().pushMatrix();
        // Fades and settles in place; it never slides, so showing it again finds it where it was.
        g.pose().translate(0, (1 - appear) * 6);
        try {
            UiDraw.panel(g, x, y, PANEL_W, h, t, panelLift);
            // Header doubles as the drag handle.
            boolean headHover = !busy() && !draggingPanel && UiDraw.over(mx, my, x, y, PANEL_W, PANEL_HEAD);
            float hh = UiAnim.smooth("hud.panel.head", headHover || draggingPanel ? 1 : 0, 18);
            UiDraw.circle(g, x + 17, y + 15, 8, UiDraw.alpha(t.accent(), .16f));
            UiDraw.icon(g, "plus", x + 13, y + 11, 8, t.accent());
            UiDraw.bold(g, "Elements", x + 30, y + 11, UiDraw.TITLE, t.text());
            UiDraw.text(g, "Switch one on to add it to your screen", x + 30, y + 21, 6f, t.muted());
            boolean closeHover = headHover && UiDraw.over(mx, my, x + PANEL_W - 20, y + 7, 14, 14);
            float ch = UiAnim.smooth("hud.panel.close", closeHover ? 1 : 0, 18);
            if (ch > 0) UiDraw.circle(g, x + PANEL_W - 13, y + 14, 7, UiDraw.alpha(t.surface(), ch));
            UiDraw.icon(g, "close", x + PANEL_W - 17, y + 10, 8, UiDraw.alpha(UiDraw.mix(t.muted(), t.text(), ch), .5f + hh * .5f));
            if (closeHover) tooltip("hud.panel.close", "Hide elements", "Show them again with Elements in the toolbar");
            else if (headHover) tooltip("hud.panel.head", "Elements", "Drag the header to move this panel");
            UiDraw.rect(g, x + 1, y + PANEL_HEAD, PANEL_W - 2, .6f, UiDraw.alpha(t.overlay(), .45f));
            // Search.
            float sx = x + 10, sy = y + PANEL_HEAD + 7, sw = PANEL_W - 20, sh = 18;
            boolean searchHover = !busy() && UiDraw.over(mx, my, sx, sy, sw, sh);
            float sf = UiAnim.smooth("hud.search", searchFocused ? 1 : 0, 16), shv = UiAnim.smooth("hud.search.h", searchHover ? 1 : 0, 18);
            UiDraw.rounded(g, sx, sy, sw, sh, sh / 2, UiDraw.mix(t.surface(), t.overlay(), shv * .5f));
            UiDraw.outline(g, sx, sy, sw, sh, sh / 2, .7f, UiDraw.mix(UiDraw.alpha(t.overlay(), .5f), t.accent(), sf));
            UiDraw.icon(g, "search", sx + 7, sy + 5, 8, UiDraw.mix(t.muted(), t.accent(), sf));
            String visible = UiDraw.fit(query, sw - 36);
            if (query.isEmpty()) UiDraw.text(g, "Search elements…", sx + 19, sy + sh / 2, UiDraw.alpha(t.muted(), .8f));
            else UiDraw.text(g, visible, sx + 19, sy + sh / 2, t.text());
            if (searchFocused && (System.currentTimeMillis() / 530) % 2 == 0)
                UiDraw.rect(g, sx + 19 + (query.isEmpty() ? 0 : UiDraw.width(visible) + .8f), sy + 5, .8f, sh - 10, t.accent());
            if (!query.isEmpty()) {
                boolean clearHover = UiDraw.over(mx, my, sx + sw - 16, sy + 3, 12, 12);
                UiDraw.icon(g, "close", sx + sw - 14, sy + 5, 8, clearHover ? t.text() : t.muted());
            }
            // Filter segments.
            float fy = sy + sh + 6, fw = (PANEL_W - 20) / 3f, fh = 15;
            UiDraw.rounded(g, x + 10, fy, PANEL_W - 20, fh, fh / 2, UiDraw.alpha(t.surface(), .55f));
            float indicator = UiAnim.smooth("hud.filter", filter, 18);
            UiDraw.rounded(g, x + 10 + fw * indicator + 1, fy + 1, fw - 2, fh - 2, (fh - 2) / 2, UiDraw.alpha(t.accent(), .22f));
            for (int i = 0; i < FILTERS.length; i++) {
                boolean hover = !busy() && UiDraw.over(mx, my, x + 10 + fw * i, fy, fw, fh);
                int colour = i == filter ? t.accent() : hover ? t.text() : t.muted();
                UiDraw.center(g, FILTERS[i], x + 10 + fw * i, fy + fh / 2, fw, UiDraw.SMALL, colour);
            }
            // List.
            listTop = fy + fh + 6; listBottom = y + h - 8;
            float content = shown.size() * (ROW_H + ROW_GAP) - ROW_GAP;
            float max = Math.max(0, content - (listBottom - listTop));
            scrollTarget = Math.clamp(scrollTarget, 0, max);
            scroll = UiAnim.approach(scroll, scrollTarget, 20);
            if (shown.isEmpty()) {
                boolean none = filter == 1 && query.isBlank();
                String empty = elements().isEmpty() ? "No HUD elements found" : !query.isBlank() ? "Nothing matches “" + UiDraw.fit(query.trim(), 70) + "”"
                        : none ? "Nothing on screen yet" : "Everything is on screen";
                UiDraw.icon(g, none ? "plus" : "search", x + PANEL_W / 2 - 6, listTop + 18, 12, UiDraw.alpha(t.muted(), .5f));
                UiDraw.center(g, empty, x, listTop + 42, PANEL_W, UiDraw.SMALL, t.muted());
                if (none) UiDraw.center(g, "Switch elements on under All", x, listTop + 53, PANEL_W, 6f, UiDraw.alpha(t.muted(), .7f));
            }
            g.enableScissor(Math.round(x + 2), Math.round(listTop), Math.round(x + PANEL_W - 2), Math.round(listBottom));
            try {
                for (int i = 0; i < shown.size(); i++) {
                    float ry = listTop + i * (ROW_H + ROW_GAP) - scroll;
                    if (ry + ROW_H < listTop || ry > listBottom) continue;
                    row(g, shown.get(i), x + 8, ry, PANEL_W - 16, mx, my, t);
                }
            } finally { g.disableScissor(); }
            if (scroll > .5f) UiDraw.gradient(g, x + 1, listTop, PANEL_W - 2, 10, 0, t.base(), UiDraw.alpha(t.base(), 0));
            if (scroll < max - .5f) UiDraw.gradient(g, x + 1, listBottom - 10, PANEL_W - 2, 10, 0, UiDraw.alpha(t.base(), 0), t.base());
            if (max > 0) {
                float track = listBottom - listTop, thumb = Math.max(16, track * track / content);
                UiDraw.rounded(g, x + PANEL_W - 5, listTop + (track - thumb) * (scroll / max), 2, thumb, 1, UiDraw.alpha(t.overlay(), .8f));
            }
        } finally {
            g.pose().popMatrix();
            UiRender.opacity = prior;
        }
    }

    private void row(GuiGraphicsExtractor g, HudWidgetModule module, float x, float y, float w, int mx, int my, UiTheme t) {
        boolean hover = !busy() && !draggingPanel && my >= listTop && my < listBottom && UiDraw.over(mx, my, x, y, w, ROW_H);
        String key = "hud.row." + module.getName();
        float hv = UiAnim.smooth(key, hover ? 1 : 0, 18);
        float on = UiAnim.smooth(key + ".on", module.isToggled() ? 1 : 0, 16);
        UiDraw.rounded(g, x, y, w, ROW_H, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .55f), t.surface(), hv * .75f));
        if (on > .01f) UiDraw.rounded(g, x, y + 7, 2, ROW_H - 14, 1, UiDraw.alpha(t.accent(), on));
        float cx = x + 16, cy = y + ROW_H / 2;
        UiDraw.circle(g, cx, cy, 9, UiDraw.mix(UiDraw.alpha(t.surface(), .9f), UiDraw.alpha(t.accent(), .2f), on));
        UiDraw.icon(g, icon(module), cx - 4.5f, cy - 4.5f, 9, UiDraw.mix(t.muted(), t.accent(), on));
        float textX = x + 31, reserve = 24 + (hv > .01f ? 14 : 0);
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), w - (textX - x) - reserve), textX, y + 11, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(description(module), w - (textX - x) - reserve, 6f), textX, y + 21, 6f, t.muted());
        float tx = x + w - 24, ty = cy - 5;
        UiDraw.toggle(g, tx, ty, on, hover && UiDraw.over(mx, my, tx - 2, ty - 3, 22, 16), t);
        if (hv > .01f) {
            float gx = tx - 15;
            boolean gearHover = hover && UiDraw.over(mx, my, gx - 2, cy - 6, 12, 12);
            float before = UiRender.opacity;
            UiRender.opacity = before * hv;
            if (gearHover) UiDraw.circle(g, gx + 4, cy, 6.5f, t.overlay());
            UiDraw.icon(g, "sliders", gx, cy - 4, 8, gearHover ? t.text() : t.muted());
            UiRender.opacity = before;
            if (gearHover) tooltip(key + ".gear", GuiState.title(module.getName()) + " settings", "Or right-click the element on screen");
            else if (hover) tooltip(key, GuiState.title(module.getName()), module.isToggled() ? "Click to select it on screen · the switch removes it" : "Click to add it to your screen");
        }
    }

    private void hints(GuiGraphicsExtractor g, UiTheme t) {
        var p = GuiState.preferences();
        String text = edgeDrag != null ? "Drag to set the " + sideName(edgeDrag).toLowerCase(Locale.ROOT) + " safe zone · each edge has its own"
                : boxing ? "Drag over elements to select them · Shift adds to the selection"
                : lineOwner != null ? "Moves just this line · drop it on the rest of the element to put it back"
                : !groupStart.isEmpty() ? (p.snap ? "Moving " + groupStart.size() + " elements together on the grid" : "Moving " + groupStart.size() + " elements together")
                : dragged != null ? (p.snap ? "Snaps to the grid" + (p.hudGap > 0 ? " · keeps a " + p.hudGap + " gap" : "") : "Snapping is off in Appearance") + " · drop in a corner to stack"
                : !hud().isToggled() ? "The HUD is off, so nothing shows in game · switch it on in the toolbar"
                : !picked.isEmpty() ? picked.size() + " selected · drag to move together · Del removes · Arrows nudge · Esc clears"
                : "Drag to move · Drag empty space to select · Lines move on their own · R restacks · Right-click for settings · Drag an edge for its safe zone";
        text = UiDraw.fit(text, lw() - 40, UiDraw.SMALL);
        // The pill eases to the new text's width and the text crossfades.
        float target = UiDraw.width(text, UiDraw.SMALL) + 20, w = UiAnim.smooth("hud.hints.w", target, target, 18), h = 16, x = (lw() - w) / 2, y = lh() - h - 8;
        UiDraw.rounded(g, x, y, w, h, h / 2, UiDraw.alpha(t.mantle(), .78f));
        UiDraw.outline(g, x, y, w, h, h / 2, .6f, UiDraw.alpha(t.overlay(), .4f));
        UiMotion.text(g, "hud.hints", text, l -> UiDraw.center(g, l, x, y + h / 2, w, UiDraw.SMALL, t.muted()));
    }

    private void tooltip(Object owner, String... lines) { tooltipOwner = owner; tooltipLines = lines; }

    /** Same look and timing as {@link UiScreen}'s tooltips. */
    private void drawTooltip(GuiGraphicsExtractor g, UiTheme t) {
        if (tooltipOwner == null || !tooltipOwner.equals(shownTooltipOwner)) { shownTooltipOwner = tooltipOwner; tooltipSince = System.nanoTime(); }
        if (tooltipOwner == null || tooltipLines == null) return;
        float rested = (System.nanoTime() - tooltipSince) / 1e9f - .35f;
        float shownT = UiAnim.enabled() ? UiAnim.easeOutCubic(rested * UiAnim.speed() / .14f) : rested >= 0 ? 1 : 0;
        if (shownT <= 0) return;
        float w = 0;
        for (int i = 0; i < tooltipLines.length; i++) w = Math.max(w, UiDraw.width(tooltipLines[i], i == 0 ? UiDraw.TEXT : UiDraw.SMALL));
        w = Math.min(lw() - 12, w + 16);
        float lineH = 11, h = 8 + tooltipLines.length * lineH;
        float x = Math.clamp(mouseX + 12, 6, Math.max(6, lw() - w - 6)), y = Math.min(mouseY + 14, lh() - h - 6) + (1 - shownT) * 3;
        float before = UiRender.opacity;
        UiRender.opacity = before * shownT;
        UiDraw.shadow(g, x, y + 2, w, h, 6, 8, UiDraw.alpha(0xff000000, .35f));
        UiDraw.rounded(g, x, y, w, h, 6, t.mantle());
        UiDraw.outline(g, x, y, w, h, 6, .6f, UiDraw.alpha(t.overlay(), .8f));
        for (int i = 0; i < tooltipLines.length; i++) {
            String line = UiDraw.fit(tooltipLines[i], w - 16, i == 0 ? UiDraw.TEXT : UiDraw.SMALL);
            if (i == 0) UiDraw.bold(g, line, x + 8, y + 4 + lineH / 2 + 1, UiDraw.TEXT, t.accent());
            else UiDraw.text(g, line, x + 8, y + 4 + lineH * i + lineH / 2 + 1, UiDraw.SMALL, t.muted());
        }
        UiRender.opacity = before;
    }

    // ---- element dragging on the Modules grid --------------------------------------------------

    private void beginDrag(HudWidgetRenderer widget, double lx, double ly) {
        settling = null;
        HudAnchor from = widget.getAnchor();
        HudAnchorGroup group = getAnchorGroup(from);
        if (group != null) group.getWidgets().remove(widget);
        getAnchorGroup(HudAnchor.NONE).getWidgets().addLast(widget);
        widget.setAnchor(HudAnchor.NONE);
        widget.setSelected(true);
        widget.resetSnapping();
        float[] b = bounds(widget);
        grabX = (float) lx - b[0]; grabY = (float) ly - b[1];
        dragShownX = b[0]; dragShownY = b[1];
        targetX = Math.round(b[0]); targetY = Math.round(b[1]);
        dragged = widget;
    }

    /** Logical {minX, maxX, minY, maxY} for the top-left corner of an element of size w×h inside the safe zones. */
    private float[] limits(float w, float h) {
        float minX = zone(Side.LEFT), minY = zone(Side.TOP);
        return new float[]{minX, Math.max(minX, lw() - zone(Side.RIGHT) - w), minY, Math.max(minY, lh() - zone(Side.BOTTOM) - h)};
    }

    /** Same rules as a Modules window: follow the pointer, snap the target to the grid, keep the gap. */
    private void dragTo(double lx, double ly) {
        float[] b = bounds(dragged), m = limits(b[2], b[3]);
        dragShownX = Math.clamp((float) lx - grabX, m[0], m[1]);
        dragShownY = Math.clamp((float) ly - grabY, m[2], m[3]);
        int tx = Math.round(dragShownX), ty = Math.round(dragShownY);
        if (GuiState.preferences().snap) { tx = Math.round(tx / (float) GRID) * GRID; ty = Math.round(ty / (float) GRID) * GRID; }
        int[] spot = keepGap(tx, ty, Math.round(b[2]), Math.round(b[3]), m);
        targetX = spot[0]; targetY = spot[1];
        place(dragged, dragShownX, dragShownY);
    }

    /** The Modules page's auto gap: the nearest spot that keeps the gap from every element and stays in the safe zones. */
    private int[] keepGap(int x, int y, int w, int h, float[] m) {
        List<HudGap.Rect> others = new ArrayList<>();
        for (HudWidgetRenderer other : widgets()) {
            if (other == dragged || !other.isEnabled()) continue;
            float[] o = bounds(other);
            others.add(new HudGap.Rect(Math.round(o[0]), Math.round(o[1]), Math.round(o[2]), Math.round(o[3])));
        }
        return HudGap.resolve(x, y, w, h, others, GuiState.preferences().hudGap,
                (int) Math.ceil(m[0]), (int) Math.floor(m[1]), (int) Math.ceil(m[2]), (int) Math.floor(m[3]));
    }

    /**
     * Which screen edge (or centre) an element at logical {x, y, w, h} sits on, measured against the
     * safe zones rather than the bare screen edge, so it keeps hugging its side if the zones change.
     */
    private HudHorizontalAlignment horizontalAt(float x, float w) {
        float tolerance = 1.5f;
        if (x <= zone(Side.LEFT) + tolerance) return HudHorizontalAlignment.LEFT;
        if (x + w >= lw() - zone(Side.RIGHT) - tolerance) return HudHorizontalAlignment.RIGHT;
        if (Math.abs(x + w / 2 - lw() / 2) <= tolerance) return HudHorizontalAlignment.CENTER;
        return HudHorizontalAlignment.FREE;
    }

    private HudVerticalAlignment verticalAt(float y, float h) {
        float tolerance = 1.5f;
        if (y <= zone(Side.TOP) + tolerance) return HudVerticalAlignment.TOP;
        if (y + h >= lh() - zone(Side.BOTTOM) - tolerance) return HudVerticalAlignment.BOTTOM;
        if (Math.abs(y + h / 2 - lh() / 2) <= tolerance) return HudVerticalAlignment.CENTER;
        return HudVerticalAlignment.FREE;
    }

    private void place(HudWidgetRenderer widget, float lx, float ly) {
        widget.setX(raw(lx), true);
        widget.setY(raw(ly), true);
        widget.updateDirections();
    }

    /**
     * Drops the element: it glides to its snapped spot, then joins a stack if it was dropped on a
     * stacked element or into a screen corner (the base editor's anchoring), otherwise stays free.
     */
    private void endDrag() {
        HudWidgetRenderer widget = dragged;
        dragged = null;
        widget.setSelected(false);
        float[] b = bounds(widget);
        float[] landing = {targetX, targetY, b[2], b[3]};
        HudAnchor anchor = anchorAt(widget, landing);
        getAnchorGroup(HudAnchor.NONE).getWidgets().remove(widget);
        if (anchor == HudAnchor.NONE) {
            // Glide first; the edge alignment is applied once it lands, or it would jump there.
            settling = widget; settleX = dragShownX; settleY = dragShownY;
            settleH = horizontalAt(targetX, b[2]); settleV = verticalAt(targetY, b[3]);
            widget.resetSnapping();
            place(widget, settleX, settleY);
        }
        getAnchorGroup(anchor).getWidgets().addLast(widget);
        widget.setAnchor(anchor);
        requestDirectionRefresh();
    }

    private HudAnchor anchorAt(HudWidgetRenderer widget, float[] landing) {
        float rx = raw(landing[0]), ry = raw(landing[1]), rw = raw(landing[2]), rh = raw(landing[3]);
        for (HudAnchorGroup group : getAnchorGroups()) {
            if (group.getAnchor() == HudAnchor.NONE) continue;
            for (Object element : group.getWidgets())
                if (element instanceof HudWidgetRenderer other && other != widget && other.isEnabled() && intersects(other, rx, ry, rw, rh)) return group.getAnchor();
        }
        // The base editor's corner zones, tested against where the element lands.
        var landed = FloatBounds.fromPositionAndSize(rx, ry, rw, rh).expand(5.0F);
        for (HudAnchor anchor : HudAnchor.values())
            if (anchor != HudAnchor.NONE && anchor.getBounds().intersects(landed)) return anchor;
        return HudAnchor.NONE;
    }

    private static boolean intersects(HudWidgetRenderer other, float x, float y, float w, float h) {
        float[] size = other.getDimensions();
        float ox = other.getX(), oy = other.getY(), ow = Math.max(size[0], 4), oh = Math.max(size[1], 4);
        return x < ox + ow && x + w > ox && y < oy + oh && y + h > oy;
    }

    // ---- selection and moving several elements, or one line --------------------------------------

    /** Drops elements that left the screen from the selection, and shows the base editor's outline on picked ones. */
    private void syncSelection() {
        picked.removeIf(widget -> !widget.isEnabled());
        for (HudWidgetRenderer widget : widgets()) widget.setSelected(picked.contains(widget) || widget == dragged);
    }

    /** Selects one element's renderer, as when it is switched on or picked in the Elements panel. */
    private void select(HudWidgetModule module, boolean add) {
        if (!add) picked.clear();
        if (module.getRenderer() != null && module.isToggled()) picked.add(module.getRenderer());
    }

    /** Takes an element out of its corner stack where it is now, so it can be moved freely. */
    private void free(HudWidgetRenderer widget) {
        if (settling == widget) settling = null;
        HudAnchor from = widget.getAnchor();
        if (from != HudAnchor.NONE) {
            float x = widget.getX(), y = widget.getY();
            HudAnchorGroup group = getAnchorGroup(from);
            if (group != null) group.getWidgets().remove(widget);
            getAnchorGroup(HudAnchor.NONE).getWidgets().addLast(widget);
            widget.setAnchor(HudAnchor.NONE);
            widget.setX(x, true); widget.setY(y, true);
        }
        widget.resetSnapping();
    }

    private void boxTo(double x, double y) {
        boxX1 = (float) x; boxY1 = (float) y;
        float left = Math.min(boxX0, boxX1), right = Math.max(boxX0, boxX1), top = Math.min(boxY0, boxY1), bottom = Math.max(boxY0, boxY1);
        picked.clear(); picked.addAll(pickedBefore);
        for (HudWidgetRenderer widget : widgets()) {
            if (!widget.isEnabled()) continue;
            float[] b = bounds(widget);
            if (b[0] < right && b[0] + b[2] > left && b[1] < bottom && b[1] + b[3] > top) picked.add(widget);
        }
    }

    private void beginGroupDrag(HudWidgetRenderer lead, double lx, double ly) {
        settling = null;
        groupStart.clear();
        for (HudWidgetRenderer widget : picked) {
            free(widget);
            float[] b = bounds(widget);
            groupStart.put(widget, new float[]{b[0], b[1]});
        }
        groupLead = lead;
        pressX = (float) lx; pressY = (float) ly;
    }

    /** Moves the picked elements by the pointer's travel: the grabbed one snaps to the grid and the rest keep their places around it. */
    private void groupDragTo(double lx, double ly) {
        float moveX = (float) lx - pressX, moveY = (float) ly - pressY;
        float minX = -Float.MAX_VALUE, maxX = Float.MAX_VALUE, minY = -Float.MAX_VALUE, maxY = Float.MAX_VALUE;
        float ux0 = Float.MAX_VALUE, uy0 = Float.MAX_VALUE, ux1 = -Float.MAX_VALUE, uy1 = -Float.MAX_VALUE;
        for (var entry : groupStart.entrySet()) {
            float[] start = entry.getValue(), b = bounds(entry.getKey()), m = limits(b[2], b[3]);
            minX = Math.max(minX, m[0] - start[0]); maxX = Math.min(maxX, m[1] - start[0]);
            minY = Math.max(minY, m[2] - start[1]); maxY = Math.min(maxY, m[3] - start[1]);
            ux0 = Math.min(ux0, start[0]); uy0 = Math.min(uy0, start[1]);
            ux1 = Math.max(ux1, start[0] + b[2]); uy1 = Math.max(uy1, start[1] + b[3]);
        }
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;
        moveX = Math.clamp(moveX, minX, maxX); moveY = Math.clamp(moveY, minY, maxY);
        float[] lead = groupStart.get(groupLead);
        float dx = moveX, dy = moveY;
        if (GuiState.preferences().snap && lead != null) {
            dx = Math.round((lead[0] + moveX) / GRID) * GRID - lead[0];
            dy = Math.round((lead[1] + moveY) / GRID) * GRID - lead[1];
            dx = Math.clamp(dx, minX, maxX); dy = Math.clamp(dy, minY, maxY);
        }
        // The HUD gap applies to the group as a whole, against everything not being moved.
        int gap = GuiState.preferences().hudGap;
        if (gap > 0) {
            List<HudGap.Rect> others = new ArrayList<>();
            for (HudWidgetRenderer other : widgets()) {
                if (groupStart.containsKey(other) || !other.isEnabled()) continue;
                float[] o = bounds(other);
                others.add(new HudGap.Rect(Math.round(o[0]), Math.round(o[1]), Math.round(o[2]), Math.round(o[3])));
            }
            int w = Math.round(ux1 - ux0), h = Math.round(uy1 - uy0);
            int[] spot = HudGap.resolve(Math.round(ux0 + dx), Math.round(uy0 + dy), w, h, others, gap,
                    (int) Math.ceil(ux0 + minX), (int) Math.floor(ux0 + maxX), (int) Math.ceil(uy0 + minY), (int) Math.floor(uy0 + maxY));
            dx = spot[0] - ux0; dy = spot[1] - uy0;
        }
        for (var entry : groupStart.entrySet()) place(entry.getKey(), entry.getValue()[0] + dx, entry.getValue()[1] + dy);
    }

    private void endGroupDrag() {
        for (HudWidgetRenderer widget : groupStart.keySet()) {
            float[] b = bounds(widget);
            widget.setHorizontalAlignment(horizontalAt(b[0], b[2]));
            widget.setVerticalAlignment(verticalAt(b[1], b[3]));
        }
        groupStart.clear();
        groupLead = null;
        requestDirectionRefresh();
    }

    private void beginLineDrag(HudTextListRenderer owner, HudTextListRenderer.Line grabbed, double lx, double ly) {
        settling = null;
        free(owner);
        lineOwner = owner;
        line = grabbed.row();
        lineGrabX = (float) lx - logical(grabbed.x());
        lineGrabY = (float) ly - logical(grabbed.y());
    }

    private HudTextListRenderer.Line currentLine() {
        for (var drawn : lineOwner.lines()) if (drawn.row() == line) return drawn;
        return null;
    }

    /** Moves the grabbed line on the grid, keeping the HUD gap from other elements and from its own element's pieces. */
    private void lineDragTo(double lx, double ly) {
        var drawn = currentLine();
        float w = drawn != null ? logical(drawn.w()) : 24, h = drawn != null ? logical(drawn.h()) : 8;
        float[] m = limits(w, h);
        int tx = Math.round(Math.clamp((float) lx - lineGrabX, m[0], m[1])), ty = Math.round(Math.clamp((float) ly - lineGrabY, m[2], m[3]));
        if (GuiState.preferences().snap) { tx = Math.round(tx / (float) GRID) * GRID; ty = Math.round(ty / (float) GRID) * GRID; }
        int gap = GuiState.preferences().hudGap;
        if (gap > 0) {
            List<HudGap.Rect> others = new ArrayList<>();
            for (HudWidgetRenderer other : widgets()) {
                if (other == lineOwner || !other.isEnabled()) continue;
                float[] o = bounds(other);
                others.add(new HudGap.Rect(Math.round(o[0]), Math.round(o[1]), Math.round(o[2]), Math.round(o[3])));
            }
            for (var piece : lineOwner.lines()) {
                if (piece.row() == line || !piece.free()) continue;
                others.add(new HudGap.Rect(Math.round(logical(piece.x())), Math.round(logical(piece.y())), Math.round(logical(piece.w())), Math.round(logical(piece.h()))));
            }
            float[] stack = lineOwner.stackBounds();
            if (stack != null && drawn != null && drawn.free())
                others.add(new HudGap.Rect(Math.round(logical(stack[0])), Math.round(logical(stack[1])), Math.round(logical(stack[2])), Math.round(logical(stack[3]))));
            int[] spot = HudGap.resolve(tx, ty, Math.round(w), Math.round(h), others, gap,
                    (int) Math.ceil(m[0]), (int) Math.floor(m[1]), (int) Math.ceil(m[2]), (int) Math.floor(m[3]));
            tx = spot[0]; ty = spot[1];
        }
        lineOwner.moveLine(line, raw(tx), raw(ty));
        lineOwner.updateDirections();
    }

    /** Drops the line; over the rest of its element's stack, it goes back in. */
    private void endLineDrag() {
        var drawn = currentLine();
        float[] stack = lineOwner.stackBounds();
        if (drawn != null && drawn.free() && stack != null
                && drawn.x() < stack[0] + stack[2] && drawn.x() + drawn.w() > stack[0] && drawn.y() < stack[1] + stack[3] && drawn.y() + drawn.h() > stack[1]) {
            lineOwner.dockLine(line);
        }
        float[] b = bounds(lineOwner);
        lineOwner.setHorizontalAlignment(horizontalAt(b[0], b[2]));
        lineOwner.setVerticalAlignment(verticalAt(b[1], b[3]));
        lineOwner = null;
        line = null;
        requestDirectionRefresh();
    }

    /** Eases a dropped element into its snapped place, like a Modules window settling. */
    private void settle() {
        if (settling == null) return;
        if (settling.getAnchor() != HudAnchor.NONE || !settling.isEnabled()) { settling = null; return; }
        settleX = UiAnim.approach(settleX, targetX, 22);
        settleY = UiAnim.approach(settleY, targetY, 22);
        place(settling, settleX, settleY);
        if (settleX == targetX && settleY == targetY) {
            settling.setHorizontalAlignment(settleH);
            settling.setVerticalAlignment(settleV);
            settling = null;
        }
    }

    // ---- input --------------------------------------------------------------------------------

    private boolean overToolbar(double x, double y) { return UiDraw.over(x, y, barX, BAR_Y, barW, BAR_H); }
    private boolean overPanel(double x, double y) { return !panelHidden && panelVisible > .5f && UiDraw.over(x, y, panelShownX, panelShownY, PANEL_W, panelHeight()); }
    private boolean overUi(double x, double y) { return !busy() && (overToolbar(x, y) || overPanel(x, y)); }

    private void openSettings(Module module) {
        searchFocused = false;
        minecraft.setScreen(new ModuleSettingsScreen(this, module, null));
    }

    private void openHudSettings() {
        searchFocused = false;
        minecraft.setScreen(new SettingsScreen(this, SettingsScreen.hudSection(), null));
    }

    /**
     * Flips the whole HUD left to right: the corner stacks swap sides, and every free element moves
     * to the matching spot on the other side (keeping its distance from the opposite safe zone).
     */
    private void mirror() {
        if (settling != null) { settle(); if (settling != null) { place(settling, targetX, targetY); settling.setHorizontalAlignment(settleH); settling.setVerticalAlignment(settleV); settling = null; } }
        base().argus$swapAnchorContents(HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT);
        base().argus$swapAnchorContents(HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT);
        float left = zone(Side.LEFT), right = zone(Side.RIGHT);
        for (Object element : getAnchorGroup(HudAnchor.NONE).getWidgets()) {
            if (!(element instanceof HudWidgetRenderer widget)) continue;
            float[] b = bounds(widget);
            // Measured from the safe zones, so uneven left and right zones still mirror cleanly.
            float fromLeft = b[0] - left, mirrored = lw() - right - b[2] - fromLeft;
            float max = Math.max(left, lw() - right - b[2]);
            widget.setX(raw(Math.clamp(mirrored, left, max)), true);
            HudHorizontalAlignment h = widget.getHorizontalAlignment();
            widget.setHorizontalAlignment(h == HudHorizontalAlignment.LEFT ? HudHorizontalAlignment.RIGHT : h == HudHorizontalAlignment.RIGHT ? HudHorizontalAlignment.LEFT : h);
            widget.updateDirections();
        }
        requestDirectionRefresh();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (isClosing() || busy()) return false;
        double x = logical(event.x()), y = logical(event.y());
        int button = event.button();
        if (UIModule.INSTANCE != null) {
            var bind = UIModule.INSTANCE.getKeybind();
            if (bind.mouse() && !bind.isUnbound() && bind.code() >= 2 && button == bind.code()) { closeWithTransition(); return true; }
        }
        if (overUi(x, y)) {
            if (button == 0 || button == 1) handleUiClick(x, y, button);
            return true;
        }
        searchFocused = false;
        HudWidgetRenderer widget = widgetAt(event.x(), event.y());
        boolean shift = (event.modifiers() & 1) != 0;
        if (widget != null && button == 1) { openSettings(widget.getWidgetModule()); return true; }
        if (widget != null && button == 0 && !(widget instanceof ResizableHudWidgetRenderer resizable && resizable.isResizeHandleHovered(event.x(), event.y()))) {
            if (shift) {
                // Shift-click adds an element to the selection, or takes it out.
                if (!picked.remove(widget)) picked.add(widget);
                return true;
            }
            if (picked.contains(widget) && picked.size() > 1) { beginGroupDrag(widget, x, y); return true; }
            if (!picked.contains(widget)) {
                picked.clear();
                // A multi-line element that isn't selected moves one line at a time.
                if (widget instanceof HudTextListRenderer list && list.isSplittable()) {
                    var grabbed = list.lineAt(event.x(), event.y());
                    if (grabbed != null) { beginLineDrag(list, grabbed, x, y); return true; }
                }
            }
            beginDrag(widget, x, y);
            return true;
        }
        if (widget == null && button == 0) {
            Side edge = edgeAt(x, y);
            if (edge != null) { edgeDrag = edge; dragEdge(x, y); return true; }
            // Empty space: draw a selection box (Shift adds to the current selection).
            if (!shift) picked.clear();
            pickedBefore = Set.copyOf(picked);
            boxing = true;
            boxX0 = boxX1 = (float) x; boxY0 = boxY1 = (float) y;
            return true;
        }
        if (widget == null && button == 1) {
            // Right-click an edge to clear that safe zone.
            Side edge = edgeAt(x, y);
            if (edge != null) { HudSafeZones.set(edge, 0); return true; }
        }
        // Resize handles stay with the base editor.
        return super.mouseClicked(event, doubled);
    }

    private void handleUiClick(double mx, double my, int button) {
        if (overToolbar(mx, my)) {
            searchFocused = false;
            if (button != 0) return;
            for (int i = 0; i < BUTTONS.length; i++) if (UiDraw.over(mx, my, buttonX[i], BAR_Y, buttonW[i], BAR_H)) {
                switch (i) {
                    case B_HUD -> hud().toggle();
                    case B_ELEMENTS -> setPanelHidden(!panelHidden);
                    case B_MIRROR -> mirror();
                    case B_SETTINGS -> openHudSettings();
                    default -> closeWithTransition();
                }
            }
            return;
        }
        float x = panelShownX, y = panelShownY;
        if (UiDraw.over(mx, my, x, y, PANEL_W, PANEL_HEAD)) {
            searchFocused = false;
            if (button != 0) return;
            if (UiDraw.over(mx, my, x + PANEL_W - 20, y + 7, 14, 14)) { setPanelHidden(true); return; }
            draggingPanel = true; panelGrabX = (float) mx - panelX; panelGrabY = (float) my - panelY;
            return;
        }
        float sx = x + 10, sy = y + PANEL_HEAD + 7, sw = PANEL_W - 20, sh = 18;
        if (UiDraw.over(mx, my, sx, sy, sw, sh)) {
            if (!query.isEmpty() && UiDraw.over(mx, my, sx + sw - 16, sy + 3, 12, 12)) { query = ""; scrollTarget = 0; }
            searchFocused = true;
            return;
        }
        searchFocused = false;
        float fy = sy + sh + 6, fw = (PANEL_W - 20) / 3f;
        for (int i = 0; i < FILTERS.length; i++)
            if (UiDraw.over(mx, my, x + 10 + fw * i, fy, fw, 15)) { filter = i; scrollTarget = 0; return; }
        if (my < listTop || my >= listBottom) return;
        for (int i = 0; i < shown.size(); i++) {
            float ry = listTop + i * (ROW_H + ROW_GAP) - scroll, rx = x + 8, rw = PANEL_W - 16;
            if (!UiDraw.over(mx, my, rx, ry, rw, ROW_H)) continue;
            HudWidgetModule module = shown.get(i);
            float gx = rx + rw - 24 - 15, cy = ry + ROW_H / 2, tx = rx + rw - 24, ty = cy - 5;
            if (button == 1 || UiDraw.over(mx, my, gx - 2, cy - 6, 12, 12)) openSettings(module);
            else if (UiDraw.over(mx, my, tx - 2, ty - 3, 22, 16)) {
                // The switch adds or removes it; a newly added element starts selected, so its lines move together.
                module.toggle();
                if (module.isToggled()) select(module, false); else picked.remove(module.getRenderer());
            } else if (!module.isToggled()) { module.toggle(); select(module, false); }
            else select(module, shiftDown());
            return;
        }
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double x = logical(event.x()), y = logical(event.y());
        if (dragged != null) { dragTo(x, y); return true; }
        if (edgeDrag != null) { dragEdge(x, y); return true; }
        if (boxing) { boxTo(x, y); return true; }
        if (!groupStart.isEmpty()) { groupDragTo(x, y); return true; }
        if (lineOwner != null) { lineDragTo(x, y); return true; }
        if (draggingPanel) {
            panelShownX = Math.clamp((float) x - panelGrabX, 10, panelMaxX());
            panelShownY = Math.clamp((float) y - panelGrabY, MIN_Y, panelMaxY());
            int tx = Math.round(panelShownX), ty = Math.round(panelShownY);
            if (GuiState.preferences().snap) { tx = Math.round(tx / (float) GRID) * GRID; ty = Math.round(ty / (float) GRID) * GRID; }
            panelX = Math.clamp(tx, 10, panelMaxX()); panelY = Math.clamp(ty, (int) MIN_Y, panelMaxY());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragged != null) { endDrag(); return true; }
        if (edgeDrag != null) { edgeDrag = null; requestDirectionRefresh(); return true; }
        if (boxing) { boxing = false; return true; }
        if (!groupStart.isEmpty()) { endGroupDrag(); return true; }
        if (lineOwner != null) { endLineDrag(); return true; }
        if (draggingPanel) { draggingPanel = false; rememberPanel(); GuiState.save(); return true; }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        if (overPanel(logical(rawX), logical(rawY))) { scrollTarget -= (float) vertical * 24; return true; }
        return super.mouseScrolled(rawX, rawY, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean control = (event.modifiers() & 2) != 0;
        if (busy()) return true;
        if (control && key == 70) { if (panelHidden) setPanelHidden(false); searchFocused = true; return true; }
        if (searchFocused) {
            switch (key) {
                case 256 -> searchFocused = false;
                case 259 -> { if (!query.isEmpty()) query = control ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)); scrollTarget = 0; }
                case 257, 335 -> {
                    refreshShown();
                    if (!shown.isEmpty()) { var first = shown.getFirst(); first.toggle(); if (first.isToggled()) select(first, false); }
                }
                default -> { }
            }
            return true;
        }
        // The menu key closes the editor, like every other Argus page.
        if (UIModule.INSTANCE != null) {
            var bind = UIModule.INSTANCE.getKeybind();
            if (!bind.mouse() && !bind.isUnbound() && key == bind.code()) { closeWithTransition(); return true; }
        }
        if (overUi(mouseX, mouseY) && (key == 259 || key == 261 || key >= 262 && key <= 265)) return true;
        // I mirrors like the toolbar button (the base editor's version only swapped the corner stacks).
        if (key == 73 && !control) { mirror(); return true; }
        // Esc clears the selection before it closes the editor.
        if (key == 256 && !picked.isEmpty()) { picked.clear(); return true; }
        // Ctrl+A selects everything on screen.
        if (control && key == 65) { picked.clear(); for (HudWidgetRenderer widget : widgets()) if (widget.isEnabled()) picked.add(widget); return true; }
        // Del removes the selection.
        if ((key == 259 || key == 261) && !picked.isEmpty()) {
            for (HudWidgetRenderer widget : List.copyOf(picked)) widget.getWidgetModule().setToggled(false);
            picked.clear();
            return true;
        }
        // R puts a moved line back, or (over the rest of an element) restacks all its lines.
        if (key == 82 && !control) {
            HudWidgetRenderer widget = widgetAt(raw(mouseX), raw(mouseY));
            if (widget instanceof HudTextListRenderer list && list.isSplit()) {
                var under = list.lineAt(raw(mouseX), raw(mouseY));
                if (under != null && under.free()) list.dockLine(under.row()); else list.restack();
                requestDirectionRefresh();
            }
            return true;
        }
        // Arrow keys nudge by one grid step (one pixel with Shift), matching the snap grid.
        if (key >= 262 && key <= 265 && !picked.isEmpty()) {
            boolean fine = (event.modifiers() & 1) != 0 || !GuiState.preferences().snap;
            float step = fine ? logical(1) : GRID;
            float dx = key == 262 ? step : key == 263 ? -step : 0, dy = key == 264 ? step : key == 265 ? -step : 0;
            for (HudWidgetRenderer widget : picked) {
                free(widget);
                float[] b = bounds(widget), m = limits(b[2], b[3]);
                float nx = Math.clamp(b[0] + dx, m[0], m[1]), ny = Math.clamp(b[1] + dy, m[2], m[3]);
                place(widget, nx, ny);
                widget.setHorizontalAlignment(horizontalAt(nx, b[2]));
                widget.setVerticalAlignment(verticalAt(ny, b[3]));
            }
            return true;
        }
        if (key >= 262 && key <= 265) {
            HudWidgetRenderer widget = widgetAt(raw(mouseX), raw(mouseY));
            if (widget == null) return true;
            boolean fine = (event.modifiers() & 1) != 0 || !GuiState.preferences().snap;
            float step = fine ? logical(1) : GRID;
            float[] b = bounds(widget);
            float nx = b[0] + (key == 262 ? step : key == 263 ? -step : 0), ny = b[1] + (key == 264 ? step : key == 265 ? -step : 0);
            if (!fine) { nx = Math.round(nx / GRID) * GRID; ny = Math.round(ny / GRID) * GRID; }
            if (widget.getAnchor() != HudAnchor.NONE) { getAnchorGroup(widget.getAnchor()).getWidgets().remove(widget); getAnchorGroup(HudAnchor.NONE).getWidgets().addLast(widget); widget.setAnchor(HudAnchor.NONE); }
            float[] m = limits(b[2], b[3]);
            nx = Math.clamp(nx, m[0], m[1]); ny = Math.clamp(ny, m[2], m[3]);
            if (settling == widget) settling = null;
            widget.resetSnapping();
            place(widget, nx, ny);
            widget.setHorizontalAlignment(horizontalAt(nx, b[2]));
            widget.setVerticalAlignment(verticalAt(ny, b[3]));
            return true;
        }
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
        if (dragged != null) endDrag();
        if (!groupStart.isEmpty()) endGroupDrag();
        if (lineOwner != null) endLineDrag();
        boxing = false;
        picked.clear();
        for (HudWidgetRenderer widget : widgets()) widget.setSelected(false);
        edgeDrag = null;
        if (draggingPanel) { draggingPanel = false; rememberPanel(); }
        searchFocused = false;
        GuiState.save();
        super.removed();
    }
}

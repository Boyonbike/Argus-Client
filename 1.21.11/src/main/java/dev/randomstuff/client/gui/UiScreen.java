package dev.randomstuff.client.gui;

import dev.randomstuff.client.ClientContext;
import dev.randomstuff.client.modules.client.UIModule;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Shared navigation, open/close motion and tooltips for the module menu, friends and theme pages. */
public abstract class UiScreen extends Screen {
    private static final String[] TABS = {"Modules", "Friends", "HUD", "Theme", "Classic"};
    private static final float TAB_Y = 8, TAB_HEIGHT = 21;
    private static final long OPEN_NANOS = 220_000_000L, CLOSE_NANOS = 130_000_000L;
    /** When the last menu page closed; a page opened right after it skips the backdrop fade. */
    private static long lastRemoved;
    private static float indicatorX = Float.NaN, indicatorWidth;

    protected final Screen parent;
    private final String activeTab;
    private GuiViewport viewport = new GuiViewport(320, 240, 1);
    private long openedAt, closingAt;
    private boolean fromMenu;
    private Screen closeTarget;
    private float[] tabX = new float[0], tabWidth = new float[0];
    private float barX, barWidth;
    private Object tooltipOwner, shownTooltipOwner;
    private String[] tooltipLines;
    private long tooltipSince;
    private int mouseX, mouseY;

    protected UiScreen(String title, Screen parent, String activeTab) { super(Component.literal(title)); this.parent = parent; this.activeTab = activeTab; }
    protected Screen rootParent() { return parent instanceof UiScreen screen ? screen.rootParent() : parent; }

    @Override protected final void init() {
        var window = minecraft.getWindow();
        viewport = GuiViewport.create(window.getWidth(), window.getHeight(), window.getGuiScale(), GuiState.preferences().uiScale);
        UiDraw.density = (float) (viewport.renderScale() * window.getGuiScale());
        width = viewport.width(); height = viewport.height();
        if (openedAt == 0) { openedAt = System.nanoTime(); fromMenu = openedAt - lastRemoved < 250_000_000L; }
        tabX = new float[TABS.length]; tabWidth = new float[TABS.length];
        float total = 0;
        for (int i = 0; i < TABS.length; i++) { tabWidth[i] = Math.max(34, UiDraw.width(TABS[i]) + 20); total += tabWidth[i]; }
        barWidth = total + 6; barX = Math.max(5, (width - barWidth) / 2);
        float x = barX + 3;
        for (int i = 0; i < TABS.length; i++) { tabX[i] = x; x += tabWidth[i]; }
        buildContent();
    }

    protected abstract void buildContent();
    protected abstract void renderUi(GuiGraphics g, int mx, int my, float delta);

    /** Eased 0…1 progress of this page's entrance (and exit while closing). */
    protected float appear() {
        float open = UiAnim.easeOutCubic((System.nanoTime() - openedAt) / (float) OPEN_NANOS);
        if (closingAt != 0) open *= 1 - Math.clamp((System.nanoTime() - closingAt) / (float) CLOSE_NANOS, 0f, 1f);
        return open;
    }
    /** Staggered entrance for the n-th element of a page. */
    protected float appear(int index) {
        float t = ((System.nanoTime() - openedAt) / 1e9f - index * .028f) / .26f;
        float value = UiAnim.easeOutCubic(t);
        if (closingAt != 0) value *= 1 - Math.clamp((System.nanoTime() - closingAt) / (float) CLOSE_NANOS, 0f, 1f);
        return value;
    }

    @Override public final void render(GuiGraphics g, int rawX, int rawY, float delta) {
        UiAnim.frame();
        int mx = (int) viewport.toLogical(rawX), my = (int) viewport.toLogical(rawY);
        mouseX = mx; mouseY = my;
        float appear = appear();
        boolean keepBackdrop = closingAt != 0 ? closeTarget instanceof UiScreen : fromMenu;
        g.pose().pushMatrix();
        g.pose().scale(viewport.renderScale(), viewport.renderScale());
        try {
            UiRender.opacity = keepBackdrop ? 1 : appear;
            backdrop(g);
            UiRender.opacity = appear;
            tooltipOwner = null;
            g.pose().pushMatrix();
            g.pose().translate(0, (1 - appear) * 6);
            try {
                renderUi(g, mx, my, delta);
                super.render(g, mx, my, delta);
            } finally { g.pose().popMatrix(); }
            UiRender.opacity = keepBackdrop ? 1 : appear;
            tabs(g, mx, my);
            footer(g);
            UiRender.opacity = appear;
            drawTooltip(g);
        } finally {
            UiRender.opacity = 1;
            g.pose().popMatrix();
        }
        if (closingAt != 0 && System.nanoTime() - closingAt >= CLOSE_NANOS) { closingAt = 0; minecraft.setScreen(closeTarget); }
    }

    private void tabs(GuiGraphics g, int mx, int my) {
        UiTheme t = GuiState.theme();
        UiDraw.shadow(g, barX, TAB_Y + 1, barWidth, TAB_HEIGHT + 6, 9, 8, UiDraw.alpha(0xff000000, .25f));
        UiDraw.rounded(g, barX, TAB_Y, barWidth, TAB_HEIGHT + 6, 9, t.mantle());
        UiDraw.outline(g, barX, TAB_Y, barWidth, TAB_HEIGHT + 6, 9, .6f, UiDraw.alpha(t.overlay(), .5f));
        int active = java.util.Arrays.asList(TABS).indexOf(activeTab);
        if (active >= 0) {
            if (Float.isNaN(indicatorX)) { indicatorX = tabX[active]; indicatorWidth = tabWidth[active]; }
            indicatorX = UiAnim.approach(indicatorX, tabX[active], 16);
            indicatorWidth = UiAnim.approach(indicatorWidth, tabWidth[active], 16);
            UiDraw.rounded(g, indicatorX, TAB_Y + 3, indicatorWidth, TAB_HEIGHT, 7, UiDraw.alpha(t.accent(), .2f));
        }
        for (int i = 0; i < TABS.length; i++) {
            boolean hover = closingAt == 0 && UiDraw.over(mx, my, tabX[i], TAB_Y + 3, tabWidth[i], TAB_HEIGHT);
            float h = UiAnim.smooth("tab." + i, hover ? 1 : 0, 18);
            if (h > 0 && i != active) UiDraw.rounded(g, tabX[i], TAB_Y + 3, tabWidth[i], TAB_HEIGHT, 7, UiDraw.alpha(t.surface(), h * .7f));
            int colour = i == active ? t.accent() : UiDraw.mix(t.muted(), t.text(), h);
            UiDraw.center(g, TABS[i], tabX[i], TAB_Y + 3 + TAB_HEIGHT / 2, tabWidth[i], colour);
        }
    }

    private void footer(GuiGraphics g) {
        UiTheme t = GuiState.theme();
        boolean error = !GuiState.saveError.isEmpty();
        UiDraw.circle(g, 12, height - 10, 2, error ? t.red() : t.accent());
        UiDraw.text(g, error ? GuiState.saveError : "Random Stuff Client", 18, height - 10, UiDraw.SMALL, error ? t.red() : t.muted());
    }

    /** Requests a tooltip for this frame; it fades in after the pointer rests on {@code owner}. */
    protected void tooltip(Object owner, String... lines) { tooltipOwner = owner; tooltipLines = lines; }

    private void drawTooltip(GuiGraphics g) {
        if (tooltipOwner == null || !tooltipOwner.equals(shownTooltipOwner)) { shownTooltipOwner = tooltipOwner; tooltipSince = System.nanoTime(); }
        if (tooltipOwner == null || tooltipLines == null) return;
        float shown = UiAnim.easeOutCubic(((System.nanoTime() - tooltipSince) / 1e9f - .35f) / .14f);
        if (shown <= 0) return;
        UiTheme t = GuiState.theme();
        float w = 0;
        for (int i = 0; i < tooltipLines.length; i++) w = Math.max(w, UiDraw.width(tooltipLines[i], i == 0 ? UiDraw.TEXT : UiDraw.SMALL));
        w = Math.min(width - 12, w + 16);
        float lineH = 11, h = 8 + tooltipLines.length * lineH;
        float x = Math.clamp(mouseX + 12, 6, Math.max(6, width - w - 6)), y = Math.min(mouseY + 14, height - h - 6) + (1 - shown) * 3;
        float before = UiRender.opacity;
        UiRender.opacity = before * shown;
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

    protected boolean closing() { return closingAt != 0; }

    /** Fades the page out, then shows {@code target}. Moving to another menu page keeps the backdrop. */
    public void beginClose(Screen target) {
        if (closingAt != 0) return;
        closeTarget = target;
        closingAt = System.nanoTime();
        setFocused(null);
    }

    private MouseButtonEvent logical(MouseButtonEvent event) { return new MouseButtonEvent(viewport.toLogical(event.x()), viewport.toLogical(event.y()), event.buttonInfo()); }

    @Override public final boolean mouseClicked(MouseButtonEvent raw, boolean doubled) {
        if (closingAt != 0) return true;
        MouseButtonEvent event = logical(raw);
        if (event.button() == 0) for (int i = 0; i < TABS.length; i++)
            if (UiDraw.over(event.x(), event.y(), tabX[i], TAB_Y + 3, tabWidth[i], TAB_HEIGHT)) { if (!TABS[i].equals(activeTab) || this.getClass() != tabScreen(TABS[i])) navigate(TABS[i]); return true; }
        return mouseClickedUi(event, doubled);
    }
    private static Class<?> tabScreen(String tab) {
        return switch (tab) { case "Modules" -> ModulesScreen.class; case "Friends" -> FriendsScreen.class; case "Theme" -> AppearanceScreen.class; default -> Object.class; };
    }
    protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) { return super.mouseClicked(event, doubled); }
    @Override public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return closingAt == 0 && mouseDraggedUi(logical(event), viewport.toLogical(dx), viewport.toLogical(dy)); }
    protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) { return super.mouseDragged(event, dx, dy); }
    @Override public final boolean mouseReleased(MouseButtonEvent event) { return mouseReleasedUi(logical(event)); }
    protected boolean mouseReleasedUi(MouseButtonEvent event) { return super.mouseReleased(event); }
    @Override public final boolean mouseScrolled(double mx, double my, double horizontal, double vertical) { return closingAt == 0 && mouseScrolledUi(viewport.toLogical(mx), viewport.toLogical(my), horizontal, vertical); }
    protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) { return super.mouseScrolled(mx, my, horizontal, vertical); }
    @Override public final void mouseMoved(double mx, double my) { super.mouseMoved(viewport.toLogical(mx), viewport.toLogical(my)); }

    protected void navigate(String label) {
        Screen root = rootParent();
        switch (label) {
            case "Modules" -> minecraft.setScreen(new ModulesScreen(root));
            case "Friends" -> minecraft.setScreen(new FriendsScreen(this instanceof FriendsScreen ? parent : this));
            case "HUD" -> ClientContext.getHudEditor().open();
            case "Theme" -> minecraft.setScreen(new AppearanceScreen(this instanceof AppearanceScreen ? parent : this));
            case "Classic" -> ClientContext.getClickGui().open();
        }
    }

    protected void backdrop(GuiGraphics g) {
        boolean dark = GuiState.preferences().mode == GuiPreferences.Mode.DARK;
        UiDraw.gradient(g, 0, 0, width, height, 0, dark ? 0x7a07070d : 0x50000000, dark ? 0xa009090f : 0x70000000);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (closingAt != 0) return true;
        if (UIModule.INSTANCE != null) {
            var bind = UIModule.INSTANCE.getKeybind();
            if (!bind.mouse() && !bind.isUnbound() && event.key() == bind.code() && (!(getFocused() instanceof EditBox) || bind.code() >= 340)) {
                beginClose(rootParent()); return true;
            }
        }
        if ((event.modifiers() & 2) != 0 && event.key() == 70) {
            if (this instanceof ModulesScreen screen) screen.focusSearch(); else minecraft.setScreen(new ModulesScreen(rootParent(), true));
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public void onClose() { beginClose(parent); }
    @Override public void removed() { lastRemoved = System.nanoTime(); GuiState.save(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}

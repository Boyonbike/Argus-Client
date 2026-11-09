package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.client.UIModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Shared navigation, open/close motion and tooltips for the module menu, friends and theme pages. */
public abstract class UiScreen extends Screen {
    /** Pages that can sit in the bar, stored in preferences as {@code tab:<name>}. */
    static final String[] PAGE_TABS = {"Modules", "HUD"};
    static final String PAGE_PREFIX = "tab:";
    /** Bar geometry: the collapsed bar is a circle of {@code BAR} units holding the player's face. */
    private static final float BAR_Y = 7, BAR = 28, FACE = 22, TAB_HEIGHT = 21, GEAR = 13;
    private static final long OPEN_NANOS = 220_000_000L, CLOSE_NANOS = 130_000_000L;
    private static float indicatorX = Float.NaN, indicatorWidth;
    /** Bar reveal state; kept for the session so moving between pages doesn't replay it. */
    private static final NavbarAnimation bar = new NavbarAnimation();

    protected final Screen parent;
    private final String activeTab;
    private GuiViewport viewport = new GuiViewport(320, 240, 1);
    private long openedAt, closingAt;
    private final boolean fromMenu;
    private Screen closeTarget;
    /** Per tab: its saved entry, display name, icon, and the Settings section it opens (null for pages). */
    private String[] tabEntries = new String[0], tabLabels = new String[0], tabIcons = new String[0], tabSections = new String[0];
    /** Whether tabs show icons (chosen, or because names don't fit the screen). */
    private boolean iconTabs;
    /** Tab being pressed or dragged in the bar, where it was grabbed, and whether it has moved. */
    private int pressedTab = -1;
    private float grabOffset, pressX;
    private boolean draggingTab;
    private float[] tabOffset = new float[0], tabWidth = new float[0];
    private float tabsWidth;
    private Object tooltipOwner, shownTooltipOwner;
    private String[] tooltipLines;
    private long tooltipSince;
    private int mouseX, mouseY;

    protected UiScreen(String title, Screen parent, String activeTab) { super(Component.literal(title)); this.parent = parent; this.activeTab = activeTab; this.fromMenu = Minecraft.getInstance().screen instanceof UiScreen; }
    protected Screen rootParent() { return parent instanceof UiScreen screen ? screen.rootParent() : parent; }

    @Override protected final void init() {
        var window = minecraft.getWindow();
        viewport = GuiViewport.create(window.getWidth(), window.getHeight(), window.getGuiScale(), GuiState.preferences().uiScale);
        UiDraw.density = (float) (viewport.renderScale() * window.getGuiScale());
        width = viewport.width(); height = viewport.height();
        if (openedAt == 0) {
            openedAt = System.nanoTime();
            // A fresh open restores the bar as it was left: closed stays closed, open replays its reveal.
            if (!fromMenu) bar.restore(GuiState.preferences().barOpen);
        }
        layoutTabs();
        buildContent();
    }

    /**
     * The tabs between the face and the settings circle, chosen with the Top bar chips in
     * Settings → Appearance. Each is a page (Modules, HUD editor) or a Settings section.
     * Tabs show names or icons; names fall back to icons, and icons shrink, to fit the screen.
     */
    final void layoutTabs() {
        var entries = new java.util.ArrayList<String>();
        for (String entry : GuiState.preferences().barTabs) {
            boolean page = entry.startsWith(PAGE_PREFIX) && java.util.Arrays.asList(PAGE_TABS).contains(entry.substring(PAGE_PREFIX.length()));
            if (page || SettingsScreen.isSection(entry)) entries.add(entry);
        }
        int n = entries.size();
        tabEntries = entries.toArray(String[]::new);
        tabLabels = new String[n]; tabIcons = new String[n]; tabSections = new String[n];
        tabOffset = new float[n]; tabWidth = new float[n];
        float namesWidth = 0;
        for (int i = 0; i < n; i++) {
            tabLabels[i] = SettingsScreen.chipLabel(tabEntries[i]);
            tabIcons[i] = SettingsScreen.tabIcon(tabEntries[i]);
            tabSections[i] = tabEntries[i].startsWith(PAGE_PREFIX) ? null : tabEntries[i];
            namesWidth += Math.max(34, UiDraw.width(tabLabels[i]) + 20);
        }
        float room = width - 20 - (BAR * 2 + 8);
        iconTabs = GuiState.preferences().tabStyle == GuiPreferences.TabStyle.ICONS || namesWidth > room;
        float iconWidth = n == 0 ? 0 : Math.max(18, Math.min(TAB_HEIGHT + 8, room / n));
        tabsWidth = 0;
        for (int i = 0; i < n; i++) {
            tabWidth[i] = iconTabs ? iconWidth : Math.max(34, UiDraw.width(tabLabels[i]) + 20);
            tabOffset[i] = tabsWidth; tabsWidth += tabWidth[i];
        }
    }

    /** Moves a bar tab to another slot while it is being dragged. */
    private void moveTab(int from, int to) {
        var bar = GuiState.preferences().barTabs;
        String moving = tabEntries[from], target = tabEntries[to];
        bar.remove(moving);
        int at = bar.indexOf(target);
        bar.add(to > from ? at + 1 : at, moving);
        layoutTabs();
    }

    private void removeTab(int index) {
        GuiState.preferences().barTabs.remove(tabEntries[index]);
        GuiState.save();
        layoutTabs();
    }

    /** Opens the navigation bar, e.g. so a newly added tab is visible. */
    static void showBar() { setBarOpen(true); }

    private static void setBarOpen(boolean open) {
        bar.setOpen(open);
        GuiState.preferences().barOpen = open;
        GuiState.save();
    }
    private static boolean barOpen() { return bar.open(); }
    private static float barProgress() { return bar.progress(); }

    /** The Settings section this page is showing, so a bar tab for it can light up. */
    protected String activeSection() { return null; }

    private int activeTabIndex() {
        String section = activeSection();
        for (int i = 0; i < tabLabels.length; i++) {
            if (section != null && section.equals(tabSections[i])) return i;
            if (section == null && tabSections[i] == null && tabEntries[i].equals(PAGE_PREFIX + activeTab)) return i;
        }
        return -1;
    }

    /** The settings circle lights up on the Settings page unless a bar tab already shows its section. */
    private boolean settingsActive() { return this instanceof SettingsScreen && activeTabIndex() < 0; }

    /** Opens a Settings section; the Settings page overrides this to switch in place. */
    protected void openSection(String section) {
        minecraft.setScreen(new SettingsScreen(this instanceof SettingsScreen ? parent : this, section, null));
    }

    /** A modal keeps the parent visible and suppresses navigation shortcuts and chrome. */
    protected boolean modal() { return false; }

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
        if (modal() && parent != null) parent.render(g, -1000, -1000, delta);
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
            if (!modal()) { tabs(g, mx, my); footer(g); }
            UiRender.opacity = appear;
            ColorPicker.render(g, mx, my, GuiState.theme());
            drawTooltip(g);
        } finally {
            UiRender.opacity = 1;
            g.pose().popMatrix();
        }
        if (closingAt != 0 && System.nanoTime() - closingAt >= CLOSE_NANOS) { closingAt = 0; minecraft.setScreen(closeTarget); }
    }

    // ---- navigation bar: a face that springs open into a pill of tabs ---------------------

    private float fullBarWidth() { return BAR + 4 + tabsWidth + 4 + BAR; }
    /** Eased width progress; overshoots slightly while opening for a springy feel. */
    private float barShape() { return barOpen() ? UiAnim.easeOutBack(barProgress()) : UiAnim.easeOutCubic(barProgress()); }
    private float barWidthNow() { return UiAnim.lerp(BAR, fullBarWidth(), barShape()); }
    private float barLeft() { return width / 2f - barWidthNow() / 2; }
    private float tabX(int i) { return barLeft() + BAR + 4 + tabOffset[i]; }
    /** Eased position used for drawing, so reordered tabs slide into place. */
    private float shownTabX(int i) {
        if (draggingTab && i == pressedTab) return Math.clamp(mouseX - grabOffset, barLeft() + BAR + 4, barLeft() + BAR + 4 + tabsWidth - tabWidth[i]);
        return barLeft() + BAR + 4 + UiAnim.smooth("tabx." + tabEntries[i], tabOffset[i], 22);
    }
    private float gearLeft() { return barLeft() + barWidthNow() - BAR; }
    private boolean overCircle(double mx, double my, float left) { return Math.hypot(mx - (left + BAR / 2), my - (BAR_Y + BAR / 2)) <= BAR / 2 + 1; }
    private boolean overFace(double mx, double my) { return overCircle(mx, my, barLeft()); }
    private boolean overGear(double mx, double my) { return barProgress() > .6f && overCircle(mx, my, gearLeft()); }
    /** Staggered fade for each tab; the stagger shrinks with more tabs so the last still reaches full opacity. */
    private float tabReveal(int i) {
        float step = Math.min(.09f, .3f / Math.max(1, tabLabels.length - 1));
        return Math.clamp((barProgress() - .3f - i * step) / .4f, 0f, 1f);
    }

    /** An inner circle with an accent ring, shared by the face and the settings button. */
    private void circleRing(GuiGraphics g, float left, float strength, UiTheme t) {
        float fx = left + (BAR - FACE) / 2, fy = BAR_Y + (BAR - FACE) / 2;
        if (strength > 0) UiDraw.outline(g, fx - 1.6f, fy - 1.6f, FACE + 3.2f, FACE + 3.2f, FACE / 2 + 1.6f, 1.1f, UiDraw.alpha(t.accent(), strength));
    }

    private void tabs(GuiGraphics g, int mx, int my) {
        UiTheme t = GuiState.theme();
        bar.advance(UiAnim.delta());
        float w = barWidthNow(), x = barLeft(), y = BAR_Y;
        boolean faceHover = closingAt == 0 && overFace(mx, my);
        float fh = UiAnim.smooth("bar.face", faceHover ? 1 : 0, 18);
        UiDraw.shadow(g, x, y + 1, w, BAR, BAR / 2, 8 + fh * 3, UiDraw.alpha(0xff000000, .28f));
        UiDraw.rounded(g, x, y, w, BAR, BAR / 2, t.mantle());
        UiDraw.outline(g, x, y, w, BAR, BAR / 2, .6f, UiDraw.mix(UiDraw.alpha(t.overlay(), .5f), t.accent(), fh * .8f));
        // Face on the left, with an accent ring that grows on hover and while the bar is open.
        circleRing(g, x, Math.max(fh, barProgress() * .6f), t);
        UiDraw.face(g, x + (BAR - FACE) / 2, y + (BAR - FACE) / 2, FACE);
        if (faceHover) tooltip("bar.face", barOpen() ? "Hide navigation" : "Show navigation");
        if (barProgress() < .01f) return;
        float before = UiRender.opacity;
        // Settings circle on the right, mirroring the face; it fades in as the bar opens.
        float gearReveal = Math.clamp((barProgress() - .45f) / .4f, 0f, 1f);
        if (gearReveal > 0) {
            UiRender.opacity = before * gearReveal;
            float gx = gearLeft();
            boolean gearHover = closingAt == 0 && overGear(mx, my);
            float gh = UiAnim.smooth("bar.gear", gearHover ? 1 : 0, 18), ga = UiAnim.smooth("bar.gear.on", settingsActive() ? 1 : 0, 16);
            UiDraw.circle(g, gx + BAR / 2, y + BAR / 2, FACE / 2, UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), gh * .7f), UiDraw.alpha(t.accent(), .25f), ga));
            circleRing(g, gx, Math.max(gh, ga * .9f), t);
            int colour = UiDraw.mix(UiDraw.mix(t.muted(), t.text(), gh), t.accent(), ga);
            UiDraw.icon(g, "client", gx + (BAR - GEAR) / 2, y + (BAR - GEAR) / 2, GEAR, colour);
            if (gearHover) tooltip("bar.gear", "Settings", "Appearance, presets, client options and this bar's tabs");
            UiRender.opacity = before;
        }
        if (tabLabels.length == 0) return;
        int active = activeTabIndex();
        g.enableScissor((int) (x + BAR), (int) y, (int) Math.ceil(x + w - BAR), (int) (y + BAR));
        if (active >= 0) {
            float target = shownTabX(active);
            if (Float.isNaN(indicatorX)) { indicatorX = target; indicatorWidth = tabWidth[active]; }
            indicatorX = barProgress() < .99f || (draggingTab && active == pressedTab) ? target : UiAnim.approach(indicatorX, target, 16);
            indicatorWidth = UiAnim.approach(indicatorWidth, tabWidth[active], 16);
            UiRender.opacity = before * tabReveal(active);
            UiDraw.rounded(g, indicatorX, y + (BAR - TAB_HEIGHT) / 2, indicatorWidth, TAB_HEIGHT, TAB_HEIGHT / 2, UiDraw.alpha(t.accent(), .2f));
        }
        for (int i = 0; i < tabLabels.length; i++) {
            float reveal = tabReveal(i);
            if (reveal <= 0) continue;
            UiRender.opacity = before * reveal;
            float tx = shownTabX(i) - (1 - reveal) * 6, ty = y + (BAR - TAB_HEIGHT) / 2;
            boolean dragged = draggingTab && i == pressedTab;
            boolean hover = closingAt == 0 && barProgress() > .6f && (dragged || !draggingTab && UiDraw.over(mx, my, tx, ty, tabWidth[i], TAB_HEIGHT));
            float h = UiAnim.smooth("tab." + tabEntries[i], hover ? 1 : 0, 18);
            if (dragged) UiDraw.shadow(g, tx, ty + 1, tabWidth[i], TAB_HEIGHT, TAB_HEIGHT / 2, 5, UiDraw.alpha(0xff000000, .35f));
            if (h > 0 && i != active) UiDraw.rounded(g, tx, ty, tabWidth[i], TAB_HEIGHT, TAB_HEIGHT / 2, UiDraw.alpha(t.surface(), dragged ? 1 : h * .7f));
            int colour = i == active ? t.accent() : UiDraw.mix(t.muted(), t.text(), h);
            if (iconTabs) {
                float size = Math.min(11, tabWidth[i] - 8);
                UiDraw.icon(g, tabIcons[i], tx + (tabWidth[i] - size) / 2, ty + (TAB_HEIGHT - size) / 2, size, colour);
            } else UiDraw.center(g, tabLabels[i], tx, ty + TAB_HEIGHT / 2, tabWidth[i], colour);
            if (hover && !draggingTab) tooltip("tab." + tabEntries[i], tabLabels[i], "Drag to reorder · right-click to remove");
        }
        UiRender.opacity = before;
        g.disableScissor();
    }

    private void footer(GuiGraphics g) {
        UiTheme t = GuiState.theme();
        boolean error = !GuiState.saveError.isEmpty();
        UiDraw.circle(g, 12, height - 10, 2, error ? t.red() : t.accent());
        UiDraw.text(g, error ? GuiState.saveError : "Argus Client", 18, height - 10, UiDraw.SMALL, error ? t.red() : t.muted());
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

    // Hooks for embedded components such as SettingsList.
    final void hostAdd(UiInput input) { addWidget(input); }
    final void hostRemove(UiInput input) { removeWidget(input); }
    final void hostFocus(UiInput input) { setFocused(input); }
    final void hostTooltip(Object owner, String... lines) { tooltip(owner, lines); }
    final void hostOpen(Screen screen) { minecraft.setScreen(screen); }

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
        if (modal()) return mouseClickedUi(event, doubled);
        // An open colour picker takes the click first (a click outside it closes it).
        if (ColorPicker.click(event, doubled)) return true;
        if (event.button() == 0 && overFace(event.x(), event.y())) { setBarOpen(!barOpen()); return true; }
        if (event.button() == 0 && barOpen() && overGear(event.x(), event.y())) { if (!(this instanceof SettingsScreen)) navigate("Settings"); else if (!settingsActive()) openSection(SettingsScreen.APPEARANCE); return true; }
        if ((event.button() == 0 || event.button() == 1) && barOpen() && barProgress() > .6f) for (int i = 0; i < tabLabels.length; i++)
            if (UiDraw.over(event.x(), event.y(), tabX(i), BAR_Y + (BAR - TAB_HEIGHT) / 2, tabWidth[i], TAB_HEIGHT)) {
                if (event.button() == 1) { removeTab(i); return true; }
                // Opening waits for release, so a press that turns into a drag reorders instead.
                pressedTab = i; pressX = (float) event.x(); grabOffset = (float) event.x() - tabX(i); draggingTab = false;
                return true;
            }
        return mouseClickedUi(event, doubled);
    }
    private static Class<?> tabScreen(String tab) {
        return switch (tab) { case "Modules" -> ModulesScreen.class; case "Settings" -> SettingsScreen.class; default -> Object.class; };
    }
    protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) { return super.mouseClicked(event, doubled); }
    @Override public final boolean mouseDragged(MouseButtonEvent raw, double dx, double dy) {
        if (closingAt != 0) return false;
        MouseButtonEvent event = logical(raw);
        if (modal()) return mouseDraggedUi(event, viewport.toLogical(dx), viewport.toLogical(dy));
        if (ColorPicker.dragged(event)) return true;
        if (pressedTab >= 0) {
            mouseX = (int) event.x();
            if (!draggingTab && Math.abs(event.x() - pressX) > 4) draggingTab = true;
            if (draggingTab) {
                float centre = (float) event.x() - grabOffset + tabWidth[pressedTab] / 2;
                int slot = pressedTab;
                while (slot > 0 && centre < tabX(slot - 1) + tabWidth[slot - 1] / 2) slot--;
                while (slot < tabLabels.length - 1 && centre > tabX(slot + 1) + tabWidth[slot + 1] / 2) slot++;
                if (slot != pressedTab) { moveTab(pressedTab, slot); pressedTab = slot; }
            }
            return true;
        }
        return mouseDraggedUi(event, viewport.toLogical(dx), viewport.toLogical(dy));
    }
    protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) { return super.mouseDragged(event, dx, dy); }
    @Override public final boolean mouseReleased(MouseButtonEvent raw) {
        if (ColorPicker.released()) return true;
        if (pressedTab >= 0) {
            int i = pressedTab;
            boolean dragged = draggingTab;
            pressedTab = -1; draggingTab = false;
            if (dragged) GuiState.save();
            else if (i < tabLabels.length) {
                if (tabSections[i] != null) { if (i != activeTabIndex()) openSection(tabSections[i]); }
                else {
                    String page = tabEntries[i].substring(PAGE_PREFIX.length());
                    if (i != activeTabIndex() || this.getClass() != tabScreen(page)) navigate(page);
                }
            }
            return true;
        }
        return mouseReleasedUi(logical(raw));
    }
    protected boolean mouseReleasedUi(MouseButtonEvent event) { return super.mouseReleased(event); }
    @Override public final boolean mouseScrolled(double mx, double my, double horizontal, double vertical) { ColorPicker.close(); return closingAt == 0 && mouseScrolledUi(viewport.toLogical(mx), viewport.toLogical(my), horizontal, vertical); }
    protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) { return super.mouseScrolled(mx, my, horizontal, vertical); }
    @Override public final void mouseMoved(double mx, double my) { super.mouseMoved(viewport.toLogical(mx), viewport.toLogical(my)); }

    protected void navigate(String label) {
        Screen root = rootParent();
        switch (label) {
            case "Modules" -> minecraft.setScreen(new ModulesScreen(root));
            case "HUD" -> ClientContext.getHudEditor().open();
            case "Settings" -> minecraft.setScreen(new SettingsScreen(this instanceof SettingsScreen ? parent : this, null, null));
        }
    }

    protected void backdrop(GuiGraphics g) {
        boolean dark = GuiState.preferences().mode == GuiPreferences.Mode.DARK;
        UiDraw.gradient(g, 0, 0, width, height, 0, dark ? 0x7a07070d : 0x50000000, dark ? 0xa009090f : 0x70000000);
    }

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (ColorPicker.typed(event)) return true;
        return super.charTyped(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (closingAt != 0) return true;
        if (modal()) return super.keyPressed(event);
        if (ColorPicker.key(event)) return true;
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
    @Override public void removed() { ColorPicker.closeNow(); GuiState.save(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}

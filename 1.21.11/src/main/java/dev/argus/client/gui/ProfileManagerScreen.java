package dev.argus.client.gui;

import dev.argus.client.network.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Shared Argus window for the account and proxy managers: a chevron header with a status chip,
 * a toolbar, a scrolling list of row cards (or a form card while adding/editing), and a footer
 * for status messages. Also owns the screen's asynchronous operations.
 */
abstract class ProfileManagerScreen extends UiScreen {
    protected enum Tone { INFO, OK, ERROR }
    private record Hit(float x, float y, float w, float h, boolean inList, Runnable action) {
        boolean contains(double mx, double my) { return UiDraw.over(mx, my, x, y, w, h); }
    }

    protected static final int ROW = 32, TOOLBAR_Y = 36, LIST_Y = 70, FOOTER = 24;
    protected ConnectionProfiles profiles;
    protected String message = "";
    protected Tone tone = Tone.INFO;
    protected boolean busy;
    protected int left, top, panelWidth, panelHeight;
    private final List<Hit> hits = new ArrayList<>();
    private boolean drawingList;
    private int scroll;
    private float shownScroll;
    private final LoginAttempt attempts = new LoginAttempt();
    private Future<?> task;
    private final ThreadLocal<Long> workerToken = new ThreadLocal<>();

    ProfileManagerScreen(String title, Screen parent) { super(title, parent, ""); }
    @Override protected boolean modal() { return true; }

    // ---- what subclasses provide --------------------------------------------------------------

    /** One-line state shown in the header chip, and whether it reads as "good" (green dot). */
    protected abstract String status();
    protected abstract boolean statusGood();
    /** Footer text when there is no message to show. */
    protected abstract String hint();
    protected abstract void buildToolbar(int x, int y, int w);
    protected abstract int rowCount();
    protected abstract void renderRow(GuiGraphics g, int index, float x, float y, float w, int mx, int my, boolean hover);
    /** Icon, title and hint shown when the list is empty. */
    protected abstract String[] empty();
    /** A form card replaces the toolbar and list while it is open. */
    protected boolean formOpen() { return false; }
    protected void buildForm(int x, int y, int w) {}
    protected void renderForm(GuiGraphics g, int x, int y, int w, int mx, int my) {}
    protected void closeForm() {}
    protected void submitForm() {}

    // ---- layout -------------------------------------------------------------------------------

    @Override protected final void buildContent() {
        panelWidth = Math.min(460, width - 24); panelHeight = Math.min(height - 24, 340);
        left = (width - panelWidth) / 2; top = Math.max(12, (height - panelHeight) / 2);
        profiles = ProfileRuntime.profiles();
        if (profiles == null) {
            if (message.isEmpty() || tone != Tone.ERROR) error(ProfileRuntime.error().isEmpty() ? "Couldn't load saved accounts and proxies." : ProfileRuntime.error());
            int bw = 90;
            button(left + (panelWidth - bw) / 2, top + panelHeight / 2 + 22, bw, "Try again", "reset", () -> { ProfileRuntime.retry(); message = ""; rebuildWidgets(); }, true, true);
            return;
        }
        if (formOpen()) buildForm(left + 12, top + TOOLBAR_Y, panelWidth - 24);
        else buildToolbar(left + 12, top + TOOLBAR_Y, panelWidth - 24);
    }

    private int listTop() { return top + LIST_Y; }
    private int listHeight() { return Math.max(ROW, panelHeight - LIST_Y - FOOTER); }
    private int maxScroll() { return Math.max(0, rowCount() * ROW - listHeight()); }

    // ---- widgets and drawing helpers ------------------------------------------------------------

    protected UiButton button(int x, int y, int w, String label, Runnable action) { return button(x, y, w, label, null, action, false, false); }
    protected UiButton button(int x, int y, int w, String label, String icon, Runnable action, boolean selected, boolean always) {
        var b = new UiButton(x, y, w, 22, label, selected, icon, action);
        b.active = always || (!busy && profiles != null); addRenderableWidget(b); return b;
    }
    /** Evenly spaced row of toolbar buttons across {@code w}. */
    protected void toolbar(int x, int y, int w, String[] labels, String[] icons, Runnable[] actions) {
        int gap = 6, bw = (w - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) button(x + i * (bw + gap), y, i == labels.length - 1 ? w - i * (bw + gap) : bw, labels[i], icons[i], actions[i], false, false);
    }
    protected void field(UiInput input, int x, int y, int w) { input.place(x, y, w); input.active = !busy; addRenderableWidget(input); }

    /** Small rounded text button registered for this frame; returns its width. */
    protected float pill(GuiGraphics g, String label, float right, float cy, int mx, int my, int tint, boolean filled, boolean enabled, Runnable action) {
        UiTheme t = GuiState.theme();
        float pw = UiDraw.width(label, UiDraw.SMALL) + 14, px = right - pw;
        boolean hover = enabled && UiDraw.over(mx, my, px, cy - 7, pw, 14) && (!drawingList || my >= listTop() && my < listTop() + listHeight());
        float hv = UiAnim.smooth("pm." + label + "." + Math.round(cy) + "." + Math.round(right), hover ? 1 : 0, 20);
        float before = UiRender.opacity;
        if (!enabled) UiRender.opacity = before * .45f;
        UiDraw.rounded(g, px, cy - 7, pw, 14, 7, filled ? UiDraw.alpha(tint, .22f + hv * .14f) : UiDraw.mix(t.surface(), t.overlay(), hv * .8f));
        UiDraw.center(g, label, px, cy, pw, UiDraw.SMALL, filled ? tint : UiDraw.mix(t.text(), tint, hv));
        UiRender.opacity = before;
        if (enabled) hits.add(new Hit(px, cy - 7, pw, 14, drawingList, action));
        return pw;
    }
    /** Registers a clickable area for this frame (e.g. a toggle). */
    protected void hit(float x, float y, float w, float h, Runnable action) { hits.add(new Hit(x, y, w, h, drawingList, action)); }

    /** Coloured label chip; returns its width. */
    protected static float chip(GuiGraphics g, String label, float x, float cy, int tint) {
        float cw = UiDraw.width(label, 5.5f) + 10;
        UiDraw.rounded(g, x, cy - 4.5f, cw, 9, 4.5f, UiDraw.alpha(tint, .16f));
        UiDraw.center(g, label, x, cy, cw, 5.5f, tint);
        return cw;
    }
    /** Round tinted badge holding an icon, centred on (cx, cy). */
    protected static void badge(GuiGraphics g, String icon, float cx, float cy, int tint) {
        UiDraw.circle(g, cx, cy, 8.5f, UiDraw.alpha(tint, .16f));
        UiDraw.icon(g, icon, cx - 4.5f, cy - 4.5f, 9, tint);
    }
    /** Small muted caption above a form field. */
    protected static void label(GuiGraphics g, String text, float x, float cy) { UiDraw.text(g, text, x, cy, 6f, GuiState.theme().muted()); }

    // ---- messages and background work -----------------------------------------------------------

    protected void info(String text) { message = text; tone = Tone.INFO; }
    protected void ok(String text) { message = text; tone = Tone.OK; }
    protected void error(String text) { message = text; tone = Tone.ERROR; }

    @FunctionalInterface protected interface IOAction { void run() throws Exception; }
    protected void change(IOAction action, String success) {
        try { action.run(); ok(success); } catch (Exception e) { error(safeError(e)); }
        rebuildWidgets();
    }
    protected static String safeError(Exception e) {
        return e instanceof IllegalArgumentException || e instanceof java.io.IOException ? (e.getMessage() == null ? "Couldn't save the change." : e.getMessage()) : "Couldn't complete the request. Try again.";
    }
    @FunctionalInterface protected interface Work<T> { T run() throws Exception; }
    protected <T> void background(String status, Work<T> work, java.util.function.Consumer<T> done) {
        if (busy || profiles == null) return;
        long token = attempts.begin(); busy = true; info(status); rebuildWidgets();
        task = ProfileRuntime.IO.submit(() -> {
            workerToken.set(token);
            try {
                T result = work.run();
                minecraft.execute(() -> { if (!accepts(token)) return; busy = false; try { done.accept(result); } catch (Exception e) { error(safeError(e)); } rebuildWidgets(); });
            } catch (Exception e) {
                minecraft.execute(() -> { if (!accepts(token)) return; busy = false; error("Request failed. Check your connection and credentials, then try again."); rebuildWidgets(); });
            } finally { workerToken.remove(); }
        });
    }
    protected void progress(java.util.function.Consumer<ProfileManagerScreen> update) {
        Long token = workerToken.get();
        minecraft.execute(() -> { if (token != null && accepts(token) && busy) update.accept(this); });
    }
    private boolean accepts(long token) { return attempts.accepts(token) && minecraft.screen == this && !closing(); }
    protected void cancelTask() { attempts.cancel(); if (task != null) task.cancel(true); busy = false; info("Cancelled."); rebuildWidgets(); }
    @Override public void beginClose(Screen target) { attempts.cancel(); if (task != null) task.cancel(true); super.beginClose(target); }
    @Override public void removed() { attempts.cancel(); if (task != null) task.cancel(true); super.removed(); }

    // ---- rendering ------------------------------------------------------------------------------

    @Override protected final void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        hits.clear();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // Header: back chevron, title and a status chip on the right.
        float bh = UiAnim.smooth("pm.back", UiDraw.over(mx, my, left + 10, top + 10, 18, 18) ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        String title = getTitle().getString();
        UiDraw.bold(g, title, left + 36, top + 19, UiDraw.TITLE, t.text());
        float room = panelWidth - 36 - UiDraw.width(title, UiDraw.TITLE) - 34;
        String status = UiDraw.fit(status(), room - 20, UiDraw.SMALL);
        float sw = UiDraw.width(status, UiDraw.SMALL) + 20, sx = left + panelWidth - 12 - sw;
        int dot = statusGood() ? t.green() : t.muted();
        UiDraw.rounded(g, sx, top + 12, sw, 14, 7, UiDraw.alpha(t.mantle(), .9f));
        UiDraw.outline(g, sx, top + 12, sw, 14, 7, .6f, UiDraw.alpha(t.overlay(), .5f));
        UiDraw.circle(g, sx + 8, top + 19, 2.2f, dot);
        UiDraw.text(g, status, sx + 14, top + 19, UiDraw.SMALL, statusGood() ? t.text() : t.muted());
        if (UiDraw.over(mx, my, sx, top + 12, sw, 14) && !status.equals(status())) tooltip("pm.status", status());

        if (profiles == null) {
            float cy = top + panelHeight / 2f - 18;
            UiDraw.icon(g, "close", left + panelWidth / 2f - 7, cy - 14, 14, UiDraw.alpha(t.red(), .6f));
            UiDraw.center(g, "Saved profiles are unavailable", left, cy + 10, panelWidth, t.text());
            UiDraw.center(g, UiDraw.fit(message, panelWidth - 40, UiDraw.SMALL), left, cy + 22, panelWidth, UiDraw.SMALL, t.muted());
            footer(g, mx, my, t);
            return;
        }
        if (formOpen()) renderForm(g, left + 12, top + TOOLBAR_Y, panelWidth - 24, mx, my);
        else renderList(g, mx, my, t);
        footer(g, mx, my, t);
    }

    private void renderList(GuiGraphics g, int mx, int my, UiTheme t) {
        UiDraw.rect(g, left + 12, top + LIST_Y - 6, panelWidth - 24, .6f, UiDraw.alpha(t.overlay(), .6f));
        int start = listTop(), view = listHeight(), n = rowCount();
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        g.enableScissor(left + 4, start, left + panelWidth - 4, start + view);
        drawingList = true;
        try {
            for (int i = 0; i < n; i++) {
                float y = start + i * ROW - shownScroll;
                if (y + ROW < start || y > start + view) continue;
                boolean hover = UiDraw.over(mx, my, left + 8, y, panelWidth - 16, ROW) && my >= start && my < start + view;
                renderRow(g, i, left + 8, y + 1, panelWidth - 16, mx, my, hover);
            }
        } finally { drawingList = false; }
        if (n == 0) {
            String[] e = empty();
            UiDraw.icon(g, e[0], left + panelWidth / 2f - 7, start + 14, 14, UiDraw.alpha(t.muted(), .45f));
            UiDraw.center(g, e[1], left, start + 40, panelWidth, t.muted());
            UiDraw.center(g, UiDraw.fit(e[2], panelWidth - 40, UiDraw.SMALL), left, start + 52, panelWidth, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        }
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, left + 4, start, panelWidth - 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, left + 4, start + view - 8, panelWidth - 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base());
    }

    /** Row card background, shared by both lists; {@code highlight} outlines it in the accent. */
    protected void rowFrame(GuiGraphics g, String key, float x, float y, float w, boolean hover, boolean highlight) {
        UiTheme t = GuiState.theme();
        float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20), on = UiAnim.smooth(key + ".on", highlight ? 1 : 0, 16);
        UiDraw.rounded(g, x, y, w, ROW - 3, 6, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .7f));
        if (on > 0) UiDraw.outline(g, x, y, w, ROW - 3, 6, .7f, UiDraw.alpha(t.accent(), .45f * on));
    }

    private void footer(GuiGraphics g, int mx, int my, UiTheme t) {
        float cy = top + panelHeight - 12, right = left + panelWidth - 12;
        if (busy) right -= pill(g, "Cancel", right, cy, mx, my, t.red(), false, true, this::cancelTask) + 6;
        float x = left + 14;
        int colour;
        if (busy) {
            float pulse = .45f + .55f * (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1.6e8));
            UiDraw.circle(g, x + 2, cy, 2.2f, UiDraw.alpha(t.accent(), pulse));
            x += 9; colour = t.text();
        } else colour = message.isEmpty() ? UiDraw.alpha(t.muted(), .8f) : switch (tone) { case OK -> t.green(); case ERROR -> t.red(); case INFO -> t.muted(); };
        String text = message.isEmpty() ? hint() : message, shown = UiDraw.fit(text, right - x, UiDraw.SMALL);
        UiDraw.text(g, shown, x, cy, UiDraw.SMALL, colour);
        if (!shown.equals(text) && UiDraw.over(mx, my, left, cy - 8, panelWidth, 16)) tooltip("pm.message", text);
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return false;
        if (UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { if (formOpen() && !busy) { closeForm(); rebuildWidgets(); } else onClose(); return true; }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my) && (!hit.inList() || my >= listTop() && my < listTop() + listHeight())) { hit.action().run(); return true; }
        }
        return UiDraw.over(mx, my, left, top, panelWidth, panelHeight);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (formOpen() || !UiDraw.over(mx, my, left, listTop(), panelWidth, listHeight())) return false;
        scroll = Math.clamp(scroll - (int) (vertical * ROW), 0, maxScroll());
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (formOpen() && !busy && event.key() == 256) { closeForm(); rebuildWidgets(); return true; }
        if (formOpen() && !busy && (event.key() == 257 || event.key() == 335)) { submitForm(); return true; }
        return super.keyPressed(event);
    }
}

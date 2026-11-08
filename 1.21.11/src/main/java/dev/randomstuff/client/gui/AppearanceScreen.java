package dev.randomstuff.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Light/Dark mode, accent colour, GUI scale and window behaviour. */
final class AppearanceScreen extends UiScreen {
    private static final double MIN_SCALE = .65, MAX_SCALE = 1.4;
    private int left, top, panelWidth;
    private boolean slidingScale;
    private double pendingScale = Double.NaN;

    AppearanceScreen(Screen parent) { super("Appearance", parent, "Theme"); }

    @Override protected void buildContent() {
        panelWidth = Math.min(360, width - 24); left = (width - panelWidth) / 2; top = 44;
        var p = GuiState.preferences();
        addRenderableWidget(new UiButton(left + 14, top + 216, panelWidth - 28, 22, "Reset window positions", false, "misc", () -> {
            p.windows.clear(); GuiState.save(); minecraft.setScreen(new ModulesScreen(rootParent()));
        }));
    }

    private float modeX(int i) { return left + 14 + i * ((panelWidth - 28) / 2f); }
    private float swatchX(int i) { return left + 14 + i * ((panelWidth - 28) / 6f); }
    private float scaleX() { return left + 14; }
    private float scaleW() { return panelWidth - 28 - 44; }

    private static int accentColour(GuiPreferences.Accent accent, boolean light) {
        var preview = new GuiPreferences(); preview.accent = accent; preview.mode = light ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK;
        return UiTheme.of(preview).accent();
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme(); var p = GuiState.preferences();
        boolean light = p.mode == GuiPreferences.Mode.LIGHT;
        UiDraw.panel(g, left, top, panelWidth, 248, t);
        UiDraw.icon(g, "palette", left + 14, top + 12, 10, t.accent());
        UiDraw.bold(g, "Appearance", left + 30, top + 17, UiDraw.TITLE, t.text());

        // Mode: segmented control with a sliding thumb.
        UiDraw.text(g, "Mode", left + 14, top + 40, UiDraw.SMALL, t.muted());
        float segW = (panelWidth - 28) / 2f;
        UiDraw.rounded(g, left + 14, top + 48, panelWidth - 28, 22, 8, t.mantle());
        float thumb = UiAnim.smooth("app.mode", light ? 1 : 0, 16);
        UiDraw.rounded(g, left + 16 + thumb * segW, top + 50, segW - 4, 18, 6.5f, UiDraw.alpha(t.accent(), .22f));
        String[] modes = {"Dark", "Light"};
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 1) == light, hover = UiDraw.over(mx, my, modeX(i), top + 48, segW, 22);
            int colour = active ? t.accent() : UiDraw.mix(t.muted(), t.text(), UiAnim.smooth("app.mode.h" + i, hover ? 1 : 0, 18));
            UiDraw.center(g, modes[i], modeX(i), top + 59, segW, colour);
        }

        // Accent: colour swatches with an animated ring on the chosen one.
        UiDraw.text(g, "Accent colour", left + 14, top + 86, UiDraw.SMALL, t.muted());
        var accents = GuiPreferences.Accent.values();
        float cell = (panelWidth - 28) / (float) accents.length;
        for (int i = 0; i < accents.length; i++) {
            var accent = accents[i];
            float cx = swatchX(i) + cell / 2, cy = top + 108;
            boolean chosen = p.accent == accent, hover = UiDraw.over(mx, my, swatchX(i), top + 94, cell, 36);
            float hv = UiAnim.smooth("app.acc.h" + i, hover ? 1 : 0, 18), ring = UiAnim.smooth("app.acc." + i, chosen ? 1 : 0, 14);
            int colour = accentColour(accent, light);
            if (ring > 0) UiDraw.outline(g, cx - 10, cy - 10, 20, 20, 10, 1.4f, UiDraw.alpha(colour, ring));
            float r = 6.5f + hv * .8f;
            UiDraw.circle(g, cx, cy, r, colour);
            if (ring > .02f) UiDraw.icon(g, "check", cx - 4, cy - 4, 8, UiDraw.alpha(t.crust(), ring));
            UiDraw.center(g, GuiState.title(accent.name().toLowerCase()), swatchX(i), cy + 17, cell, UiDraw.SMALL, chosen ? colour : UiDraw.mix(t.muted(), t.text(), hv));
        }

        // GUI scale slider.
        double scale = Double.isNaN(pendingScale) ? p.uiScale : pendingScale;
        UiDraw.text(g, "GUI scale", left + 14, top + 148, UiDraw.SMALL, t.muted());
        float value = (float) ((scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE));
        boolean overScale = UiDraw.over(mx, my, scaleX() - 4, top + 154, scaleW() + 8, 16);
        UiDraw.slider(g, scaleX(), top + 162, scaleW(), slidingScale ? value : UiAnim.smooth("app.scale", value, 18),
                UiAnim.smooth("app.scale.h", overScale || slidingScale ? 1 : 0, 18), t);
        UiDraw.right(g, Math.round(scale * 100) + "%", left + panelWidth - 14, top + 162, slidingScale ? t.accent() : t.text());

        // Grid snap switch.
        boolean snapHover = UiDraw.over(mx, my, left + 8, top + 180, panelWidth - 16, 24);
        float sh = UiAnim.smooth("app.snap.h", snapHover ? 1 : 0, 18);
        if (sh > 0) UiDraw.rounded(g, left + 8, top + 180, panelWidth - 16, 24, 6, UiDraw.alpha(t.surface(), sh * .8f));
        UiDraw.text(g, "Snap windows to grid", left + 14, top + 192, t.text());
        UiDraw.toggle(g, left + panelWidth - 32, top + 187, UiAnim.smooth("app.snap", p.snap ? 1 : 0, 16), snapHover, t);
    }

    private void setScaleFrom(double mx) {
        double f = Math.clamp((mx - scaleX()) / scaleW(), 0, 1);
        pendingScale = Math.round((MIN_SCALE + (MAX_SCALE - MIN_SCALE) * f) * 20) / 20.0;
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        if (event.button() != 0) return false;
        var p = GuiState.preferences(); double mx = event.x(), my = event.y();
        float segW = (panelWidth - 28) / 2f;
        for (int i = 0; i < 2; i++) if (UiDraw.over(mx, my, modeX(i), top + 48, segW, 22)) {
            p.mode = i == 1 ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK; GuiState.save(); return true;
        }
        var accents = GuiPreferences.Accent.values(); float cell = (panelWidth - 28) / (float) accents.length;
        for (int i = 0; i < accents.length; i++) if (UiDraw.over(mx, my, swatchX(i), top + 94, cell, 36)) { p.accent = accents[i]; GuiState.save(); return true; }
        if (UiDraw.over(mx, my, scaleX() - 4, top + 154, scaleW() + 8, 16)) { slidingScale = true; setScaleFrom(mx); return true; }
        if (UiDraw.over(mx, my, left + 8, top + 180, panelWidth - 16, 24)) { p.snap = !p.snap; GuiState.save(); return true; }
        return false;
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (slidingScale) { setScaleFrom(event.x()); return true; }
        return super.mouseDraggedUi(event, dx, dy);
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) {
        if (slidingScale) {
            // Apply the new density on release so the page doesn't resize under the pointer.
            slidingScale = false;
            var p = GuiState.preferences();
            if (!Double.isNaN(pendingScale) && pendingScale != p.uiScale) { p.uiScale = pendingScale; GuiState.save(); rebuildWidgets(); }
            pendingScale = Double.NaN;
            return true;
        }
        return super.mouseReleasedUi(event);
    }
}

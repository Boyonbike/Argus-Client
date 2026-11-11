package dev.argus.client.gui;

import dev.argus.client.settings.ColorSetting;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Pop-over colour picker for colour settings: saturation/brightness square, hue and alpha bars,
 * hex entry, rainbow/theme-sync switches, and a saved palette you can add colours to. It sits
 * above the page, takes input first while open, and closes on Esc or a click outside.
 */
final class ColorPicker {
    private static final float W = 160, PAD = 8, SV_H = 86, BAR = 8, SWATCH = 12, SWATCH_GAP = 4;
    private static ColorPicker open;

    private final ColorSetting setting;
    private final String title;
    private final int original;
    private final UiInput hex;
    private float hue, sat, val, alpha;
    private float x, y, h;
    /** The swatch the picker was opened from; it grows out of (and shrinks back into) this point. */
    private float originX, originY;
    private String dragging;
    private boolean closing;
    private float fade;

    static boolean active() { return open != null; }
    static void close() { if (open != null) open.closing = true; }
    static void closeNow() { open = null; }

    /** Opens beside the panel when there is room, otherwise just under the row. */
    static void show(ColorSetting setting, float rowX, float rowY, float rowW, float rowH, float panelLeft, float panelRight, int screenW, int screenH) {
        ColorPicker picker = new ColorPicker(setting);
        picker.originX = rowX + rowW - 12; picker.originY = rowY + rowH / 2;
        if (screenW - panelRight - 12 >= W) picker.x = panelRight + 8;
        else if (panelLeft - 12 >= W) picker.x = panelLeft - 8 - W;
        else picker.x = Math.clamp(rowX + rowW - W, 6, screenW - W - 6);
        boolean beside = picker.x >= panelRight || picker.x + W <= panelLeft;
        picker.y = beside ? rowY + rowH / 2 - 30 : rowY + rowH + 4;
        if (!beside && picker.y + picker.h > screenH - 24) picker.y = rowY - picker.h - 4;
        picker.y = Math.clamp(picker.y, 44, Math.max(44, screenH - picker.h - 24));
        open = picker;
    }

    private ColorPicker(ColorSetting setting) {
        this.setting = setting;
        this.title = GuiState.title(setting.getName());
        this.original = setting.getValue().getRGB();
        load(original);
        hex = new UiInput(0, 0, 80, 12, "RRGGBB");
        hex.setMaxLength(9);
        hex.setValue(hexText());
        h = height();
    }

    /** Full height; grows as saved colours wrap onto more rows. */
    private float height() {
        int rows = Math.max(1, (int) Math.ceil((GuiState.preferences().savedColours.size() + 1) / (float) perRow()));
        return paletteY() - y + 12 + rows * (SWATCH + SWATCH_GAP) - SWATCH_GAP + PAD;
    }

    private boolean opaque() { return setting.opaqueColor; }
    private static int perRow() { return (int) ((W - PAD * 2 + SWATCH_GAP) / (SWATCH + SWATCH_GAP)); }

    private void load(int argb) {
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
        if (hsb[1] > 0.001f && hsb[2] > 0.001f) hue = hsb[0];
        sat = hsb[1]; val = hsb[2]; alpha = ((argb >>> 24) & 255) / 255f;
    }

    private int argb() {
        int rgb = Color.HSBtoRGB(hue, sat, val) & 0xffffff;
        int a = opaque() ? 255 : Math.round(alpha * 255);
        return (a << 24) | rgb;
    }

    private String hexText() { int c = argb(); return opaque() || (c >>> 24) == 255 ? String.format("%06X", c & 0xffffff) : String.format("%08X", c); }

    /** Writes the picked colour to the setting; picking a colour by hand turns rainbow off. */
    private void apply(boolean updateHex) {
        if (setting.isRainbow()) setting.setRainbow(false);
        setting.setColor(new Color(argb(), true));
        if (updateHex) hex.setValue(hexText());
    }

    // ---- geometry ---------------------------------------------------------------------------

    private float svX() { return x + PAD; }
    private float svY() { return y + PAD + 22; }
    private float svW() { return W - PAD * 2; }
    private float hueY() { return svY() + SV_H + 8; }
    private float alphaY() { return hueY() + BAR + 6; }
    private float hexY() { return (opaque() ? hueY() + BAR : alphaY() + BAR) + 10; }
    private float toggleY() { return hexY() + 28; }
    private float paletteY() { return toggleY() + 24; }

    // ---- drawing ----------------------------------------------------------------------------

    void draw(GuiGraphics g, int mx, int my, UiTheme t) {
        fade = UiAnim.approach(fade, closing ? 0 : 1, closing ? 22 : 16);
        h = height();
        if (closing && fade < .02f) { if (open == this) open = null; return; }
        float before = UiRender.opacity;
        UiRender.opacity = before * fade;
        float yy = y + (1 - fade) * 4;
        float dy = yy - y;
        y = yy;
        float scale = UiAnim.lerp(.9f, 1, UiAnim.easeOutCubic(fade));
        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale, scale);
        g.pose().translate(-originX, -originY);
        try {
            UiDraw.shadow(g, x, y + 3, W, h, 9, 12, UiDraw.alpha(0xff000000, .4f));
            UiDraw.rounded(g, x, y, W, h, 9, t.mantle());
            UiDraw.outline(g, x, y, W, h, 9, .6f, UiDraw.alpha(t.accent(), .45f));
            // Header: before/after swatches, name, close.
            int now = setting.getValue().getRGB();
            checker(g, x + PAD, y + PAD, 14, 14, t);
            UiDraw.rounded(g, x + PAD, y + PAD, 14, 14, 4, now);
            UiDraw.outline(g, x + PAD, y + PAD, 14, 14, 4, .6f, UiDraw.alpha(t.text(), .25f));
            UiDraw.bold(g, UiDraw.fit(title, W - PAD * 2 - 44), x + PAD + 20, y + PAD + 7, UiDraw.TEXT, t.text());
            boolean closeHover = UiDraw.over(mx, my, x + W - PAD - 12, y + PAD + 1, 12, 12);
            UiDraw.icon(g, "close", x + W - PAD - 10, y + PAD + 3, 8, closeHover ? t.red() : t.muted());
            // Saturation/brightness square, drawn as columns of vertical gradients.
            float sx = svX(), sy = svY(), sw = svW();
            int columns = 40;
            for (int i = 0; i < columns; i++) {
                float s0 = i / (float) (columns - 1);
                int top = 0xff000000 | (Color.HSBtoRGB(hue, s0, 1) & 0xffffff);
                UiDraw.gradient(g, sx + sw * i / columns, sy, sw / columns + .6f, SV_H, 0, top, 0xff000000);
            }
            UiDraw.outline(g, sx, sy, sw, SV_H, 3, .6f, UiDraw.alpha(t.text(), .2f));
            float kx = sx + sat * sw, ky = sy + (1 - val) * SV_H;
            UiDraw.circle(g, kx, ky, 4.5f, 0xffffffff);
            UiDraw.circle(g, kx, ky, 3.2f, 0xff000000 | (Color.HSBtoRGB(hue, sat, val) & 0xffffff));
            // Hue bar.
            float hy = hueY();
            for (int i = 0; i < columns; i++) UiDraw.rect(g, sx + sw * i / columns, hy, sw / columns + .6f, BAR, 0xff000000 | (Color.HSBtoRGB(i / (float) columns, 1, 1) & 0xffffff));
            UiDraw.outline(g, sx, hy, sw, BAR, 2, .6f, UiDraw.alpha(t.text(), .2f));
            knob(g, sx + hue * sw, hy, 0xff000000 | (Color.HSBtoRGB(hue, 1, 1) & 0xffffff));
            // Alpha bar over a checkerboard.
            if (!opaque()) {
                float ay = alphaY();
                checker(g, sx, ay, sw, BAR, t);
                int rgb = Color.HSBtoRGB(hue, sat, val) & 0xffffff;
                for (int i = 0; i < columns; i++) UiDraw.rect(g, sx + sw * i / columns, ay, sw / columns + .6f, BAR, (Math.round(255f * i / (columns - 1)) << 24) | rgb);
                UiDraw.outline(g, sx, ay, sw, BAR, 2, .6f, UiDraw.alpha(t.text(), .2f));
                knob(g, sx + alpha * sw, ay, argb());
            }
            // Hex entry and revert.
            float ty = hexY();
            UiDraw.text(g, "Hex", sx, ty + 6, UiDraw.SMALL, t.muted());
            hex.place(Math.round(sx + 22), Math.round(ty), Math.round(sw - 22 - 46));
            hex.render(g, mx, my, 0);
            float rx = sx + sw - 40;
            boolean revertHover = UiDraw.over(mx, my, rx, ty - 4, 40, 20);
            UiDraw.rounded(g, rx, ty - 4, 40, 20, 7, UiDraw.mix(t.surface(), t.overlay(), revertHover ? .8f : 0));
            checker(g, rx + 5, ty, 10, 10, t);
            UiDraw.rounded(g, rx + 5, ty, 10, 10, 3, original);
            UiDraw.text(g, "Undo", rx + 18, ty + 5, 6f, t.text());
            if (revertHover) host().hostTooltip("cp.revert", "Revert", "Back to the colour it had when you opened this");
            // Rainbow and theme sync.
            float half = (sw - 8) / 2, gy = toggleY();
            switchRow(g, "Rainbow", setting.isRainbow(), sx, gy, half, mx, my, t, "cp.rainbow");
            switchRow(g, "Sync theme", setting.isSynced(), sx + half + 8, gy, half, mx, my, t, "cp.sync");
            // Saved colours.
            float py = paletteY();
            UiDraw.text(g, "Saved colours", sx, py + 4, UiDraw.SMALL, t.muted());
            var saved = GuiState.preferences().savedColours;
            int per = perRow();
            for (int i = 0; i <= saved.size(); i++) {
                float cx = sx + (i % per) * (SWATCH + SWATCH_GAP), cy = py + 12 + (i / per) * (SWATCH + SWATCH_GAP);
                boolean hover = UiDraw.over(mx, my, cx, cy, SWATCH, SWATCH);
                if (i == saved.size()) {
                    if (saved.size() >= GuiPreferences.MAX_SAVED_COLOURS) break;
                    UiDraw.rounded(g, cx, cy, SWATCH, SWATCH, 3.5f, UiDraw.mix(t.surface(), t.overlay(), hover ? .9f : 0));
                    UiDraw.icon(g, "plus", cx + 2, cy + 2, SWATCH - 4, hover ? t.accent() : t.muted());
                    if (hover) host().hostTooltip("cp.add", "Save this colour", "Adds the current colour to your palette");
                } else {
                    int colour = saved.get(i);
                    checker(g, cx, cy, SWATCH, SWATCH, t);
                    UiDraw.rounded(g, cx, cy, SWATCH, SWATCH, 3.5f, colour);
                    UiDraw.outline(g, cx, cy, SWATCH, SWATCH, 3.5f, hover ? 1f : .6f, hover ? t.accent() : UiDraw.alpha(t.text(), .2f));
                    if (hover) host().hostTooltip("cp.saved." + i, "#" + String.format("%08X", colour), "Click to use · right-click to remove");
                }
            }
        } finally {
            g.pose().popMatrix();
            y -= dy;
            UiRender.opacity = before;
        }
    }

    private static UiScreen host() { return (UiScreen) net.minecraft.client.Minecraft.getInstance().screen; }

    private void knob(GuiGraphics g, float cx, float y, int colour) {
        UiDraw.rounded(g, cx - 2.5f, y - 2, 5, BAR + 4, 2.5f, 0xffffffff);
        UiDraw.rounded(g, cx - 1.5f, y - 1, 3, BAR + 2, 1.5f, colour | 0xff000000);
    }

    /** Two-tone squares behind translucent colours. */
    private static void checker(GuiGraphics g, float x, float y, float w, float h, UiTheme t) {
        UiDraw.rect(g, x, y, w, h, 0xffcfcfcf);
        float cell = 3;
        for (float cy = 0; cy < h; cy += cell) for (float cx = ((int) (cy / cell) % 2) * cell; cx < w; cx += cell * 2)
            UiDraw.rect(g, x + cx, y + cy, Math.min(cell, w - cx), Math.min(cell, h - cy), 0xff8f8f8f);
    }

    private void switchRow(GuiGraphics g, String label, boolean on, float x, float y, float w, int mx, int my, UiTheme t, String key) {
        boolean hover = UiDraw.over(mx, my, x - 2, y, w + 4, 16);
        if (hover) UiDraw.rounded(g, x - 2, y, w + 4, 16, 5, UiDraw.alpha(t.surface(), .8f));
        UiDraw.text(g, label, x + 2, y + 8, 6f, t.text());
        UiDraw.toggle(g, x + w - 19, y + 3, UiAnim.smooth(key, on ? 1 : 0, 16), hover, t);
    }

    // ---- input ------------------------------------------------------------------------------

    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (!UiDraw.over(mx, my, x, y, W, h)) { close(); return true; }
        float sx = svX(), sw = svW();
        if (hex.isMouseOver(mx, my)) { hex.setFocused(true); return hex.mouseClicked(event, doubled); }
        hex.setFocused(false);
        if (UiDraw.over(mx, my, x + W - PAD - 12, y + PAD + 1, 12, 12)) { close(); return true; }
        var saved = GuiState.preferences().savedColours;
        int per = perRow();
        for (int i = 0; i <= saved.size(); i++) {
            float cx = sx + (i % per) * (SWATCH + SWATCH_GAP), cy = paletteY() + 12 + (i / per) * (SWATCH + SWATCH_GAP);
            if (!UiDraw.over(mx, my, cx, cy, SWATCH, SWATCH)) continue;
            if (i == saved.size()) {
                if (saved.size() < GuiPreferences.MAX_SAVED_COLOURS && !saved.contains(argb())) { saved.add(argb()); GuiState.save(); }
            } else if (event.button() == 1) { saved.remove(i); GuiState.save(); }
            else { load(saved.get(i)); apply(true); }
            return true;
        }
        if (event.button() != 0) return true;
        if (UiDraw.over(mx, my, sx, svY(), sw, SV_H)) { dragging = "sv"; drag(mx, my); return true; }
        if (UiDraw.over(mx, my, sx - 3, hueY() - 3, sw + 6, BAR + 6)) { dragging = "hue"; drag(mx, my); return true; }
        if (!opaque() && UiDraw.over(mx, my, sx - 3, alphaY() - 3, sw + 6, BAR + 6)) { dragging = "alpha"; drag(mx, my); return true; }
        float rx = sx + sw - 40, ty = hexY();
        if (UiDraw.over(mx, my, rx, ty - 4, 40, 20)) { load(original); apply(true); return true; }
        float half = (sw - 8) / 2, gy = toggleY();
        if (UiDraw.over(mx, my, sx - 2, gy, half + 4, 16)) { setting.setRainbow(!setting.isRainbow()); return true; }
        if (UiDraw.over(mx, my, sx + half + 6, gy, half + 4, 16)) { setting.setSynced(!setting.isSynced()); return true; }
        return true;
    }

    private void drag(double mx, double my) {
        float f = (float) Math.clamp((mx - svX()) / svW(), 0, 1);
        switch (dragging) {
            case "sv" -> { sat = f; val = 1 - (float) Math.clamp((my - svY()) / SV_H, 0, 1); }
            case "hue" -> hue = Math.min(f, .999f);
            case "alpha" -> alpha = f;
            default -> { return; }
        }
        apply(true);
    }

    boolean mouseDragged(MouseButtonEvent event) {
        if (dragging == null) return hex.isFocused() && hex.mouseDragged(event, 0, 0);
        drag(event.x(), event.y()); return true;
    }

    boolean mouseReleased() { boolean was = dragging != null; dragging = null; return was; }

    boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { close(); return true; }
        if (!hex.isFocused()) return false;
        if (event.key() == 257 || event.key() == 335) { applyHex(); return true; }
        hex.keyPressed(event);
        return true;
    }

    boolean charTyped(CharacterEvent event) {
        if (!hex.isFocused()) return false;
        hex.charTyped(event);
        // Apply as soon as a full colour has been typed (6 digits, or 8 with alpha).
        int digits = hex.getValue().trim().replace("#", "").length();
        if (digits == 6 || digits == 8) applyHex();
        return true;
    }

    private void applyHex() {
        String text = hex.getValue().trim().replace("#", "").toUpperCase(Locale.ROOT);
        if (!text.matches("[0-9A-F]{6}|[0-9A-F]{8}")) return;
        long bits = Long.parseLong(text, 16);
        load((int) (text.length() == 6 ? bits | 0xff000000L : bits));
        apply(false);
    }

    // ---- static routing used by UiScreen ----------------------------------------------------

    static void render(GuiGraphics g, int mx, int my, UiTheme t) { if (open != null) open.draw(g, mx, my, t); }
    static boolean click(MouseButtonEvent e, boolean doubled) { return open != null && !open.closing && open.mouseClicked(e, doubled); }
    static boolean dragged(MouseButtonEvent e) { return open != null && !open.closing && open.mouseDragged(e); }
    static boolean released() { return open != null && open.mouseReleased(); }
    static boolean key(KeyEvent e) { return open != null && !open.closing && open.keyPressed(e); }
    static boolean typed(CharacterEvent e) { return open != null && !open.closing && open.charTyped(e); }
}

package dev.randomstuff.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

/** Shared drawing vocabulary. All coordinates are logical GUI units; output is pixel exact. */
final class UiDraw {
    private UiDraw() {}

    /** Body text, secondary text and headings, in logical units. */
    static final float TEXT = 7.5f, SMALL = 6.5f, TITLE = 8.25f;

    /** Framebuffer pixels per logical unit for the open menu; text is measured at this density. */
    static float density = 2;

    private static final FontDescription LEGACY = new FontDescription.Resource(Identifier.fromNamespaceAndPath("randomstuffclient", "ui"));
    static MutableComponent legacy(String text) { return Component.literal(text).withStyle(Style.EMPTY.withFont(LEGACY)); }

    // ---- text -------------------------------------------------------------------------------

    static float width(String text, float size) { return UiFont.width(text, size, density); }
    static float width(String text) { return width(text, TEXT); }

    static String fit(String text, float available, float size) {
        if (width(text, size) <= available) return text;
        int low = 0, high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (width(text.substring(0, middle) + "…", size) <= available) low = middle; else high = middle - 1;
        }
        return text.substring(0, low).stripTrailing() + "…";
    }
    static String fit(String text, float available) { return fit(text, available, TEXT); }

    /** Left-aligned text, vertically centred on {@code centreY}. */
    static void text(GuiGraphics g, String text, float x, float centreY, float size, int colour) { UiFont.drawCentred(g, text, x, centreY, size, colour, false); }
    static void text(GuiGraphics g, String text, float x, float centreY, int colour) { text(g, text, x, centreY, TEXT, colour); }
    static void bold(GuiGraphics g, String text, float x, float centreY, float size, int colour) { UiFont.drawCentred(g, text, x, centreY, size, colour, true); }

    static void center(GuiGraphics g, String text, float x, float centreY, float width, float size, int colour) {
        text(g, text, x + (width - width(text, size)) / 2, centreY, size, colour);
    }
    static void center(GuiGraphics g, String text, float x, float centreY, float width, int colour) { center(g, text, x, centreY, width, TEXT, colour); }

    static void right(GuiGraphics g, String text, float right, float centreY, float size, int colour) {
        text(g, text, right - width(text, size), centreY, size, colour);
    }
    static void right(GuiGraphics g, String text, float right, float centreY, int colour) { right(g, text, right, centreY, TEXT, colour); }

    // ---- colour -----------------------------------------------------------------------------

    static int alpha(int colour, int alpha) { return (colour & 0x00ffffff) | (Math.clamp(alpha, 0, 255) << 24); }
    static int alpha(int colour, float alpha) { return alpha(colour, Math.round(((colour >>> 24) & 255) * Math.clamp(alpha, 0f, 1f))); }

    static int mix(int first, int second, float progress) {
        float t = Math.clamp(progress, 0, 1); int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= ((int) (((first >>> shift) & 255) * (1 - t) + ((second >>> shift) & 255) * t)) << shift;
        return result;
    }

    static boolean over(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    // ---- shapes -----------------------------------------------------------------------------

    static void rect(GuiGraphics g, float x, float y, float w, float h, int colour) { UiRender.shape(g, x, y, w, h, 0, colour, colour, 0); }
    static void rounded(GuiGraphics g, float x, float y, float w, float h, float radius, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, 0); }
    static void gradient(GuiGraphics g, float x, float y, float w, float h, float radius, int top, int bottom) { UiRender.shape(g, x, y, w, h, radius, top, bottom, 0); }
    static void outline(GuiGraphics g, float x, float y, float w, float h, float radius, float thickness, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, Math.max(.01f, thickness)); }
    static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, float blur, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, -Math.max(.5f, blur)); }
    static void circle(GuiGraphics g, float cx, float cy, float r, int colour) { rounded(g, cx - r, cy - r, r * 2, r * 2, r, colour); }

    static void icon(GuiGraphics g, String name, float x, float y, float size, int colour) { UiIcons.draw(g, name, x, y, size, colour); }

    /** Floating window: soft shadow, base fill and a hairline edge. {@code lift} deepens the shadow. */
    static void panel(GuiGraphics g, float x, float y, float w, float h, UiTheme theme, float lift) {
        boolean dark = GuiState.preferences().mode == GuiPreferences.Mode.DARK;
        shadow(g, x, y + 2 + lift * 3, w, h, 7, 9 + lift * 8, dark ? alpha(0xff000000, .42f + lift * .2f) : alpha(0xff4c4f69, .16f + lift * .12f));
        rounded(g, x, y, w, h, 7, theme.base());
        outline(g, x, y, w, h, 7, .6f, alpha(theme.overlay(), dark ? .55f : .7f));
    }
    static void panel(GuiGraphics g, float x, float y, float w, float h, UiTheme theme) { panel(g, x, y, w, h, theme, 0); }

    // ---- controls ---------------------------------------------------------------------------

    /** Pill switch; {@code on} animates 0…1. */
    static void toggle(GuiGraphics g, float x, float y, float on, boolean hover, UiTheme theme) {
        float w = 18, h = 10;
        rounded(g, x, y, w, h, h / 2, mix(hover ? theme.overlay() : theme.surface(), theme.accent(), on));
        float knob = h - 3.4f, travel = w - h;
        circle(g, x + h / 2 + travel * on, y + h / 2, knob / 2, mix(theme.muted(), theme.crust(), on));
    }

    /** Rounded checkbox with an animated tick. */
    static void checkbox(GuiGraphics g, float x, float y, float size, float on, UiTheme theme) {
        rounded(g, x, y, size, size, 2.5f, mix(theme.surface(), theme.accent(), on));
        if (on < 1) outline(g, x, y, size, size, 2.5f, .7f, alpha(theme.overlay(), 1 - on));
        if (on > .02f) icon(g, "check", x + 1, y + 1, size - 2, alpha(theme.crust(), on));
    }

    /** Horizontal value slider; {@code value} 0…1, {@code hover}/{@code active} animate the thumb. */
    static void slider(GuiGraphics g, float x, float y, float w, float value, float hover, UiTheme theme) {
        float track = 3, cy = y;
        rounded(g, x, cy - track / 2, w, track, track / 2, theme.surface());
        rounded(g, x, cy - track / 2, Math.max(track, w * value), track, track / 2, theme.accent());
        float r = 3.4f + hover * .8f;
        if (hover > 0) circle(g, x + w * value, cy, r + 2.5f, alpha(theme.accent(), .18f * hover));
        circle(g, x + w * value, cy, r, theme.accent());
        circle(g, x + w * value, cy, r - 1.6f, theme.crust());
    }
}

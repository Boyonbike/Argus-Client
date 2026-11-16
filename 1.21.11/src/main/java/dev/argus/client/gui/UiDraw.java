package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

/** Shared drawing vocabulary. All coordinates are logical GUI units; output is pixel exact. */
final class UiDraw {
    private UiDraw() {}

    /** Body text, secondary text and headings, in logical units. */
    static final float TEXT = 7.5f, SMALL = UiSpace.CAPTION, TITLE = 8.25f;

    /** Framebuffer pixels per logical unit for the open menu; text is measured at this density. */
    static float density = 2;

    private static final FontDescription LEGACY = new FontDescription.Resource(Identifier.fromNamespaceAndPath("argusclient", "ui"));
    static MutableComponent legacy(String text) { return Component.literal(text).withStyle(Style.EMPTY.withFont(LEGACY)); }

    // ---- text -------------------------------------------------------------------------------

    static float width(String text, float size) { return UiFont.width(text, size, density); }
    static float width(String text) { return width(text, TEXT); }

    // fit() and wrap() run for most labels every frame and their results only depend on the inputs below,
    // so they are memoised. The caches are cleared when they grow large (e.g. many distinct live values).
    private record TextKey(String text, float available, float size, float density) {}
    private static final int TEXT_CACHE_LIMIT = 4096;
    private static final java.util.HashMap<TextKey, String> fitCache = new java.util.HashMap<>();
    private static final java.util.HashMap<TextKey, java.util.List<String>> wrapCache = new java.util.HashMap<>();

    static String fit(String text, float available, float size) {
        TextKey key = new TextKey(text, available, size, density);
        String cached = fitCache.get(key);
        if (cached != null) return cached;
        String result = computeFit(text, available, size);
        if (fitCache.size() >= TEXT_CACHE_LIMIT) fitCache.clear();
        fitCache.put(key, result);
        return result;
    }

    private static String computeFit(String text, float available, float size) {
        if (width(text, size) <= available) return text;
        int low = 0, high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (width(text.substring(0, middle) + "…", size) <= available) low = middle; else high = middle - 1;
        }
        return text.substring(0, low).stripTrailing() + "…";
    }
    static String fit(String text, float available) { return fit(text, available, TEXT); }

    /** Word-wraps text (honouring line breaks) to lines no wider than {@code available}. */
    static java.util.List<String> wrap(String text, float available, float size) {
        TextKey key = new TextKey(text, available, size, density);
        java.util.List<String> cached = wrapCache.get(key);
        if (cached != null) return cached;
        java.util.List<String> result = java.util.List.copyOf(computeWrap(text, available, size));
        if (wrapCache.size() >= TEXT_CACHE_LIMIT) wrapCache.clear();
        wrapCache.put(key, result);
        return result;
    }

    private static java.util.List<String> computeWrap(String text, float available, float size) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, size) <= available || line.isEmpty()) { line.setLength(0); line.append(candidate.length() > 0 ? fit(candidate, available, size) : candidate); }
                else { lines.add(line.toString()); line.setLength(0); line.append(fit(word, available, size)); }
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        return lines;
    }

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

    // ---- key legend -------------------------------------------------------------------------
    // A key legend is one string of "cap\tverb" lines joined with "\n"; a line without a tab is a plain label.
    // Encoding it as a string lets it crossfade like plain hint sentences do.

    private static final float LEGEND_CAP_PAD = 3, LEGEND_CAP_GAP = 4, LEGEND_ENTRY_GAP = 10;

    /** True if {@code text} is a key legend rather than a plain sentence. */
    static boolean isLegend(String text) { return text != null && text.indexOf('\t') >= 0; }

    static float legendWidth(String legend) {
        float w = 0;
        String[] entries = legend.split("\n");
        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split("\t", 2);
            if (i > 0) w += LEGEND_ENTRY_GAP;
            w += parts.length == 1 ? width(parts[0], UiSpace.CAPTION)
                    : width(parts[0], UiSpace.CAPTION) + LEGEND_CAP_PAD * 2 + LEGEND_CAP_GAP + width(parts[1], UiSpace.CAPTION);
        }
        return w;
    }

    /** Keycaps in a raised fill with the verb after each; plain labels in the primary text colour. */
    static void legend(GuiGraphics g, String legend, float x, float centreY, UiTheme t) {
        legend(g, legend, x, centreY, t, t.text(), t.textDim());
    }

    /** As {@link #legend(GuiGraphics, String, float, float, UiTheme)} with explicit label and verb colours. */
    static void legend(GuiGraphics g, String legend, float x, float centreY, UiTheme t, int labelColour, int verbColour) {
        String[] entries = legend.split("\n");
        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split("\t", 2);
            if (i > 0) x += LEGEND_ENTRY_GAP;
            if (parts.length == 1) {
                text(g, parts[0], x, centreY, UiSpace.CAPTION, labelColour);
                x += width(parts[0], UiSpace.CAPTION);
                continue;
            }
            float capW = width(parts[0], UiSpace.CAPTION) + LEGEND_CAP_PAD * 2;
            box(g, x, centreY - 5.5f, capW, 11, UiSpace.CHAMFER_SMALL, t.fill());
            rect(g, x + 1, centreY + 4.5f, capW - 2, 1, t.line());
            text(g, parts[0], x + LEGEND_CAP_PAD, centreY - .5f, UiSpace.CAPTION, t.text());
            x += capW + LEGEND_CAP_GAP;
            text(g, parts[1], x, centreY, UiSpace.CAPTION, verbColour);
            x += width(parts[1], UiSpace.CAPTION);
        }
    }

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

    /**
     * Shapes. Corners are stepped pixel-art chamfers, never smooth: a {@code radius} picks how many GUI pixels are
     * cut (none at 0, one for small radii, two otherwise), so older call sites keep their relative weight.
     * Only {@link #circle} stays round (toggle knobs, slider thumbs, status dots, the avatar).
     */
    static int steps(float radius, float w, float h) {
        if (radius <= 0) return 0;
        int steps = radius < 2.5f ? 1 : UiSpace.chamfer();
        // A cut deeper than a third of the short side reads as a blob, not a corner.
        return Math.max(0, Math.min(steps, (int) (Math.min(w, h) / 3)));
    }

    static void rect(GuiGraphics g, float x, float y, float w, float h, int colour) { UiRender.shape(g, x, y, w, h, 0, colour, colour, 0); }
    static void rounded(GuiGraphics g, float x, float y, float w, float h, float radius, int colour) { UiRender.chamfer(g, x, y, w, h, steps(radius, w, h), colour, 0); }
    static void gradient(GuiGraphics g, float x, float y, float w, float h, float radius, int top, int bottom) { UiRender.chamfer(g, x, y, w, h, steps(radius, w, h), top, bottom, 0); }
    /**
     * Marks a scrolled-off edge of a clipped list: a 1px rule exactly at the clip line, so rows end on a hard
     * pane edge instead of dissolving mid-line under a fade. {@code top} draws it on the line's lower side.
     */
    static void scrollEdge(GuiGraphics g, float x, float y, float w, boolean top, UiTheme t) {
        rect(g, x, top ? y : y - 1, w, 1, t.line());
    }
    /** Stepped outline; thickness is rounded to whole GUI pixels (at least one). */
    static void outline(GuiGraphics g, float x, float y, float w, float h, float radius, float thickness, int colour) { UiRender.chamfer(g, x, y, w, h, steps(radius, w, h), colour, Math.max(1, Math.round(thickness))); }
    /**
     * Hard drop shadow, Minecraft style: the shape repeated one GUI pixel down and right. {@code blur} is ignored;
     * the soft shadows it used to draw are gone.
     */
    static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, float blur, int colour) { UiRender.chamfer(g, x + 1, y + 1, w, h, steps(radius, w, h), colour, 0); }
    /**
     * Pixel disc: a square with a stepped cut deep enough to read as round at GUI scale. Nothing in the menu is
     * drawn smooth-round any more, so dots, knobs, swatches and hover discs share the stepped corners.
     */
    static void circle(GuiGraphics g, float cx, float cy, float r, int colour) {
        if (GuiState.preferences().smoothCorners) { UiRender.shape(g, cx - r, cy - r, r * 2, r * 2, r, colour, colour, 0); return; }
        // Below 3 units a cut corner reads as a plus sign, so small dots stay plain squares.
        int steps = r < 3 ? 0 : Math.max(1, Math.round(r * .45f));
        UiRender.chamfer(g, cx - r, cy - r, r * 2, r * 2, Math.min(steps, (int) (r * 2 / 3)), colour, 0);
    }

    static void icon(GuiGraphics g, String name, float x, float y, float size, int colour) { UiIcons.draw(g, name, x, y, size, colour); }

    private static java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skin;
    private static Object skinKey;

    /**
     * Skin lookup for whichever account is signed in right now. Rebuilt when the account (or its skin manager)
     * changes; while a switched account's profile is still being fetched it uses the bare id so the render thread
     * never waits on the network, then upgrades once the textured profile arrives.
     */
    static java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skin() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var future = ((dev.argus.client.gui.mixin.AccountSessionAccessor) mc).argus$profile();
        boolean ready = future.isDone() && !future.isCompletedExceptionally();
        var user = mc.getUser();
        Object key = java.util.List.of(user.getProfileId(), ready, System.identityHashCode(mc.getSkinManager()));
        if (skin == null || !key.equals(skinKey)) {
            var profile = ready ? mc.getGameProfile() : new com.mojang.authlib.GameProfile(user.getProfileId(), user.getName());
            skin = mc.getSkinManager().createLookup(profile, false);
            skinKey = key;
        }
        return skin;
    }

    /** The signed-in player's face (with hat layer), square like the skin it comes from. */
    static void face(GuiGraphics g, float x, float y, float size) { face(g, skin(), x, y, size, 1); }

    /** A player's face (with hat layer), faded by {@code opacity}. */
    static void face(GuiGraphics g, java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> lookup,
                     float x, float y, float size, float opacity) {
        if (opacity <= 0 || size <= 0) return;
        var texture = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(lookup.get().body().texturePath());
        float r = 0, s = 1 / 64f;
        int colour = alpha(0xffffffff, opacity);
        UiRender.image(g, texture.getTextureView(), texture.getSampler(), x, y, size, size, r, 8 * s, 8 * s, 16 * s, 16 * s, colour);
        UiRender.image(g, texture.getTextureView(), texture.getSampler(), x, y, size, size, r, 40 * s, 8 * s, 48 * s, 16 * s, colour);
    }

    /** Height of a card's title area. */
    static final float CARD_HEAD = 30, CARD_PAD = 10;

    /**
     * Settings group. No box, badge or icon: a heading, a 1px rule running from its end to the right edge, and
     * an optional status line (live state such as "Syncing…", never a description of what is below).
     * {@code icon} is ignored and goes once every caller stops passing it.
     */
    static void card(GuiGraphics g, float x, float y, float w, float h, String icon, String title, String subtitle, float reserveRight, UiTheme t) {
        float available = w - CARD_PAD * 2 - reserveRight;
        boolean sub = subtitle != null && !subtitle.isBlank();
        String shown = fit(title, available, UiSpace.BODY);
        float cy = y + (sub ? 10 : 15);
        header(g, shown, x + CARD_PAD, cy, t);
        float ruleX = x + CARD_PAD + width(shown, UiSpace.BODY) + 6, ruleEnd = x + w - CARD_PAD - reserveRight;
        if (ruleEnd - ruleX > 8) rect(g, ruleX, Math.round(cy), ruleEnd - ruleX, 1, alpha(t.line(), .7f));
        if (sub) text(g, fit(subtitle, available, UiSpace.CAPTION), x + CARD_PAD, y + 20, UiSpace.CAPTION, t.textDim());
    }

    /** Group header: sentence case, bold, primary text colour. The same on every screen. */
    static void header(GuiGraphics g, String title, float x, float centreY, UiTheme t) {
        bold(g, title, x, centreY, UiSpace.BODY, t.text());
    }

    /**
     * Page title, in Minecraft's own font at a whole number of framebuffer pixels per font pixel, with vanilla's
     * shadow, so it stays crisp. This is the menu's display voice: Lexend is for reading, the pixel face for naming.
     */
    static void title(GuiGraphics g, String text, float x, float centreY, int colour) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float k = UiRender.pixelsPerUnit(g);
        // Vanilla capitals are 7 font pixels tall; pick the whole-pixel scale closest to the page-title cap height.
        int perPixel = Math.max(1, Math.round(UiSpace.PAGE_TITLE * .72f / 7f * k));
        float s = perPixel / k;
        g.pose().pushMatrix();
        g.pose().translate(x, centreY - 3.5f * s);
        g.pose().scale(s, s);
        // Vanilla's shadow reads as depth on dark glass but as a smudge under dark text on the light theme.
        g.drawString(font, Component.literal(text), 0, 0, UiRender.fade(colour), GuiState.preferences().mode == GuiPreferences.Mode.DARK);
        g.pose().popMatrix();
    }

    /**
     * Vanilla-style bevelled face (the bevel style trial): a 1px light edge top and left, a dark edge bottom and
     * right, swapped while {@code pressed}. Corners are left out, which reads as a one-step chamfer.
     */
    static void bevel(GuiGraphics g, float x, float y, float w, float h, boolean pressed, int fill) {
        int light = mix(fill, alpha(0xffffffff, fill >>> 24), .14f), dark = mix(fill, alpha(0xff000000, fill >>> 24), .35f);
        int topLeft = pressed ? dark : light, bottomRight = pressed ? light : dark;
        box(g, x, y, w, h, UiSpace.CHAMFER_SMALL, fill);
        rect(g, x + 1, y, w - 2, 1, topLeft);
        rect(g, x, y + 1, 1, h - 2, topLeft);
        rect(g, x + 1, y + h - 1, w - 2, 1, bottomRight);
        rect(g, x + w - 1, y + 1, 1, h - 2, bottomRight);
    }

    /** Width of {@link #title} for the current title style. */
    static float titleWidth(GuiGraphics g, String text) {
        float k = UiRender.pixelsPerUnit(g);
        int perPixel = Math.max(1, Math.round(UiSpace.PAGE_TITLE * .72f / 7f * k));
        return net.minecraft.client.Minecraft.getInstance().font.width(text) * perPixel / k;
    }

    /** A number or other changing value: tabular digits so it doesn't jitter as it changes. */
    static void number(GuiGraphics g, String text, float x, float centreY, float size, int colour) {
        UiFont.drawCentred(g, text, x, centreY, size, colour, false, true);
    }
    static float numberWidth(String text, float size) { return UiFont.width(text, size, density, true); }
    static void numberRight(GuiGraphics g, String text, float right, float centreY, float size, int colour) {
        number(g, text, right - numberWidth(text, size), centreY, size, colour);
    }

    /** Box with stepped pixel corners. */
    static void box(GuiGraphics g, float x, float y, float w, float h, int steps, int colour) { UiRender.chamfer(g, x, y, w, h, steps, colour, 0); }
    /** One-pixel outline of a {@link #box}. */
    static void frame(GuiGraphics g, float x, float y, float w, float h, int steps, int colour) { UiRender.chamfer(g, x, y, w, h, steps, colour, 1); }

    /** Minecraft's shadow colour for something drawn in {@code colour}: the same hue at a quarter of the brightness. */
    static int shade(int colour) {
        int r = ((colour >> 16) & 255) / 4, gr = ((colour >> 8) & 255) / 4, b = (colour & 255) / 4;
        return (colour & 0xff000000) | r << 16 | gr << 8 | b;
    }

    /**
     * Window: opaque body with stepped corners, a 1px edge and a hard 1-unit shadow down and right, coloured
     * the way vanilla shades text. {@code lift} (0…1, e.g. while dragged) pushes the shadow one unit further.
     */
    static void panel(GuiGraphics g, float x, float y, float w, float h, UiTheme theme, float lift) {
        float offset = 1 + Math.round(Math.clamp(lift, 0, 1));
        int steps = UiSpace.chamfer();
        boolean grain = GuiState.preferences().grain && GuiState.preferences().mode == GuiPreferences.Mode.DARK;
        box(g, x + offset, y + offset, w, h, steps, shade(theme.bg()));
        UiRender.chamfer(g, x, y, w, h, steps, theme.bg(), theme.bg(), 0, grain);
        frame(g, x, y, w, h, steps, theme.line());
    }
    static void panel(GuiGraphics g, float x, float y, float w, float h, UiTheme theme) { panel(g, x, y, w, h, theme, 0); }

    // ---- controls ---------------------------------------------------------------------------
    // Controls carry state through colour alone: no glows, halos or shadows.

    /**
     * Switch; {@code on} animates 0…1. A sunken stepped track with an edge, so it reads on the window body and
     * inside a filled chip alike, and a square knob that slides on whole pixels. The one on/off control.
     */
    static void toggle(GuiGraphics g, float x, float y, float on, boolean hover, UiTheme theme) {
        float w = 18, h = 10, knob = 6, travel = w - knob - 4;
        box(g, x, y, w, h, UiSpace.CHAMFER_SMALL, mix(theme.deep(), theme.on(), on));
        frame(g, x, y, w, h, UiSpace.CHAMFER_SMALL, mix(hover ? theme.textDim() : theme.line(), theme.on(), on));
        int knobColour = mix(hover ? theme.text() : theme.textDim(), theme.deep(), on);
        if (GuiState.preferences().smoothCorners) circle(g, x + 2 + Math.round(travel * on) + knob / 2f, y + 2 + knob / 2f, knob / 2f, knobColour);
        else rect(g, x + 2 + Math.round(travel * on), y + 2, knob, knob, knobColour);
    }

    /** Checkbox with an animated tick. */
    static void checkbox(GuiGraphics g, float x, float y, float size, float on, UiTheme theme) {
        box(g, x, y, size, size, UiSpace.CHAMFER_SMALL, mix(theme.fill(), theme.on(), on));
        if (on < 1) frame(g, x, y, size, size, UiSpace.CHAMFER_SMALL, alpha(theme.line(), 1 - on));
        if (on > .02f) icon(g, "check", x + 1, y + 1, size - 2, alpha(theme.deep(), on));
    }

    /** Horizontal value slider; {@code value} 0…1, {@code hover} 0…1 grows the thumb slightly. */
    static void slider(GuiGraphics g, float x, float y, float w, float value, float hover, UiTheme theme) {
        float track = 2, cy = y;
        rect(g, x, cy - track / 2, w, track, theme.fill());
        rect(g, x, cy - track / 2, Math.max(track, w * value), track, theme.on());
        // Square thumb with a sunken core; grows by one pixel on hover rather than easing.
        float r = hover > .5f ? 4 : 3.5f, tx = x + w * value;
        box(g, tx - r, cy - r, r * 2, r * 2, UiSpace.CHAMFER_SMALL, theme.on());
        rect(g, tx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, theme.deep());
    }
}

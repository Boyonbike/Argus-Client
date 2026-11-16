package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

/** Shared drawing vocabulary. All coordinates are logical GUI units; output is pixel exact. */
final class UiDraw {
    private UiDraw() {}

    /** Body text, secondary text and headings, in logical units. */
    static final float TEXT = 7.5f, SMALL = 6.5f, TITLE = 8.25f;

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
    static void text(GuiGraphicsExtractor g, String text, float x, float centreY, float size, int colour) { UiFont.drawCentred(g, text, x, centreY, size, colour, false); }
    static void text(GuiGraphicsExtractor g, String text, float x, float centreY, int colour) { text(g, text, x, centreY, TEXT, colour); }
    static void bold(GuiGraphicsExtractor g, String text, float x, float centreY, float size, int colour) { UiFont.drawCentred(g, text, x, centreY, size, colour, true); }

    static void center(GuiGraphicsExtractor g, String text, float x, float centreY, float width, float size, int colour) {
        text(g, text, x + (width - width(text, size)) / 2, centreY, size, colour);
    }
    static void center(GuiGraphicsExtractor g, String text, float x, float centreY, float width, int colour) { center(g, text, x, centreY, width, TEXT, colour); }

    static void right(GuiGraphicsExtractor g, String text, float right, float centreY, float size, int colour) {
        text(g, text, right - width(text, size), centreY, size, colour);
    }
    static void right(GuiGraphicsExtractor g, String text, float right, float centreY, int colour) { right(g, text, right, centreY, TEXT, colour); }

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

    static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int colour) { UiRender.shape(g, x, y, w, h, 0, colour, colour, 0); }
    static void rounded(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, 0); }
    static void gradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) { UiRender.shape(g, x, y, w, h, radius, top, bottom, 0); }
    static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float thickness, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, Math.max(.01f, thickness)); }
    static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float blur, int colour) { UiRender.shape(g, x, y, w, h, radius, colour, colour, -Math.max(.5f, blur)); }
    static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int colour) { rounded(g, cx - r, cy - r, r * 2, r * 2, r, colour); }

    static void icon(GuiGraphicsExtractor g, String name, float x, float y, float size, int colour) { UiIcons.draw(g, name, x, y, size, colour); }

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

    /** The signed-in player's face (with hat layer) clipped to a circle. */
    static void face(GuiGraphicsExtractor g, float x, float y, float size) { face(g, skin(), x, y, size, 1); }

    /** A player's face (with hat layer) clipped to a circle, faded by {@code opacity}. */
    static void face(GuiGraphicsExtractor g, java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> lookup,
                     float x, float y, float size, float opacity) {
        if (opacity <= 0 || size <= 0) return;
        var texture = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(lookup.get().body().texturePath());
        float r = size / 2, s = 1 / 64f;
        int colour = alpha(0xffffffff, opacity);
        UiRender.image(g, texture.getTextureView(), texture.getSampler(), x, y, size, size, r, 8 * s, 8 * s, 16 * s, 16 * s, colour);
        UiRender.image(g, texture.getTextureView(), texture.getSampler(), x, y, size, size, r, 40 * s, 8 * s, 48 * s, 16 * s, colour);
    }

    /** Height of a card's title area. */
    static final float CARD_HEAD = 30, CARD_PAD = 10;

    /**
     * Settings group. No box, badge or icon: the group is a header (uppercase caption) and, while copy is
     * still being trimmed, an optional one-line subtitle. Groups are separated by space, not borders.
     * {@code icon} is ignored and goes once every caller stops passing it.
     */
    static void card(GuiGraphicsExtractor g, float x, float y, float w, float h, String icon, String title, String subtitle, float reserveRight, UiTheme t) {
        float available = w - CARD_PAD * 2 - reserveRight;
        boolean sub = subtitle != null && !subtitle.isBlank();
        header(g, fit(title, available, UiSpace.CAPTION), x + CARD_PAD, y + (sub ? 10 : 15), t);
        if (sub) text(g, fit(subtitle, available, UiSpace.CAPTION), x + CARD_PAD, y + 20, UiSpace.CAPTION, t.textDim());
    }

    /** Group header: uppercase caption in the secondary text colour, the same on every screen. */
    static void header(GuiGraphicsExtractor g, String title, float x, float centreY, UiTheme t) {
        text(g, title.toUpperCase(java.util.Locale.ROOT), x, centreY, UiSpace.CAPTION, t.textDim());
    }

    /** A number or other changing value: tabular digits so it doesn't jitter as it changes. */
    static void number(GuiGraphicsExtractor g, String text, float x, float centreY, float size, int colour) {
        UiFont.drawCentred(g, text, x, centreY, size, colour, false, true);
    }
    static float numberWidth(String text, float size) { return UiFont.width(text, size, density, true); }
    static void numberRight(GuiGraphicsExtractor g, String text, float right, float centreY, float size, int colour) {
        number(g, text, right - numberWidth(text, size), centreY, size, colour);
    }

    /** Box with stepped pixel corners. */
    static void box(GuiGraphicsExtractor g, float x, float y, float w, float h, int steps, int colour) { UiRender.chamfer(g, x, y, w, h, steps, colour, 0); }
    /** One-pixel outline of a {@link #box}. */
    static void frame(GuiGraphicsExtractor g, float x, float y, float w, float h, int steps, int colour) { UiRender.chamfer(g, x, y, w, h, steps, colour, 1); }

    /** Minecraft's shadow colour for something drawn in {@code colour}: the same hue at a quarter of the brightness. */
    static int shade(int colour) {
        int r = ((colour >> 16) & 255) / 4, gr = ((colour >> 8) & 255) / 4, b = (colour & 255) / 4;
        return (colour & 0xff000000) | r << 16 | gr << 8 | b;
    }

    /**
     * Window: opaque body with stepped corners, a 1px edge and a hard 1-unit shadow down and right, coloured
     * the way vanilla shades text. {@code lift} (0…1, e.g. while dragged) pushes the shadow one unit further.
     */
    static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, UiTheme theme, float lift) {
        float offset = 1 + Math.round(Math.clamp(lift, 0, 1));
        box(g, x + offset, y + offset, w, h, UiSpace.CHAMFER, shade(theme.bg()));
        box(g, x, y, w, h, UiSpace.CHAMFER, theme.bg());
        frame(g, x, y, w, h, UiSpace.CHAMFER, theme.line());
    }
    static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, UiTheme theme) { panel(g, x, y, w, h, theme, 0); }

    // ---- controls ---------------------------------------------------------------------------
    // Controls carry state through colour alone: no glows, halos or shadows.

    /** Switch; {@code on} animates 0…1. Stepped track, round knob. */
    static void toggle(GuiGraphicsExtractor g, float x, float y, float on, boolean hover, UiTheme theme) {
        float w = 18, h = 10;
        box(g, x, y, w, h, UiSpace.CHAMFER_SMALL, mix(hover ? theme.line() : theme.fill(), theme.on(), on));
        float knob = h - 4, travel = w - h;
        circle(g, x + h / 2 + travel * on, y + h / 2, knob / 2, mix(theme.textDim(), theme.deep(), on));
    }

    /** Checkbox with an animated tick. */
    static void checkbox(GuiGraphicsExtractor g, float x, float y, float size, float on, UiTheme theme) {
        box(g, x, y, size, size, UiSpace.CHAMFER_SMALL, mix(theme.fill(), theme.on(), on));
        if (on < 1) frame(g, x, y, size, size, UiSpace.CHAMFER_SMALL, alpha(theme.line(), 1 - on));
        if (on > .02f) icon(g, "check", x + 1, y + 1, size - 2, alpha(theme.deep(), on));
    }

    /** Horizontal value slider; {@code value} 0…1, {@code hover} 0…1 grows the thumb slightly. */
    static void slider(GuiGraphicsExtractor g, float x, float y, float w, float value, float hover, UiTheme theme) {
        float track = 2, cy = y;
        rect(g, x, cy - track / 2, w, track, theme.fill());
        rect(g, x, cy - track / 2, Math.max(track, w * value), track, theme.on());
        float r = 3.5f + hover * .5f;
        circle(g, x + w * value, cy, r, theme.on());
        circle(g, x + w * value, cy, r - 1.5f, theme.deep());
    }
}

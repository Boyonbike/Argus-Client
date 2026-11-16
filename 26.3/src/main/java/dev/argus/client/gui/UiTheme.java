package dev.argus.client.gui;

/**
 * Argus colour roles. Every colour is named for its job, not its hue:
 * <ul>
 *   <li>{@code bg} window body, {@code sunk} wells and sidebars, {@code deep} the darkest step (and the
 *       colour drawn on top of {@code on}, e.g. a toggle knob or tick)</li>
 *   <li>{@code fill} control tracks and hover rows, {@code line} 1px dividers and outlines</li>
 *   <li>{@code text} primary copy, {@code textDim} secondary copy (≥4.5:1 on bg, sunk and fill)</li>
 *   <li>{@code on} the accent: enabled, selected and focused state only, never decoration</li>
 *   <li>{@code ok}, {@code info}, {@code warn}, {@code danger} status only</li>
 * </ul>
 * Neutrals are warm graphite rather than tinted blue-black so the accent stays the only hue on screen.
 */
public record UiTheme(int bg, int sunk, int deep, int fill, int line, int text,
                      int textDim, int on, int ok, int info, int warn, int danger) {
    public static UiTheme of(GuiPreferences preferences) {
        boolean light = preferences.mode == GuiPreferences.Mode.LIGHT;
        int on = 0xff000000 | accent(preferences.accent, light);
        return light
                ? new UiTheme(0xfff2f1ee, 0xffe8e6e1, 0xffdedbd4, 0xffdcd9d2, 0xffc4c0b7, 0xff1f1f1d,
                        0xff5d5b55, on, 0xff3d7a24, 0xff2a64a3, 0xff8f5f0a, 0xffb23a2c)
                : new UiTheme(0xff1b1c1e, 0xff161719, 0xff111213, 0xff2a2c30, 0xff3a3d42, 0xffe6e4df,
                        0xffa19e97, on, 0xff8cc26e, 0xff7db3e0, 0xffd9a54a, 0xffe07a6a);
    }

    /** Accents share one lightness band per mode so switching never changes the UI's contrast. */
    static int accent(GuiPreferences.Accent accent, boolean light) {
        return switch (accent) {
            case TEAL -> light ? 0x1d7a6d : 0x6db8ab;
            case AMBER -> light ? 0x8f5f0a : 0xd9a54a;
            case LEAF -> light ? 0x3d7a24 : 0x8cc26e;
            case SKY -> light ? 0x2a64a3 : 0x7db3e0;
            case ROSE -> light ? 0xa83a52 : 0xe08a9a;
            case STONE -> light ? 0x5f5a50 : 0xb8b2a6;
        };
    }

    static UiTheme blend(UiTheme a, UiTheme b, float t) {
        return new UiTheme(UiDraw.mix(a.bg, b.bg, t), UiDraw.mix(a.sunk, b.sunk, t), UiDraw.mix(a.deep, b.deep, t),
                UiDraw.mix(a.fill, b.fill, t), UiDraw.mix(a.line, b.line, t), UiDraw.mix(a.text, b.text, t),
                UiDraw.mix(a.textDim, b.textDim, t), UiDraw.mix(a.on, b.on, t), UiDraw.mix(a.ok, b.ok, t),
                UiDraw.mix(a.info, b.info, t), UiDraw.mix(a.warn, b.warn, t), UiDraw.mix(a.danger, b.danger, t));
    }

    // ---- Old palette names, kept so screens compile while phase 3 moves them to the roles above. ----
    @Deprecated public int base() { return bg; }
    @Deprecated public int mantle() { return sunk; }
    @Deprecated public int crust() { return deep; }
    @Deprecated public int surface() { return fill; }
    @Deprecated public int overlay() { return line; }
    @Deprecated public int muted() { return textDim; }
    @Deprecated public int accent() { return on; }
    @Deprecated public int green() { return ok; }
    @Deprecated public int blue() { return info; }
    @Deprecated public int yellow() { return warn; }
    @Deprecated public int red() { return danger; }
}

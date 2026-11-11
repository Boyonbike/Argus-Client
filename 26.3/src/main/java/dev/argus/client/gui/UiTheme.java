package dev.argus.client.gui;

/** Catppuccin-inspired colours, exposed as Light/Dark and an independent accent. */
public record UiTheme(int base, int mantle, int crust, int surface, int overlay, int text,
                      int muted, int accent, int green, int blue, int yellow, int red) {
    public static UiTheme of(GuiPreferences preferences) {
        boolean light = preferences.mode == GuiPreferences.Mode.LIGHT;
        int accent = switch (preferences.accent) {
            case LAVENDER -> light ? 0x8839ef : 0xcba6f7;
            case BLUE -> light ? 0x1e66f5 : 0x89b4fa;
            case GREEN -> light ? 0x40a02b : 0xa6e3a1;
            case PEACH -> light ? 0xfe640b : 0xfab387;
            case PINK -> light ? 0xea76cb : 0xf5c2e7;
            case RED -> light ? 0xd20f39 : 0xf38ba8;
        };
        return light ? new UiTheme(0xffeff1f5,0xffe6e9ef,0xffdce0e8,0xffccd0da,0xffbcc0cc,0xff4c4f69,
                0xff6c6f85,0xff000000|accent,0xff40a02b,0xff1e66f5,0xffdf8e1d,0xffd20f39)
                : new UiTheme(0xff1e1e2e,0xff181825,0xff11111b,0xff313244,0xff45475a,0xffcdd6f4,
                0xffa6adc8,0xff000000|accent,0xffa6e3a1,0xff89b4fa,0xfff9e2af,0xfff38ba8);
    }

    static UiTheme blend(UiTheme a, UiTheme b, float t) {
        return new UiTheme(UiDraw.mix(a.base, b.base, t), UiDraw.mix(a.mantle, b.mantle, t), UiDraw.mix(a.crust, b.crust, t),
                UiDraw.mix(a.surface, b.surface, t), UiDraw.mix(a.overlay, b.overlay, t), UiDraw.mix(a.text, b.text, t),
                UiDraw.mix(a.muted, b.muted, t), UiDraw.mix(a.accent, b.accent, t), UiDraw.mix(a.green, b.green, t),
                UiDraw.mix(a.blue, b.blue, t), UiDraw.mix(a.yellow, b.yellow, t), UiDraw.mix(a.red, b.red, t));
    }
}

package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Soft pill button with eased hover/press feedback. {@code selected} buttons use the accent. */
final class UiButton extends Button {
    private final boolean selected;
    private final String icon;
    private float hover, press, arm;
    private long pressedAt;
    /** Optional two-click confirm: when it was armed (0 = not), how long it stays armed, and the armed label. */
    private java.util.function.LongSupplier armedAt;
    private long armWindow;
    private String armedLabel;

    UiButton(int x, int y, int width, int height, String label, Runnable action) { this(x, y, width, height, label, false, null, action); }
    UiButton(int x, int y, int width, int height, String label, boolean selected, Runnable action) { this(x, y, width, height, label, selected, null, action); }
    UiButton(int x, int y, int width, int height, String label, boolean selected, String icon, Runnable action) {
        super(x, y, width, height, Component.literal(label), button -> action.run(), DEFAULT_NARRATION);
        this.selected = selected; this.icon = icon;
    }

    /** Shows a red armed state with {@code label} and a draining timer while {@code armedAt} is within {@code window}. */
    UiButton confirm(java.util.function.LongSupplier armedAt, long window, String label) { this.armedAt = armedAt; this.armWindow = window; this.armedLabel = label; return this; }

    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { pressedAt = System.nanoTime(); super.onPress(input); }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        hover = UiAnim.approach(hover, isHoveredOrFocused() && active ? 1 : 0, 18);
        press = 1 - UiAnim.progress(pressedAt, .18f);
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float r = Math.min(7, h / 2);
        long at = armedAt == null ? 0 : armedAt.getAsLong();
        boolean armed = at != 0 && System.nanoTime() - at < armWindow;
        arm = UiAnim.approach(arm, armed ? 1 : 0, 16);
        int fill = selected ? UiDraw.alpha(t.accent(), .2f + hover * .08f) : UiDraw.mix(t.surface(), t.overlay(), hover * .8f);
        UiDraw.rounded(g, x, y, w, h, r, UiDraw.mix(fill, UiDraw.alpha(t.red(), .22f + hover * .1f), arm));
        if (press > 0) UiDraw.rounded(g, x, y, w, h, r, UiDraw.alpha(armed ? t.red() : t.accent(), press * .22f));
        UiDraw.outline(g, x, y, w, h, r, .6f, UiDraw.mix(selected ? UiDraw.alpha(t.accent(), .55f) : UiDraw.alpha(t.overlay(), .5f + hover * .3f), UiDraw.alpha(t.red(), .6f), arm));
        if (armed) UiMotion.drain(g, x + 6, y + h - 3, w - 12, at, armWindow, UiDraw.alpha(t.red(), .8f));
        String label = armed ? armedLabel : getMessage().getString();
        int colour = UiDraw.mix(!active ? t.muted() : selected ? t.accent() : UiDraw.mix(t.text(), 0xffffffff, hover * .15f), t.red(), arm);
        float iconSize = 8, gap = label.isEmpty() ? 0 : 4;
        float labelWidth = label.isEmpty() ? 0 : UiDraw.width(UiDraw.fit(label, w - 10 - (icon == null ? 0 : iconSize + gap)));
        float content = labelWidth + (icon == null ? 0 : iconSize + gap);
        float start = x + (w - content) / 2;
        if (icon != null) { UiDraw.icon(g, icon, start, y + (h - iconSize) / 2, iconSize, colour); start += iconSize + gap; }
        if (armedAt != null) {
            // Confirm buttons crossfade between their two labels, each centred on its own.
            float labelRoom = w - 10 - (icon == null ? 0 : iconSize + gap);
            UiMotion.crossfade(g, "btn." + System.identityHashCode(this), label, l -> {
                String fitted = UiDraw.fit(l, labelRoom);
                float lx = icon == null ? x + (w - UiDraw.width(fitted)) / 2 : x + (w - UiDraw.width(fitted) - iconSize - gap) / 2 + iconSize + gap;
                UiDraw.text(g, fitted, lx, y + h / 2, colour);
            });
        } else if (!label.isEmpty()) UiDraw.text(g, UiDraw.fit(label, w - 10), start, y + h / 2, colour);
    }
}

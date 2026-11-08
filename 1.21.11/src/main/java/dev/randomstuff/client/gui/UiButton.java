package dev.randomstuff.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Soft pill button with eased hover/press feedback. {@code selected} buttons use the accent. */
final class UiButton extends Button {
    private final boolean selected;
    private final String icon;
    private float hover, press;
    private long pressedAt;

    UiButton(int x, int y, int width, int height, String label, Runnable action) { this(x, y, width, height, label, false, null, action); }
    UiButton(int x, int y, int width, int height, String label, boolean selected, Runnable action) { this(x, y, width, height, label, selected, null, action); }
    UiButton(int x, int y, int width, int height, String label, boolean selected, String icon, Runnable action) {
        super(x, y, width, height, Component.literal(label), button -> action.run(), DEFAULT_NARRATION);
        this.selected = selected; this.icon = icon;
    }

    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { pressedAt = System.nanoTime(); super.onPress(input); }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        hover = UiAnim.approach(hover, isHoveredOrFocused() && active ? 1 : 0, 18);
        press = 1 - Math.clamp((System.nanoTime() - pressedAt) / 180e6f, 0f, 1f);
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float r = Math.min(7, h / 2);
        int fill = selected ? UiDraw.alpha(t.accent(), .2f + hover * .08f) : UiDraw.mix(t.surface(), t.overlay(), hover * .8f);
        UiDraw.rounded(g, x, y, w, h, r, fill);
        if (press > 0) UiDraw.rounded(g, x, y, w, h, r, UiDraw.alpha(t.accent(), press * .22f));
        UiDraw.outline(g, x, y, w, h, r, .6f, selected ? UiDraw.alpha(t.accent(), .55f) : UiDraw.alpha(t.overlay(), .5f + hover * .3f));
        String label = getMessage().getString();
        int colour = !active ? t.muted() : selected ? t.accent() : UiDraw.mix(t.text(), 0xffffffff, hover * .15f);
        float iconSize = 8, gap = label.isEmpty() ? 0 : 4;
        float labelWidth = label.isEmpty() ? 0 : UiDraw.width(UiDraw.fit(label, w - 10 - (icon == null ? 0 : iconSize + gap)));
        float content = labelWidth + (icon == null ? 0 : iconSize + gap);
        float start = x + (w - content) / 2;
        if (icon != null) { UiDraw.icon(g, icon, start, y + (h - iconSize) / 2, iconSize, colour); start += iconSize + gap; }
        if (!label.isEmpty()) UiDraw.text(g, UiDraw.fit(label, w - 10), start, y + h / 2, colour);
    }
}

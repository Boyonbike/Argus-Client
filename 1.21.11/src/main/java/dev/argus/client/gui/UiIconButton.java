package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Round icon-only button with the same hover/press feedback as {@link UiButton}; the label shows as a tooltip. */
final class UiIconButton extends Button {
    private final String icon;
    private float hover;
    private long pressedAt;

    UiIconButton(int x, int y, int size, String icon, String label, Runnable action) {
        super(x, y, size, size, Component.literal(label), button -> action.run(), DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(Component.literal(label)));
    }

    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { pressedAt = System.nanoTime(); super.onPress(input); }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        hover = UiAnim.approach(hover, isHoveredOrFocused() && active ? 1 : 0, 18);
        float press = 1 - UiAnim.progress(pressedAt, .18f);
        float r = getWidth() / 2f, cx = getX() + r, cy = getY() + r;
        UiDraw.circle(g, cx, cy, r, UiDraw.mix(t.surface(), t.overlay(), hover * .8f));
        if (press > 0) UiDraw.circle(g, cx, cy, r, UiDraw.alpha(t.accent(), press * .22f));
        UiDraw.outline(g, getX(), getY(), getWidth(), getHeight(), r, .6f, UiDraw.alpha(t.overlay(), .5f + hover * .3f));
        float size = 10;
        UiDraw.icon(g, icon, cx - size / 2, cy - size / 2, size, UiDraw.mix(t.text(), t.accent(), hover));
    }
}

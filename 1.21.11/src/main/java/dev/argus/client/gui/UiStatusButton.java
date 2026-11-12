package dev.argus.client.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Round button whose glyph reflects some live state (the active proxy, the signed-in account). When the state
 * changes the old glyph shrinks away, the new one pops in with a slight overshoot and a ring ripples outward.
 * The last state each button showed is remembered across screens, so a change made in the Accounts or Proxies
 * menu plays its animation when you come back.
 */
final class UiStatusButton<S> extends Button {
    /** Draws the glyph for {@code state} centred on (cx, cy), scaled by {@code scale} and faded by {@code opacity}. */
    interface Glyph<S> { void draw(GuiGraphics g, S state, float cx, float cy, float r, float scale, float opacity, float hover, UiTheme t); }

    private static final Map<String, Object> LAST_SEEN = new HashMap<>();
    private static final float OUT = .16f, IN = .42f, RIPPLE = .6f;

    private final String key;
    private final Supplier<S> state;
    private final Glyph<S> glyph;
    private final java.util.function.BiFunction<S, UiTheme, Integer> tint;
    private final java.util.function.Function<S, String> label;
    private final java.util.function.BooleanSupplier foreground;
    private S shown, previous;
    private long changedAt;
    private float hover, tintAmount = -1;
    private long pressedAt;

    /**
     * {@code foreground} says whether the button's own screen is the one in front. While a dialog (e.g. Accounts)
     * draws this screen underneath itself, the button keeps showing the old state; the change is picked up, and
     * animated, once the dialog closes and you can actually see it.
     */
    UiStatusButton(int x, int y, int size, String key, Supplier<S> state, Glyph<S> glyph,
                   java.util.function.BiFunction<S, UiTheme, Integer> tint, java.util.function.Function<S, String> label,
                   java.util.function.BooleanSupplier foreground, Runnable action) {
        super(x, y, size, size, Component.empty(), button -> action.run(), DEFAULT_NARRATION);
        this.key = key; this.state = state; this.glyph = glyph; this.tint = tint; this.label = label; this.foreground = foreground;
        @SuppressWarnings("unchecked") S last = (S) LAST_SEEN.get(key);
        this.shown = LAST_SEEN.containsKey(key) ? last : state.get();
        setMessage(Component.literal(label.apply(shown)));
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { pressedAt = System.nanoTime(); super.onPress(input); }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        S now = foreground.getAsBoolean() ? state.get() : shown;
        if (!Objects.equals(now, shown)) {
            previous = shown; shown = now; changedAt = System.nanoTime();
            setMessage(Component.literal(label.apply(now)));
            setTooltip(Tooltip.create(getMessage()));
        }
        LAST_SEEN.put(key, shown);

        hover = UiAnim.approach(hover, isHoveredOrFocused() && active ? 1 : 0, 18);
        Integer target = tint.apply(shown, t);
        float tintTarget = target == null ? 0 : 1;
        tintAmount = tintAmount < 0 ? tintTarget : UiAnim.approach(tintAmount, tintTarget, 8);
        int tc = target != null ? target : t.accent();
        float press = 1 - UiAnim.progress(pressedAt, .18f);
        float r = getWidth() / 2f, cx = getX() + r, cy = getY() + r;

        // Soft halo that breathes while the tinted state is on (e.g. a live proxy).
        if (tintAmount > .01f)
            UiDraw.shadow(g, cx - r, cy - r, r * 2, r * 2, r, 3 + UiAnim.pulse(4.5e8) * 2.5f, UiDraw.alpha(tc, tintAmount * (.22f + .14f * UiAnim.pulse(4.5e8))));
        UiDraw.circle(g, cx, cy, r, UiDraw.mix(UiDraw.mix(t.surface(), t.overlay(), hover * .8f), tc, tintAmount * .16f));
        if (press > 0) UiDraw.circle(g, cx, cy, r, UiDraw.alpha(t.accent(), press * .22f));

        boolean animating = changedAt != 0 && UiAnim.progress(changedAt, RIPPLE) < 1;
        if (animating) {
            float out = UiAnim.progress(changedAt, OUT);
            if (previous != null && out < 1) {
                float e = UiAnim.easeInOutSine(out);
                glyph.draw(g, previous, cx, cy, r, 1 - e * .6f, 1 - e, hover, t);
            }
            float in = UiAnim.progress(changedAt + (long) (OUT * .5e9f / UiAnim.speed()), IN);
            if (in > 0) glyph.draw(g, shown, cx, cy, r, .4f + .6f * UiAnim.easeOutBack(in), Math.min(1, in * 2.5f), hover, t);
            float rp = UiAnim.progress(changedAt, RIPPLE), re = UiAnim.easeOutCubic(rp);
            UiDraw.outline(g, cx - r - re * 7, cy - r - re * 7, (r + re * 7) * 2, (r + re * 7) * 2, r + re * 7, 1.2f * (1 - rp) + .2f,
                    UiDraw.alpha(tc, (1 - rp) * .85f));
        } else {
            glyph.draw(g, shown, cx, cy, r, 1, 1, hover, t);
        }
        UiDraw.outline(g, getX(), getY(), getWidth(), getHeight(), r, .6f,
                UiDraw.mix(UiDraw.alpha(t.overlay(), .5f + hover * .3f), UiDraw.alpha(tc, .75f + hover * .25f), Math.max(tintAmount * .8f, hover * .6f)));
    }
}

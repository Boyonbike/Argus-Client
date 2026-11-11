package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small, shared motion patterns built on {@link UiAnim}: crossfading a value when it changes,
 * flashes on change, rows that fade in and glide when their list changes, and confirm timers.
 * Everything here follows the Appearance animation switch and speed.
 */
final class UiMotion {
    private UiMotion() {}

    /** How long a crossfade between two values takes at normal speed. */
    static final float SWAP_SECONDS = .16f;
    /** How far (logical units) the old value drifts out and the new one drifts in. */
    static final float SWAP_DRIFT = 3;

    private static final class Swap { Object previous, current; long changedAt; }
    private static final Map<String, Swap> swaps = new HashMap<>();

    private static Swap swap(String key, Object value) {
        Swap s = swaps.get(key);
        if (s == null) { s = new Swap(); s.previous = s.current = value; swaps.put(key, s); }
        else if (!Objects.equals(s.current, value)) { s.previous = s.current; s.current = value; s.changedAt = System.nanoTime(); }
        return s;
    }

    /** Eased 0…1 since {@code value} last changed under {@code key}; 1 when it has settled (or never changed). */
    static float sinceChange(String key, Object value) {
        Swap s = swap(key, value);
        return s.changedAt == 0 ? 1 : UiAnim.eased(s.changedAt, .22f);
    }

    /** A 1 → 0 flash for {@code value} changing under {@code key}, e.g. to tint a counter that just ticked. */
    static float flash(String key, Object value) {
        Swap s = swap(key, value);
        return s.changedAt == 0 ? 0 : 1 - UiAnim.eased(s.changedAt, .45f);
    }

    /** Crossfades vertically: the old value rises out while the new one rises in. */
    static <T> void crossfade(GuiGraphics g, String key, T value, Consumer<T> draw) { crossfade(g, key, value, 0, SWAP_DRIFT, draw); }

    /**
     * Draws {@code value} with {@code draw}, crossfading from the previous value whenever it
     * changes. The old value drifts out by ({@code -dx}, {@code -dy}) and the new one drifts in from
     * ({@code dx}, {@code dy}). A null value draws nothing, so text can fade out to empty.
     */
    static <T> void crossfade(GuiGraphics g, String key, T value, float dx, float dy, Consumer<T> draw) {
        Swap s = swap(key, value);
        float t = s.changedAt == 0 ? 1 : UiAnim.eased(s.changedAt, SWAP_SECONDS);
        float before = UiRender.opacity;
        try {
            if (t < 1 && s.previous != null) {
                UiRender.opacity = before * (1 - t);
                g.pose().pushMatrix();
                g.pose().translate(-dx * t, -dy * t);
                try { @SuppressWarnings("unchecked") T old = (T) s.previous; draw.accept(old); } finally { g.pose().popMatrix(); }
            }
            if (value != null) {
                UiRender.opacity = before * t;
                g.pose().pushMatrix();
                g.pose().translate(dx * (1 - t), dy * (1 - t));
                try { draw.accept(value); } finally { g.pose().popMatrix(); }
            }
        } finally { UiRender.opacity = before; }
    }

    /** Text that crossfades when it changes; empty or null text fades out. */
    static void text(GuiGraphics g, String key, String text, Consumer<String> draw) {
        crossfade(g, key, text == null || text.isEmpty() ? null : text, draw);
    }

    /** A line of text with a semantic kind (hint, error, notice…) so a crossfade keeps each line's own colour. */
    record Line(String text, int kind) {}

    /** Footer colours by kind: 0 hint, 1 error, 2 success, 3 waiting for input. */
    static int footerColour(int kind, UiTheme t) {
        return switch (kind) { case 1 -> t.red(); case 2 -> t.green(); case 3 -> t.accent(); default -> UiDraw.alpha(t.muted(), .8f); };
    }

    /** Eased y for a list row identified by {@code id}, so rows glide when others are added or removed. */
    static float rowY(String key, Object id, float y) { return UiAnim.smooth(key + ".y." + id, y, y, 16); }

    /** 0 → 1 the first time a row is seen, so new rows fade in. */
    static float rowAppear(String key, Object id) { return UiAnim.smooth(key + ".a." + id, 0, 1, 14); }

    /** Draws a confirm timer: a line under a button that drains as the confirm window runs out. */
    static void drain(GuiGraphics g, float x, float y, float w, long armedAt, long windowNanos, int colour) {
        float left = 1 - Math.clamp((System.nanoTime() - armedAt) / (float) windowNanos, 0f, 1f);
        if (left <= 0) return;
        float lw = w * left;
        UiDraw.rounded(g, x + (w - lw) / 2, y, lw, 1.2f, .6f, colour);
    }

    /** Runs {@code draw} at {@code alpha} of the current opacity; skips it when fully transparent. */
    static void faded(float alpha, Runnable draw) {
        if (alpha <= 0) return;
        float before = UiRender.opacity;
        UiRender.opacity = before * alpha;
        try { draw.run(); } finally { UiRender.opacity = before; }
    }

    /** Alpha for a scroll edge shadow: grows over the first few units of scroll instead of switching on. */
    static float edge(float distance) { return Math.clamp(distance / 12f, 0f, 1f); }
}

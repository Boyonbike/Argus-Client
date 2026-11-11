package dev.argus.client.gui;

import java.util.HashMap;
import java.util.Map;

/** Frame-rate independent easing used by every screen. */
final class UiAnim {
    private UiAnim() {}

    private static long lastFrame = System.nanoTime();
    private static float delta = 1 / 60f;
    private static final Map<String, float[]> values = new HashMap<>();

    /** Advances the shared clock; call once per rendered frame. */
    static void frame() {
        long now = System.nanoTime();
        delta = Math.clamp((now - lastFrame) / 1e9f, 0f, .1f);
        lastFrame = now;
    }

    static float delta() { return delta; }

    /** Whether menu motion is on (Settings → Appearance → Theme → Animations). Off, every change lands at once. */
    static boolean enabled() { return GuiState.preferences().animations; }

    /** The Appearance speed multiplier: above 1 plays motion faster, below 1 slower. */
    static float speed() { return (float) GuiState.preferences().animationSpeed; }

    /** Seconds since {@code startNanos} on the animation clock, i.e. scaled by {@link #speed()}. */
    static float elapsed(long startNanos) { return (System.nanoTime() - startNanos) / 1e9f * speed(); }

    /** This frame's delta on the animation clock. */
    static float animDelta() { return delta * speed(); }

    /** Linear 0…1 progress of a timed transition that started at {@code startNanos}; 1 at once when animations are off. */
    static float progress(long startNanos, float seconds) {
        if (!enabled()) return 1;
        return Math.clamp(elapsed(startNanos) / seconds, 0f, 1f);
    }

    /** {@link #progress} eased out, for entrances and content swaps. */
    static float eased(long startNanos, float seconds) { return easeOutCubic(progress(startNanos, seconds)); }

    /** A soft 0…1 pulse for "waiting for input" states; holds steady at the middle when animations are off. */
    static float pulse(double periodNanos) { return enabled() ? .5f + .5f * (float) Math.sin(System.nanoTime() / periodNanos) : .5f; }

    /** Exponential approach: {@code speed} ≈ how many e-folds per second. Snaps when animations are off. */
    static float approach(float current, float target, float speed) {
        if (!enabled()) return target;
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * animDelta()));
        return Math.abs(target - next) < .0005f ? target : next;
    }

    /** Keyed smoothed value, initialised to {@code target} the first time it is seen. */
    static float smooth(String key, float target, float speed) {
        float[] cell = values.computeIfAbsent(key, k -> new float[]{target});
        cell[0] = approach(cell[0], target, speed);
        return cell[0];
    }

    /** Keyed smoothed value with an explicit starting point the first time it is seen. */
    static float smooth(String key, float start, float target, float speed) {
        float[] cell = values.computeIfAbsent(key, k -> new float[]{start});
        cell[0] = approach(cell[0], target, speed);
        return cell[0];
    }

    static void set(String key, float value) { values.computeIfAbsent(key, k -> new float[1])[0] = value; }

    static void forget(String prefix) { values.keySet().removeIf(key -> key.startsWith(prefix)); }

    /** Smooth start and end, used for exits so they read as clearly as entrances. */
    static float easeInOutSine(float t) { t = Math.clamp(t, 0f, 1f); return .5f - .5f * (float) Math.cos(Math.PI * t); }

    static float easeOutCubic(float t) { t = Math.clamp(t, 0f, 1f); float u = 1 - t; return 1 - u * u * u; }

    /** Slight overshoot for things that pop in. */
    static float easeOutBack(float t) {
        t = Math.clamp(t, 0f, 1f);
        float c = 1.4f, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

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

    /** Exponential approach: {@code speed} ≈ how many e-folds per second. */
    static float approach(float current, float target, float speed) {
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * delta));
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

    static float easeOutCubic(float t) { t = Math.clamp(t, 0f, 1f); float u = 1 - t; return 1 - u * u * u; }

    /** Slight overshoot for things that pop in. */
    static float easeOutBack(float t) {
        t = Math.clamp(t, 0f, 1f);
        float c = 1.4f, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

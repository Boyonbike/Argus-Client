package dev.argus.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;

/**
 * The HUD's safe zones, one per screen edge. The base client only had SafeX (left and right) and
 * SafeY (top and bottom); those stay in the config, hidden, and seed the four sides the first time
 * a config without them is loaded.
 */
public final class HudSafeZones {
    public static final int MAX = 40;
    public static final String LEFT = "SafeLeft", RIGHT = "SafeRight", TOP = "SafeTop", BOTTOM = "SafeBottom";

    private static Setting<Integer> left, right, top, bottom, legacyX, legacyY;

    private HudSafeZones() {}

    /** Adds the four sides to the HUD module, right after the old SafeX/SafeY pair, and hides that pair. */
    public static void install(Module hud, Setting<Boolean> group, Setting<Integer> safeX, Setting<Integer> safeY) {
        legacyX = safeX; legacyY = safeY;
        left = side(LEFT, safeX.getValue(), group);
        right = side(RIGHT, safeX.getValue(), group);
        top = side(TOP, safeY.getValue(), group);
        bottom = side(BOTTOM, safeY.getValue(), group);
        Setting<?> after = safeY;
        for (Setting<Integer> side : new Setting[]{left, right, top, bottom}) { hud.add(side, after); after = side; }
        safeX.addVisibilityPredicate(value -> false);
        safeY.addVisibilityPredicate(value -> false);
    }

    @SuppressWarnings("unchecked")
    private static Setting<Integer> side(String name, int value, Setting<Boolean> group) {
        return (Setting<Integer>) new NumberSetting<>(name, Math.clamp(value, 0, MAX), 0, MAX).dependsOn(group);
    }

    /** A config saved before the four sides existed: carry its SafeX/SafeY over to them. */
    public static void migrate(JsonElement json) {
        if (left == null || !(json instanceof JsonObject module) || !(module.get("settings") instanceof JsonObject settings)) return;
        if (settings.has(left.getConfigName())) return;
        left.setValue(Math.clamp(legacyX.getValue(), 0, MAX)); right.setValue(Math.clamp(legacyX.getValue(), 0, MAX));
        top.setValue(Math.clamp(legacyY.getValue(), 0, MAX)); bottom.setValue(Math.clamp(legacyY.getValue(), 0, MAX));
    }

    private static HUDModule hud() { return ClientContext.getModule(HUDModule.class); }

    public static int left() { return left != null ? left.getValue() : hud().getSafeX(); }
    public static int right() { return right != null ? right.getValue() : hud().getSafeX(); }
    public static int top() { return top != null ? top.getValue() : hud().getSafeY(); }
    public static int bottom() { return bottom != null ? bottom.getValue() : hud().getSafeY(); }

    public enum Side { LEFT, RIGHT, TOP, BOTTOM }

    public static int get(Side side) {
        return switch (side) { case LEFT -> left(); case RIGHT -> right(); case TOP -> top(); case BOTTOM -> bottom(); };
    }

    public static void set(Side side, int value) {
        Setting<Integer> setting = switch (side) { case LEFT -> left; case RIGHT -> right; case TOP -> top; case BOTTOM -> bottom; };
        int clamped = Math.clamp(value, 0, MAX);
        if (setting != null) setting.setValue(clamped);
        // Keep the old pair close for anything in the base client that still reads it.
        if (legacyX != null && (side == Side.LEFT || side == Side.RIGHT)) legacyX.setValue(Math.min(20, Math.min(left(), right())));
        if (legacyY != null && (side == Side.TOP || side == Side.BOTTOM)) legacyY.setValue(Math.min(10, Math.min(top(), bottom())));
    }
}

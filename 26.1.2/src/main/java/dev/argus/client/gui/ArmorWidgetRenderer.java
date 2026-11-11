package dev.argus.client.gui;

import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.HudHorizontalAlignment;
import dev.argus.client.render.HudVerticalAlignment;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.settings.options.HudAnchor;

/**
 * The armour HUD as a real element: four item slots with the durability text above them, so the
 * editor can select, drag, mirror and gap it like everything else. The base client drew it at a
 * fixed spot above the hotbar and reported it as never on screen.
 */
public final class ArmorWidgetRenderer extends HudWidgetRenderer {
    /** Room for the durability text above the items, and the size of the four slots. */
    public static final float TEXT = 7, WIDTH = 4 * 18 - 2, HEIGHT = TEXT + 16;

    public ArmorWidgetRenderer(HudWidgetModule module) { super(module); }

    @Override public float[] calculateDimensions() { return new float[]{WIDTH, HEIGHT}; }

    /**
     * An armour element that was never placed (the base client left it at the default middle of the
     * screen) starts where the armour always used to be, just above the hotbar.
     */
    public void placeIfNew(float homeX, float homeTop) {
        if (getAnchor() != HudAnchor.NONE || getRelativeX() != .5f || getRelativeY() != .5f
                || getHorizontalAlignment() != HudHorizontalAlignment.FREE || getVerticalAlignment() != HudVerticalAlignment.FREE) return;
        setX(homeX, true);
        setY(homeTop, true);
        updateDirections();
    }
}

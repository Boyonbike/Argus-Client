package dev.argus.client.gui;

import net.minecraft.client.Minecraft;

/**
 * The Modules page's snap grid in Minecraft GUI units, for the HUD. With "Fit HUD to grid" on, every element's size is
 * rounded up to whole grid steps, so elements dropped on the grid line up edge to edge and corner stacks space evenly.
 */
public final class HudGrid {
    private HudGrid() {}

    public static boolean enabled() {
        return GuiState.preferences().hudFitGrid;
    }

    /** One grid step in GUI units at the current window size and menu scale. */
    public static float step() {
        var window = Minecraft.getInstance().getWindow();
        var viewport = GuiViewport.create(window.getWidth(), window.getHeight(), window.getGuiScale(), GuiState.preferences().uiScale);
        return ModulesScreen.GRID * viewport.renderScale();
    }

    /** {@code size} rounded up to whole grid steps; unchanged when fitting is off or the element is empty. */
    public static float fit(float size) {
        return enabled() ? fit(size, step()) : size;
    }

    static float fit(float size, float step) {
        if (size <= 0 || step <= 0 || !Float.isFinite(size)) return size;
        // A hair of tolerance so a size already on the grid isn't pushed a whole step by float error.
        return (float) Math.ceil(size / step - 1e-3) * step;
    }
}

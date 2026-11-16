package dev.argus.client.gui;

/**
 * Spacing and size scale for the GUI, in logical units. Everything snaps to a 4-unit step so screens
 * line up with each other; weight comes from choosing a different step, not from one-off numbers.
 */
final class UiSpace {
    private UiSpace() {}

    /** The step scale. */
    static final float S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24;

    /** Row heights: compact lists (modules, HUD elements, sidebar) and rows that hold a control. */
    static final float ROW = 18, ROW_SETTING = 26;

    /** Inside a window, between groups, from a label to its control, inside a control. */
    static final float WINDOW_PAD = S3, GROUP_GAP = S4, LABEL_GAP = S2, CONTROL_PAD = S1;

    /** Corner chamfer of small controls, in GUI pixels (stepped, Minecraft style). */
    static final int CHAMFER_SMALL = 1;

    /** Corner chamfer of windows and large controls; a style trial, 2 unless changed in Appearance. */
    static int chamfer() { return GuiState.preferences().corner; }

    // ---- type scale ----
    /** Captions and hints. Nothing in the GUI is drawn smaller than this. */
    static final float CAPTION = 7;
    /** Body copy and values. */
    static final float BODY = 8;
    /** Row titles and group headers. */
    static final float ROW_TITLE = 9;
    /** Page titles. */
    static final float PAGE_TITLE = 11;
}

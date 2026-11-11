package dev.argus.client.gui;

/**
 * Reveal motion for the navigation bar, shared by the menu pages.
 *
 * <p>A fresh menu open restores the bar as it was left but always replays its reveal from the
 * collapsed face. The reveal is held back until the page's own fade-in is visible, then runs for
 * a fixed duration, so a long first frame can neither skip the start nor make the bar pop open.
 */
final class NavbarAnimation {
    /** How long the collapsed face is held after a fresh open, matching the page fade-in. */
    private static final float OPEN_HOLD = .28f;
    private static final float OPEN_SECONDS = .45f, CLOSE_SECONDS = .22f;

    private boolean open;
    private float progress;
    /** Remaining hold before a restored reveal starts, in seconds. */
    private float hold;

    boolean open() { return open; }
    float progress() { return progress; }

    /** A click on the face animates immediately, from wherever the bar currently is. */
    void setOpen(boolean value) { open = value; hold = 0; }

    /** A fresh menu open starts collapsed and replays the reveal after the page has faded in. */
    void restore(boolean value) {
        open = value;
        progress = 0;
        hold = value ? OPEN_HOLD : 0;
    }

    /** Jumps straight to where the bar is heading, for when animations are off. */
    void settle() { hold = 0; progress = open ? 1 : 0; }

    /** Advances the motion; fixed durations keep the reveal visible at any frame rate. */
    void advance(float seconds) {
        if (seconds <= 0) return;
        float remaining = seconds;
        if (hold > 0) {
            hold -= remaining;
            if (hold > 0) return;
            remaining = -hold;
            hold = 0;
        }
        float target = open ? 1 : 0;
        if (progress == target) return;
        float step = remaining / (open ? OPEN_SECONDS : CLOSE_SECONDS);
        progress = open ? Math.min(1, progress + step) : Math.max(0, progress - step);
    }
}

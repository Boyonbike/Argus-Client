package dev.argus.client.modules.player.rekit;

/** Keep the manual button out of the storage title when a tall chest fills the screen. */
public final class RekitButtonPlacement {
    private RekitButtonPlacement() {}
    public record Position(int x, int y) {}
    public static Position place(int screenWidth, int left, int top, int panelWidth) {
        if (top >= 24) return new Position(Math.max(2, left), top - 22);
        if (left + panelWidth + 3 + 58 <= screenWidth - 2)
            return new Position(left + panelWidth + 3, Math.max(2, top));
        if (left >= 63) return new Position(left - 61, Math.max(2, top));
        return new Position(Math.max(2, screenWidth - 60), 2);
    }
}

package dev.argus.client.utils.inventory;

import java.util.HexFormat;

/** Immutable 16×16 icon, encoded as ARGB pixels. */
public final class PixelIcon {
    public static final int SIZE = 16;
    private final int[] pixels;

    public PixelIcon(int[] pixels) {
        if (pixels.length != SIZE * SIZE) throw new IllegalArgumentException("Invalid icon dimensions");
        this.pixels = pixels.clone();
    }

    public int[] copyPixels() {
        return pixels.clone();
    }

    public int pixel(int x, int y) {
        return pixels[y * SIZE + x];
    }

    public String encode() {
        StringBuilder result = new StringBuilder(pixels.length * 8);
        for (int pixel : pixels) result.append(HexFormat.of().toHexDigits(pixel));
        return result.toString();
    }

    public static PixelIcon decode(String value) {
        if (value.length() != SIZE * SIZE * 8) throw new IllegalArgumentException("Invalid icon data");
        int[] pixels = new int[SIZE * SIZE];
        for (int i = 0; i < pixels.length; i++) pixels[i] = HexFormat.fromHexDigits(value, i * 8, i * 8 + 8);
        return new PixelIcon(pixels);
    }
}

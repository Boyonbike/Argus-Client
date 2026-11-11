package dev.argus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Icons are authored as 128px white masks and area-averaged down to the exact framebuffer size
 * they are drawn at, then shown 1:1 — no nearest-neighbour shimmer or jagged diagonals.
 */
final class UiIcons {
    private UiIcons() {}

    private static final Map<String, float[]> sources = new HashMap<>();
    private static final Map<String, DynamicTexture> sized = new HashMap<>();
    private static final float[] quad = new float[8];
    private static final int SOURCE = 128;

    private static float[] source(String name) {
        return sources.computeIfAbsent(name, key -> {
            try (InputStream input = Minecraft.getInstance().getResourceManager()
                    .getResourceOrThrow(UiRender.id("textures/icons/" + key + ".png")).open();
                 NativeImage image = NativeImage.read(input)) {
                int w = image.getWidth(), h = image.getHeight();
                float[] alpha = new float[SOURCE * SOURCE];
                for (int y = 0; y < SOURCE; y++) for (int x = 0; x < SOURCE; x++)
                    alpha[y * SOURCE + x] = (image.getPixel(x * w / SOURCE, y * h / SOURCE) >>> 24) / 255f;
                return alpha;
            } catch (Exception missing) {
                return new float[0];
            }
        });
    }

    private static DynamicTexture texture(String name, int pixels) {
        return sized.computeIfAbsent(name + "@" + pixels, key -> {
            float[] alpha = source(name);
            if (alpha.length == 0) return null;
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, pixels, pixels, true);
            double step = SOURCE / (double) pixels;
            for (int ty = 0; ty < pixels; ty++) for (int tx = 0; tx < pixels; tx++) {
                double sx0 = tx * step, sx1 = sx0 + step, sy0 = ty * step, sy1 = sy0 + step, total = 0;
                for (int sy = (int) sy0; sy < Math.min(SOURCE, Math.ceil(sy1)); sy++) {
                    double wy = Math.min(sy + 1, sy1) - Math.max(sy, sy0);
                    for (int sx = (int) sx0; sx < Math.min(SOURCE, Math.ceil(sx1)); sx++) {
                        double wx = Math.min(sx + 1, sx1) - Math.max(sx, sx0);
                        total += alpha[sy * SOURCE + sx] * wx * wy;
                    }
                }
                int a = (int) Math.round(Math.min(1, total / (step * step)) * 255);
                image.setPixel(tx, ty, (a << 24) | 0xffffff);
            }
            DynamicTexture texture = new DynamicTexture(() -> "Argus Client icon " + key, image);
            Minecraft.getInstance().getTextureManager().register(UiRender.id("dynamic/icon_" + name + "_" + pixels), texture);
            return texture;
        });
    }

    /** Draws a square icon of logical {@code size} with its top-left corner at (x, y). */
    static void draw(GuiGraphicsExtractor g, String name, float x, float y, float size, int colour) {
        float k = UiRender.pixelsPerUnit(g);
        int pixels = Math.max(4, Math.round(size * k));
        DynamicTexture texture = texture(name, pixels);
        if (texture == null) return;
        float px = Math.round(UiRender.physicalX(g, x, y)), py = Math.round(UiRender.physicalY(g, x, y));
        quad[0] = px; quad[1] = py; quad[2] = px + pixels; quad[3] = py + pixels;
        quad[4] = 0; quad[5] = 0; quad[6] = 1; quad[7] = 1;
        UiRender.quads(g, texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), quad, 1, colour);
    }
}

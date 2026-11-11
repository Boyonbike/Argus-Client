package dev.argus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.io.InputStream;
import java.nio.ByteBuffer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Lexend Deca rasterised by stb_truetype at the exact framebuffer pixel size it is displayed at,
 * with three horizontal sub-pixel positions, so text is sharp at every GUI scale. If the native
 * rasteriser is unavailable the pre-rendered bitmap font is used instead.
 */
final class UiFont {
    private UiFont() {}

    private static final Identifier SOURCE = UiRender.id("fonts/lexend-deca.ttf");
    private static final Identifier ATLAS_ID = UiRender.id("dynamic/ui_font");
    private static final int ATLAS = 1024, SUBPIXELS = 3, PAD = 1;

    private static boolean loaded, failed;
    private static ByteBuffer data;
    private static STBTTFontinfo info;
    private static int ascent, descent, capHeight;
    private static NativeImage pixels;
    private static DynamicTexture texture;
    private static GpuSampler sampler;
    private static boolean dirty;
    private static int penX = PAD, penY = PAD, shelf;
    private static final Long2ObjectOpenHashMap<Glyph> glyphs = new Long2ObjectOpenHashMap<>();
    private static final IntOpenHashSet warmed = new IntOpenHashSet();
    // Font metrics are looked up for every character measured or drawn each frame. They never change, so
    // they are cached here instead of calling into stb_truetype (JNI) per character.
    private static final int UNSET = Integer.MIN_VALUE, BMP = 0x10000, ASCII = 128;
    private static final byte KNOWN = 1, MISSING = 2;
    private static final byte[] knownBmp = new byte[BMP];
    private static final int[] advanceBmp = new int[BMP];
    private static final Int2IntOpenHashMap advanceOther = new Int2IntOpenHashMap();
    private static final int[] kernAscii = new int[ASCII * ASCII];
    private static final Long2IntOpenHashMap kernOther = new Long2IntOpenHashMap();
    static {
        Arrays.fill(advanceBmp, UNSET);
        Arrays.fill(kernAscii, UNSET);
        advanceOther.defaultReturnValue(UNSET);
        kernOther.defaultReturnValue(UNSET);
    }
    private static float[] scratch = new float[8 * 64];

    private record Glyph(int width, int height, int left, int top, float u0, float v0, float u1, float v1) {}

    static boolean available() {
        if (!loaded) load();
        return !failed;
    }

    private static void load() {
        loaded = true;
        try (InputStream input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(SOURCE).open()) {
            byte[] bytes = input.readAllBytes();
            data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, data)) throw new IllegalStateException("Unreadable font");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var a = stack.mallocInt(1); var d = stack.mallocInt(1); var gap = stack.mallocInt(1);
                STBTruetype.stbtt_GetFontVMetrics(info, a, d, gap);
                ascent = a.get(0); descent = d.get(0);
                var x0 = stack.mallocInt(1); var y0 = stack.mallocInt(1); var x1 = stack.mallocInt(1); var y1 = stack.mallocInt(1);
                STBTruetype.stbtt_GetCodepointBox(info, 'H', x0, y0, x1, y1);
                capHeight = y1.get(0);
            }
            pixels = new NativeImage(NativeImage.Format.RGBA, ATLAS, ATLAS, true);
            texture = new DynamicTexture(() -> "Argus Client UI font", pixels);
            Minecraft.getInstance().getTextureManager().register(ATLAS_ID, texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        } catch (Throwable failure) {
            // Includes UnsatisfiedLinkError: never let a font problem take the menu down.
            failed = true;
        }
    }

    private static float scale(int pixels) { return STBTruetype.stbtt_ScaleForMappingEmToPixels(info, pixels); }

    private static int emPixels(float size, float pixelsPerUnit) { return Math.max(4, Math.round(size * pixelsPerUnit)); }

    private static int advance(int codepoint) {
        int cached = codepoint < BMP ? advanceBmp[codepoint] : advanceOther.get(codepoint);
        if (cached != UNSET) return cached;
        int value;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var advance = stack.mallocInt(1); var bearing = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointHMetrics(info, codepoint, advance, bearing);
            value = advance.get(0);
        }
        if (codepoint < BMP) advanceBmp[codepoint] = value; else advanceOther.put(codepoint, value);
        return value;
    }

    private static boolean known(int codepoint) {
        if (codepoint == ' ') return true;
        if (codepoint >= BMP) return STBTruetype.stbtt_FindGlyphIndex(info, codepoint) != 0;
        byte state = knownBmp[codepoint];
        if (state == 0) knownBmp[codepoint] = state = STBTruetype.stbtt_FindGlyphIndex(info, codepoint) != 0 ? KNOWN : MISSING;
        return state == KNOWN;
    }

    /** Kerning between two codepoints in font units. */
    private static int kern(int previous, int codepoint) {
        boolean ascii = previous < ASCII && codepoint < ASCII;
        long key = ((long) previous << 32) | (codepoint & 0xffffffffL);
        int cached = ascii ? kernAscii[previous * ASCII + codepoint] : kernOther.get(key);
        if (cached != UNSET) return cached;
        int value = STBTruetype.stbtt_GetCodepointKernAdvance(info, previous, codepoint);
        if (ascii) kernAscii[previous * ASCII + codepoint] = value; else kernOther.put(key, value);
        return value;
    }

    /** Logical width of {@code text} at {@code size}, at the given pixel density. */
    static float width(String text, float size, float pixelsPerUnit) {
        if (!available()) return fallbackWidth(text) * size / 8f;
        int em = emPixels(size, pixelsPerUnit);
        float s = scale(em), pen = 0;
        int previous = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (!known(cp)) cp = '?';
            if (previous >= 0) pen += kern(previous, cp) * s;
            pen += advance(cp) * s;
            previous = cp;
        }
        return pen / pixelsPerUnit;
    }

    /** Height from the top of the line box to the baseline, in logical units. */
    static float ascent(float size) { return available() ? size * ascent / (float) (ascent - descent) * 1.08f : size; }
    static float lineHeight(float size) { return size * 1.25f; }

    /**
     * Draws text whose cap height is vertically centred on {@code centreY}. This keeps labels
     * optically centred in rows regardless of font size or density.
     */
    static void drawCentred(GuiGraphicsExtractor g, String text, float x, float centreY, float size, int colour, boolean bold) {
        if (text.isEmpty()) return;
        if (!available()) { fallback(g, text, x, centreY - size * .55f, size, colour); return; }
        float k = UiRender.pixelsPerUnit(g);
        int em = emPixels(size, k);
        float s = scale(em);
        warm(em, bold);
        float originX = UiRender.physicalX(g, x, centreY), originY = UiRender.physicalY(g, x, centreY);
        int baseline = Math.round(originY + capHeight * s / 2f);
        float pen = originX;
        int count = 0, previous = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (!known(cp)) cp = '?';
            if (previous >= 0) pen += kern(previous, cp) * s;
            if (cp != ' ') {
                int whole = (int) Math.floor(pen);
                int sub = Math.min(SUBPIXELS - 1, (int) ((pen - whole) * SUBPIXELS));
                Glyph glyph = glyph(cp, em, sub, bold);
                if (glyph != null && glyph.width > 0) {
                    if ((count + 1) * 8 > scratch.length) scratch = java.util.Arrays.copyOf(scratch, scratch.length * 2);
                    int o = count++ * 8;
                    float gx = whole + glyph.left, gy = baseline + glyph.top;
                    scratch[o] = gx; scratch[o + 1] = gy; scratch[o + 2] = gx + glyph.width; scratch[o + 3] = gy + glyph.height;
                    scratch[o + 4] = glyph.u0; scratch[o + 5] = glyph.v0; scratch[o + 6] = glyph.u1; scratch[o + 7] = glyph.v1;
                }
            }
            pen += advance(cp) * s + (bold ? .35f : 0);
            previous = cp;
        }
        flush();
        UiRender.quads(g, texture.getTextureView(), sampler, scratch, count, colour);
    }

    /** Pre-rasterise printable ASCII for a size so a new size costs one texture upload. */
    private static void warm(int em, boolean bold) {
        int key = em * 2 + (bold ? 1 : 0);
        if (!warmed.add(key)) return;
        for (int cp = 33; cp < 127; cp++) for (int sub = 0; sub < SUBPIXELS; sub++) glyph(cp, em, sub, bold);
    }

    private static void flush() {
        if (!dirty) return;
        dirty = false;
        texture.upload();
    }

    private static Glyph glyph(int codepoint, int em, int sub, boolean bold) {
        long key = ((long) codepoint << 24) | ((long) em << 4) | ((long) sub << 1) | (bold ? 1 : 0);
        Glyph cached = glyphs.get(key);
        if (cached != null) return cached;
        float s = scale(em), shift = sub / (float) SUBPIXELS;
        Glyph glyph;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x0 = stack.mallocInt(1); var y0 = stack.mallocInt(1); var x1 = stack.mallocInt(1); var y1 = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointBitmapBoxSubpixel(info, codepoint, s, s, shift, 0, x0, y0, x1, y1);
            int w = x1.get(0) - x0.get(0), h = y1.get(0) - y0.get(0);
            if (w <= 0 || h <= 0) { glyph = new Glyph(0, 0, 0, 0, 0, 0, 0, 0); glyphs.put(key, glyph); return glyph; }
            int outW = w + (bold ? 1 : 0);
            if (penX + outW + PAD > ATLAS) { penX = PAD; penY += shelf + PAD; shelf = 0; }
            if (penY + h + PAD > ATLAS) reset();
            ByteBuffer bitmap = MemoryUtil.memAlloc(w * h);
            try {
                STBTruetype.stbtt_MakeCodepointBitmapSubpixel(info, bitmap, w, h, w, s, s, shift, 0, codepoint);
                for (int y = 0; y < h; y++) for (int x = 0; x < outW; x++) {
                    float here = x < w ? (bitmap.get(y * w + x) & 255) / 255f : 0;
                    if (bold) { float left = x > 0 ? (bitmap.get(y * w + x - 1) & 255) / 255f : 0; here = Math.min(1, here + left * .7f); }
                    // A mild curve compensates for light-on-dark text looking thinner than its coverage.
                    int alpha = Math.round((float) Math.pow(here, .82) * 255);
                    pixels.setPixel(penX + x, penY + y, (alpha << 24) | 0xffffff);
                }
            } finally { MemoryUtil.memFree(bitmap); }
            glyph = new Glyph(outW, h, x0.get(0), y0.get(0), penX / (float) ATLAS, penY / (float) ATLAS,
                    (penX + outW) / (float) ATLAS, (penY + h) / (float) ATLAS);
            penX += outW + PAD; shelf = Math.max(shelf, h);
        }
        dirty = true;
        glyphs.put(key, glyph);
        return glyph;
    }

    private static void reset() {
        glyphs.clear(); warmed.clear();
        pixels.fillRect(0, 0, ATLAS, ATLAS, 0);
        penX = PAD; penY = PAD; shelf = 0; dirty = true;
    }

    private static float fallbackWidth(String text) { return Minecraft.getInstance().font.width(UiDraw.legacy(text)); }

    private static void fallback(GuiGraphicsExtractor g, String text, float x, float y, float size, int colour) {
        float factor = size / 8f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(factor, factor);
        g.text(Minecraft.getInstance().font, UiDraw.legacy(text), 0, 0, UiRender.fade(colour), false);
        g.pose().popMatrix();
    }
}

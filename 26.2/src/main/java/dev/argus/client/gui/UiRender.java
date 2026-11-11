package dev.argus.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Resolution-independent drawing: every primitive is placed on the real framebuffer pixel grid,
 * rounded shapes are evaluated per pixel by a signed-distance shader, and textured quads
 * (glyphs and icons) are rasterised at the exact pixel size they are shown at.
 */
final class UiRender {
    private UiRender() {}

    static Identifier id(String path) { return Identifier.fromNamespaceAndPath("argusclient", path); }

    /** Position, offset-from-centre (UV0), colour, half size (UV1) and radius/mode (UV2). */
    static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    static final RenderPipeline SHAPE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(id("pipeline/ui_shape"))
            .withVertexShader(id("core/ui_shape"))
            .withFragmentShader(id("core/ui_shape"))
            .withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
            .build();

    /** Shape format plus a texture coordinate; Normal carries the quad corner (±1, ±1). */
    static final VertexFormat IMAGE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    static final RenderPipeline IMAGE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(id("pipeline/ui_image"))
            .withVertexShader(id("core/ui_image"))
            .withFragmentShader(id("core/ui_image"))
            .withSampler("Sampler0")
            .withVertexFormat(IMAGE_FORMAT, VertexFormat.Mode.QUADS)
            .build();

    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    /** Multiplies the alpha of everything drawn; used for open/close fades. */
    static float opacity = 1;

    static int fade(int colour) {
        if (opacity >= 1) return colour;
        int alpha = Math.round((colour >>> 24) * Math.max(0, opacity));
        return (colour & 0x00ffffff) | (alpha << 24);
    }

    static double guiScale() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    /** Framebuffer pixels per logical unit under the current pose. */
    static float pixelsPerUnit(GuiGraphicsExtractor g) {
        Matrix3x2fc pose = g.pose();
        return (float) (Math.hypot(pose.m00(), pose.m01()) * guiScale());
    }

    /** Logical point → framebuffer pixels. */
    static float physicalX(GuiGraphicsExtractor g, float x, float y) { Matrix3x2fc p = g.pose(); return (float) ((p.m00() * x + p.m10() * y + p.m20()) * guiScale()); }
    static float physicalY(GuiGraphicsExtractor g, float x, float y) { Matrix3x2fc p = g.pose(); return (float) ((p.m01() * x + p.m11() * y + p.m21()) * guiScale()); }

    /**
     * Rounded rectangle. {@code mode} 0 = fill, &gt;0 = outline of that logical thickness,
     * &lt;0 = soft shadow with that logical blur width.
     */
    static void shape(GuiGraphicsExtractor g, float x, float y, float width, float height, float radius,
                      int top, int bottom, float mode) {
        if (width <= 0 || height <= 0) return;
        top = fade(top); bottom = fade(bottom);
        if ((top >>> 24) == 0 && (bottom >>> 24) == 0) return;
        float k = pixelsPerUnit(g);
        // Snap origin and size independently so equal logical sizes are always equal pixel sizes.
        float px0 = Math.round(physicalX(g, x, y)), py0 = Math.round(physicalY(g, x, y));
        float pw = Math.max(1, Math.round(width * k)), ph = Math.max(1, Math.round(height * k));
        float physicalMode = mode > 0 ? Math.max(1, Math.round(mode * k)) : mode * k;
        float margin = mode < 0 ? -physicalMode + 1 : 1;
        float scale = (float) guiScale();
        float cx = px0 + pw / 2, cy = py0 + ph / 2;
        float qx0 = px0 - margin, qy0 = py0 - margin, qx1 = px0 + pw + margin, qy1 = py0 + ph + margin;
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle bounds = bounds(qx0 / scale, qy0 / scale, qx1 / scale, qy1 / scale, scissor);
        if (bounds == null) return;
        g.guiRenderState.addGuiElement(new ShapeState(qx0 / scale, qy0 / scale, qx1 / scale, qy1 / scale,
                qx0 - cx, qy0 - cy, qx1 - cx, qy1 - cy,
                Math.round(pw / 2 * 8), Math.round(ph / 2 * 8), Math.round(Math.max(0, radius * k) * 8),
                Math.round(physicalMode * 8), top, bottom, scissor, bounds));
    }

    /** A texture region clipped to a rounded rectangle (a circle when {@code radius} ≥ half the size). */
    static void image(GuiGraphicsExtractor g, GpuTextureView texture, GpuSampler sampler, float x, float y, float width, float height,
                      float radius, float u0, float v0, float u1, float v1, int colour) {
        if (width <= 0 || height <= 0) return;
        colour = fade(colour);
        if ((colour >>> 24) == 0) return;
        float k = pixelsPerUnit(g), scale = (float) guiScale();
        float px0 = Math.round(physicalX(g, x, y)), py0 = Math.round(physicalY(g, x, y));
        float pw = Math.max(1, Math.round(width * k)), ph = Math.max(1, Math.round(height * k)), margin = 1;
        // Extend the texture mapping over the anti-aliasing margin so the image stays aligned.
        float du = (u1 - u0) * margin / pw, dv = (v1 - v0) * margin / ph;
        float qx0 = px0 - margin, qy0 = py0 - margin, qx1 = px0 + pw + margin, qy1 = py0 + ph + margin;
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle bounds = bounds(qx0 / scale, qy0 / scale, qx1 / scale, qy1 / scale, scissor);
        if (bounds == null) return;
        g.guiRenderState.addGuiElement(new ImageState(TextureSetup.singleTexture(texture, sampler),
                qx0 / scale, qy0 / scale, qx1 / scale, qy1 / scale, u0 - du, v0 - dv, u1 + du, v1 + dv,
                Math.round(pw / 2 * 8), Math.round(ph / 2 * 8), Math.round(Math.max(0, radius * k) * 8), Math.round(margin * 8),
                colour, scissor, bounds));
    }

    /** Draws already-pixel-aligned textured quads ({@code x0,y0,x1,y1,u0,v0,u1,v1} per quad, in pixels). */
    static void quads(GuiGraphicsExtractor g, GpuTextureView texture, GpuSampler sampler, float[] quads, int count, int colour) {
        if (count == 0) return;
        colour = fade(colour);
        if ((colour >>> 24) == 0) return;
        float scale = (float) guiScale();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float[] copy = new float[count * 8];
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            for (int j = 0; j < 4; j++) copy[o + j] = quads[o + j] / scale;
            for (int j = 4; j < 8; j++) copy[o + j] = quads[o + j];
            minX = Math.min(minX, copy[o]); minY = Math.min(minY, copy[o + 1]);
            maxX = Math.max(maxX, copy[o + 2]); maxY = Math.max(maxY, copy[o + 3]);
        }
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle bounds = bounds(minX, minY, maxX, maxY, scissor);
        if (bounds == null) return;
        g.guiRenderState.addGuiElement(new QuadsState(TextureSetup.singleTexture(texture, sampler), copy, colour, scissor, bounds));
    }

    private static @Nullable ScreenRectangle bounds(float x0, float y0, float x1, float y1, @Nullable ScreenRectangle scissor) {
        int left = (int) Math.floor(x0), top = (int) Math.floor(y0);
        ScreenRectangle area = new ScreenRectangle(left, top, Math.max(1, (int) Math.ceil(x1) - left), Math.max(1, (int) Math.ceil(y1) - top));
        if (scissor == null) return area;
        ScreenRectangle clipped = scissor.intersection(area);
        return clipped == null || clipped.width() <= 0 || clipped.height() <= 0 ? null : clipped;
    }

    private record ShapeState(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                              int halfWidth, int halfHeight, int radius, int mode, int top, int bottom,
                              @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(IDENTITY, x0, y0).setUv(u0, v0).setColor(top).setUv1(halfWidth, halfHeight).setUv2(radius, mode);
            consumer.addVertexWith2DPose(IDENTITY, x0, y1).setUv(u0, v1).setColor(bottom).setUv1(halfWidth, halfHeight).setUv2(radius, mode);
            consumer.addVertexWith2DPose(IDENTITY, x1, y1).setUv(u1, v1).setColor(bottom).setUv1(halfWidth, halfHeight).setUv2(radius, mode);
            consumer.addVertexWith2DPose(IDENTITY, x1, y0).setUv(u1, v0).setColor(top).setUv1(halfWidth, halfHeight).setUv2(radius, mode);
        }
        @Override public RenderPipeline pipeline() { return SHAPE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }

    private record ImageState(TextureSetup textureSetup, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                              int halfWidth, int halfHeight, int radius, int margin, int colour,
                              @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(IDENTITY, x0, y0).setUv(u0, v0).setColor(colour).setUv1(halfWidth, halfHeight).setUv2(radius, margin).setNormal(-1, -1, 0);
            consumer.addVertexWith2DPose(IDENTITY, x0, y1).setUv(u0, v1).setColor(colour).setUv1(halfWidth, halfHeight).setUv2(radius, margin).setNormal(-1, 1, 0);
            consumer.addVertexWith2DPose(IDENTITY, x1, y1).setUv(u1, v1).setColor(colour).setUv1(halfWidth, halfHeight).setUv2(radius, margin).setNormal(1, 1, 0);
            consumer.addVertexWith2DPose(IDENTITY, x1, y0).setUv(u1, v0).setColor(colour).setUv1(halfWidth, halfHeight).setUv2(radius, margin).setNormal(1, -1, 0);
        }
        @Override public RenderPipeline pipeline() { return IMAGE; }
    }

    private record QuadsState(TextureSetup textureSetup, float[] quads, int colour,
                              @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer consumer) {
            for (int o = 0; o < quads.length; o += 8) {
                consumer.addVertexWith2DPose(IDENTITY, quads[o], quads[o + 1]).setUv(quads[o + 4], quads[o + 5]).setColor(colour);
                consumer.addVertexWith2DPose(IDENTITY, quads[o], quads[o + 3]).setUv(quads[o + 4], quads[o + 7]).setColor(colour);
                consumer.addVertexWith2DPose(IDENTITY, quads[o + 2], quads[o + 3]).setUv(quads[o + 6], quads[o + 7]).setColor(colour);
                consumer.addVertexWith2DPose(IDENTITY, quads[o + 2], quads[o + 1]).setUv(quads[o + 6], quads[o + 5]).setColor(colour);
            }
        }
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }
    }
}

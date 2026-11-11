package dev.argus.client.gui;

import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.PixelIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

final class ShulkerPixelEditorScreen extends UiScreen {
    private static final int SIZE = PixelIcon.SIZE;
    private static final int[] PALETTE = {0xff191919, 0xffffffff, 0xffef5350, 0xffffca28, 0xff66bb6a, 0xff42a5f5};
    private final Item item;
    private final Consumer<PixelIcon> save;
    private final UiInput hex = new UiInput(0, 0, 90, 16, "AARRGGBB");
    private final UiInput path = new UiInput(0, 0, 100, 16, "Path to PNG");
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private final ArrayDeque<int[]> redo = new ArrayDeque<>();
    private int[] pixels;
    private String status = "";
    private int left, top, panelWidth, cell, gridX, gridY, controls;
    private int lastX = -1, lastY = -1;
    private int paint = 0xffffffff;

    ShulkerPixelEditorScreen(Screen parent, Item item, PixelIcon icon, Consumer<PixelIcon> save) {
        super("Pixel icon editor", parent, "Modules");
        this.item = item;
        this.save = save;
        pixels = icon == null ? new int[SIZE * SIZE] : icon.copyPixels();
        hex.setMaxLength(8);
        hex.setValue("FFFFFFFF");
        hex.setResponder(value -> {
            if (value.matches("[0-9a-fA-F]{8}")) paint = Integer.parseUnsignedInt(value, 16);
        });
        path.setMaxLength(4096);
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(490, width - 20);
        left = (width - panelWidth) / 2;
        top = 44;
        gridX = left + 10;
        gridY = top + 40;
        cell = Math.min(10, Math.max(4, (height - gridY - 24) / SIZE));
        controls = gridX + cell * SIZE + 10;
        int controlWidth = left + panelWidth - controls - 10;

        addRenderableWidget(new UiButton(left + 10, top + 8, 44, 18, "Back", this::onClose));
        addRenderableWidget(new UiButton(left + panelWidth - 56, top + 8, 46, 18, "Save", () -> {
            save.accept(new PixelIcon(pixels));
            onClose();
        }));
        hex.place(controls + 19, gridY + 4, Math.min(89, controlWidth - 19));
        addRenderableWidget(hex);
        addRenderableWidget(new UiButton(controls, gridY + 26, 46, 18, "Undo", this::undo));
        addRenderableWidget(new UiButton(controls + 50, gridY + 26, 46, 18, "Redo", this::redo));
        addRenderableWidget(new UiButton(controls, gridY + 49, 46, 18, "Blank", () -> replace(new int[SIZE * SIZE])));
        addRenderableWidget(new UiButton(controls + 50, gridY + 49, 84, 18, "Use item texture", this::loadItem));
        path.place(controls, gridY + 77, controlWidth);
        addRenderableWidget(path);
        addRenderableWidget(new UiButton(controls, gridY + 100, 66, 18, "Load PNG", this::loadPng));
    }

    @Override protected void renderUi(GuiGraphicsExtractor g, int mx, int my, float delta) {
        UiTheme theme = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, height - top - 10, theme);
        UiDraw.bold(g, "Pixel icon editor", left + 62, top + 17, UiDraw.TITLE, theme.text());
        for (int i = 0; i < PALETTE.length; i++) {
            int x = controls + i * 18;
            g.fill(x, gridY - 14, x + 14, gridY - 2, PALETTE[i]);
        }
        g.fill(controls, gridY + 4, controls + 15, gridY + 20, 0xff686868);
        g.fill(controls, gridY + 4, controls + 15, gridY + 20, paint);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int px = gridX + x * cell, py = gridY + y * cell;
                int background = (x + y) % 2 == 0 ? 0xff5d5d5d : 0xff373737;
                g.fill(px, py, px + cell, py + cell, background);
                g.fill(px, py, px + cell, py + cell, pixels[y * SIZE + x]);
            }
        }
        ShulkerIconRenderer.drawPixels(g, new PixelIcon(pixels), controls + 73, gridY + 101, 1);
        int labelWidth = left + panelWidth - controls - 10;
        UiDraw.text(g, UiDraw.fit(status, labelWidth), controls, gridY + 126, UiDraw.SMALL, theme.text());
        UiDraw.text(g, "Left paint · Right erase · Middle sample", gridX, height - 17, UiDraw.SMALL, theme.muted());
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        lastX = lastY = -1;
        if (event.button() == 0) {
            for (int i = 0; i < PALETTE.length; i++) {
                if (UiDraw.over(event.x(), event.y(), controls + i * 18, gridY - 14, 14, 12)) {
                    hex.setValue(String.format("%08X", PALETTE[i]));
                    return true;
                }
            }
        }
        if (!inside(event)) return super.mouseClickedUi(event, doubled);
        int x = column(event), y = row(event);
        if (event.button() == 2) {
            hex.setValue(String.format("%08X", pixels[y * SIZE + x]));
        } else if (event.button() == 0 || event.button() == 1) {
            remember();
            lastX = x;
            lastY = y;
            pixels[y * SIZE + x] = event.button() == 1 ? 0 : paint;
        }
        return true;
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (lastX < 0 || (event.button() != 0 && event.button() != 1)) {
            return super.mouseDraggedUi(event, dx, dy);
        }
        int x = Math.clamp(column(event), 0, SIZE - 1), y = Math.clamp(row(event), 0, SIZE - 1);
        int steps = Math.max(Math.abs(x - lastX), Math.abs(y - lastY));
        for (int i = 1; i <= steps; i++) {
            int px = lastX + Math.round((x - lastX) * i / (float) steps);
            int py = lastY + Math.round((y - lastY) * i / (float) steps);
            pixels[py * SIZE + px] = event.button() == 1 ? 0 : paint;
        }
        lastX = x;
        lastY = y;
        return true;
    }

    private int column(MouseButtonEvent event) { return (int) Math.floor((event.x() - gridX) / cell); }
    private int row(MouseButtonEvent event) { return (int) Math.floor((event.y() - gridY) / cell); }
    private boolean inside(MouseButtonEvent event) {
        return UiDraw.over(event.x(), event.y(), gridX, gridY, cell * SIZE, cell * SIZE);
    }

    private void remember() {
        if (undo.size() == 64) undo.removeFirst();
        undo.addLast(pixels.clone());
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.addLast(pixels);
        pixels = undo.removeLast();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.addLast(pixels);
        pixels = redo.removeLast();
    }

    private void replace(int[] next) {
        if (Arrays.equals(pixels, next)) return;
        remember();
        pixels = next;
    }

    @Override public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty()) {
            path.setValue(files.getFirst().toString());
            loadPng();
        }
    }

    private void loadPng() {
        try (InputStream input = Files.newInputStream(Path.of(path.getValue().strip()))) {
            loadImage(input, false);
        } catch (IOException | IllegalArgumentException exception) {
            status = "Could not load PNG (maximum 1024×1024).";
        }
    }

    private void loadItem() {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (loadTexture(id.withPath("item/" + id.getPath())) || loadTexture(id.withPath("block/" + id.getPath()))) return;

        var model = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(
            model, item.getDefaultInstance(), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0);
        var material = model.pickParticleMaterial(RandomSource.create(0));
        var sprite = material == null ? null : material.sprite();
        if (sprite != null && !MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())
            && loadTexture(sprite.contents().name())) return;

        status = "No editable texture found. Try a PNG.";
    }

    private boolean loadTexture(Identifier sprite) {
        var texture = sprite.withPath("textures/" + sprite.getPath() + ".png");
        var resource = minecraft.getResourceManager().getResource(texture);
        if (resource.isEmpty()) return false;
        try (InputStream input = resource.get().open()) {
            loadImage(input, true);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void loadImage(InputStream input, boolean firstFrame) throws IOException {
        var reader = ImageIO.getImageReadersByFormatName("png").next();
        try (var stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new IOException("Could not read PNG");
            reader.setInput(stream);
            int w = reader.getWidth(0), h = reader.getHeight(0);
            if (w > 1024 || h > 1024) throw new IOException("Image too large");
            BufferedImage image = reader.read(0);
            if (firstFrame && h > w && h % w == 0) h = w;
            int[] loaded = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    loaded[y * SIZE + x] = image.getRGB((2 * x + 1) * w / (2 * SIZE), (2 * y + 1) * h / (2 * SIZE));
                }
            }
            replace(loaded);
            status = "Loaded " + w + "×" + h + " as 16×16.";
        } finally {
            reader.dispose();
        }
    }
}

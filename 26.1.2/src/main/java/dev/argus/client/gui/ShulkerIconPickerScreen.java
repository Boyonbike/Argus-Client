package dev.argus.client.gui;

import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.ShulkerIconShape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

/**
 * Searchable item list. Normal icons keep every item; solid silhouettes collapse identical block
 * model shapes into one entry (searching a block's name still finds its shared entry).
 */
final class ShulkerIconPickerScreen extends UiScreen {
    private static final int ROW = 22;
    private record Choice(Item item, boolean shared, String search) {}

    private final boolean solid, multi;
    private final int colour;
    private final Consumer<Item> selected;
    private final List<Choice> choices;
    private final UiInput filter = new UiInput(0, 0, 100, 16, "Search items…");
    private List<Choice> shown = List.of();
    private String lastFilter = null;
    private int left, top, panelWidth, panelHeight, scroll;

    ShulkerIconPickerScreen(Screen parent, boolean solid, int colour, boolean multi, Consumer<Item> selected) {
        super(multi ? "Add required item" : "Select icon", parent, "Modules");
        this.solid = solid; this.colour = colour; this.multi = multi; this.selected = selected;
        // Rebuilt on open so resource-pack model changes are respected.
        this.choices = choices(solid);
        filter.setMaxLength(64);
    }

    private static List<Choice> choices(boolean solid) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var groups = new LinkedHashMap<Object, List<Item>>();
        var model = new TrackingItemStackRenderState();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Object key = item;
            if (solid && item instanceof BlockItem) {
                model.clear();
                minecraft.getItemModelResolver().updateForTopItem(model, item.getDefaultInstance(), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0);
                var vertices = new ArrayList<ShulkerIconShape.Point>();
                model.visitExtents(point -> vertices.add(ShulkerIconShape.Point.of(point.x(), point.y(), point.z())));
                if (!vertices.isEmpty()) key = ShulkerIconShape.key(vertices);
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<Choice> result = new ArrayList<>();
        for (List<Item> items : groups.values()) {
            StringBuilder text = new StringBuilder();
            for (Item item : items)
                text.append(item.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT)).append('\n').append(BuiltInRegistries.ITEM.getKey(item)).append('\n');
            result.add(new Choice(items.getFirst(), items.size() > 1, text.toString()));
        }
        return result;
    }

    private int visibleRows() { return Math.max(1, (panelHeight - 78) / ROW); }

    @Override protected void buildContent() {
        panelWidth = Math.min(460, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        addRenderableWidget(new UiButton(left + 10, top + 10, 42, 18, "Back", this::onClose));
        filter.place(left + 14, top + 42, panelWidth - 28);
        addRenderableWidget(filter);
        setFocused(filter);
        lastFilter = null;
    }

    private void refilter() {
        String text = filter.getValue().trim().toLowerCase(Locale.ROOT);
        if (text.equals(lastFilter)) return;
        lastFilter = text;
        shown = choices.stream().filter(choice -> choice.search().contains(text)).toList();
        scroll = 0;
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        refilter();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.bold(g, multi ? "Add required item" : solid ? "Select solid icon shape" : "Select icon", left + 62, top + 19, UiDraw.TITLE, t.text());
        scroll = Math.clamp(scroll, 0, Math.max(0, shown.size() - visibleRows()));
        int listTop = top + 70;
        for (int row = 0; row < visibleRows() && scroll + row < shown.size(); row++) {
            Choice choice = shown.get(scroll + row);
            float y = listTop + row * ROW;
            boolean hover = UiDraw.over(mx, my, left + 10, y, panelWidth - 20, ROW - 2);
            if (hover) UiDraw.rounded(g, left + 10, y, panelWidth - 20, ROW - 2, 6, t.surface());
            ShulkerIconRenderer.drawAt(g, choice.item().getDefaultInstance(), solid ? colour : null, left + 16, y + 2, 1);
            String label = choice.item().getDefaultInstance().getHoverName().getString() + (choice.shared() ? " (shared block shape)" : "");
            UiDraw.text(g, UiDraw.fit(label, panelWidth - 70), left + 40, y + ROW / 2f - 1, t.text());
        }
        if (shown.isEmpty()) UiDraw.text(g, "No items match.", left + 14, listTop + 10, UiDraw.SMALL, t.muted());
        else UiDraw.text(g, (multi ? "Click an item to add it. " : "Click an item to select it. ") + shown.size() + " shown", left + 14, top + panelHeight - 12, UiDraw.SMALL, t.muted());
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            int listTop = top + 70;
            for (int row = 0; row < visibleRows() && scroll + row < shown.size(); row++) {
                if (UiDraw.over(event.x(), event.y(), left + 10, listTop + row * ROW, panelWidth - 20, ROW - 2)) {
                    selected.accept(shown.get(scroll + row).item());
                    if (!multi) onClose();
                    return true;
                }
            }
        }
        return super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical) * 3;
        return true;
    }
}

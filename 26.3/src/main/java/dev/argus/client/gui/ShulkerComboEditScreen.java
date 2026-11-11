package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.PixelIcon;
import dev.argus.client.utils.inventory.ShulkerIconSelector.ContentsMode;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Match;
import dev.argus.client.utils.inventory.ShulkerIconSelector.NameMode;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Style;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.Item;

/** Edits one combo. Every change is saved straight away. */
final class ShulkerComboEditScreen extends UiScreen {
    private static final int CELL = 22;
    private final TooltipsModule module;
    private final int index;
    private final UiInput name = new UiInput(0, 0, 100, 16, "Combo name");
    private final UiInput hex = new UiInput(0, 0, 100, 16, "RRGGBB");
    private final UiInput boxName = new UiInput(0, 0, 100, 16, "Custom shulker name (optional)");
    private final UiInput minimum = new UiInput(0, 0, 55, 16, "Count");
    private String nameText;
    private NameMode nameMode;
    private ContentsMode contentsMode;
    private PixelIcon pixels;
    private boolean enabled, solid;
    private Item icon;
    private int colour, minimumCount, itemPage;
    private final List<Item> required;
    private int left, top, panelWidth, panelHeight, requiredTop;

    ShulkerComboEditScreen(Screen parent, TooltipsModule module, int index) {
        super("Edit combo", parent, "Modules");
        this.module = module;
        this.index = index;
        Rule<Item> rule = module.getIconRules().get(index);
        nameText = rule.name(); enabled = rule.enabled(); solid = rule.style() == Style.SolidColour;
        icon = rule.icon(); colour = rule.colour(); required = new ArrayList<>(rule.required());
        pixels = rule.pixels();
        nameMode = rule.match().nameMode();
        contentsMode = rule.match().contentsMode();
        boxName.setValue(rule.match().name());
        boxName.setResponder(value -> commit());
        minimumCount = rule.match().minimum();
        minimum.setMaxLength(9);
        minimum.setValue(Integer.toString(minimumCount));
        minimum.setFilter(value -> value.chars().allMatch(digit -> digit >= '0' && digit <= '9'));
        minimum.setResponder(value -> {
            minimumCount = value.isEmpty() ? 1 : Math.max(1, Integer.parseInt(value));
            commit();
        });
        name.setMaxLength(32); name.setValue(nameText);
        name.setResponder(value -> { nameText = value.isBlank() ? "Combo" : value.trim(); commit(); });
        hex.setMaxLength(6); hex.setValue(String.format("%06X", colour & 0xffffff));
        hex.setResponder(value -> {
            if (value.length() != 6) return;
            try { colour = 0xff000000 | Integer.parseInt(value, 16); commit(); } catch (NumberFormatException ignored) { }
        });
    }

    private void commit() {
        List<Rule<Item>> rules = new ArrayList<>(module.getIconRules());
        if (index >= rules.size()) return;
        rules.set(index, new Rule<>(nameText, enabled, required, icon, solid ? Style.SolidColour : Style.Normal, colour,
            new Match(boxName.getValue(), nameMode, contentsMode, minimumCount), pixels));
        module.setIconRules(rules);
    }

    private Integer iconColour() { return solid ? colour : null; }

    @Override protected void buildContent() {
        panelWidth = Math.min(520, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 8;
        addRenderableWidget(new UiButton(left + 10, top + 8, 42, 18, "Back", this::onClose));
        addRenderableWidget(new UiButton(left + panelWidth - 61, top + 8, 49, 18, enabled ? "On" : "Off", enabled, () -> {
            enabled = !enabled;
            commit();
            rebuildWidgets();
        }));
        name.place(left + 12, top + 39, panelWidth - 24);
        addRenderableWidget(name);
        boxName.place(left + 12, top + 64, panelWidth - 116);
        addRenderableWidget(boxName);
        addRenderableWidget(new UiButton(left + panelWidth - 99, top + 62, 87, 20, nameMode.name(), () -> {
            nameMode = NameMode.values()[(nameMode.ordinal() + 1) % NameMode.values().length];
            commit();
            rebuildWidgets();
        }));
        addRenderableWidget(new UiButton(left + 12, top + 86, 77, 20, "Choose item", () ->
            minecraft.gui.setScreen(new ShulkerIconPickerScreen(this, solid, colour, false, item -> {
                icon = item;
                pixels = null;
                commit();
            }))));
        addRenderableWidget(new UiButton(left + 93, top + 86, 64, 20, "Edit pixels", () ->
            minecraft.gui.setScreen(new ShulkerPixelEditorScreen(this, icon, pixels, result -> {
                pixels = result;
                commit();
                if (ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
            }))));
        addRenderableWidget(new UiButton(left + 161, top + 86, 54, 20, solid ? "Solid" : "Normal", solid, () -> {
            solid = !solid;
            commit();
            rebuildWidgets();
        }));
        hex.place(left + 219, top + 88, 52);
        addRenderableWidget(hex);
        addRenderableWidget(new UiButton(left + 12, top + 113, 82, 20, "Items: " + contentsMode.name(), () -> {
            contentsMode = ContentsMode.values()[(contentsMode.ordinal() + 1) % ContentsMode.values().length];
            commit();
            rebuildWidgets();
        }));
        minimum.place(left + 141, top + 115, 41);
        addRenderableWidget(minimum);
        addRenderableWidget(new UiButton(left + 187, top + 113, 67, 20, "Add item", () ->
            minecraft.gui.setScreen(new ShulkerIconPickerScreen(this, false, 0xffffffff, true, item -> {
                if (!required.contains(item)) {
                    required.add(item);
                    itemPage = (required.size() - 1) / itemsPerPage();
                    commit();
                }
            }))));
        requiredTop = top + 153;
        int pages = Math.max(1, (required.size() + itemsPerPage() - 1) / itemsPerPage());
        itemPage = Math.clamp(itemPage, 0, pages - 1);
        if (pages > 1) {
            addRenderableWidget(new UiButton(left + panelWidth - 36, requiredTop, 24, 18, ">", () -> {
                itemPage++;
                rebuildWidgets();
            }));
            addRenderableWidget(new UiButton(left + panelWidth - 62, requiredTop, 24, 18, "<", () -> {
                itemPage--;
                rebuildWidgets();
            }));
        }
    }

    private int itemsPerPage() { return Math.max(1, (panelWidth - 79) / CELL); }

    @Override protected void renderUi(GuiGraphicsExtractor g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.bold(g, "Edit combo", left + 62, top + 19, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Rule name", left + 12, top + 33, UiDraw.SMALL, t.muted());
        UiDraw.text(g, "Min count", left + 98, top + 123, UiDraw.SMALL, t.muted());
        if (UiDraw.over(mx, my, left + 12, top + 113, 82, 20)) {
            String help = switch (contentsMode) {
                case All -> "Every listed item must reach the minimum count.";
                case Any -> "At least one listed item must reach the minimum count.";
                case Exact -> "Every listed item must reach the minimum. Extra item types are excluded.";
            };
            tooltip("combo.items", help);
        }
        int previewX = left + panelWidth - 23;
        if (pixels == null) {
            ShulkerIconRenderer.drawAt(g, icon.getDefaultInstance(), iconColour(), previewX, top + 88, 1);
        } else {
            ShulkerIconRenderer.drawPixels(g, pixels, previewX, top + 88, 1);
        }
        UiDraw.text(g, "Required items · click to remove", left + 12, requiredTop - 10, UiDraw.SMALL, t.muted());
        if (required.isEmpty()) UiDraw.text(g, "Add items or enter a shulker name.", left + 12, requiredTop + 8, UiDraw.SMALL, t.muted());
        int start = itemPage * itemsPerPage();
        for (int i = start; i < Math.min(required.size(), start + itemsPerPage()); i++) {
            int x = left + 12 + (i - start) * CELL, y = requiredTop;
            boolean hover = UiDraw.over(mx, my, x, y, CELL - 2, CELL - 2);
            UiDraw.rounded(g, x, y, CELL - 2, CELL - 2, 5, hover ? UiDraw.alpha(t.red(), .35f) : t.surface());
            ShulkerIconRenderer.drawAt(g, required.get(i).getDefaultInstance(), null, x + 1, y + 1, 1);
            if (hover) UiDraw.text(g, UiDraw.fit(required.get(i).getDefaultInstance().getHoverName().getString(), panelWidth - 28), left + 14, top + panelHeight - 12, UiDraw.SMALL, t.text());
        }
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            int start = itemPage * itemsPerPage();
            for (int i = start; i < Math.min(required.size(), start + itemsPerPage()); i++) {
                int x = left + 12 + (i - start) * CELL, y = requiredTop;
                if (UiDraw.over(event.x(), event.y(), x, y, CELL - 2, CELL - 2)) { required.remove(i); commit(); rebuildWidgets(); return true; }
            }
        }
        return super.mouseClickedUi(event, doubled);
    }
}

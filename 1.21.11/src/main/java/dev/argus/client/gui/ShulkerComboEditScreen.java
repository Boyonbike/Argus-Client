package dev.argus.client.gui;

import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Style;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
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
    private String nameText;
    private boolean enabled, solid;
    private Item icon;
    private int colour;
    private final List<Item> required;
    private int left, top, panelWidth, panelHeight, requiredTop;

    ShulkerComboEditScreen(Screen parent, TooltipsModule module, int index) {
        super("Edit combo", parent, "Modules");
        this.module = module;
        this.index = index;
        Rule<Item> rule = module.getIconRules().get(index);
        nameText = rule.name(); enabled = rule.enabled(); solid = rule.style() == Style.SolidColour;
        icon = rule.icon(); colour = rule.colour(); required = new ArrayList<>(rule.required());
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
        rules.set(index, new Rule<>(nameText, enabled, required, icon, solid ? Style.SolidColour : Style.Normal, colour));
        module.setIconRules(rules);
    }

    private Integer iconColour() { return solid ? colour : null; }

    private int perRow() { return Math.max(1, (panelWidth - 28) / CELL); }

    @Override protected void buildContent() {
        panelWidth = Math.min(520, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        addRenderableWidget(new UiButton(left + 10, top + 10, 42, 18, "Back", this::onClose));
        name.place(left + 14, top + 56, panelWidth - 28);
        addRenderableWidget(name);
        addRenderableWidget(new UiButton(left + 14, top + 88, 90, 20, enabled ? "Enabled" : "Disabled", enabled, () -> { enabled = !enabled; commit(); rebuildWidgets(); }));
        addRenderableWidget(new UiButton(left + 110, top + 88, 110, 20, solid ? "Solid colour: on" : "Solid colour: off", solid, () -> { solid = !solid; commit(); rebuildWidgets(); }));
        hex.place(left + 262, top + 88, 90);
        addRenderableWidget(hex);
        int iconY = top + 124;
        addRenderableWidget(new UiButton(left + 14, iconY + 2, 100, 20, "Select icon", () ->
            minecraft.setScreen(new ShulkerIconPickerScreen(this, solid, colour, false, item -> { icon = item; commit(); }))));
        requiredTop = top + 178;
        addRenderableWidget(new UiButton(left + panelWidth - 114, requiredTop - 26, 100, 20, "Add item", () ->
            minecraft.setScreen(new ShulkerIconPickerScreen(this, false, 0xffffffff, true, item -> {
                if (!required.contains(item)) { required.add(item); commit(); }
            }))));
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.bold(g, "Edit combo", left + 62, top + 19, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Name", left + 14, top + 46, UiDraw.SMALL, t.muted());
        UiDraw.text(g, "Colour (hex)", left + 232 - 4 + 30, top + 80, UiDraw.SMALL, t.muted());
        UiDraw.rounded(g, left + panelWidth - 40, top + 88, 26, 20, 6, 0xff000000 | colour);
        UiDraw.outline(g, left + panelWidth - 40, top + 88, 26, 20, 6, .6f, UiDraw.alpha(t.text(), .25f));
        int iconY = top + 124;
        UiDraw.text(g, "Icon", left + 14, iconY - 6, UiDraw.SMALL, t.muted());
        ShulkerIconRenderer.drawAt(g, icon.getDefaultInstance(), iconColour(), left + 122, iconY + 3, 1.25f);
        UiDraw.text(g, UiDraw.fit(icon.getDefaultInstance().getHoverName().getString(), panelWidth - 170), left + 148, iconY + 12, t.text());
        UiDraw.text(g, "Required items (at least one of each; click to remove)", left + 14, requiredTop - 16, UiDraw.SMALL, t.muted());
        if (required.isEmpty()) UiDraw.text(g, "None yet. A combo with no required items never matches.", left + 14, requiredTop + 10, UiDraw.SMALL, t.muted());
        int perRow = perRow();
        for (int i = 0; i < required.size(); i++) {
            float x = left + 14 + (i % perRow) * CELL, y = requiredTop + (i / perRow) * CELL;
            boolean hover = UiDraw.over(mx, my, x, y, CELL - 2, CELL - 2);
            UiDraw.rounded(g, x, y, CELL - 2, CELL - 2, 5, hover ? UiDraw.alpha(t.red(), .35f) : t.surface());
            ShulkerIconRenderer.drawAt(g, required.get(i).getDefaultInstance(), null, x + 1, y + 1, 1);
            if (hover) UiDraw.text(g, UiDraw.fit(required.get(i).getDefaultInstance().getHoverName().getString(), panelWidth - 28), left + 14, top + panelHeight - 12, UiDraw.SMALL, t.text());
        }
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            int perRow = perRow();
            for (int i = 0; i < required.size(); i++) {
                float x = left + 14 + (i % perRow) * CELL, y = requiredTop + (i / perRow) * CELL;
                if (UiDraw.over(event.x(), event.y(), x, y, CELL - 2, CELL - 2)) { required.remove(i); commit(); return true; }
            }
        }
        return super.mouseClickedUi(event, doubled);
    }
}

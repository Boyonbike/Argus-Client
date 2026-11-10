package dev.argus.client.gui;

import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Style;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Ordered shulker icon combos. The first enabled combo whose required items are all present picks the icon. */
final class ShulkerCombosScreen extends UiScreen {
    private static final int ROW = 26;
    private final TooltipsModule module;
    private int left, top, panelWidth, panelHeight, page;
    private int confirming = -1;
    private long confirmAt;
    private boolean confirmingReset;

    ShulkerCombosScreen(Screen parent, TooltipsModule module) {
        super("Shulker icon combos", parent, "Modules");
        this.module = module;
    }

    private void update(List<Rule<Item>> rules) { module.setIconRules(rules); rebuildWidgets(); }

    private boolean pending(int index) { return confirming == index && System.currentTimeMillis() - confirmAt < 4000; }

    @Override protected void buildContent() {
        panelWidth = Math.min(520, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        addRenderableWidget(new UiButton(left + 10, top + 10, 42, 18, "Back", this::onClose));
        addRenderableWidget(new UiButton(left + panelWidth - 202, top + 10, 90, 18, "Add combo", () -> {
            List<Rule<Item>> rules = new ArrayList<>(module.getIconRules());
            rules.add(new Rule<>("Combo " + (rules.size() + 1), true, List.of(), Items.DIAMOND_CHESTPLATE));
            module.setIconRules(rules);
            minecraft.setScreen(new ShulkerComboEditScreen(this, module, rules.size() - 1));
        }));
        boolean resetting = confirmingReset && System.currentTimeMillis() - confirmAt < 4000;
        addRenderableWidget(new UiButton(left + panelWidth - 106, top + 10, 96, 18, resetting ? "Confirm reset" : "Reset all", () -> {
            if (module.getIconRules().isEmpty()) return;
            if (confirmingReset && System.currentTimeMillis() - confirmAt < 4000) { confirmingReset = false; update(List.of()); }
            else { confirmingReset = true; confirmAt = System.currentTimeMillis(); rebuildWidgets(); }
        }));

        List<Rule<Item>> rules = module.getIconRules();
        int perPage = Math.max(1, (panelHeight - 90) / ROW);
        int pages = Math.max(1, (rules.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * perPage; i < Math.min(rules.size(), (page + 1) * perPage); i++) {
            int index = i, y = top + 62 + (i - page * perPage) * ROW;
            Rule<Item> rule = rules.get(i);
            int x = left + panelWidth - 14;
            x -= 56; addRenderableWidget(new UiButton(x, y, 56, 20, pending(index) ? "Confirm" : "Delete", () -> {
                if (pending(index)) { confirming = -1; List<Rule<Item>> next = new ArrayList<>(rules); next.remove(index); update(next); }
                else { confirming = index; confirmAt = System.currentTimeMillis(); rebuildWidgets(); }
            }));
            x -= 26; addRenderableWidget(new UiButton(x, y, 22, 20, "v", () -> move(index, index + 1)));
            x -= 26; addRenderableWidget(new UiButton(x, y, 22, 20, "^", () -> move(index, index - 1)));
            x -= 44; addRenderableWidget(new UiButton(x, y, 40, 20, "Edit", () -> minecraft.setScreen(new ShulkerComboEditScreen(this, module, index))));
            x -= 44; addRenderableWidget(new UiButton(x, y, 40, 20, rule.enabled() ? "On" : "Off", rule.enabled(), () -> {
                List<Rule<Item>> next = new ArrayList<>(rules);
                next.set(index, new Rule<>(rule.name(), !rule.enabled(), rule.required(), rule.icon(), rule.style(), rule.colour()));
                update(next);
            }));
        }
        if (pages > 1) {
            addRenderableWidget(new UiButton(left + 14, top + panelHeight - 30, 58, 18, "Previous", () -> { page--; rebuildWidgets(); }));
            addRenderableWidget(new UiButton(left + 78, top + panelHeight - 30, 42, 18, "Next", () -> { page++; rebuildWidgets(); }));
        }
    }

    private void move(int from, int to) {
        List<Rule<Item>> rules = new ArrayList<>(module.getIconRules());
        if (to < 0 || to >= rules.size()) return;
        Collections.swap(rules, from, to);
        update(rules);
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.bold(g, "Shulker icon combos", left + 62, top + 19, UiDraw.TITLE, t.text());
        UiDraw.text(g, "The first matching combo wins. Every required item must be inside; extras are allowed.", left + 14, top + 43, UiDraw.SMALL, t.muted());
        List<Rule<Item>> rules = module.getIconRules();
        if (rules.isEmpty()) UiDraw.text(g, "No combos yet. Without one, the most common item is shown.", left + 14, top + 76, UiDraw.SMALL, t.muted());
        int perPage = Math.max(1, (panelHeight - 90) / ROW);
        for (int i = page * perPage; i < Math.min(rules.size(), (page + 1) * perPage); i++) {
            Rule<Item> rule = rules.get(i);
            int y = top + 62 + (i - page * perPage) * ROW;
            int colour = rule.enabled() ? t.text() : t.muted();
            ShulkerIconRenderer.drawAt(g, rule.icon().getDefaultInstance(), rule.style() == Style.SolidColour ? rule.colour() : null, left + 14, y + 2, 1);
            float textRight = left + panelWidth - 14 - 56 - 26 - 26 - 44 - 44 - 6;
            UiDraw.text(g, UiDraw.fit((i + 1) + ". " + rule.name(), textRight - (left + 38)), left + 38, y + 7, colour);
            UiDraw.text(g, rule.required().size() + (rule.required().size() == 1 ? " required item" : " required items"), left + 38, y + 18, UiDraw.SMALL, t.muted());
        }
    }
}

package dev.argus.client.gui;

import dev.argus.client.modules.player.AutoRekitModule;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Inventory presets live with Auto Rekit, independently of whole-client configuration presets. */
final class RekitPresetsScreen extends UiScreen {
    private final AutoRekitModule module;
    private final UiInput name = new UiInput(0, 0, 100, 16, "Preset name…");
    private int left, top, panelWidth, panelHeight, page;
    private String message = "";
    private String deleting;
    private long deleteAt;

    RekitPresetsScreen(Screen parent, AutoRekitModule module) {
        super("Rekit presets", parent, "Modules");
        this.module = module;
        name.setMaxLength(32);
    }

    private void action(Runnable action, String success) {
        try { action.run(); message = success; }
        catch (RuntimeException failure) { message = failure.getMessage() == null ? "Couldn't update the preset." : failure.getMessage(); }
        rebuildWidgets();
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(460, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        addRenderableWidget(new UiButton(left + 10, top + 10, 42, 18, "Back", this::onClose));
        name.place(left + 14, top + 74, panelWidth - 144);
        addRenderableWidget(name);
        addRenderableWidget(new UiButton(left + panelWidth - 122, top + 72, 108, 20, "Save inventory", () ->
                action(() -> module.capturePreset(name.getValue().trim()), "Saved and activated the inventory preset.")));
        List<String> names = module.presetNames();
        int perPage = Math.max(1, (panelHeight - 170) / 28);
        int pages = Math.max(1, (names.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * perPage; i < Math.min(names.size(), (page + 1) * perPage); i++) {
            String preset = names.get(i);
            int y = top + 112 + (i - page * perPage) * 28;
            boolean active = preset.equals(module.activePreset.getValue());
            addRenderableWidget(new UiButton(left + 14, y, panelWidth - 152, 20,
                    (active ? "✓ " : "") + preset, active, () -> action(() -> module.selectPreset(preset), "Active preset: " + preset)));
            addRenderableWidget(new UiButton(left + panelWidth - 132, y, 48, 20, "Update", () ->
                    action(() -> module.capturePreset(preset), "Updated and activated " + preset + ".")));
            boolean confirm = preset.equals(deleting) && System.currentTimeMillis() - deleteAt < 4000;
            addRenderableWidget(new UiButton(left + panelWidth - 78, y, 64, 20, confirm ? "Confirm" : "Delete", () -> {
                if (preset.equals(deleting) && System.currentTimeMillis() - deleteAt < 4000) {
                    action(() -> module.deletePreset(preset), "Deleted " + preset + "."); deleting = null;
                } else { deleting = preset; deleteAt = System.currentTimeMillis(); message = "Click Confirm to delete " + preset + "."; rebuildWidgets(); }
            }));
        }
        if (pages > 1) {
            addRenderableWidget(new UiButton(left + 14, top + panelHeight - 42, 58, 18, "Previous", () -> { page--; rebuildWidgets(); }));
            addRenderableWidget(new UiButton(left + 78, top + panelHeight - 42, 42, 18, "Next", () -> { page++; rebuildWidgets(); }));
        }
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.bold(g, "Rekit presets", left + 62, top + 19, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Save inventory, worn armour and offhand, then click a preset to activate it.", left + 14, top + 43, UiDraw.SMALL, t.muted());
        UiDraw.text(g, "Active: " + (module.activePreset.getValue().isEmpty() ? "None" : module.activePreset.getValue()), left + 14, top + 57, UiDraw.SMALL, t.accent());
        if (module.presetNames().isEmpty()) UiDraw.text(g, "No presets yet. Arrange your inventory, name it and save.", left + 14, top + 124, UiDraw.SMALL, t.muted());
        UiDraw.text(g, UiDraw.fit(message.isEmpty() ? "Saved slots keep their item types, components and refill counts." : message, panelWidth - 28),
                left + 14, top + panelHeight - 12, UiDraw.SMALL, t.muted());
    }
}

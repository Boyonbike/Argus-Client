package dev.argus.client.gui;


import dev.argus.client.modules.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** One module's page: a floating panel with a back button holding the shared {@link ModulePage}. */
final class ModuleSettingsScreen extends UiScreen {
    private static final float BAR = 24;
    private final ModulePage page;
    private int left, top, panelWidth, panelHeight;

    ModuleSettingsScreen(Screen parent, Module module, String setting) {
        super(module.getName(), parent, "Modules");
        DescriptionCatalog.ensureApplied();
        page = new ModulePage(this, module, setting, true);
        ModuleTools.attach(page, this, module, () -> this);
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(580, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 20;
        page.layout(left + 14, top + BAR + 8, panelWidth - 28, panelHeight - BAR - 8, left, left + panelWidth);
    }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // Breadcrumb bar: back · Modules › Category.
        boolean backHover = UiDraw.over(mx, my, left + 8, top + 6, 64, 16);
        float bh = UiAnim.smooth("set.back", backHover ? 1 : 0, 18);
        if (bh > 0) UiDraw.rounded(g, left + 8, top + 6, 64, 16, 8, UiDraw.alpha(t.surface(), bh));
        UiDraw.icon(g, "chevron_left", left + 12 - bh * 1.5f, top + 10, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.text(g, "Modules", left + 23, top + 14, UiDraw.SMALL, UiDraw.mix(t.muted(), t.text(), bh));
        String crumb = GuiState.title(page.module().getCategory().getName());
        UiDraw.icon(g, "chevron_right", left + 74, top + 10.5f, 7, UiDraw.alpha(t.muted(), .6f));
        UiDraw.text(g, crumb, left + 84, top + 14, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
        UiDraw.rect(g, left + 1, top + BAR, panelWidth - 2, .6f, UiDraw.alpha(t.overlay(), .45f));
        page.render(g, mx, my, delta, t);
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && UiDraw.over(event.x(), event.y(), left + 8, top + 6, 64, 16)) { page.stopEditing(); onClose(); return true; }
        return page.mouseClicked(event, doubled) || super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) { return page.mouseDragged(event) || super.mouseDraggedUi(event, dx, dy); }
    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) { return page.mouseReleased() || super.mouseReleasedUi(event); }
    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) { return page.mouseScrolled(mx, my, vertical); }
    @Override public boolean keyPressed(KeyEvent event) { return page.keyPressed(event) || super.keyPressed(event); }
}

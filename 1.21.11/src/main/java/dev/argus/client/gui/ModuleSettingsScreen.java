package dev.argus.client.gui;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.Locale;

/** One module's page: title, enable switch and keybind, then its settings as scrolling cards. */
final class ModuleSettingsScreen extends UiScreen {
    private static final int HEAD = 56;
    private final Module module;
    private final SettingsCards cards;
    private int left, top, panelWidth, panelHeight;
    private boolean binding, scrolledToFocus;
    private float pageScroll, shownPageScroll;

    ModuleSettingsScreen(Screen parent, Module module, String setting) {
        super(module.getName(), parent, "Modules");
        DescriptionCatalog.ensureApplied();
        this.module = module;
        cards = new SettingsCards(this, module, setting);
        scrolledToFocus = setting == null;
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(460, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        cards.panelEdges(left, left + panelWidth);
        cards.reattach();
    }

    private float pageTop() { return top + HEAD; }
    private float pageBottom() { return top + panelHeight - 24; }
    private String bindLabel() { return module.getKeybind().isUnbound() ? "Not bound" : module.getKeybind().getDisplayName(); }
    private float chipWidth(String label) { return UiDraw.width(label, UiDraw.SMALL) + 24; }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        boolean backHover = UiDraw.over(mx, my, left + 10, top + 10, 18, 18);
        float bh = UiAnim.smooth("set.back", backHover ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.icon(g, module.getCategory().name().toLowerCase(Locale.ROOT), left + 36, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, UiDraw.fit(GuiState.title(module.getName()), panelWidth - 150), left + 49, top + 18, UiDraw.TITLE, t.text());
        String description = UiText.stripFormatting(module.getDescription());
        if (description == null || description.isBlank()) description = "No description";
        UiDraw.text(g, UiDraw.fit(description, panelWidth - 140), left + 36, top + 31, UiDraw.SMALL, t.muted());
        boolean enabledHover = UiDraw.over(mx, my, left + panelWidth - 80, top + 10, 70, 18);
        float on = UiAnim.smooth("set.enabled." + module.getName(), module.isToggled() ? 1 : 0, 16);
        UiDraw.right(g, module.isToggled() ? "Enabled" : "Disabled", left + panelWidth - 38, top + 19, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
        UiDraw.toggle(g, left + panelWidth - 32, top + 14, on, enabledHover, t);
        String label = binding ? "Press a key…" : bindLabel();
        float chipW = chipWidth(label), chipX = left + panelWidth - 14 - chipW;
        boolean chipHover = UiDraw.over(mx, my, chipX, top + 33, chipW, 14);
        float ch = UiAnim.smooth("set.chip", chipHover || binding ? 1 : 0, 18);
        float pulse = binding ? .5f + .5f * (float) Math.sin(System.nanoTime() / 1.5e8) : 0;
        UiDraw.rounded(g, chipX, top + 33, chipW, 14, 7, binding ? UiDraw.alpha(t.accent(), .18f + pulse * .12f) : UiDraw.mix(t.surface(), t.overlay(), ch * .7f));
        if (binding) UiDraw.outline(g, chipX, top + 33, chipW, 14, 7, .7f, UiDraw.alpha(t.accent(), .5f + pulse * .5f));
        UiDraw.icon(g, "keyboard", chipX + 6, top + 36, 8, binding ? t.accent() : t.muted());
        UiDraw.text(g, label, chipX + 17, top + 40, UiDraw.SMALL, binding ? t.accent() : t.text());
        UiDraw.right(g, "Keybind", chipX - 6, top + 40, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
        if (chipHover && !binding) tooltip("set.chip", "Keybind", "Click to set · Right-click to clear");
        UiDraw.rect(g, left + 10, pageTop() - 6, panelWidth - 20, .6f, UiDraw.alpha(t.overlay(), .6f));
        // Cards, scrolling as one page.
        float view = pageBottom() - pageTop(), total = cards.height();
        if (!scrolledToFocus) { pageScroll = cards.focusOffset(); shownPageScroll = pageScroll; scrolledToFocus = true; }
        pageScroll = Math.clamp(pageScroll, 0, Math.max(0, total - view));
        shownPageScroll = UiAnim.approach(shownPageScroll, pageScroll, 18);
        float x = left + 10, w = panelWidth - 20;
        g.enableScissor((int) x - 2, (int) pageTop(), (int) (x + w + 2), (int) pageBottom());
        if (cards.isEmpty()) UiDraw.center(g, "This module has no settings.", left, pageTop() + 16, panelWidth, t.muted());
        else cards.render(g, mx, my, delta, t, x, pageTop() - shownPageScroll, w, pageTop(), pageBottom());
        g.disableScissor();
        if (shownPageScroll > 1) UiDraw.gradient(g, x - 2, pageTop(), w + 4, 10, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownPageScroll < Math.max(0, total - view) - 1) UiDraw.gradient(g, x - 2, pageBottom() - 10, w + 4, 10, 0, UiDraw.alpha(t.base(), 0), t.base());
        if (total > view) {
            float track = Math.max(16, view * view / total), offset = (view - track) * shownPageScroll / Math.max(1, total - view);
            UiDraw.rounded(g, left + panelWidth - 6, pageTop() + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
        cards.popup(g, t, width, height);
        String message = cards.message();
        String hint = binding ? "Press a key or mouse button · Esc cancels · Delete clears"
                : "Click a card's title to fold it · hover a setting for details · the reset icon restores the default";
        UiDraw.text(g, UiDraw.fit(message.isEmpty() ? hint : message, panelWidth - 24), left + 14, top + panelHeight - 11, UiDraw.SMALL,
                message.isEmpty() ? UiDraw.alpha(t.muted(), .8f) : t.red());
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (binding) { module.setKeybind(new Keybind(event.button(), module.getKeybind().mode(), true)); binding = false; return true; }
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { onClose(); return true; }
        if (event.button() == 0 && UiDraw.over(mx, my, left + panelWidth - 80, top + 10, 70, 18)) { module.toggle(); return true; }
        float chipW = chipWidth(bindLabel());
        if (UiDraw.over(mx, my, left + panelWidth - 14 - chipW, top + 33, chipW, 14)) {
            if (event.button() == 1) module.setKeybind(Keybind.UNBOUND); else { cards.stopEditing(); binding = true; }
            return true;
        }
        if (cards.mouseClicked(event, doubled, pageTop(), pageBottom())) return true;
        return super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) { return cards.mouseDragged(event) || super.mouseDraggedUi(event, dx, dy); }
    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) { return cards.mouseReleased() || super.mouseReleasedUi(event); }
    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (!UiDraw.over(mx, my, left, pageTop(), panelWidth, pageBottom() - pageTop())) return false;
        pageScroll = Math.max(0, pageScroll - (float) vertical * 40);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (binding) {
            if (event.key() != 256) module.setKeybind(event.key() == 259 || event.key() == 261 ? Keybind.UNBOUND : new Keybind(event.key(), module.getKeybind().mode(), false));
            binding = false; return true;
        }
        if (cards.keyPressed(event)) return true;
        return super.keyPressed(event);
    }
}

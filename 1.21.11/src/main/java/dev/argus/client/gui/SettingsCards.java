package dev.argus.client.gui;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A module's settings as a stack of cards: a General card for settings before the first group,
 * then one collapsible card per settings group (membership follows the module's setting order).
 */
final class SettingsCards {
    private static final float GAP = 8;

    private static final class Section {
        final Setting<?> group;
        final SettingsList list;
        final int size;
        float body;
        float top;
        Section(Setting<?> group, SettingsList list, int size) { this.group = group; this.list = list; this.size = size; }
        boolean open() { return group == null || group.isExpanded(); }
    }

    private final Module module;
    private final List<Section> sections = new ArrayList<>();
    private final String focusSetting;
    private float x, w;

    SettingsCards(UiScreen host, Module module, String focusSetting) {
        this.module = module;
        this.focusSetting = focusSetting;
        if (focusSetting != null) GuiSettings.expandGroups(module.getSettings());
        Setting<?> group = null;
        List<Setting<?>> members = new ArrayList<>();
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isGroupOnly()) { add(host, group, members); group = setting; members = new ArrayList<>(); }
            else members.add(setting);
        }
        add(host, group, members);
        for (Section section : sections) section.body = section.open() ? section.list.contentHeight() + 6 : 0;
    }

    private void add(UiScreen host, Setting<?> group, List<Setting<?>> members) {
        if (members.isEmpty()) return;
        boolean focused = focusSetting != null && members.stream().anyMatch(s -> s.getName().equals(focusSetting));
        sections.add(new Section(group, new SettingsList(host, module, focused ? focusSetting : null, List.copyOf(members)), members.size()));
    }

    Module module() { return module; }
    boolean isEmpty() { return sections.isEmpty(); }

    void panelEdges(float left, float right) { for (Section s : sections) s.list.panelEdges(left, right); }

    /** Total height of all cards (with their current, animated body heights). */
    float height() {
        float total = 0;
        for (Section s : sections) total += UiDraw.CARD_HEAD + s.body + GAP;
        return Math.max(0, total - GAP);
    }

    /** Offset of the card holding the focused setting, for scrolling it into view. */
    float focusOffset() {
        float offset = 0;
        for (Section s : sections) {
            if (focusSetting != null && s.list.showsSetting(focusSetting)) return offset;
            offset += UiDraw.CARD_HEAD + s.body + GAP;
        }
        return 0;
    }

    void render(GuiGraphics g, int mx, int my, float delta, UiTheme t, float x, float y, float w, float clipTop, float clipBottom) {
        this.x = x; this.w = w;
        for (Section s : sections) {
            float target = s.open() ? s.list.contentHeight() + 6 : 0;
            s.body = UiAnim.approach(s.body, target, 16);
            s.top = y;
            float h = UiDraw.CARD_HEAD + s.body;
            String title = s.group == null ? "General" : GuiState.title(s.group.getName());
            // Open cards count the rows showing (options under a switch stay hidden until it is expanded).
            int count = s.open() ? s.list.rowCount() : s.size;
            String subtitle = count + (count == 1 ? " setting" : " settings") + (s.group != null && !s.open() ? " · click to show" : "");
            UiDraw.card(g, x, y, w, h, s.group == null ? "sliders" : "misc", title, subtitle, 40, t);
            if (s.group != null) {
                boolean hover = UiDraw.over(mx, my, x, y, w, UiDraw.CARD_HEAD) && my >= clipTop && my < clipBottom;
                float hv = UiAnim.smooth("card.h." + System.identityHashCode(s.group), hover ? 1 : 0, 20);
                float open = UiAnim.smooth("card.o." + System.identityHashCode(s.group), s.open() ? 1 : 0, 16);
                int colour = UiDraw.mix(t.muted(), t.text(), hv);
                if (hv > 0) UiDraw.circle(g, x + w - UiDraw.CARD_PAD - 7, y + 15, 8, UiDraw.alpha(t.surface(), hv));
                UiDraw.icon(g, "chevron_down", x + w - UiDraw.CARD_PAD - 11, y + 11, 8, UiDraw.alpha(colour, open));
                UiDraw.icon(g, "chevron_right", x + w - UiDraw.CARD_PAD - 11, y + 11, 8, UiDraw.alpha(colour, 1 - open));
            }
            if (s.body > 1) {
                // The body is clipped to the card while it opens or closes.
                float bottom = Math.min(clipBottom, y + h);
                g.enableScissor((int) x, (int) Math.max(clipTop, y + UiDraw.CARD_HEAD - 2), (int) (x + w), (int) bottom);
                s.list.embed(x + UiDraw.CARD_PAD - 4, y + UiDraw.CARD_HEAD, w - UiDraw.CARD_PAD * 2 + 8, clipTop, bottom);
                s.list.render(g, mx, my, delta, t);
                g.disableScissor();
            }
            y += h + GAP;
        }
    }

    void popup(GuiGraphics g, UiTheme t, int width, int height) { for (Section s : sections) if (s.body > 1) s.list.popup(g, t, width, height); }

    String message() {
        for (Section s : sections) { String m = s.list.message(); if (!m.isEmpty()) return m; }
        return "";
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubled, float clipTop, float clipBottom) {
        double mx = event.x(), my = event.y();
        if (my < clipTop || my >= clipBottom) { stopEditing(); return false; }
        for (Section s : sections) {
            if (s.group != null && event.button() == 0 && UiDraw.over(mx, my, x, s.top, w, UiDraw.CARD_HEAD)) {
                stopEditing();
                s.group.expanded = !s.group.isExpanded();
                return true;
            }
        }
        for (Section s : sections) if (s.body > 1 && s.list.mouseClicked(event, doubled)) return true;
        return false;
    }

    boolean mouseDragged(MouseButtonEvent event) { for (Section s : sections) if (s.list.mouseDragged(event)) return true; return false; }
    boolean mouseReleased() { for (Section s : sections) if (s.list.mouseReleased()) return true; return false; }
    boolean keyPressed(KeyEvent event) { for (Section s : sections) if (s.list.keyPressed(event)) return true; return false; }
    void reattach() { for (Section s : sections) s.list.reattach(); }
    void stopEditing() { for (Section s : sections) s.list.stopEditing(); }
}

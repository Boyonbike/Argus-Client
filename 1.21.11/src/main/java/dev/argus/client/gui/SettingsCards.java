package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.modules.misc.ChatToolsModule;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        final List<Setting<?>> members;
        final int size;
        /** Title and icon for a card that isn't one of the module's own groups, or null. */
        final String title, icon;
        float body;
        float top;
        Section(Setting<?> group, SettingsList list, List<Setting<?>> members, String title, String icon) {
            this.group = group; this.list = list; this.members = members; this.size = members.size(); this.title = title; this.icon = icon;
        }
        boolean open() { return group == null || group.isExpanded(); }
        String title() { return title != null ? title : group == null ? "General" : GuiState.title(group.getName()); }
    }

    private final UiScreen host;
    private final Module module;
    private final List<Section> sections = new ArrayList<>();
    private final String focusSetting;
    private float x, w;

    SettingsCards(UiScreen host, Module module, String focusSetting) {
        this.host = host;
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
        addBetterChat(host);
        for (Section section : sections) section.body = section.open() ? section.list.contentHeight() + 6 : 0;
    }

    private void add(UiScreen host, Setting<?> group, List<Setting<?>> members) { add(host, module, group, members, null, null); }

    private void add(UiScreen host, Module owner, Setting<?> group, List<Setting<?>> members, String title, String icon) {
        if (members.isEmpty()) return;
        boolean focused = focusSetting != null && members.stream().anyMatch(s -> s.getName().equals(focusSetting));
        List<Setting<?>> copy = List.copyOf(members);
        sections.add(new Section(group, new SettingsList(host, owner, focused ? focusSetting : null, copy), copy, title, icon));
    }

    /**
     * BetterChat is hidden and runs inside Chat Tools, so its settings follow Chat Tools' own as extra cards. Each card's
     * list still belongs to BetterChat, so its descriptions and saving work as before.
     */
    private void addBetterChat(UiScreen host) {
        if (!(module instanceof ChatToolsModule)) return;
        Module betterChat = ClientContext.getModule(BetterChatModule.class);
        if (betterChat == null) return;
        List<Setting<?>> settings = betterChat.getSettings();
        if (focusSetting != null) GuiSettings.expandGroups(settings);
        for (var span : ChatSettingsSections.split(settings.stream().map(Setting::getName).toList()))
            add(host, betterChat, null, settings.subList(span.from(), span.to()), span.title(), span.icon());
    }

    // ---- sections, for the module page's section rail -----------------------------------------

    int sectionCount() { return sections.size(); }
    String title(int index) { return sections.get(index).title(); }
    String icon(int index) { Section s = sections.get(index); return s.icon != null ? s.icon : s.group == null ? "sliders" : "misc"; }

    /** Offset of a card from the top of the stack, using the current (animated) card heights. */
    float offset(int index) {
        float offset = 0;
        for (int i = 0; i < index; i++) offset += UiDraw.CARD_HEAD + sections.get(i).body + GAP;
        return offset;
    }

    /** Opens a folded card (the General card is always open). */
    void open(int index) { Section s = sections.get(index); if (s.group != null) s.group.expanded = true; }

    private static boolean changed(Setting<?> setting) { return !Objects.equals(setting.getValue(), setting.getDefaultValue()); }

    /** Settings in a card that differ from their defaults. */
    int changed(int index) { return (int) sections.get(index).members.stream().filter(SettingsCards::changed).count(); }
    int changed() { int total = 0; for (int i = 0; i < sections.size(); i++) total += changed(i); return total; }

    /** Restores every setting on the page to its default and flashes the rows that changed. */
    int resetAll() {
        stopEditing();
        int count = 0;
        for (Section s : sections) for (Setting<?> setting : s.members) if (changed(setting)) {
            setting.reset(); count++;
            UiAnim.set("set." + System.identityHashCode(setting) + ".reset", 1);
        }
        return count;
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
        for (int index = 0; index < sections.size(); index++) {
            Section s = sections.get(index);
            float target = s.open() ? s.list.contentHeight() + 6 : 0;
            s.body = UiAnim.approach(s.body, target, 16);
            s.top = y;
            float h = UiDraw.CARD_HEAD + s.body;
            String title = s.title();
            // Open cards count the rows showing (options under a switch stay hidden until it is expanded).
            int count = s.open() ? s.list.rowCount() : s.size;
            int changed = changed(index);
            String subtitle = count + (count == 1 ? " setting" : " settings") + (changed > 0 ? " · " + changed + " changed" : "")
                    + (s.group != null && !s.open() ? " · click to show" : "");
            // Cards rise into place one after another when the page opens.
            float appear = host.appear(index + 3), opacity = UiRender.opacity;
            UiRender.opacity = opacity * appear;
            g.pose().pushMatrix();
            g.pose().translate(0, (1 - appear) * 10);
            try {
            boolean cardHover = UiDraw.over(mx, my, x, y, w, h) && my >= clipTop && my < clipBottom;
            float lift = UiAnim.smooth("card.l." + module.getName() + "." + index, cardHover ? 1 : 0, 14);
            UiDraw.card(g, x, y, w, h, icon(index), title, subtitle, 40, t);
            if (lift > .01f) UiDraw.outline(g, x, y, w, h, 8, .7f, UiDraw.alpha(t.accent(), .28f * lift));
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
            } finally { g.pose().popMatrix(); UiRender.opacity = opacity; }
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

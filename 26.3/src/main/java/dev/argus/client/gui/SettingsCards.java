package dev.argus.client.gui;

import dev.argus.client.utils.InputButtons;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.modules.misc.ChatToolsModule;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A module's page as a stack of foldable cards: a General card for settings before the first group, one card per
 * settings group (membership follows the module's setting order), and the module's own tools (see {@link InlineCard}),
 * each placed where it belongs next to the settings it works with.
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
        /** Fold state for a card that isn't a settings group (a group folds through its own setting). */
        boolean folded;
        Section(Setting<?> group, SettingsList list, List<Setting<?>> members, String title, String icon) {
            this.group = group; this.list = list; this.members = members; this.size = members.size(); this.title = title; this.icon = icon;
        }
        boolean open() { return group != null ? group.isExpanded() : !folded; }
        void toggle() { if (group != null) group.expanded = !group.isExpanded(); else folded = !folded; }
        String title() { return title != null ? title : group == null ? "General" : GuiState.title(group.getName()); }
    }

    private final UiScreen host;
    private final Module module;
    private final List<Section> sections = new ArrayList<>();
    private final List<InlineCard> inline = new ArrayList<>();
    private final java.util.Map<InlineCard, Placement> anchors = new java.util.IdentityHashMap<>();
    private final String focusSetting;
    /** Top of each card this frame, for clicks on the header. */
    private final java.util.Map<Object, Float> tops = new java.util.IdentityHashMap<>();
    private float x, w;

    SettingsCards(UiScreen host, Module module, String focusSetting) {
        this.host = host;
        this.module = module;
        this.focusSetting = focusSetting;
        GuiSettings.expandGroups(module.getSettings());
        Setting<?> group = null;
        List<Setting<?>> members = new ArrayList<>();
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isGroupOnly()) { add(host, group, members); group = setting; members = new ArrayList<>(); }
            else members.add(setting);
        }
        add(host, group, members);
        addBetterChat(host);
        for (Section section : sections) section.body = section.list.contentHeight() + 6;
    }

    /**
     * Adds a card of the module's own tools.
     */
    void addInline(InlineCard card, Placement placement) { inline.add(card); anchors.put(card, placement); }

    /** Every card in display order. */
    private List<Object> order() {
        List<Object> order = new ArrayList<>();
        for (InlineCard card : inline) if (anchors.get(card).after() == null && !anchors.get(card).atEnd()) order.add(card);
        for (Section s : sections) {
            order.add(s);
            if (s.group != null) for (InlineCard card : inline) if (s.group.getName().equals(anchors.get(card).after())) order.add(card);
        }
        for (InlineCard card : inline) if (!order.contains(card)) order.add(card);
        return order;
    }

    private float bodyOf(Object item) { return item instanceof Section s ? s.body : ((InlineCard) item).shown(); }

    private void add(UiScreen host, Setting<?> group, List<Setting<?>> members) { add(host, module, group, members, null, null); }

    private void add(UiScreen host, Module owner, Setting<?> group, List<Setting<?>> members, String title, String icon) {
        // A group whose header is hidden is kept for config only (e.g. ESP's old target cards).
        if (members.isEmpty() || group != null && !group.isVisible()) return;
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

    private String icon(int index) { Section s = sections.get(index); return s.icon != null ? s.icon : s.group == null ? "sliders" : "misc"; }

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
    boolean isEmpty() { return sections.isEmpty() && inline.isEmpty(); }

    void panelEdges(float left, float right) { for (Section s : sections) s.list.panelEdges(left, right); }

    /** Total height of all cards (with their current, animated body heights). */
    float height() {
        float total = 0;
        for (Object item : order()) total += UiDraw.CARD_HEAD + bodyOf(item) + GAP;
        return Math.max(0, total - GAP);
    }

    /** Offset of the card holding the focused setting, for scrolling it into view. */
    float focusOffset() {
        float offset = 0;
        for (Object item : order()) {
            if (item instanceof Section s && focusSetting != null && s.list.showsSetting(focusSetting)) return offset;
            offset += UiDraw.CARD_HEAD + bodyOf(item) + GAP;
        }
        return 0;
    }

    void render(GuiGraphicsExtractor g, int mx, int my, float delta, UiTheme t, float x, float y, float w, float clipTop, float clipBottom) {
        this.x = x; this.w = w;
        tops.clear();
        List<Object> order = order();
        for (int index = 0; index < order.size(); index++) {
            Object item = order.get(index);
            Section s = item instanceof Section section ? section : null;
            InlineCard card = item instanceof InlineCard c ? c : null;
            boolean open = s != null ? s.open() : card.open;
            if (s != null) s.body = UiAnim.approach(s.body, open ? s.list.contentHeight() + 6 : 0, 16);
            else card.animate(open);
            tops.put(item, y);
            float body = bodyOf(item), h = UiDraw.CARD_HEAD + body;
            String title = s != null ? s.title() : card.title();
            String subtitle;
            if (s != null) {
                int count = open ? s.list.rowCount() : s.size, changed = changed(sections.indexOf(s));
                subtitle = count + (count == 1 ? " setting" : " settings") + (changed > 0 ? " · " + changed + " changed" : "");
            } else subtitle = card.subtitle();
            if (!open) subtitle += " · click to show";
            String icon = s != null ? icon(sections.indexOf(s)) : card.icon();
            // Cards rise into place one after another when the page opens.
            float appear = host.appear(index + 3), opacity = UiRender.opacity;
            UiRender.opacity = opacity * appear;
            g.pose().pushMatrix();
            g.pose().translate(0, (1 - appear) * 10);
            try {
                boolean cardHover = UiDraw.over(mx, my, x, y, w, h) && my >= clipTop && my < clipBottom;
                float lift = UiAnim.smooth("card.l." + module.getName() + "." + title, cardHover ? 1 : 0, 14);
                UiDraw.card(g, x, y, w, h, icon, title, subtitle, 40, t);
                if (lift > .01f) UiDraw.outline(g, x, y, w, h, 8, .7f, UiDraw.alpha(t.accent(), .28f * lift));
                boolean hover = UiDraw.over(mx, my, x, y, w, UiDraw.CARD_HEAD) && my >= clipTop && my < clipBottom;
                String foldKey = "card.o." + module.getName() + "." + title;
                float hv = UiAnim.smooth(foldKey + ".h", hover ? 1 : 0, 20);
                float shown = UiAnim.smooth(foldKey, open ? 1 : 0, 16);
                int colour = UiDraw.mix(t.muted(), t.text(), hv);
                if (hv > 0) UiDraw.circle(g, x + w - UiDraw.CARD_PAD - 7, y + 15, 8, UiDraw.alpha(t.surface(), hv));
                UiDraw.icon(g, "chevron_down", x + w - UiDraw.CARD_PAD - 11, y + 11, 8, UiDraw.alpha(colour, shown));
                UiDraw.icon(g, "chevron_right", x + w - UiDraw.CARD_PAD - 11, y + 11, 8, UiDraw.alpha(colour, 1 - shown));
                if (body > 1) {
                    // The body is clipped to the card while it opens or closes.
                    float bottom = Math.min(clipBottom, y + h);
                    g.enableScissor((int) x, (int) Math.max(clipTop, y + UiDraw.CARD_HEAD - 2), (int) (x + w), (int) bottom);
                    if (s != null) {
                        s.list.embed(x + UiDraw.CARD_PAD - 4, y + UiDraw.CARD_HEAD, w - UiDraw.CARD_PAD * 2 + 8, clipTop, bottom);
                        s.list.render(g, mx, my, delta, t);
                    } else card.render(g, mx, my, t, x + UiDraw.CARD_PAD, y + UiDraw.CARD_HEAD, w - UiDraw.CARD_PAD * 2, clipTop, Math.min(clipBottom, bottom));
                    g.disableScissor();
                } else if (card != null) card.hide();
            } finally { g.pose().popMatrix(); UiRender.opacity = opacity; }
            y += h + GAP;
        }
    }

    void popup(GuiGraphicsExtractor g, UiTheme t, int width, int height) {
        for (Section s : sections) if (s.body > 1) s.list.popup(g, t, width, height);
        for (InlineCard card : inline) if (card.open && card.shown() > 1) card.popup(g, t, width, height);
    }

    String message() {
        for (InlineCard card : inline) { String m = card.error(); if (!m.isEmpty()) return m; }
        for (Section s : sections) { String m = s.list.message(); if (!m.isEmpty()) return m; }
        return "";
    }

    /** The latest success message from an inline card, or an empty string. */
    String notice() {
        for (InlineCard card : inline) { String m = card.notice(); if (!m.isEmpty()) return m; }
        return "";
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubled, float clipTop, float clipBottom) {
        double my = event.y();
        if (my < clipTop || my >= clipBottom) { stopEditing(); return false; }
        for (InlineCard card : inline) if (card.open && card.shown() > 1 && card.mouseClicked(event, doubled)) { for (Section s : sections) s.list.stopEditing(); return true; }
        for (Object item : order()) {
            Float top = tops.get(item);
            if (top != null && InputButtons.glfw(event) == 0 && UiDraw.over(event.x(), my, x, top, w, UiDraw.CARD_HEAD)) {
                stopEditing();
                if (item instanceof Section s) s.toggle(); else { InlineCard card = (InlineCard) item; card.open = !card.open; }
                return true;
            }
        }
        for (Section s : sections) if (s.body > 1 && s.list.mouseClicked(event, doubled)) return true;
        return false;
    }

    boolean mouseDragged(MouseButtonEvent event) {
        for (InlineCard card : inline) if (card.dragged(event)) return true;
        for (Section s : sections) if (s.list.mouseDragged(event)) return true;
        return false;
    }
    boolean mouseReleased() {
        for (InlineCard card : inline) if (card.released()) return true;
        for (Section s : sections) if (s.list.mouseReleased()) return true;
        return false;
    }
    boolean keyPressed(KeyEvent event) { for (InlineCard card : inline) if (card.keyPressed(event)) return true; for (Section s : sections) if (s.list.keyPressed(event)) return true; return false; }
    void reattach() { for (InlineCard card : inline) card.reattach(); for (Section s : sections) s.list.reattach(); }
    void stopEditing() { for (InlineCard card : inline) card.stopEditing(); for (Section s : sections) s.list.stopEditing(); }
}

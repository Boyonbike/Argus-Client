package dev.argus.client.gui;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.SocialEntry;
import dev.argus.client.settings.options.SocialRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Settings → Friends: edits the core social registry (the same list /friend commands use). */
final class FriendsPanel {
    private static final int ROW = 24, LIST_TOP = 146;
    private final UiScreen host;
    private final UiInput nameBox;
    private int x, y, w, h, scroll;
    private float shownScroll;
    private List<String> friends = List.of();
    private String status = "";
    private boolean statusError;
    private long statusAt;

    FriendsPanel(UiScreen host) {
        this.host = host;
        nameBox = new UiInput(0, 0, 100, 12, "Player name…").withIcon("player");
        nameBox.setMaxLength(16);
        reload();
    }

    void layout(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        nameBox.place(x, y + 82, w - 70);
        host.hostAdd(nameBox);
    }

    void detach() { host.hostRemove(nameBox); }
    void focus() { host.hostFocus(nameBox); }

    private void reload() {
        List<String> names = new ArrayList<>();
        for (Object object : ClientContext.socialRegistry.getRegistry())
            if (object instanceof SocialEntry entry && entry.role() == SocialRole.FRIEND) names.add(entry.name());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        friends = List.copyOf(names);
    }

    private void status(String text, boolean error) { status = text; statusError = error; statusAt = System.nanoTime(); }

    private void add() {
        String name = nameBox.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { status("Use 1–16 letters, numbers or underscores.", true); return; }
        if (ClientContext.socialRegistry.isFriendName(name)) { status(name + " is already a friend.", true); return; }
        ClientContext.socialRegistry.addFriend(name); status("Added " + name + ".", false); nameBox.setValue(""); reload();
        UiAnim.set("friend." + name + ".a", 0);
    }

    private void remove(String name) { ClientContext.socialRegistry.removeSocial(name); status("Removed " + name + ".", false); reload(); }

    private int listTop() { return y + LIST_TOP; }
    private int listHeight() { return Math.max(ROW, h - LIST_TOP - 30); }
    private int maxScroll() { return Math.max(0, friends.size() * ROW - listHeight()); }
    private float removeX() { return x + w - 18; }
    private float addX() { return x + w - 60; }
    private float addY() { return y + 78; }

    void render(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        reload();
        UiDraw.icon(g, "people", x, y + 13.5f, 9, t.accent());
        UiDraw.bold(g, "Friends", x + 13, y + 18, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Friends are skipped by combat modules", x, y + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, y + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));
        UiDraw.card(g, x - 6, y + 46, w + 12, 66, "plus", "Add a friend", "Type their exact player name", 0, t);
        UiDraw.card(g, x - 6, y + 118, w + 12, listHeight() + 34, "people", "Friends",
                friends.size() + (friends.size() == 1 ? " friend" : " friends") + " · hover one to remove it", 0, t);
        nameBox.render(g, mx, my, delta);
        boolean addHover = UiDraw.over(mx, my, addX(), addY(), 60, 20);
        float ah = UiAnim.smooth("fr.add", addHover ? 1 : 0, 20);
        UiDraw.rounded(g, addX(), addY(), 60, 20, 7, UiDraw.alpha(t.accent(), .22f + ah * .14f));
        UiDraw.icon(g, "plus", addX() + 14, addY() + 6, 8, t.accent());
        UiDraw.text(g, "Add", addX() + 26, addY() + 10, t.accent());
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        int top = listTop(), view = listHeight();
        g.enableScissor(x - 4, top, x + w + 4, top + view);
        for (int i = 0; i < friends.size(); i++) {
            float rowY = top + i * ROW - shownScroll;
            if (rowY + ROW < top || rowY > top + view) continue;
            String name = friends.get(i), key = "friend." + name;
            boolean hover = UiDraw.over(mx, my, x - 4, rowY, w + 8, ROW) && my >= top && my < top + view;
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20), appear = UiAnim.smooth(key + ".a", 0, 1, 12);
            float before = UiRender.opacity; UiRender.opacity = before * appear;
            UiDraw.rounded(g, x - 4, rowY + 1, w + 8, ROW - 2, 6, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .8f));
            UiDraw.circle(g, x + 8, rowY + ROW / 2f, 7, UiDraw.alpha(t.accent(), .22f));
            UiDraw.center(g, name.substring(0, 1).toUpperCase(Locale.ROOT), x + 1, rowY + ROW / 2f, 14, UiDraw.SMALL, t.accent());
            UiDraw.text(g, UiDraw.fit(name, w - 60), x + 21, rowY + ROW / 2f, t.text());
            boolean removeHover = hover && UiDraw.over(mx, my, removeX() - 4, rowY + 4, 16, 16);
            float rh = UiAnim.smooth(key + ".rh", removeHover ? 1 : 0, 20);
            if (hv > 0) {
                UiDraw.circle(g, removeX() + 4, rowY + ROW / 2f, 7, UiDraw.alpha(t.red(), rh * .2f));
                UiDraw.icon(g, "close", removeX(), rowY + ROW / 2f - 4, 8, UiDraw.alpha(UiDraw.mix(t.muted(), t.red(), rh), hv));
            }
            if (removeHover) host.hostTooltip("remove." + name, "Remove " + name);
            UiRender.opacity = before;
        }
        if (friends.isEmpty()) {
            UiDraw.icon(g, "people", x + w / 2f - 8, top + 14, 16, UiDraw.alpha(t.muted(), .5f));
            UiDraw.center(g, "No friends yet", x, top + 42, w, t.muted());
            UiDraw.center(g, "Type a player name above and press Enter.", x, top + 54, w, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        }
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, x - 4, top, w + 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, x - 4, top + view - 8, w + 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base());
        float alpha = status.isEmpty() ? 0 : Math.clamp(1 - ((System.nanoTime() - statusAt) / 1e9f - 4) / .5f, 0f, 1f);
        String footer = alpha > 0 ? status : friends.size() * ROW > view ? "Scroll to see more friends" : "Hover a friend and click × to remove them";
        int colour = alpha > 0 ? UiDraw.alpha(statusError ? t.red() : t.green(), alpha) : UiDraw.alpha(t.muted(), .8f);
        UiDraw.text(g, UiDraw.fit(footer, w), x, y + h - 11, UiDraw.SMALL, colour);
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (nameBox.isMouseOver(mx, my)) { host.hostFocus(nameBox); return nameBox.mouseClicked(event, doubled); }
        if (event.button() != 0) return false;
        if (UiDraw.over(mx, my, addX(), addY(), 60, 20)) { add(); return true; }
        if (!UiDraw.over(mx, my, x - 4, listTop(), w + 8, listHeight())) return false;
        int row = (int) Math.floor((my - listTop() + shownScroll) / ROW);
        if (row < 0 || row >= friends.size()) return true;
        float rowY = listTop() + row * ROW - shownScroll;
        if (UiDraw.over(mx, my, removeX() - 4, rowY + 4, 16, 16)) remove(friends.get(row));
        return true;
    }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!UiDraw.over(mx, my, x - 4, listTop(), w + 8, listHeight())) return false;
        scroll = Math.clamp(scroll - (int) (vertical * ROW * 2), 0, maxScroll());
        return true;
    }

    boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && nameBox.isFocused()) { add(); return true; }
        return false;
    }
}

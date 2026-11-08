package dev.randomstuff.client.gui;

import dev.randomstuff.client.ClientContext;
import dev.randomstuff.client.modules.misc.SocialEntry;
import dev.randomstuff.client.settings.options.SocialRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Friends uses the same palette and navigation while editing the existing social registry. */
public final class FriendsScreen extends UiScreen {
    private static final int ROW = 24;
    private int left, top, panelWidth, panelHeight, viewHeight, scroll;
    private float shownScroll;
    private UiInput nameBox;
    private List<String> friends = List.of();
    private String status = "";
    private boolean statusError;
    private long statusAt;

    public FriendsScreen(Screen parent) { super("Friends", parent, "Friends"); }

    @Override protected void buildContent() {
        panelWidth = Math.min(360, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24; viewHeight = Math.max(ROW, panelHeight - 96);
        reload();
        String draft = nameBox == null ? "" : nameBox.getValue();
        nameBox = new UiInput(left + 12, top + 42, panelWidth - 92, 12, "Player name…").withIcon("player");
        nameBox.setMaxLength(16); nameBox.setValue(draft);
        addRenderableWidget(nameBox); setInitialFocus(nameBox);
        addRenderableWidget(new UiButton(left + panelWidth - 72, top + 37, 60, 22, "Add", true, "plus", this::addFriend));
    }

    private void reload() {
        List<String> names = new ArrayList<>();
        for (Object object : ClientContext.socialRegistry.getRegistry()) if (object instanceof SocialEntry entry && entry.role() == SocialRole.FRIEND) names.add(entry.name());
        names.sort(String.CASE_INSENSITIVE_ORDER); friends = List.copyOf(names);
    }

    private void status(String text, boolean error) { status = text; statusError = error; statusAt = System.nanoTime(); }

    private void addFriend() {
        String name = nameBox.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { status("Use 1–16 letters, numbers or underscores.", true); return; }
        if (ClientContext.socialRegistry.isFriendName(name)) { status(name + " is already a friend.", true); return; }
        ClientContext.socialRegistry.addFriend(name); status("Added " + name + ".", false); nameBox.setValue(""); reload();
        UiAnim.set("friend." + name + ".a", 0);
    }

    private int start() { return top + 70; }
    private int maxScroll() { return Math.max(0, friends.size() * ROW - viewHeight); }
    private float removeX() { return left + panelWidth - 30; }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        UiDraw.icon(g, "people", left + 14, top + 12, 10, t.accent());
        UiDraw.bold(g, "Friends", left + 30, top + 17, UiDraw.TITLE, t.text());
        String count = Integer.toString(friends.size());
        float cw = Math.max(16, UiDraw.width(count, UiDraw.SMALL) + 10);
        UiDraw.rounded(g, left + panelWidth - 14 - cw, top + 11, cw, 13, 6.5f, UiDraw.alpha(t.accent(), .2f));
        UiDraw.center(g, count, left + panelWidth - 14 - cw, top + 17.5f, cw, UiDraw.SMALL, t.accent());
        scroll = Math.clamp(scroll, 0, maxScroll());
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        int start = start();
        g.enableScissor(left + 4, start, left + panelWidth - 4, start + viewHeight);
        for (int i = 0; i < friends.size(); i++) {
            float y = start + i * ROW - shownScroll; if (y + ROW < start || y > start + viewHeight) continue;
            String name = friends.get(i), key = "friend." + name;
            boolean hover = UiDraw.over(mx, my, left + 8, y, panelWidth - 16, ROW) && my >= start && my < start + viewHeight;
            float hv = UiAnim.smooth(key + ".h", hover ? 1 : 0, 20), appear = UiAnim.smooth(key + ".a", 0, 1, 12);
            float before = UiRender.opacity; UiRender.opacity = before * appear;
            UiDraw.rounded(g, left + 8, y + 1, panelWidth - 16, ROW - 2, 6, UiDraw.mix(t.mantle(), t.surface(), hv * .8f));
            int hue = t.accent();
            UiDraw.circle(g, left + 20, y + ROW / 2f, 7, UiDraw.alpha(hue, .22f));
            UiDraw.center(g, name.substring(0, 1).toUpperCase(Locale.ROOT), left + 13, y + ROW / 2f, 14, UiDraw.SMALL, hue);
            UiDraw.text(g, UiDraw.fit(name, panelWidth - 80), left + 33, y + ROW / 2f, t.text());
            boolean removeHover = hover && UiDraw.over(mx, my, removeX(), y + 4, 16, 16);
            float rh = UiAnim.smooth(key + ".rh", removeHover ? 1 : 0, 20);
            if (hv > 0) {
                UiDraw.circle(g, removeX() + 8, y + ROW / 2f, 7, UiDraw.alpha(t.red(), rh * .2f));
                UiDraw.icon(g, "close", removeX() + 4, y + ROW / 2f - 4, 8, UiDraw.alpha(UiDraw.mix(t.muted(), t.red(), rh), hv));
            }
            if (removeHover) tooltip("remove." + name, "Remove " + name);
            UiRender.opacity = before;
        }
        if (friends.isEmpty()) {
            UiDraw.icon(g, "people", left + panelWidth / 2f - 8, start + 14, 16, UiDraw.alpha(t.muted(), .5f));
            UiDraw.center(g, "No friends yet", left, start + 42, panelWidth, t.muted());
            UiDraw.center(g, "Type a player name above and press Enter.", left, start + 54, panelWidth, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        }
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, left + 4, start, panelWidth - 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, left + 4, start + viewHeight - 8, panelWidth - 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base());
        float statusAlpha = status.isEmpty() ? 0 : Math.clamp(1 - ((System.nanoTime() - statusAt) / 1e9f - 4) / .5f, 0f, 1f);
        String footer = statusAlpha > 0 ? status : friends.size() > viewHeight / ROW ? "Scroll to see more friends." : "Friends are skipped by combat modules.";
        int colour = statusAlpha > 0 ? UiDraw.alpha(statusError ? t.red() : t.green(), statusAlpha) : UiDraw.alpha(t.muted(), .8f);
        UiDraw.text(g, UiDraw.fit(footer, panelWidth - 24), left + 14, top + panelHeight - 11, UiDraw.SMALL, colour);
    }

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        if (event.button() != 0 || !UiDraw.over(event.x(), event.y(), left + 8, start(), panelWidth - 16, viewHeight)) return false;
        int row = (int) Math.floor((event.y() - start() + shownScroll) / ROW);
        if (row < 0 || row >= friends.size()) return false;
        float y = start() + row * ROW - shownScroll;
        if (UiDraw.over(event.x(), event.y(), removeX(), y + 4, 16, 16)) {
            String name = friends.get(row);
            ClientContext.socialRegistry.removeSocial(name); status("Removed " + name + ".", false); reload();
        }
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && nameBox.isFocused()) { addFriend(); return true; }
        return super.keyPressed(event);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (UiDraw.over(mx, my, left, start(), panelWidth, viewHeight)) { scroll = Math.clamp(scroll - (int) (vertical * ROW * 2), 0, maxScroll()); return true; }
        return false;
    }
}

package dev.randomstuff.client.gui;

import dev.eggclient.client.ClientContext;
import dev.eggclient.client.managers.SocialRegistry;
import dev.eggclient.client.modules.misc.SocialEntry;
import dev.eggclient.client.settings.options.SocialRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Meteor-style friends window: a name box + "Add" button on top, and a
 * scrollable list of friends, each with a "Remove" button.
 *
 * Uses only vanilla widgets plus the client's existing SocialRegistry, so it
 * does not depend on the client's custom panel framework.
 *
 * Integrated with Random Stuff Client for Minecraft 1.21.11 (Mojang mappings).
 */
public final class FriendsScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int ROW_H = 22;
    private static final int HEADER_H = 54;
    private static final int PAD = 8;

    private static final int COLOR_PANEL = 0xE0181818;
    private static final int COLOR_HEADER = 0xFF202020;
    private static final int COLOR_ROW = 0xFF262626;
    private static final int COLOR_ROW_ALT = 0xFF2C2C2C;
    private static final int COLOR_ACCENT = 0xFFB978FF; // matches the lavender "enabled" colour
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFAAAAAA;

    private final Screen parent;
    private EditBox nameBox;
    private Button addButton;
    private final List<Button> removeButtons = new ArrayList<>();
    private List<String> friends = new ArrayList<>();

    private int scroll = 0;
    private String status = "";

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    // ---- layout helpers -------------------------------------------------

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return Math.max(20, this.height / 2 - 110); }
    private int listTop() { return top() + HEADER_H; }
    private int listHeight() { return Math.min(220, this.height - listTop() - 20); }
    private int visibleRows() { return Math.max(1, listHeight() / ROW_H); }

    // ---- data -----------------------------------------------------------

    private void reloadFriends() {
        SocialRegistry registry = ClientContext.socialRegistry;
        List<String> names = new ArrayList<>();
        for (Object o : registry.getRegistry()) {
            SocialEntry entry = (SocialEntry) o;
            if (entry.role() == SocialRole.FRIEND) {
                names.add(entry.name());
            }
        }
        names.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        this.friends = names;
        int maxScroll = Math.max(0, friends.size() - visibleRows());
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private void addFriend() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            status = "Type a name first.";
            return;
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            status = "Invalid name.";
            return;
        }
        SocialRegistry registry = ClientContext.socialRegistry;
        if (registry.isFriendName(name)) {
            status = name + " is already a friend.";
            return;
        }
        registry.addFriend(name);
        status = "Added " + name + ".";
        nameBox.setValue("");
        rebuild();
    }

    private void removeFriend(String name) {
        ClientContext.socialRegistry.removeSocial(name);
        status = "Removed " + name + ".";
        rebuild();
    }

    private void rebuild() {
        this.rebuildWidgets();
    }

    // ---- widgets --------------------------------------------------------

    @Override
    protected void init() {
        String draft = nameBox == null ? "" : nameBox.getValue();
        reloadFriends();
        removeButtons.clear();

        int x = left();
        int y = top();

        nameBox = new EditBox(this.font, x + PAD, y + 26, PANEL_W - PAD * 2 - 58, 18, Component.literal("Name"));
        nameBox.setMaxLength(16);
        nameBox.setValue(draft);
        nameBox.setHint(Component.literal("Player name..."));
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);

        addButton = Button.builder(Component.literal("Add"), b -> addFriend())
                .bounds(x + PANEL_W - PAD - 52, y + 25, 52, 20)
                .build();
        this.addRenderableWidget(addButton);

        int rows = Math.min(visibleRows(), Math.max(0, friends.size() - scroll));
        for (int i = 0; i < rows; i++) {
            final String name = friends.get(scroll + i);
            int ry = listTop() + i * ROW_H;
            Button remove = Button.builder(Component.literal("Remove"), b -> removeFriend(name))
                    .bounds(x + PANEL_W - PAD - 54, ry + 1, 54, ROW_H - 2)
                    .build();
            removeButtons.add(remove);
            this.addRenderableWidget(remove);
        }
    }

    // ---- rendering ------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // dim the game behind the window
        g.fill(0, 0, this.width, this.height, 0x90000000);

        int x = left();
        int y = top();
        int h = HEADER_H + listHeight() + 26;

        // panel + header
        g.fill(x, y, x + PANEL_W, y + h, COLOR_PANEL);
        g.fill(x, y, x + PANEL_W, y + 20, COLOR_HEADER);
        g.fill(x, y + 20, x + PANEL_W, y + 21, COLOR_ACCENT);
        g.drawString(this.font, "Friends (" + friends.size() + ")", x + PAD, y + 6, COLOR_TEXT, true);

        // rows
        int rows = Math.min(visibleRows(), Math.max(0, friends.size() - scroll));
        for (int i = 0; i < rows; i++) {
            int ry = listTop() + i * ROW_H;
            g.fill(x + 2, ry, x + PANEL_W - 2, ry + ROW_H, (i % 2 == 0) ? COLOR_ROW : COLOR_ROW_ALT);
            g.fill(x + 2, ry, x + 4, ry + ROW_H, COLOR_ACCENT);
            g.drawString(this.font, friends.get(scroll + i), x + PAD + 4, ry + 7, COLOR_TEXT, true);
        }

        if (friends.isEmpty()) {
            g.drawString(this.font, "No friends yet. Add one above.", x + PAD, listTop() + 8, COLOR_DIM, true);
        }

        // status line
        if (!status.isEmpty()) {
            g.drawString(this.font, status, x + PAD, y + h - 16, COLOR_DIM, true);
        }

        super.render(g, mouseX, mouseY, delta); // widgets on top
    }

    // ---- input ----------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, friends.size() - visibleRows());
        int newScroll = scroll - (int) Math.signum(vertical);
        newScroll = Math.max(0, Math.min(maxScroll, newScroll));
        if (newScroll != scroll) {
            scroll = newScroll;
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Enter adds the typed name
        if ((event.key() == 257 || event.key() == 335) && nameBox != null && nameBox.isFocused()) {
            addFriend();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

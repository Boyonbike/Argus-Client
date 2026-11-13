package dev.argus.client.gui;

import dev.argus.client.chat.AutoTpyQueue;
import dev.argus.client.chat.AutoTpyWhitelistImport;
import dev.argus.client.chat.ChatClan;
import dev.argus.client.chat.ChatCountdowns;
import dev.argus.client.chat.ChatMessages;
import dev.argus.client.chat.ChatPlayerSearch;
import dev.argus.client.chat.ChatSavedCooldowns;
import dev.argus.client.chat.ChatServerSettings;
import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.chat.ChatTpaRequests;
import dev.argus.client.chat.DiscordRoles;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The Chat Tools window, in the same frame as Settings: a sidebar of pages on the left and the page on the right, with
 * a composer along the bottom for public chat, commands and private messages. Pages are drawn top to bottom each frame;
 * every control records where it was drawn, so clicks always match what's on screen. Anything that needs typed input
 * opens a small dialog, so the composer is only ever for chat.
 */
public final class ChatToolsScreen extends UiScreen {
    enum Page {
        OVERVIEW("Overview", "modules", "What needs you, favourite homes and recent players"),
        CHAT("Chat", "chat", "Chat on this server · newest at the bottom"),
        MESSAGES("Messages", "player", "Private conversations this session"),
        PLAYERS("Players", "people", "Recent contacts, friends and who's online"),
        CLAN("Clan", "shield", "Members and roles from your Discord"),
        HOMES("Homes", "star", "Teleport to, rename and pin your homes"),
        TELEPORTS("Teleports", "compass", "Requests, warmups and cooldowns"),
        AUTO_TPY("Auto TPY", "check", "Accept teleport requests automatically, at the server's pace"),
        SERVER("Server", "sliders", "Your hotspot and this server's own settings"),
        SETTINGS("Settings", "client", "Every Social and chat setting");
        final String label, icon, subtitle;
        Page(String label, String icon, String subtitle) { this.label = label; this.icon = icon; this.subtitle = subtitle; }
    }

    private enum Tone { PRIMARY, SECONDARY, DANGER, GHOST }
    private record Hit(float x, float y, float w, float h, Runnable action) {}
    /** A modal question. {@code field} is the input's label, or null for a plain confirmation. */
    private record Dialog(String title, String description, String field, Function<String, String> check,
                          Function<String, String> preview, String confirm, boolean danger, Consumer<String> done) {}

    private static final float SIDE = 112, ITEM = 20, PAD = 8, ROW = 26, CONTROL = 18, GAP = 5;
    /** Chat is drawn with the menu font at this size, rasterised for the screen so it stays sharp. */
    private static final float CHAT_SIZE = UiDraw.TEXT;
    private static final long NOTICE_NANOS = 4_000_000_000L;
    private static Page remembered = Page.OVERVIEW;
    private static float indicatorY = Float.NaN;

    private final ChatToolsModule tools;
    private Page page;
    private String recipient, selectedPlayer;
    private final Map<String, String> drafts = new HashMap<>();
    private final UiInput composer = new UiInput(0, 0, 100, 12, "Message public chat · / for commands");
    private final UiInput search = new UiInput(0, 0, 100, 12, "Find a player…").withIcon("search");
    private final UiInput listField = new UiInput(0, 0, 100, 12, "Usernames, separated by spaces or commas");
    private final UiInput dialogField = new UiInput(0, 0, 100, 12, "");
    private ModulePage settingsPage;
    private Dialog dialog;
    /** The dialog being drawn: the open one, or the one fading out after it closed; and how far it is shown (0…1). */
    private Dialog shownDialog;
    private float dialogShown;
    /** Server page submenu depth, the direction of the last move (1 in, -1 back) and when it happened. */
    private int serverDepth, serverDir;
    private long serverDepthChanged;
    private boolean dialogTouched;

    private int left, top, panelWidth, panelHeight;
    private final List<Hit> hits = new ArrayList<>(), dialogHits = new ArrayList<>();
    private float clipTop = -Float.MAX_VALUE, clipBottom = Float.MAX_VALUE;
    private float cursor, scroll, shownScroll, contentHeight;
    private float chatScroll, conversationScroll;
    private long chatSeen;
    private int autoSection;
    private boolean blocklistEditing, importBusy;
    private long importRevision;
    private final List<ChatServerSettings.Entry> serverPath = new ArrayList<>();
    private String notice = "";
    private boolean noticeError;
    private long noticeAt;
    private long pageChanged;
    /** Which way the last page switch went in the sidebar: 1 down, -1 up; the new page slides in from that side. */
    private int pageDir = 1;
    private int mouseX, mouseY;

    // Per-frame snapshots, so a frame never asks the module the same thing twice.
    private List<ChatTpaRequests.Request> requests = List.of();
    private List<ChatTeleportState.Timer> timers = List.of();
    private final Map<String, Object> memo = new HashMap<>();

    ChatToolsScreen(Screen parent, String player) {
        super("Social", parent, "Social");
        tools = ChatToolsModule.get();
        page = remembered;
        composer.setMaxLength(256);
        listField.setMaxLength(4096);
        dialogField.setMaxLength(256);
        search.setResponder(value -> { scroll = 0; selectedPlayer = null; });
        if (player != null) openConversation(player);
    }

    /** Opens on one page, e.g. Players for /friends. */
    static ChatToolsScreen on(Screen parent, Page start) {
        ChatToolsScreen screen = new ChatToolsScreen(parent, null);
        screen.page = start; remembered = start;
        return screen;
    }

    // ---- the module's window hook -------------------------------------------------------------------------------

    /** Lets Chat Tools open this window, ask whether a conversation is being read, and close it. */
    static void attach() {
        ChatToolsModule tools = ChatToolsModule.get();
        if (tools == null || attached == tools) return;
        attached = tools;
        tools.attachWindow(new ChatToolsModule.Window() {
            @Override public boolean showing() { return Minecraft.getInstance().screen instanceof ChatToolsScreen; }
            @Override public boolean reading(String player) {
                return Minecraft.getInstance().screen instanceof ChatToolsScreen screen && screen.reading(player);
            }
            @Override public void open(String player) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ChatToolsScreen screen) { if (player != null) screen.openConversation(player); }
                else mc.setScreen(new ChatToolsScreen(mc.screen, player));
            }
            @Override public void close() {
                if (Minecraft.getInstance().screen instanceof ChatToolsScreen screen) screen.onClose();
            }
        });
    }
    private static ChatToolsModule attached;

    // ---- layout ---------------------------------------------------------------------------------------------------

    @Override protected void buildContent() {
        panelWidth = Math.min(700, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        if (page == Page.SETTINGS && settingsPage == null && tools != null) settingsPage = new ModulePage(this, tools, null, false).icon("chat");
        if (settingsPage != null) settingsPage.layout(contentX(), top + 12, contentWidth(), panelHeight - 12, left, left + panelWidth);
        placeInputs();
    }

    private float contentX() { return left + SIDE + 24; }
    private float contentWidth() { return panelWidth - SIDE - 34; }
    private float bodyTop() { return top + 46; }
    private boolean hasComposer() { return page != Page.SETTINGS && dialog == null; }
    private float composerY() { return top + panelHeight - 34; }
    private float trayHeight() { return Math.min(2, outgoing().size()) * 14f; }
    private float bodyBottom() { return contentBottom() - dockHeight(); }
    private float contentBottom() { return hasComposer() ? composerY() - 6 - trayHeight() : top + panelHeight - 20; }
    /** Overview keeps the newest chat in a card above the message bar when there's room; the page scrolls above it. */
    private float dockHeight() {
        if (page != Page.OVERVIEW || dialog != null) return 0;
        float room = contentBottom() - bodyTop();
        return room >= 220 ? Math.clamp(room * .32f, 84, 140) : 0;
    }

    /** Only the fields on the current page take clicks and keys. */
    private void placeInputs() {
        for (UiInput input : List.of(composer, search, listField, dialogField)) hostRemove(input);
        int x = Math.round(contentX()), w = Math.round(contentWidth());
        if (dialog != null) {
            if (dialog.field() != null) { hostAdd(dialogField); }
            return;
        }
        if (hasComposer()) {
            composer.placeholder(page == Page.MESSAGES && recipient != null ? "Message " + recipient + " · / for commands" : "Message public chat · / for commands");
            composer.place(x, Math.round(composerY()) + 4, w - 58);
            hostAdd(composer);
        }
        if (page == Page.PLAYERS) hostAdd(search);
        if (page == Page.AUTO_TPY && autoSection == 2) hostAdd(listField);
    }

    // ---- navigation -----------------------------------------------------------------------------------------------

    private List<Page> pages() {
        List<Page> shown = new ArrayList<>();
        for (Page p : Page.values()) if (p != Page.SERVER || serverAvailable()) shown.add(p);
        return shown;
    }
    private boolean serverAvailable() {
        if (tools == null) return false;
        return tools.commandAvailable("hotspot") || !ChatServerSettings.available(ChatServerSettings.groups(), this::commandAvailable).isEmpty();
    }
    private boolean commandAvailable(String root) {
        return root.equals("tpacancel") ? tools.commandAvailable(root) || tools.commandAvailable("tpcancel") : tools.commandAvailable(root);
    }

    private void select(Page next) {
        saveDraft();
        clearSelection(); transcriptPressed = false; transcriptArea = null;
        if (next != Page.MESSAGES) recipient = null;
        pageDir = page == null || next.ordinal() >= page.ordinal() ? 1 : -1;
        page = next; remembered = next;
        scroll = shownScroll = 0;
        serverPath.clear();
        pageChanged = System.nanoTime();
        memo.clear();
        if (next == Page.SETTINGS && settingsPage == null && tools != null) {
            settingsPage = new ModulePage(this, tools, null, false).icon("chat");
            settingsPage.layout(contentX(), top + 12, contentWidth(), panelHeight - 12, left, left + panelWidth);
        }
        if (tools != null && next == Page.HOMES) tools.refreshHomes();
        if (tools != null && next == Page.SERVER && tools.commandAvailable("hotspot")) tools.refreshHotspot();
        composer.setValue(drafts.getOrDefault(draftKey(), ""));
        placeInputs();
        if (hasComposer()) hostFocus(composer);
    }

    void openConversation(String player) {
        if (!ChatMessages.validName(player)) return;
        saveDraft();
        pageDir = page == null || Page.MESSAGES.ordinal() >= page.ordinal() ? 1 : -1;
        page = Page.MESSAGES; remembered = Page.MESSAGES;
        recipient = player;
        conversationScroll = 0;
        pageChanged = System.nanoTime();
        if (tools != null) { tools.rememberPlayer(player); tools.messages().read(player); }
        composer.setValue(drafts.getOrDefault(draftKey(), ""));
        if (width > 0) { placeInputs(); hostFocus(composer); }
    }

    /** Whether the player is looking at this conversation, scrolled to the newest message. */
    boolean reading(String player) {
        return page == Page.MESSAGES && recipient != null && recipient.equalsIgnoreCase(player) && conversationScroll < 1
            && Minecraft.getInstance().isWindowActive() && dialog == null;
    }

    private String draftKey() { return page == Page.MESSAGES && recipient != null ? recipient.toLowerCase(Locale.ROOT) : ""; }
    private void saveDraft() { drafts.put(draftKey(), composer.getValue()); }

    private void notice(String text) { notice(text, false); }
    private void notice(String text, boolean error) { notice = text; noticeError = error; noticeAt = System.nanoTime(); }

    @SuppressWarnings("unchecked")
    private <T> T memo(String key, java.util.function.Supplier<T> source) {
        return (T) memo.computeIfAbsent(key, ignored -> source.get());
    }
    private boolean friend(String name) { return memo("friend:" + name, () -> tools.isFriend(name)); }
    private boolean enemy(String name) { return memo("enemy:" + name, () -> tools.isEnemy(name)); }
    private boolean clanMember(String name) { return memo("clan:" + name, () -> tools.inClan(name)); }
    private boolean online(String name) { return memo("online:" + name, () -> tools.onlineContact(name)); }

    // ---- sending --------------------------------------------------------------------------------------------------

    private void sendComposer() {
        String text = composer.getValue();
        if (text.isBlank() || minecraft.getConnection() == null) return;
        boolean sent;
        if (text.startsWith("/")) sent = text.length() > 1 && tools.sendCommand(text);
        else if (page == Page.MESSAGES && recipient != null) sent = tools.sendPrivate(recipient, text);
        else sent = tools.sendPublic(text);
        if (sent) { composer.setValue(""); drafts.put(draftKey(), ""); conversationScroll = 0; chatScroll = 0; }
        else notice("Couldn't send. The queue may be full, or the message is too long.", true);
        hostFocus(composer);
    }

    private void command(String value) { tools.send(value); notice("Sent " + value); }

    // ---- dialogs --------------------------------------------------------------------------------------------------

    private void ask(Dialog next, String value) {
        saveDraft();
        if (dialog == null) { dialogShown = 0; UiAnim.forget("ct.dialog.h"); }
        dialog = next; dialogTouched = false;
        dialogField.setValue(value == null ? "" : value);
        dialogField.moveCursorToEnd(false);
        placeInputs();
        if (next.field() != null) hostFocus(dialogField);
    }
    private void closeDialog() { dialog = null; placeInputs(); if (hasComposer()) hostFocus(composer); }
    private String dialogProblem() { return dialog == null || dialog.check() == null ? null : dialog.check().apply(dialogField.getValue().strip()); }
    private void confirmDialog() {
        if (dialog == null) return;
        if (dialogProblem() != null) { dialogTouched = true; return; }
        Dialog done = dialog;
        String value = dialogField.getValue().strip();
        closeDialog();
        done.done().accept(value);
    }
    private void confirm(String title, String description, String confirm, String preview, Runnable action) {
        ask(new Dialog(title, description, null, null, ignored -> preview, confirm, true, ignored -> action.run()), "");
    }
    /** Asks for text and sends {@code prefix + text}. */
    private void request(String title, String label, String prefix, String confirm) {
        ask(new Dialog(title, null, label, value -> value.isEmpty() ? "Type something first." : null,
            value -> prefix + (value.isEmpty() ? "…" : value), confirm, false,
            value -> { tools.sendCommand(prefix + value); notice("Sent " + prefix + value); }), "");
    }
    private String homeNameProblem(String name, String except) {
        if (name.isEmpty()) return "Enter a name.";
        if (name.chars().anyMatch(Character::isWhitespace)) return "Home names can't contain spaces.";
        if (name.length() > 32) return "Use 32 characters or fewer.";
        if (except != null && name.equals(except)) return "Choose a different name.";
        if (tools.homes().stream().anyMatch(home -> home.equalsIgnoreCase(name) && !home.equals(except))) return "You already have a home called " + name + ".";
        return null;
    }
    private static String nameProblem(String value) {
        return ChatMessages.validName(value) ? null : "Use 1–16 letters, numbers or underscores.";
    }
    private void newHome() {
        ask(new Dialog("New home", "Sets a home where you're standing now.", "Home name", value -> homeNameProblem(value, null),
            value -> "/sethome " + (value.isEmpty() ? "…" : value), "Set home", false, value -> command("/sethome " + value)), "");
    }
    private void renameHome(String name) {
        ask(new Dialog("Rename " + name, null, "New name", value -> homeNameProblem(value, name),
            value -> "/homerename " + name + " " + (value.isEmpty() ? "…" : value), "Rename", false,
            value -> command("/homerename " + name + " " + value)), name);
    }
    private void deleteHome(String name) {
        confirm("Delete " + name + "?", "The server removes this home. You can't undo this.", "Delete home", "/delhome " + name,
            () -> command("/delhome " + name));
    }
    private void newMessage() {
        ask(new Dialog("New message", "Start a private conversation.", "Player name", ChatToolsScreen::nameProblem,
            value -> "Message " + (value.isEmpty() ? "…" : value), "Open", false, this::openConversation), "");
    }
    private void reply() {
        if (tools.replyPlayer() != null) { openConversation(tools.replyPlayer()); return; }
        request("Reply to your last message", "Message", tools.commandAvailable("r") ? "/r " : "/reply ", "Send");
    }
    private void requestTeleport() {
        ask(new Dialog("Request a teleport", "Asks a player to let you teleport to them.", "Player name", ChatToolsScreen::nameProblem,
            value -> "/tpa " + (value.isEmpty() ? "…" : value), "Send request", false,
            value -> { tools.requestTeleport(value); notice("Teleport request sent to " + value); }), "");
    }
    private void addClanMembers() {
        ask(new Dialog("Add clan members", "Names you add by hand stay, even after a Discord sync.", "Usernames",
            value -> ChatClan.typed(value).names().isEmpty() ? "Type at least one valid username." : null,
            value -> ChatClan.typed(value).names().size() + " name(s)", "Add", false, value -> {
                var typed = ChatClan.typed(value);
                notice("Added " + tools.addToClan(typed.names()) + " to the clan" + (typed.invalid() > 0 ? "; skipped " + typed.invalid() + " invalid." : "."));
            }), "");
    }

    // ---- rendering ------------------------------------------------------------------------------------------------

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        mouseX = mx; mouseY = my;
        UiTheme t = GuiState.theme();
        hits.clear(); dialogHits.clear(); memo.clear();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        sidebar(g, mx, my, t);
        if (tools == null || !tools.isToggled() || minecraft.getConnection() == null) { unavailable(g, t); return; }
        requests = tools.requests();
        timers = tools.timers();
        if (page == Page.SERVER && !serverAvailable()) select(Page.OVERVIEW);
        float change = UiAnim.eased(pageChanged, .22f), before = UiRender.opacity;
        UiRender.opacity = before * change;
        g.pose().pushMatrix();
        g.pose().translate(0, pageDir * (1 - change) * 8);
        try {
            if (page == Page.SETTINGS) { if (settingsPage != null) settingsPage.render(g, mx, my, delta, t); }
            else {
                header(g, t);
                switch (page) {
                    case CHAT -> chatPage(g, t);
                    case MESSAGES -> { if (recipient != null) conversation(g, t); else scrolling(g, t, () -> conversations(g, t)); }
                    default -> scrolling(g, t, () -> body(g, t));
                }
                if (dockHeight() > 0) chatDock(g, t);
                tray(g, t);
                composerBar(g, mx, my, delta, t);
            }
            footer(g, t);
        } finally { g.pose().popMatrix(); UiRender.opacity = before; }
        // Dialogs fade and grow in, and fade back out after they close.
        if (dialog != null) shownDialog = dialog;
        dialogShown = UiAnim.approach(dialogShown, dialog != null ? 1 : 0, dialog != null ? 16 : 22);
        if (shownDialog != null && dialogShown > .01f) dialogBox(g, mx, my, delta, t);
        else if (dialog == null) shownDialog = null;
    }

    private void body(GuiGraphics g, UiTheme t) {
        switch (page) {
            case OVERVIEW -> overview(g, t);
            case PLAYERS -> players(g, t);
            case CLAN -> clan(g, t);
            case HOMES -> homes(g, t);
            case TELEPORTS -> teleports(g, t);
            case AUTO_TPY -> autoTpy(g, t);
            case SERVER -> server(g, t);
            default -> { }
        }
    }

    private void sidebar(GuiGraphics g, int mx, int my, UiTheme t) {
        UiDraw.rounded(g, left + 4, top + 4, SIDE + 8, panelHeight - 8, 5, UiDraw.alpha(t.mantle(), .7f));
        UiDraw.icon(g, "chat", left + 12, top + 13, 10, t.accent());
        UiDraw.bold(g, "Social", left + 27, top + 18, UiDraw.TITLE, t.text());
        List<Page> shown = pages();
        int selected = Math.max(0, shown.indexOf(page));
        float targetY = itemY(selected);
        indicatorY = Float.isNaN(indicatorY) ? targetY : UiAnim.approach(indicatorY, targetY, 18);
        UiDraw.rounded(g, left + 8, indicatorY + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.accent(), .2f));
        UiDraw.rounded(g, left + 8, indicatorY + 5, 2, ITEM - 10, 1, t.accent());
        boolean live = tools != null && tools.isToggled() && minecraft.getConnection() != null;
        for (int i = 0; i < shown.size(); i++) {
            Page p = shown.get(i);
            float iy = itemY(i);
            boolean active = i == selected, hover = UiDraw.over(mx, my, left + 8, iy, SIDE, ITEM) && dialog == null;
            float hv = UiAnim.smooth("ct.side." + p, hover && !active ? 1 : 0, 20);
            if (hv > 0) UiDraw.rounded(g, left + 8, iy + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.surface(), hv * .8f));
            int colour = active ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv);
            UiDraw.icon(g, p.icon, left + 16, iy + ITEM / 2f - 4, 8, colour);
            UiDraw.text(g, UiDraw.fit(p.label, SIDE - 40), left + 29, iy + ITEM / 2f, colour);
            int count = !live ? 0 : switch (p) {
                case MESSAGES -> tools.messages().unread();
                case TELEPORTS -> tools.requests().size();
                case AUTO_TPY -> tools.autoTpyEnabled() ? tools.autoTpyStats().live() : 0;
                default -> 0;
            };
            if (count > 0) badge(g, String.valueOf(count), left + SIDE - 2, iy + ITEM / 2f, t.accent(), t.base(), true);
        }
        String problem = tools == null ? null : tools.storeProblem();
        if (problem != null) {
            UiDraw.icon(g, "bell", left + 14, top + panelHeight - 22, 8, t.red());
            UiDraw.text(g, "Data not saved", left + 26, top + panelHeight - 18, UiDraw.SMALL, t.red());
            if (UiDraw.over(mx, my, left + 8, top + panelHeight - 28, SIDE, 18)) tooltip("ct.store", "Chat data isn't being saved", problem);
        }
    }
    private float itemY(int index) { return top + 32 + index * ITEM; }

    private void unavailable(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), cy = top + panelHeight / 2f - 20;
        UiDraw.icon(g, "chat", x + w / 2 - 8, cy - 18, 16, UiDraw.alpha(t.muted(), .5f));
        boolean off = tools == null || !tools.isToggled();
        UiDraw.center(g, off ? "Social is off" : "Join a server to use Social", x, cy + 8, w, t.text());
        UiDraw.center(g, off ? "Turn it on to collect messages, requests and homes." : "Messages, requests and homes are collected per server.",
            x, cy + 20, w, UiDraw.SMALL, t.muted());
        if (off && tools != null) {
            float bw = 90;
            button(g, t, "Turn on", "check", x + (w - bw) / 2, cy + 32, bw, Tone.PRIMARY, true, tools::toggle);
        }
    }

    private void header(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        String title = page == Page.MESSAGES && recipient != null ? recipient : page.label;
        String subtitle = page == Page.MESSAGES && recipient != null ? (online(recipient) ? "Online" : "Offline") + " · private messages" : page.subtitle;
        UiDraw.icon(g, page.icon, x, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, UiDraw.fit(title, w - 20), x + 13, top + 18, UiDraw.TITLE, t.text());
        UiDraw.text(g, UiDraw.fit(subtitle, w), x, top + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, top + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));
    }

    /** A page that scrolls as a whole, with edge fades and a slim scrollbar. */
    private void scrolling(GuiGraphics g, UiTheme t, Runnable content) {
        float x = contentX(), w = contentWidth(), view = bodyBottom() - bodyTop();
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - view));
        shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        clipTop = bodyTop(); clipBottom = bodyBottom();
        g.enableScissor((int) x - 6, (int) clipTop, (int) (x + w + 6), (int) clipBottom);
        cursor = bodyTop() + 2 - shownScroll;
        float start = cursor;
        content.run();
        contentHeight = cursor - start + 4;
        g.disableScissor();
        clipTop = -Float.MAX_VALUE; clipBottom = Float.MAX_VALUE;
        UiMotion.faded(UiMotion.edge(shownScroll), () -> UiDraw.gradient(g, x - 4, bodyTop(), w + 8, 8, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge((contentHeight - view) - shownScroll), () -> UiDraw.gradient(g, x - 4, bodyBottom() - 8, w + 8, 8, 0, UiDraw.alpha(t.base(), 0), t.base()));
        if (contentHeight > view) {
            float track = Math.max(16, view * view / contentHeight), offset = (view - track) * shownScroll / Math.max(1, contentHeight - view);
            UiDraw.rounded(g, x + w + 3, bodyTop() + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
    }

    private void footer(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = top + panelHeight - 9;
        if (!notice.isEmpty() && System.nanoTime() - noticeAt < NOTICE_NANOS + 250_000_000L) {
            UiMotion.crossfade(g, "chat.footer", new UiMotion.Line(UiDraw.fit(notice, w), noticeError ? 1 : 2), l -> UiDraw.text(g, l.text(), x, y, UiDraw.SMALL, UiMotion.footerColour(l.kind(), t)));
            return;
        }
        int picked = selectedLines().size();
        String hint = picked > 0 ? "Ctrl+C copies " + picked + (picked == 1 ? " line" : " lines") + " · right-click a line to copy it · Esc clears" : switch (page) {
            case CHAT -> "Drag over lines to select them · scroll to read older lines · Enter sends, / runs a command";
            case OVERVIEW -> dockHeight() > 0 ? "Click the chat card to open Chat · drag over lines to select and copy them" : "Click a player's name in chat to open them here";
            case MESSAGES -> recipient != null ? "Esc goes back to all conversations" : "Click a conversation to open it";
            case SETTINGS -> "Changes apply instantly and are saved automatically";
            default -> "Click a player's name in chat to open them here";
        };
        UiMotion.crossfade(g, "chat.footer", new UiMotion.Line(UiDraw.fit(hint, w), 0), l -> UiDraw.text(g, l.text(), x, y, UiDraw.SMALL, UiMotion.footerColour(l.kind(), t)));
    }

    // ---- composer and send tray -----------------------------------------------------------------------------------

    private List<ChatToolsModule.Outgoing> outgoing() { return tools == null || minecraft.getConnection() == null ? List.of() : memo("outgoing", tools::outgoing); }

    private void tray(GuiGraphics g, UiTheme t) {
        var queued = outgoing();
        if (queued.isEmpty()) return;
        float x = contentX(), w = contentWidth(), y = composerY() - 4 - trayHeight();
        long now = ChatToolsModule.now();
        for (int i = 0; i < Math.min(2, queued.size()); i++) {
            var item = queued.get(i);
            float ry = y + i * 14;
            String who = item.whisper() ? "→ " + item.target() : "Chat";
            String when = item.eta() <= now ? "sending…" : "in " + ChatTeleportState.remaining(item.eta() - now);
            String more = i == 1 && queued.size() > 2 ? " · +" + (queued.size() - 2) + " more" : "";
            UiDraw.rounded(g, x, ry, w, 12, 4, UiDraw.alpha(t.accent(), .08f));
            UiDraw.text(g, UiDraw.fit(who + " · " + item.text(), w - 120), x + 6, ry + 6, UiDraw.SMALL, t.text());
            UiDraw.right(g, when + more, x + w - 18, ry + 6, UiDraw.SMALL, t.muted());
            iconButton(g, t, "close", x + w - 14, ry, 12, t.muted(), "Cancel this message", () -> tools.cancelOutgoing(item));
        }
    }

    private void composerBar(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        if (!hasComposer()) return;
        float x = contentX(), w = contentWidth(), y = composerY();
        composer.render(g, mx, my, delta);
        boolean ready = !composer.getValue().isBlank();
        button(g, t, page == Page.MESSAGES && recipient != null ? "Send" : "Send", "chevron_right", x + w - 52, y, 52, ready ? Tone.PRIMARY : Tone.SECONDARY, ready, this::sendComposer);
    }

    // ---- drawing helpers ------------------------------------------------------------------------------------------

    private boolean visible(float y, float h) { return y + h > clipTop && y < clipBottom; }
    private void hit(float x, float y, float w, float h, Runnable action) {
        float top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom > top) (dialog != null && dialogDrawing ? dialogHits : hits).add(new Hit(x, top, w, bottom - top, action));
    }
    private boolean dialogDrawing;
    private boolean hovered(float x, float y, float w, float h) {
        return (dialog == null || dialogDrawing) && UiDraw.over(mouseX, mouseY, x, y, w, h) && mouseY >= clipTop && mouseY < clipBottom;
    }

    private float buttonWidth(String label, String icon) { return Math.max(CONTROL, UiDraw.width(label, UiDraw.SMALL) + 16 + (icon == null ? 0 : 11)); }

    /** A pill button; returns its width. */
    private float button(GuiGraphics g, UiTheme t, String label, String icon, float x, float y, float w, Tone tone, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(x, y, w, CONTROL);
        float hv = UiAnim.smooth("ct.b." + label + "." + (int) x + "." + (int) y, hover ? 1 : 0, 20);
        int fill = switch (tone) {
            case PRIMARY -> UiDraw.alpha(t.accent(), enabled ? .24f + hv * .12f : .1f);
            case DANGER -> UiDraw.alpha(t.red(), .14f + hv * .14f);
            case GHOST -> UiDraw.alpha(t.surface(), hv * .8f);
            case SECONDARY -> UiDraw.mix(t.surface(), t.overlay(), hv * .7f);
        };
        UiDraw.rounded(g, x, y, w, CONTROL, 6, fill);
        if (tone != Tone.GHOST) UiDraw.outline(g, x, y, w, CONTROL, 6, .6f, UiDraw.alpha(tone == Tone.PRIMARY ? t.accent() : tone == Tone.DANGER ? t.red() : t.overlay(), .45f + hv * .2f));
        int colour = !enabled ? t.muted() : tone == Tone.PRIMARY ? t.accent() : tone == Tone.DANGER ? t.red() : UiDraw.mix(t.text(), 0xffffffff, hv * .15f);
        float content = (icon == null ? 0 : 8 + (label.isEmpty() ? 0 : 3)) + (label.isEmpty() ? 0 : UiDraw.width(UiDraw.fit(label, w - 10), UiDraw.SMALL));
        float sx = x + (w - content) / 2;
        if (icon != null) { UiDraw.icon(g, icon, sx, y + CONTROL / 2 - 4, 8, colour); sx += 8 + 3; }
        if (!label.isEmpty()) UiDraw.text(g, UiDraw.fit(label, w - 10), sx, y + CONTROL / 2, UiDraw.SMALL, colour);
        if (enabled) hit(x, y, w, CONTROL, action);
        return w;
    }

    /** Buttons laid out leftwards from a right edge. */
    private final class Actions {
        private final GuiGraphics g; private final UiTheme t;
        private float right; private final float y;
        Actions(GuiGraphics g, UiTheme t, float right, float y) { this.g = g; this.t = t; this.right = right; this.y = y; }
        Actions add(String label, String icon, Tone tone, boolean enabled, Runnable action) {
            float w = buttonWidth(label, icon);
            right -= w;
            button(g, t, label, icon, right, y, w, tone, enabled, action);
            right -= GAP;
            return this;
        }
        Actions icon(String icon, int colour, String tooltip, Runnable action) {
            right -= CONTROL;
            iconButton(g, t, icon, right, y, CONTROL, colour, tooltip, action);
            right -= GAP;
            return this;
        }
        float left() { return right; }
    }

    private void iconButton(GuiGraphics g, UiTheme t, String icon, float x, float y, float size, int colour, String tooltip, Runnable action) {
        boolean hover = hovered(x, y, size, size);
        float hv = UiAnim.smooth("ct.i." + icon + "." + (int) x + "." + (int) y, hover ? 1 : 0, 20);
        if (hv > 0) UiDraw.circle(g, x + size / 2, y + size / 2, size / 2, UiDraw.alpha(t.surface(), hv));
        UiDraw.icon(g, icon, x + size / 2 - 4, y + size / 2 - 4, 8, UiDraw.mix(colour, t.text(), hv * .5f));
        if (hover && tooltip != null) tooltip("ct.tip." + tooltip, tooltip);
        hit(x, y, size, size, action);
    }

    private void badge(GuiGraphics g, String text, float right, float cy, int fill, int colour, boolean alignRight) {
        float w = Math.max(10, UiDraw.width(text, UiDraw.SMALL) + 6);
        float x = alignRight ? right - w : right;
        UiDraw.rounded(g, x, cy - 5, w, 10, 5, fill);
        UiDraw.center(g, text, x, cy, w, UiDraw.SMALL, colour);
    }

    private void avatar(GuiGraphics g, UiTheme t, String name, float x, float cy, int colour, boolean online) {
        UiDraw.circle(g, x + 8, cy, 8, UiDraw.alpha(colour, .22f));
        UiDraw.center(g, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x, cy, 16, UiDraw.SMALL, colour);
        if (online) UiDraw.circle(g, x + 14, cy + 6, 2.5f, t.green());
    }

    private int nameColour(UiTheme t, String name) {
        return friend(name) ? t.green() : enemy(name) ? t.red() : clanMember(name) ? t.yellow() : t.text();
    }

    private void rowBackground(GuiGraphics g, UiTheme t, float y, float h, boolean hover, String key) {
        float hv = UiAnim.smooth(key, hover ? 1 : 0, 20);
        UiDraw.rounded(g, contentX() - 4, y + 1, contentWidth() + 8, h - 2, 6, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .8f));
    }

    private void caption(GuiGraphics g, UiTheme t, String label, String detail, String link, Runnable action) {
        float y = cursor;
        cursor += 16;
        if (!visible(y, 16)) return;
        float x = contentX(), w = contentWidth();
        UiDraw.bold(g, label, x, y + 8, UiDraw.SMALL, t.muted());
        if (detail != null) UiDraw.text(g, detail, x + UiDraw.width(label, UiDraw.SMALL) + 6, y + 8, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
        if (link != null) {
            float lw = UiDraw.width(link, UiDraw.SMALL);
            boolean hover = hovered(x + w - lw - 2, y, lw + 4, 14);
            UiDraw.right(g, link, x + w, y + 8, UiDraw.SMALL, hover ? t.text() : t.accent());
            hit(x + w - lw - 2, y, lw + 4, 14, action);
        }
    }

    private void emptyRow(GuiGraphics g, UiTheme t, String heading, String hint) {
        float y = cursor, h = 34;
        cursor += h + PAD;
        if (!visible(y, h)) return;
        float x = contentX(), w = contentWidth();
        UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.mantle(), .5f));
        UiDraw.center(g, heading, x, y + 12, w, t.text());
        UiDraw.center(g, UiDraw.fit(hint, w - 10), x, y + 24, w, UiDraw.SMALL, t.muted());
    }

    private void noteRow(GuiGraphics g, UiTheme t, String title, String text) {
        float x = contentX(), w = contentWidth();
        List<String> lines = UiDraw.wrap(text, w - 16, UiDraw.SMALL);
        float y = cursor, h = 16 + lines.size() * 9 + 4;
        cursor += h + PAD;
        if (!visible(y, h)) return;
        UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.yellow(), .08f));
        UiDraw.bold(g, title, x + 4, y + 9, UiDraw.SMALL, t.yellow());
        for (int i = 0; i < lines.size(); i++) UiDraw.text(g, lines.get(i), x + 4, y + 19 + i * 9, UiDraw.SMALL, t.muted());
    }

    private void toggleRow(GuiGraphics g, UiTheme t, String label, String hint, boolean on, Runnable flip) {
        float x = contentX(), w = contentWidth(), y = cursor;
        cursor += ROW;
        if (!visible(y, ROW)) return;
        boolean hover = hovered(x - 4, y, w + 8, ROW);
        rowBackground(g, t, y, ROW, hover, "ct.tr." + label);
        UiDraw.text(g, label, x + 4, y + 9, t.text());
        UiDraw.text(g, UiDraw.fit(hint, w - 50), x + 4, y + 19, UiDraw.SMALL, t.muted());
        UiDraw.toggle(g, x + w - 22, y + ROW / 2 - 5, UiAnim.smooth("ct.tg." + label, on ? 1 : 0, 16), hover, t);
        hit(x - 4, y, w + 8, ROW, flip);
    }

    private void menuRow(GuiGraphics g, UiTheme t, String label, String detail, boolean submenu, Runnable action) {
        float x = contentX(), w = contentWidth(), y = cursor, h = 22;
        cursor += h;
        if (!visible(y, h)) return;
        boolean hover = hovered(x - 4, y, w + 8, h);
        rowBackground(g, t, y, h, hover, "ct.mr." + label + y);
        float labelWidth = UiDraw.width(label);
        UiDraw.text(g, UiDraw.fit(label, w - 30), x + 4, y + h / 2, t.text());
        if (submenu) UiDraw.icon(g, "chevron_right", x + w - 10, y + h / 2 - 4, 8, hover ? t.text() : t.muted());
        else if (detail != null && w - labelWidth - 30 > 30)
            UiDraw.right(g, UiDraw.fit(detail, w - labelWidth - 30, UiDraw.SMALL), x + w - 4, y + h / 2, UiDraw.SMALL, t.muted());
        hit(x - 4, y, w + 8, h, action);
    }

    private void segmented(GuiGraphics g, UiTheme t, String key, String[] labels, int chosen, IntConsumer pick) {
        float x = contentX(), w = contentWidth(), y = cursor, seg = w / labels.length;
        cursor += 18 + PAD;
        if (!visible(y, 18)) return;
        UiDraw.rounded(g, x, y, w, 18, 7, t.crust());
        float thumb = UiAnim.smooth(key, chosen, 16);
        UiDraw.rounded(g, x + 2 + thumb * seg, y + 2, seg - 4, 14, 5.5f, UiDraw.alpha(t.accent(), .22f));
        for (int i = 0; i < labels.length; i++) {
            boolean hover = hovered(x + i * seg, y, seg, 18);
            int colour = i == chosen ? t.accent() : UiDraw.mix(t.muted(), t.text(), UiAnim.smooth(key + ".h" + i, hover ? 1 : 0, 18));
            UiDraw.center(g, UiDraw.fit(labels[i], seg - 6, UiDraw.SMALL), x + i * seg, y + 9, seg, UiDraw.SMALL, colour);
            int index = i;
            hit(x + i * seg, y, seg, 18, () -> pick.accept(index));
        }
    }

    // ---- shared rows ----------------------------------------------------------------------------------------------

    private String requestText(ChatTpaRequests.Request request) {
        return (request.toThem() ? "Asks you to teleport to them" : "Wants to teleport to you") + " · "
            + ChatTeleportState.remaining(request.expires() - ChatToolsModule.now());
    }

    private void requestRow(GuiGraphics g, UiTheme t, ChatTpaRequests.Request request) {
        float x = contentX(), w = contentWidth(), y = cursor, h = ROW + 2;
        cursor += h + 2;
        if (!visible(y, h)) return;
        UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.yellow(), .1f));
        avatar(g, t, request.player(), x, y + h / 2, nameColour(t, request.player()), online(request.player()));
        var actions = new Actions(g, t, x + w, y + (h - CONTROL) / 2)
            .add("Decline", null, Tone.GHOST, true, () -> tools.answer(request, false))
            .add("Accept", "check", Tone.PRIMARY, true, () -> tools.answer(request, true));
        float tw = actions.left() - GAP - (x + 22);
        UiDraw.bold(g, UiDraw.fit(request.player(), tw), x + 22, y + 9, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(requestText(request), tw), x + 22, y + 20, UiDraw.SMALL, t.yellow());
    }

    private void timerRows(GuiGraphics g, UiTheme t) {
        long now = ChatToolsModule.now();
        float x = contentX(), w = contentWidth();
        for (var timer : timers) {
            float y = cursor, h = 22;
            cursor += h + 2;
            if (!visible(y, h)) continue;
            UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.yellow(), .1f));
            UiDraw.icon(g, "compass", x + 2, y + h / 2 - 4, 8, t.yellow());
            boolean home = timer.kind() == ChatTeleportState.Kind.Home;
            boolean hotspot = timer.kind() == ChatTeleportState.Kind.Hotspot;
            var actions = new Actions(g, t, x + w, y + 2);
            if (!hotspot && (home ? tools.canCancelHome() : tools.canCancelTpa()))
                actions.add("Cancel", "close", Tone.SECONDARY, true, () -> {
                    if (home) tools.cancelHome(); else tools.cancelTpa();
                    notice(home ? "Home cancellation sent." : "Teleport cancellation sent.");
                });
            String target = timer.target().isEmpty() ? "" : " " + timer.target();
            UiDraw.text(g, UiDraw.fit((home ? "Home" : hotspot ? "Hotspot" : "Teleport to") + target + " · " + timer.status(now), actions.left() - GAP - x - 14), x + 14, y + h / 2, t.yellow());
        }
        if (!timers.isEmpty()) cursor += PAD;
    }

    private void playerList(GuiGraphics g, UiTheme t, List<String> names, boolean clanPage, String key) {
        float base = cursor;
        for (String name : names) glide(g, "ct.pl." + key, name, base, () -> playerRow(g, t, name, clanPage));
        cursor += PAD;
    }

    /**
     * Draws one row of a list so it fades in when it first appears and glides when rows above it
     * are added or removed. The row lays out at its real place; it is only drawn (and clicked) at
     * the eased one, measured from the list's {@code base} so scrolling never lags.
     */
    private void glide(GuiGraphics g, String key, Object id, float base, Runnable row) {
        float shown = base + UiMotion.rowY(key, id, cursor - base), shift = shown - cursor;
        float before = UiRender.opacity;
        UiRender.opacity = before * UiMotion.rowAppear(key, id);
        cursor = shown;
        try { row.run(); } finally { cursor -= shift; UiRender.opacity = before; }
    }

    private void playerRow(GuiGraphics g, UiTheme t, String name, boolean clanPage) {
        float x = contentX(), w = contentWidth(), y = cursor, h = ROW + 2;
        cursor += h;
        if (!visible(y, h)) return;
        boolean hover = hovered(x - 4, y, w + 8, h);
        rowBackground(g, t, y, h, hover, "ct.pr." + name + clanPage);
        boolean on = online(name);
        avatar(g, t, name, x, y + h / 2, nameColour(t, name) == t.text() ? t.accent() : nameColour(t, name), on);
        var actions = new Actions(g, t, x + w, y + (h - CONTROL) / 2);
        if (clanPage) actions.icon("close", t.muted(), "Remove " + name + " from the clan (a later sync may add them back)", () -> {
            tools.removeFromClan(name); notice("Removed " + name + " from the clan.");
        });
        actions.icon("sliders", t.muted(), "More actions for " + name, () -> showPlayer(name))
            .add("TP", "compass", Tone.GHOST, true, () -> { tools.requestTeleport(name); notice("Teleport request sent to " + name); })
            .add("Message", "chat", Tone.SECONDARY, true, () -> openConversation(name));
        float tx = x + 22, tw = actions.left() - GAP - tx;
        int unread = tools.messages().unread(name);
        UiDraw.bold(g, UiDraw.fit(name, tw - (unread > 0 ? 16 : 0)), tx, y + 9, UiDraw.TEXT, nameColour(t, name));
        if (unread > 0) badge(g, String.valueOf(unread), tx + UiDraw.width(name) + 4, y + 9, t.accent(), t.base(), false);
        String detail;
        String roles = clanPage ? tools.discordRoleLabel(name) : "", discord = clanPage ? tools.discordUsername(name) : "";
        String countdown = ChatCountdowns.player(name, timers, requests, ChatToolsModule.now());
        if (!discord.isEmpty() || !roles.isEmpty()) detail = (discord.isEmpty() ? "" : "@" + discord) + (roles.isEmpty() ? "" : (discord.isEmpty() ? "" : " · ") + roles);
        else if (!countdown.isEmpty()) detail = countdown;
        else detail = (on ? "Online" : "Offline") + (friend(name) ? " · Friend" : enemy(name) ? " · Enemy" : "") + (!clanPage && clanMember(name) ? " · Clan" : "");
        UiDraw.text(g, UiDraw.fit(detail, tw), tx, y + 20, UiDraw.SMALL, countdown.isEmpty() || !detail.equals(countdown) ? t.muted() : t.yellow());
    }

    private void showPlayer(String name) {
        select(Page.PLAYERS);
        search.setValue(name);
        selectedPlayer = name;
    }

    // ---- Overview -------------------------------------------------------------------------------------------------

    private void overview(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        float y = cursor;
        cursor += CONTROL + PAD;
        if (visible(y, CONTROL)) {
            var actions = new Actions(g, t, x + w, y)
                .add("Request teleport", "compass", Tone.SECONDARY, true, this::requestTeleport)
                .add("New message", "plus", Tone.SECONDARY, true, this::newMessage)
                .add(tools.replyPlayer() != null ? "Reply to " + tools.replyPlayer() : "Reply", "chat", Tone.PRIMARY, true, this::reply);
            if (tools.commandAvailable("hotspot") && actions.left() - x > 70) actions.add("Hotspot", "star", Tone.GHOST, true, () -> command("/hotspot"));
        }
        waiting(g, t);
        timerRows(g, t);
        statusTiles(g, t);
        caption(g, t, "Favourite homes", null, "Manage", () -> select(Page.HOMES));
        List<String> favourites = tools.favoriteHomes();
        if (favourites.isEmpty()) emptyRow(g, t, "No favourite homes yet", "Star a home in Homes to pin it here.");
        else homeTiles(g, t, favourites);
        caption(g, t, "Recent players", null, "View all", () -> select(Page.PLAYERS));
        List<String> recent = new ArrayList<>(tools.recentPlayers());
        for (var request : requests) if (recent.stream().noneMatch(n -> n.equalsIgnoreCase(request.player()))) recent.add(request.player());
        if (recent.isEmpty()) emptyRow(g, t, "No recent players yet", "People you message or teleport to appear here.");
        else playerList(g, t, recent.subList(0, Math.min(8, recent.size())), false, "home");
    }

    /** Everything that needs the player, pinned to the top of Overview until it's dealt with. */
    private void waiting(GuiGraphics g, UiTheme t) {
        var unread = tools.messages().recent().stream().filter(p -> tools.messages().unread(p) > 0).toList();
        var waiting = tools.waiting();
        int items = requests.size() + unread.size() + (waiting.problem() == null ? 0 : 1);
        if (items == 0) return;
        float x = contentX(), w = contentWidth();
        caption(g, t, "Waiting for you", String.valueOf(items), null, null);
        for (var request : requests) requestRow(g, t, request);
        for (String player : unread) {
            float y = cursor, h = ROW + 2;
            cursor += h + 2;
            if (!visible(y, h)) continue;
            UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.accent(), .08f));
            avatar(g, t, player, x, y + h / 2, t.accent(), online(player));
            var actions = new Actions(g, t, x + w, y + (h - CONTROL) / 2).add("Open", "chat", Tone.PRIMARY, true, () -> openConversation(player));
            int count = tools.messages().unread(player);
            var messages = tools.messages().conversation(player).messages();
            float tw = actions.left() - GAP - (x + 22);
            UiDraw.bold(g, UiDraw.fit(player + " · " + count + " new message" + (count == 1 ? "" : "s"), tw), x + 22, y + 9, UiDraw.TEXT, t.text());
            if (!messages.isEmpty()) UiDraw.text(g, UiDraw.fit("“" + messages.getLast().text() + "”", tw), x + 22, y + 20, UiDraw.SMALL, t.muted());
        }
        if (waiting.problem() != null) {
            float y = cursor, h = ROW + 2;
            cursor += h + 2;
            if (visible(y, h)) {
                UiDraw.rounded(g, x - 4, y, w + 8, h, 6, UiDraw.alpha(t.red(), .1f));
                UiDraw.icon(g, "bell", x + 4, y + h / 2 - 4, 8, t.red());
                var actions = new Actions(g, t, x + w, y + (h - CONTROL) / 2).add("Fix", "sliders", Tone.SECONDARY, true, this::openDiscordSettings);
                float tw = actions.left() - GAP - (x + 22);
                UiDraw.bold(g, "Discord sync needs attention", x + 22, y + 9, UiDraw.TEXT, t.text());
                UiDraw.text(g, UiDraw.fit(waiting.problem(), tw), x + 22, y + 20, UiDraw.SMALL, t.muted());
            }
        }
        cursor += PAD;
    }

    private void statusTiles(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = cursor, h = 32, tw = (w - 6) / 2;
        cursor += h + PAD;
        if (!visible(y, h)) return;
        var stats = tools.autoTpyStats();
        tile(g, t, x, y, tw, h, "check", "Auto TPY", tools.autoTpyEnabled() ? "On · " + stats.live() + " buffered" : "Off", () -> select(Page.AUTO_TPY));
        DiscordSyncModule sync = DiscordSyncModule.get();
        String clan = sync == null ? "Unavailable" : !sync.isToggled() ? tools.clanMembers().size() + " members · sync off"
            : sync.syncBusy() ? "Syncing…" : sync.syncFailed() ? "Last sync failed"
            : sync.lastSuccessfulSync() > 0 ? sync.syncedAccounts() + " linked · synced " + ago(sync.lastSuccessfulSync()) : "Not synced yet";
        tile(g, t, x + tw + 6, y, w - tw - 6, h, "shield", "Clan", clan, () -> select(Page.CLAN));
    }
    private void tile(GuiGraphics g, UiTheme t, float x, float y, float w, float h, String icon, String title, String detail, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        float hv = UiAnim.smooth("ct.tile." + title, hover ? 1 : 0, 20);
        UiDraw.rounded(g, x, y, w, h, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .8f));
        UiDraw.outline(g, x, y, w, h, 7, .6f, UiDraw.alpha(UiDraw.mix(t.overlay(), t.accent(), hv), .5f));
        UiDraw.circle(g, x + 14, y + h / 2, 8, UiDraw.alpha(t.accent(), .16f));
        UiDraw.icon(g, icon, x + 10, y + h / 2 - 4, 8, t.accent());
        UiDraw.bold(g, title, x + 28, y + 11, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(detail, w - 44), x + 28, y + 22, UiDraw.SMALL, t.muted());
        UiDraw.icon(g, "chevron_right", x + w - 12, y + h / 2 - 4, 8, t.muted());
        hit(x, y, w, h, action);
    }
    private static String ago(long since) { return ChatTeleportState.remaining(Math.max(0, ChatToolsModule.now() - since)) + " ago"; }

    private void homeTiles(GuiGraphics g, UiTheme t, List<String> homes) {
        float x = contentX(), w = contentWidth();
        int columns = w >= 420 ? 3 : 2;
        float gap = 6, tw = (w - gap * (columns - 1)) / columns, h = 28;
        for (int i = 0; i < homes.size(); i++) {
            String home = homes.get(i);
            float tx = x + (i % columns) * (tw + gap), ty = cursor + (i / columns) * (h + gap);
            if (!visible(ty, h)) continue;
            boolean hover = hovered(tx, ty, tw, h);
            float hv = UiAnim.smooth("ct.home." + home, hover ? 1 : 0, 20);
            UiDraw.rounded(g, tx, ty, tw, h, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .8f));
            UiDraw.icon(g, "star", tx + 7, ty + h / 2 - 4, 8, t.yellow());
            UiDraw.bold(g, UiDraw.fit(home, tw - 26), tx + 20, ty + 10, UiDraw.TEXT, t.text());
            String countdown = ChatCountdowns.home(home, timers, ChatToolsModule.now());
            UiDraw.text(g, UiDraw.fit(countdown.isEmpty() ? hover ? "Teleport now" : "Home" : countdown, tw - 26), tx + 20, ty + 20,
                UiDraw.SMALL, countdown.isEmpty() ? hover ? t.accent() : t.muted() : t.yellow());
            hit(tx, ty, tw, h, () -> command("/home " + home));
        }
        cursor += ((homes.size() + columns - 1) / columns) * (h + gap) + PAD;
    }

    // ---- Chat -----------------------------------------------------------------------------------------------------

    private record ChatRow(String time, List<StyledText.Line> lines, boolean dm) {}
    private final Map<Long, ChatRow> chatRows = new HashMap<>();
    private float chatRowsWidth, chatRowsDensity;
    private String chatRowsFormat = "";

    private ChatRow chatRow(ChatToolsModule.ChatLine line, float width, DateTimeFormatter format) {
        return chatRows.computeIfAbsent(line.id(), id -> new ChatRow(
            format.format(Instant.ofEpochMilli(line.time()).atZone(ZoneId.systemDefault())),
            StyledText.wrap(StyledText.runs(line.message()), Math.max(20, width), CHAT_SIZE), line.dm()));
    }
    private static float chatLineHeight() { return CHAT_SIZE + 3; }
    private float rowHeight(ChatRow row) { return Math.max(1, row.lines().size()) * chatLineHeight() + 3; }

    private void chatPage(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), top = bodyTop() + 2, bottom = bodyBottom();
        var lines = tools.chatLines();
        if (lines.isEmpty()) {
            float cy = (top + bottom) / 2;
            UiDraw.center(g, "No chat yet", x, cy - 6, w, t.text());
            UiDraw.center(g, "Chat on this server appears here as it arrives. Type below to send.", x, cy + 6, w, UiDraw.SMALL, t.muted());
            transcriptArea = null;
            return;
        }
        chatScroll = transcript(g, t, lines, x, w, top, bottom, chatScroll, true);
        float bx = x + w;
        if (chatScroll > 0) {
            float bw = 62;
            bx -= bw;
            button(g, t, "Newest", "chevron_down", bx, bottom - CONTROL - 4, bw, Tone.PRIMARY, true, () -> chatScroll = 0);
            bx -= GAP;
        }
        if (!selectedLines().isEmpty()) {
            float bw = buttonWidth("Copy", "check");
            button(g, t, "Copy", "check", bx - bw, bottom - CONTROL - 4, bw, Tone.SECONDARY, true, this::copySelection);
        }
    }

    // ---- chat transcript, shared by the Chat page and Overview's chat card ------------------------------------------

    /** Where the transcript was last drawn and the rows it showed, so presses can find the line under the pointer. */
    private record RowSpan(long id, float top, float bottom) {}
    private record TranscriptArea(float x, float y, float w, float h, boolean dock, List<RowSpan> rows) {}
    private TranscriptArea transcriptArea;
    /** Selected chat lines, by id: the line the press started on and the line the pointer is over now. */
    private long selectAnchor = -1, selectEnd = -1;
    /** A press on the transcript waiting for release (a click) or movement (a drag that selects). */
    private boolean transcriptPressed, transcriptDragged;
    private double pressX, pressY;
    private float dockScroll;
    private long dockSeen;

    /**
     * Draws chat lines newest-at-the-bottom, {@code fromBottom} units scrolled up, and highlights the selection.
     * While scrolled up, what's on screen holds still as new lines arrive. Returns the clamped scroll.
     */
    private float transcript(GuiGraphics g, UiTheme t, List<ChatToolsModule.ChatLine> lines, float x, float w, float top, float bottom,
                             float fromBottom, boolean chatPage) {
        String pattern = tools.chatTimeFormat();
        float timeWidth = UiDraw.width("00:00:00", UiDraw.SMALL) * (pattern.length() / 8f) + 8;
        float textWidth = w - timeWidth;
        if (textWidth != chatRowsWidth || UiDraw.density != chatRowsDensity || !pattern.equals(chatRowsFormat)) {
            chatRows.clear(); chatRowsWidth = textWidth; chatRowsDensity = UiDraw.density; chatRowsFormat = pattern;
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern);
        float total = 0;
        Set<Long> ids = new java.util.HashSet<>();
        for (var line : lines) { ids.add(line.id()); total += rowHeight(chatRow(line, textWidth, format)); }
        chatRows.keySet().retainAll(ids);
        // While reading older lines, new ones arriving below must not move what's on screen.
        long newest = lines.getLast().id(), seen = chatPage ? chatSeen : dockSeen;
        if (fromBottom > 0 && newest > seen) for (var line : lines) if (line.id() > seen) fromBottom += rowHeight(chatRow(line, textWidth, format));
        if (chatPage) chatSeen = newest; else dockSeen = newest;
        fromBottom = Math.clamp(fromBottom, 0, Math.max(0, total - (bottom - top)));
        long low = Math.min(selectAnchor, selectEnd), high = Math.max(selectAnchor, selectEnd);
        List<RowSpan> spans = new ArrayList<>();
        g.enableScissor((int) x - 4, (int) top, (int) (x + w + 4), (int) bottom);
        float y = bottom - total + fromBottom;
        for (var line : lines) {
            ChatRow row = chatRow(line, textWidth, format);
            float h = rowHeight(row);
            if (y + h > top && y < bottom) {
                spans.add(new RowSpan(line.id(), y, y + h));
                boolean selected = selectAnchor >= 0 && line.id() >= low && line.id() <= high;
                if (selected) UiDraw.rounded(g, x - 4, y, w + 8, h, 4, UiDraw.alpha(t.accent(), .24f));
                else if (row.dm()) UiDraw.rounded(g, x - 4, y, w + 8, h, 4, UiDraw.alpha(t.accent(), .08f));
                UiDraw.text(g, row.time(), x, y + 1 + chatLineHeight() / 2, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
                float ly = y + 1.5f;
                for (var text : row.lines()) {
                    if (ly + chatLineHeight() > top && ly < bottom) StyledText.draw(g, text, x + timeWidth, ly + chatLineHeight() / 2, CHAT_SIZE, t.text());
                    ly += chatLineHeight();
                }
            }
            y += h;
        }
        g.disableScissor();
        transcriptArea = new TranscriptArea(x - 4, top, w + 8, bottom - top, !chatPage, spans);
        return fromBottom;
    }

    /** Overview's chat card: the newest lines, scrolled on their own. A click opens Chat; a drag selects lines to copy. */
    private void chatDock(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = bodyBottom() + 4, h = dockHeight() - 8;
        boolean hover = hovered(x - 4, y, w + 8, h);
        float hv = UiAnim.smooth("ct.dock", hover ? 1 : 0, 18);
        UiDraw.rounded(g, x - 4, y, w + 8, h, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hv * .35f));
        UiDraw.outline(g, x - 4, y, w + 8, h, 7, .6f, UiDraw.alpha(UiDraw.mix(t.overlay(), t.accent(), hv), .5f));
        UiDraw.icon(g, "chat", x + 2, y + 6, 8, t.accent());
        UiDraw.bold(g, "Chat", x + 14, y + 10, UiDraw.TEXT, t.text());
        float right = x + w;
        float ow = buttonWidth("Open chat", "chevron_right");
        right -= ow;
        button(g, t, "Open chat", "chevron_right", right, y + 1, ow, Tone.GHOST, true, this::openChatPage);
        if (!selectedLines().isEmpty()) {
            float cw = buttonWidth("Copy", "check");
            right -= cw + GAP;
            button(g, t, "Copy", "check", right, y + 1, cw, Tone.SECONDARY, true, this::copySelection);
        }
        var lines = tools.chatLines();
        float top = y + 21, bottom = y + h - 4;
        if (lines.isEmpty()) {
            UiDraw.text(g, "Chat on this server appears here as it arrives.", x + 2, top + 6, UiDraw.SMALL, t.muted());
            transcriptArea = new TranscriptArea(x - 4, top, w + 8, bottom - top, true, List.of());
            return;
        }
        dockScroll = transcript(g, t, lines, x + 2, w - 4, top, bottom, dockScroll, false);
        UiMotion.faded(UiMotion.edge(dockScroll), () -> UiDraw.gradient(g, x - 3, top, w + 6, 6, 0, UiDraw.alpha(t.mantle(), .8f), UiDraw.alpha(t.mantle(), 0)));
    }

    private void openChatPage() { clearSelection(); chatScroll = 0; select(Page.CHAT); }

    /** The chat line under the pointer, or the nearest one above or below the shown rows. */
    private long lineAt(double my) {
        var area = transcriptArea;
        if (area == null || area.rows().isEmpty()) return -1;
        for (RowSpan row : area.rows()) if (my >= row.top() && my < row.bottom()) return row.id();
        return my < area.rows().getFirst().top() ? area.rows().getFirst().id() : area.rows().getLast().id();
    }
    private boolean overTranscript(double mx, double my) {
        var area = transcriptArea;
        return area != null && (page == Page.CHAT || page == Page.OVERVIEW && area.dock() && dockHeight() > 0)
            && UiDraw.over(mx, my, area.x(), area.y(), area.w(), area.h());
    }
    private void clearSelection() { selectAnchor = selectEnd = -1; }

    /** The selected lines, oldest first, as plain text. */
    private List<String> selectedLines() {
        if (selectAnchor < 0 || tools == null || minecraft.getConnection() == null) return List.of();
        long low = Math.min(selectAnchor, selectEnd), high = Math.max(selectAnchor, selectEnd);
        return memo("selected:" + low + ":" + high, () -> tools.chatLines().stream()
            .filter(line -> line.id() >= low && line.id() <= high).map(line -> line.message().getString()).toList());
    }
    private void copySelection() {
        var picked = selectedLines();
        if (picked.isEmpty()) return;
        minecraft.keyboardHandler.setClipboard(String.join("\n", picked));
        notice("Copied " + picked.size() + (picked.size() == 1 ? " line" : " lines"));
    }
    private void copyLine(long id) {
        if (tools == null) return;
        tools.chatLines().stream().filter(line -> line.id() == id).findFirst().ifPresent(line -> {
            minecraft.keyboardHandler.setClipboard(line.message().getString());
            notice("Copied line");
        });
    }

    // ---- Messages -------------------------------------------------------------------------------------------------

    private void conversations(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = cursor;
        cursor += CONTROL + PAD;
        if (visible(y, CONTROL)) new Actions(g, t, x + w, y)
            .add("New message", "plus", Tone.PRIMARY, true, this::newMessage)
            .add(tools.replyPlayer() != null ? "Reply to " + tools.replyPlayer() : "Reply", "chat", Tone.SECONDARY, true, this::reply);
        var recent = tools.messages().recent();
        if (recent.isEmpty()) { emptyRow(g, t, "No conversations yet", "Private messages you send or receive this session appear here."); return; }
        for (String player : recent) {
            float ry = cursor, h = ROW + 4;
            cursor += h;
            if (!visible(ry, h)) continue;
            boolean hover = hovered(x - 4, ry, w + 8, h);
            rowBackground(g, t, ry, h, hover, "ct.conv." + player);
            avatar(g, t, player, x, ry + h / 2, nameColour(t, player) == t.text() ? t.accent() : nameColour(t, player), online(player));
            var messages = tools.messages().conversation(player).messages();
            int unread = tools.messages().unread(player);
            float tw = w - 40;
            UiDraw.bold(g, UiDraw.fit(player, tw), x + 22, ry + 10, UiDraw.TEXT, nameColour(t, player));
            if (unread > 0) badge(g, String.valueOf(unread), x + w, ry + 10, t.accent(), t.base(), true);
            if (!messages.isEmpty()) {
                var last = messages.getLast();
                UiDraw.text(g, UiDraw.fit((last.incoming() ? "" : "You: ") + last.text(), tw), x + 22, ry + 21, UiDraw.SMALL,
                    unread > 0 ? t.text() : t.muted());
            }
            hit(x - 4, ry, w + 8, h, () -> openConversation(player));
        }
    }

    private record Bubble(boolean incoming, boolean pending, List<String> lines, float width) {
        float height() { return lines.size() * 9 + 8; }
    }

    private void conversation(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = bodyTop() + 2;
        boolean isFriend = friend(recipient), on = online(recipient);
        var actions = new Actions(g, t, x + w, y)
            .add(isFriend ? "Unfriend" : "Add friend", "people", Tone.SECONDARY, true, () -> tools.toggleFriend(recipient))
            .add("TP", "compass", Tone.SECONDARY, true, () -> { tools.requestTeleport(recipient); notice("Teleport request sent to " + recipient); })
            .icon("sliders", t.muted(), "More actions", () -> showPlayer(recipient));
        iconButton(g, t, "chevron_left", x, y, CONTROL, t.muted(), "All conversations (Esc)", () -> { saveDraft(); recipient = null; composer.setValue(drafts.getOrDefault("", "")); placeInputs(); });
        String countdown = ChatCountdowns.player(recipient, timers, List.of(), ChatToolsModule.now());
        String status = (on ? "Online" : "Offline") + (isFriend ? " · Friend" : enemy(recipient) ? " · Enemy" : "") + (clanMember(recipient) ? " · Clan" : "");
        UiDraw.text(g, UiDraw.fit(countdown.isEmpty() ? status : countdown, actions.left() - GAP - x - 24), x + 24, y + CONTROL / 2, UiDraw.SMALL,
            countdown.isEmpty() ? t.muted() : t.yellow());
        float top = y + CONTROL + 6;
        cursor = top;
        for (var request : requests) if (request.player().equalsIgnoreCase(recipient)) requestRow(g, t, request);
        top = cursor + 2;
        float bottom = bodyBottom();
        var messages = tools.messages().conversation(recipient).messages();
        var pending = tools.pendingWhispers(recipient);
        float maxBubble = w * .72f;
        List<Bubble> bubbles = new ArrayList<>();
        for (var message : messages) bubbles.add(bubble(message.text(), message.incoming(), false, maxBubble));
        for (String text : pending) bubbles.add(bubble(text, false, true, maxBubble));
        float total = 0;
        for (int i = 0; i < bubbles.size(); i++) total += bubbles.get(i).height() + (i == 0 ? 0 : bubbles.get(i - 1).incoming() == bubbles.get(i).incoming() ? 3 : 8);
        conversationScroll = Math.clamp(conversationScroll, 0, Math.max(0, total - (bottom - top)));
        if (bubbles.isEmpty()) {
            float cy = (top + bottom) / 2;
            UiDraw.center(g, "No messages with " + recipient + " yet", x, cy - 6, w, t.text());
            UiDraw.center(g, "Type below to send a private message.", x, cy + 6, w, UiDraw.SMALL, t.muted());
            return;
        }
        g.enableScissor((int) x - 4, (int) top, (int) (x + w + 4), (int) bottom);
        float by = bottom - total + conversationScroll;
        long wait = tools.whisperWait();
        for (int i = 0; i < bubbles.size(); i++) {
            Bubble bubble = bubbles.get(i);
            if (i > 0) by += bubbles.get(i - 1).incoming() == bubble.incoming() ? 3 : 8;
            float bx = bubble.incoming() ? x : x + w - bubble.width(), bh = bubble.height();
            if (by + bh > top && by < bottom) {
                int fill = bubble.pending() ? UiDraw.alpha(t.accent(), .12f) : bubble.incoming() ? t.surface() : UiDraw.alpha(t.accent(), .3f);
                UiDraw.rounded(g, bx, by, bubble.width(), bh, 7, fill);
                if (bubble.pending()) UiDraw.outline(g, bx, by, bubble.width(), bh, 7, .6f, UiDraw.alpha(t.accent(), .5f));
                for (int l = 0; l < bubble.lines().size(); l++)
                    UiDraw.text(g, bubble.lines().get(l), bx + 7, by + 8.5f + l * 9, bubble.pending() ? t.muted() : t.text());
                if (bubble.pending() && UiDraw.over(mouseX, mouseY, bx, by, bubble.width(), bh) && dialog == null)
                    tooltip("ct.pending." + i, wait > 0 ? "Waiting for the message cooldown · sends in " + ChatTeleportState.remaining(wait) : "Waiting for the server to confirm");
            }
            by += bh;
        }
        g.disableScissor();
        if (conversationScroll > 0) button(g, t, "Newest", "chevron_down", x + w - 62, bottom - CONTROL - 4, 62, Tone.PRIMARY, true, () -> conversationScroll = 0);
        if (reading(recipient)) tools.messages().read(recipient);
    }

    private Bubble bubble(String text, boolean incoming, boolean pending, float maxWidth) {
        List<String> lines = UiDraw.wrap(text, maxWidth - 14, UiDraw.TEXT);
        if (lines.isEmpty()) lines = List.of("");
        float widest = 0;
        for (String line : lines) widest = Math.max(widest, UiDraw.width(line));
        return new Bubble(incoming, pending, lines, Math.min(maxWidth, widest + 14));
    }

    // ---- Players --------------------------------------------------------------------------------------------------

    private void players(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = cursor;
        cursor += 20 + PAD;
        search.place(Math.round(x), Math.round(y) + 4, Math.round(w));
        if (visible(y, 20)) search.render(g, mouseX, mouseY, 0);
        String query = search.getValue().strip();
        String lower = query.toLowerCase(Locale.ROOT);
        if (selectedPlayer == null && ChatMessages.validName(query)) {
            var exact = ChatPlayerSearch.matching(lower, tools.recentPlayers(), tools.friends(), tools.onlinePlayers(), tools.clanMembers())
                .stream().filter(n -> n.equalsIgnoreCase(query)).findFirst();
            if (exact.isPresent()) selectedPlayer = exact.get();
        }
        if (selectedPlayer != null && selectedPlayer.equalsIgnoreCase(query)) playerActions(g, t, selectedPlayer);
        var recent = ChatPlayerSearch.matching(lower, tools.recentPlayers(), requests.stream().map(ChatTpaRequests.Request::player).toList());
        var friends = ChatPlayerSearch.matching(lower, tools.friends());
        caption(g, t, "Recent", String.valueOf(recent.size()), null, null);
        if (recent.isEmpty()) emptyRow(g, t, query.isEmpty() ? "Nothing here yet" : "No matching recent players", "Search for a player to start a conversation.");
        else playerList(g, t, recent, false, "recent");
        caption(g, t, "Friends", String.valueOf(friends.size()), null, null);
        if (friends.isEmpty()) emptyRow(g, t, query.isEmpty() ? "No friends yet" : "No matching friends", "Add a friend from their player actions.");
        else playerList(g, t, friends, false, "friends");
        if (!query.isEmpty()) {
            var online = ChatPlayerSearch.matching(lower, tools.onlinePlayers()).stream()
                .filter(name -> recent.stream().noneMatch(name::equalsIgnoreCase) && friends.stream().noneMatch(name::equalsIgnoreCase)).toList();
            caption(g, t, "Online", String.valueOf(online.size()), null, null);
            if (online.isEmpty()) emptyRow(g, t, "Nobody online matches", ChatMessages.validName(query) ? "Press Enter to open actions for " + query + "." : "Try another name.");
            else playerList(g, t, online.subList(0, Math.min(30, online.size())), false, "online");
        } else {
            float hy = cursor;
            cursor += 12;
            if (visible(hy, 12)) UiDraw.text(g, "Search to see who's online.", x, hy + 6, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
        }
    }

    private void playerActions(GuiGraphics g, UiTheme t, String player) {
        caption(g, t, "Actions for " + player, null, null, null);
        boolean canIgnore = tools.commandAvailable("ignore");
        String ignore = "/ignore " + player;
        menuRow(g, t, "Message", null, false, () -> openConversation(player));
        menuRow(g, t, "Request teleport", "/tpa " + player, false, () -> { tools.requestTeleport(player); notice("Teleport request sent to " + player); });
        menuRow(g, t, friend(player) ? "Remove friend" : "Add friend", "Argus friends", false, () -> tools.toggleFriend(player));
        menuRow(g, t, enemy(player) ? "Remove enemy" : "Mark as enemy", "Argus friends", false, () -> tools.toggleEnemy(player));
        menuRow(g, t, clanMember(player) ? "Remove from clan" : "Add to clan", "Clan list", false, () -> tools.toggleClan(player));
        if (canIgnore) menuRow(g, t, "Ignore player", ignore, false, () -> confirm("Ignore " + player + "?",
            "The server hides their chat and messages. Server → Settings lists ignored players.", "Ignore", ignore, () -> command(ignore)));
        for (var option : tools.options(player)) {
            if (option.command().strip().equalsIgnoreCase(ignore) || option.command().strip().equalsIgnoreCase("/tpa " + player)) continue;
            menuRow(g, t, option.label(), option.command().strip(), false, () -> {
                if (tools.runsImmediately(option) || !Character.isWhitespace(option.command().charAt(option.command().length() - 1)))
                    tools.sendCommand(option.command().strip());
                else request(option.label() + " · " + player, "Text to add", option.command(), "Send");
            });
        }
        cursor += PAD;
    }

    // ---- Clan -----------------------------------------------------------------------------------------------------

    private void clan(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        DiscordSyncModule sync = DiscordSyncModule.get();
        float y = cursor, h = 34;
        cursor += h + PAD;
        if (visible(y, h)) {
            UiDraw.rounded(g, x - 4, y, w + 8, h, 7, UiDraw.alpha(t.mantle(), .8f));
            var actions = new Actions(g, t, x + w - 4, y + (h - CONTROL) / 2)
                .add("Discord settings", "sliders", Tone.GHOST, true, this::openDiscordSettings);
            if (sync != null && sync.isToggled()) actions.add(sync.syncBusy() ? "Syncing…" : "Sync now", "reset", Tone.PRIMARY, !sync.syncBusy(), sync::syncNow);
            String title = sync == null ? "Discord sync unavailable" : !sync.isToggled() ? "Discord sync is off" : sync.syncFailed() ? "Last sync failed" : "Discord sync";
            String detail = sync == null ? "" : !sync.isToggled() ? "Turn it on in Settings → Discord Sync to fill this list from #ign." : sync.syncStatus();
            float tw = actions.left() - GAP - x - 18;
            UiDraw.icon(g, "shield", x + 2, y + h / 2 - 4, 8, sync != null && sync.syncFailed() ? t.red() : t.accent());
            UiDraw.bold(g, UiDraw.fit(title, tw), x + 16, y + 11, UiDraw.TEXT, t.text());
            UiDraw.text(g, UiDraw.fit(detail, tw), x + 16, y + 23, UiDraw.SMALL, t.muted());
        }
        float ay = cursor;
        cursor += CONTROL + PAD;
        if (visible(ay, CONTROL)) new Actions(g, t, x + w, ay)
            .add(importBusy ? "Importing…" : "Import CSV", "plus", Tone.SECONDARY, !importBusy, () -> importNames(null, false))
            .add("Add members", "plus", Tone.PRIMARY, true, this::addClanMembers);
        var groups = tools.discordClanGroups();
        if (groups.friends().isEmpty() && groups.others().isEmpty()) {
            emptyRow(g, t, "No clan members yet", "Add members by hand, import a CSV, or set up Discord sync.");
            return;
        }
        if (!groups.friends().isEmpty()) { caption(g, t, "Friends", String.valueOf(groups.friends().size()), null, null); playerList(g, t, groups.friends(), true, "clan.friends"); }
        if (!groups.others().isEmpty()) { caption(g, t, "Members by role", String.valueOf(groups.others().size()), null, null); playerList(g, t, groups.others(), true, "clan.others"); }
    }

    private void openDiscordSettings() { openSection(SettingsScreen.sectionFor("DiscordSync")); }

    /** Imports names into the clan list or the Auto TPY whitelist from a picked or dropped file. */
    private void importNames(List<Path> dropped, boolean whitelist) {
        if (importBusy) return;
        importBusy = true;
        Minecraft client = Minecraft.getInstance();
        long revision = ++importRevision;
        var ticket = new AutoTpyWhitelistImport.Ticket(revision, tools.sessionKey(), tools.autoTpyWhitelist());
        Set<String> blocked = Set.copyOf(tools.autoTpyBlocklist());
        Thread worker = new Thread(() -> {
            String problem = null;
            List<byte[]> files = List.of();
            try {
                var chosen = dropped == null ? ChatToolsFiles.choose(whitelist ? "Import Auto TPY whitelist" : "Import clan members") : List.copyOf(dropped);
                files = ChatToolsFiles.read(chosen);
            } catch (java.io.IOException exception) {
                problem = exception.getMessage();
            } catch (RuntimeException | LinkageError exception) {
                problem = "Couldn't open a file picker. Drop the .csv file on this window instead.";
            }
            String error = problem;
            List<byte[]> contents = files;
            client.execute(() -> {
                importBusy = false;
                if (client.screen != this) return;
                if (error != null) { notice(error, true); return; }
                if (contents.isEmpty()) return;
                if (whitelist) {
                    if (!ticket.canApply(importRevision, tools.sessionKey(), tools.autoTpyWhitelist())) { notice("Import discarded because the list changed meanwhile.", true); return; }
                    var result = AutoTpyWhitelistImport.parse(ticket.original(), blocked, contents);
                    tools.setAutoTpyWhitelist(result.merged());
                    notice("Whitelist: added " + result.added() + ", " + result.duplicates() + " duplicates, " + result.invalid() + " invalid, " + result.blocked() + " blocklisted.");
                } else {
                    List<String> names = new ArrayList<>();
                    int invalid = 0;
                    for (byte[] content : contents) {
                        var parsed = ChatClan.csv(ChatClan.decode(content));
                        names = ChatClan.add(names, parsed.names());
                        invalid += parsed.invalid();
                    }
                    notice("Added " + tools.addToClan(names) + " to the clan" + (invalid > 0 ? "; skipped " + invalid + " invalid." : "."));
                }
            });
        }, "Argus Chat Tools import");
        worker.setDaemon(true);
        worker.start();
    }

    @Override public void onFilesDrop(List<Path> files) {
        if (tools == null || dialog != null) return;
        if (page == Page.CLAN) importNames(files, false);
        else if (page == Page.AUTO_TPY && autoSection == 2 && !blocklistEditing) importNames(files, true);
    }

    // ---- Homes ----------------------------------------------------------------------------------------------------

    private void homes(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = cursor;
        cursor += CONTROL + PAD;
        if (visible(y, CONTROL)) new Actions(g, t, x + w, y)
            .add("Server list", null, Tone.GHOST, true, () -> command("/homes"))
            .add(tools.loadingHomes() ? "Refreshing…" : "Refresh", "reset", Tone.GHOST, !tools.loadingHomes(), tools::refreshHomes)
            .add("New home", "plus", Tone.PRIMARY, true, this::newHome);
        timerRows(g, t);
        tools.loadHomes();
        var homes = tools.homes();
        caption(g, t, "Your homes", homes.isEmpty() ? null : String.valueOf(homes.size()), null, null);
        if (homes.isEmpty()) emptyRow(g, t, tools.loadingHomes() ? "Finding your homes…" : "No homes found", "Create a home, or ask the server for its list.");
        for (String home : homes) {
            float ry = cursor, h = ROW + 2;
            cursor += h;
            if (!visible(ry, h)) continue;
            rowBackground(g, t, ry, h, hovered(x - 4, ry, w + 8, h), "ct.hr." + home);
            boolean favourite = tools.favoriteHome(home);
            iconButton(g, t, "star", x, ry + (h - CONTROL) / 2, CONTROL, favourite ? t.yellow() : t.muted(),
                favourite ? "Unpin from Overview" : "Pin to Overview", () -> tools.toggleFavoriteHome(home));
            var actions = new Actions(g, t, x + w, ry + (h - CONTROL) / 2)
                .icon("close", t.red(), "Delete " + home, () -> deleteHome(home))
                .add("Rename", null, Tone.GHOST, true, () -> renameHome(home))
                .add("Go", "chevron_right", Tone.PRIMARY, true, () -> command("/home " + home));
            float tx = x + 24, tw = actions.left() - GAP - tx;
            UiDraw.bold(g, UiDraw.fit(home, tw), tx, ry + 9, UiDraw.TEXT, t.text());
            String countdown = ChatCountdowns.home(home, timers, ChatToolsModule.now());
            UiDraw.text(g, UiDraw.fit(countdown.isEmpty() ? favourite ? "Pinned to Overview" : "Saved home" : countdown, tw), tx, ry + 20,
                UiDraw.SMALL, countdown.isEmpty() ? t.muted() : t.yellow());
        }
        cursor += PAD;
    }

    // ---- Teleports ------------------------------------------------------------------------------------------------

    private void teleports(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth(), y = cursor;
        cursor += CONTROL + PAD;
        if (visible(y, CONTROL)) new Actions(g, t, x + w, y).add("Request teleport", "compass", Tone.PRIMARY, true, this::requestTeleport);
        caption(g, t, "Incoming requests", requests.isEmpty() ? null : String.valueOf(requests.size()), null, null);
        if (requests.isEmpty()) emptyRow(g, t, "No requests waiting", tools.autoTpyEnabled() ? "Auto TPY is on, so eligible requests are accepted for you." : "Requests to teleport appear here.");
        for (var request : requests) requestRow(g, t, request);
        if (!requests.isEmpty()) cursor += PAD;
        caption(g, t, "Warmups and cooldowns", null, null, null);
        if (timers.isEmpty()) emptyRow(g, t, "Nothing running", "Home and teleport warmups and cooldowns show here.");
        else timerRows(g, t);
        caption(g, t, "Cooldown lengths", null, null, null);
        cooldownRow(g, t, "After a home teleport", ChatTeleportState.Kind.Home);
        cooldownRow(g, t, "After a player teleport", ChatTeleportState.Kind.TPA);
        cursor += PAD;
    }

    /** Manual pins your own length (the same setting as in the module's settings); Auto uses the one learned from the server. */
    private void cooldownRow(GuiGraphics g, UiTheme t, String label, ChatTeleportState.Kind kind) {
        float x = contentX(), w = contentWidth(), y = cursor, h = ROW + 4;
        cursor += h;
        if (!visible(y, h)) return;
        rowBackground(g, t, y, h, false, "ct.cd." + kind);
        var length = tools.cooldownLength(kind);
        boolean manual = length.manual() != null;
        long seconds = length.effective() / 1_000;
        float by = y + (h - CONTROL) / 2;
        var actions = new Actions(g, t, x + w, by)
            .add("+", null, Tone.SECONDARY, seconds < 3_600, () -> tools.manualCooldown(kind, ChatSavedCooldowns.step(seconds, true) * 1_000));
        String value = manual ? seconds == 0 ? "Off" : ChatTeleportState.remaining(length.effective())
            : length.learned() == null ? "—" : ChatTeleportState.remaining(length.learned());
        float valueRight = actions.left();
        UiDraw.center(g, value, valueRight - 40, by + CONTROL / 2, 40, manual ? t.text() : t.muted());
        actions = new Actions(g, t, valueRight - 40 - GAP, by)
            .add("−", null, Tone.SECONDARY, seconds > 0, () -> tools.manualCooldown(kind, ChatSavedCooldowns.step(seconds, false) * 1_000))
            .add(manual ? "Manual" : "Auto", null, manual ? Tone.PRIMARY : Tone.GHOST, true, () -> tools.manualCooldown(kind, manual ? null : length.effective()));
        String source = manual ? "Manual · always used" + (length.learned() == null ? "" : " (server: " + ChatTeleportState.remaining(length.learned()) + ")")
            : length.learned() != null ? "Auto · learned from the server"
            : "Auto · learned from the server's next \"wait\" reply";
        float tw = actions.left() - GAP - x - 4;
        UiDraw.text(g, UiDraw.fit(label, tw), x + 4, y + 10, t.text());
        UiDraw.text(g, UiDraw.fit(source, tw), x + 4, y + 21, UiDraw.SMALL, t.muted());
    }

    // ---- Auto TPY -------------------------------------------------------------------------------------------------

    private void autoTpy(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        boolean on = tools.autoTpyEnabled();
        var stats = tools.autoTpyStats();
        float y = cursor, h = 34;
        cursor += h + 6;
        if (visible(y, h)) {
            boolean hover = hovered(x - 4, y, w + 8, h);
            UiDraw.rounded(g, x - 4, y, w + 8, h, 7, UiDraw.mix(UiDraw.alpha(t.mantle(), .8f), t.surface(), hover ? .6f : 0));
            if (on) UiDraw.outline(g, x - 4, y, w + 8, h, 7, .7f, UiDraw.alpha(t.accent(), .5f));
            UiDraw.icon(g, "check", x + 4, y + h / 2 - 4, 8, on ? t.accent() : t.muted());
            UiDraw.bold(g, "Accept automatically", x + 18, y + 11, UiDraw.TEXT, t.text());
            int roles = tools.autoTpyRoleIds().size();
            String rule = !on ? "Off · requests wait for you in Teleports" : roles > 0 ? "On · accepting synced players with " + roles + " selected role" + (roles == 1 ? "" : "s")
                : tools.autoTpyWhitelistOnly() ? "On · accepting the whitelist" + (tools.autoTpyAllowsFriends() ? " and friends" : "") : "On · accepting anyone who isn't blocklisted";
            UiDraw.text(g, UiDraw.fit(rule, w - 50), x + 18, y + 23, UiDraw.SMALL, t.muted());
            UiDraw.toggle(g, x + w - 22, y + h / 2 - 5, UiAnim.smooth("ct.auto.on", on ? 1 : 0, 16), hover, t);
            hit(x - 4, y, w + 8, h, () -> tools.autoTpyEnabled(!tools.autoTpyEnabled()));
        }
        float ky = cursor, kh = 28, kw = (w - 18) / 4;
        cursor += kh + PAD;
        if (visible(ky, kh)) {
            String[] values = {String.valueOf(stats.live()), String.valueOf(stats.sent()), String.valueOf(stats.overflow()), tools.autoTpyIntervalMs() + " ms"};
            String[] labels = {"Queued", "Accepted", "Overflowed", "Between accepts"};
            for (int i = 0; i < 4; i++) {
                float kx = x + i * (kw + 6);
                UiDraw.rounded(g, kx, ky, kw, kh, 6, UiDraw.alpha(t.mantle(), .8f));
                UiDraw.bold(g, values[i], kx + 7, ky + 10, UiDraw.TEXT, t.text());
                UiDraw.text(g, UiDraw.fit(labels[i], kw - 10), kx + 7, ky + 21, UiDraw.SMALL, t.muted());
            }
        }
        segmented(g, t, "ct.auto.section", new String[] {"Queue", "Roles", "Lists", "Timing"}, autoSection, choice -> {
            autoSection = choice; scroll = 0; importRevision++; placeInputs();
        });
        switch (autoSection) {
            case 1 -> autoRoles(g, t);
            case 2 -> autoLists(g, t);
            case 3 -> autoTiming(g, t);
            default -> autoQueue(g, t, stats);
        }
    }

    private void autoQueue(GuiGraphics g, UiTheme t, AutoTpyQueue.Stats stats) {
        toggleRow(g, t, "Requests to teleport to you", "Let accepted players arrive where you are", tools.autoTpyToMe(), tools::toggleAutoTpyToMe);
        toggleRow(g, t, "Requests to teleport you to them", "Let accepted players move you to them", tools.autoTpyToThem(), tools::toggleAutoTpyToThem);
        cursor += PAD;
        caption(g, t, "Live requests", stats.overflow() > 0 ? stats.overflow() + " overflowed" : null, stats.live() > 0 ? "Clear all" : null, tools::clearAutoTpy);
        var live = tools.autoTpyEntries().stream().limit(64).toList();
        if (live.isEmpty()) emptyRow(g, t, "No requests waiting", stats.latest().isEmpty() ? "Eligible requests appear here while they're accepted." : stats.latest());
        for (var entry : live) {
            menuRow(g, t, entry.player() + (entry.friend() ? " · Friend" : ""), "Queued · click to cancel", false,
                () -> tools.cancelAutoTpy(entry.player()));
        }
        if (stats.live() > 64) emptyRow(g, t, "Showing the first 64 live requests", stats.live() + " requests are still tracked.");
        cursor += PAD;
    }

    private void autoRoles(GuiGraphics g, UiTheme t) {
        var roles = tools.autoTpyAvailableRoles();
        var selected = tools.autoTpyRoleIds();
        caption(g, t, "Discord roles", selected.isEmpty() ? "no filter" : selected.size() + " selected · " + tools.autoTpyRoleMemberCount() + " players",
            selected.isEmpty() ? null : "Clear filter", tools::clearAutoTpyRoles);
        if (roles.isEmpty()) {
            emptyRow(g, t, "No Discord roles yet", "Sync Discord (Clan → Sync now) to choose roles that may use Auto TPY.");
            return;
        }
        noteRow(g, t, "How the role filter works", "With roles selected, only synced players holding one of them are accepted. The blocklist still wins. With none selected, the whitelist settings apply.");
        for (DiscordRoles.Role role : roles)
            toggleRow(g, t, role.name(), "Role " + role.id(), selected.contains(role.id()), () -> tools.toggleAutoTpyRole(role.id()));
        cursor += PAD;
    }

    private void autoLists(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        if (!tools.autoTpyRoleIds().isEmpty()) noteRow(g, t, "Role filter active", "These whitelist and friend preferences apply again when the role filter is cleared. Blocklisted players are always declined.");
        toggleRow(g, t, "Whitelist only", "Off accepts anyone who isn't blocklisted", tools.autoTpyWhitelistOnly(), tools::toggleAutoTpyWhitelistOnly);
        toggleRow(g, t, "Always accept friends", "Argus friends skip the whitelist; the blocklist always wins", tools.autoTpyAllowsFriends(), tools::toggleAutoTpyFriends);
        cursor += PAD;
        segmented(g, t, "ct.auto.list", new String[] {"Whitelist · " + tools.autoTpyWhitelist().size(), "Blocklist · " + tools.autoTpyBlocklist().size()},
            blocklistEditing ? 1 : 0, choice -> { blocklistEditing = choice == 1; importRevision++; listField.setValue(""); scroll = 0; });
        float y = cursor;
        cursor += 20 + PAD;
        var actions = new Actions(g, t, x + w, y + 1);
        actions.add("Add", "plus", Tone.PRIMARY, !ChatClan.typed(listField.getValue()).names().isEmpty(), this::addListNames);
        if (!blocklistEditing) actions.add(importBusy ? "Importing…" : "Import CSV", null, Tone.SECONDARY, !importBusy, () -> importNames(null, true));
        listField.place(Math.round(x), Math.round(y) + 4, Math.round(actions.left() - GAP - x));
        if (visible(y, 20)) listField.render(g, mouseX, mouseY, 0);
        String typed = listField.getValue().strip().toLowerCase(Locale.ROOT);
        var names = blocklistEditing ? tools.autoTpyBlocklist() : tools.autoTpyWhitelist();
        var matches = names.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(typed)).limit(100).toList();
        if (matches.isEmpty()) emptyRow(g, t, names.isEmpty() ? "This list is empty" : "No matching names", "Type usernames to add. Click a name to remove it. Dropping a .csv file also imports.");
        float listBase = cursor;
        for (String name : matches) glide(g, "ct.wl." + blocklistEditing, name, listBase, () -> menuRow(g, t, name, "Remove", false, () -> {
            if (blocklistEditing) tools.setAutoTpyBlocklist(ChatClan.remove(tools.autoTpyBlocklist(), name));
            else tools.setAutoTpyWhitelist(ChatClan.remove(tools.autoTpyWhitelist(), name));
            importRevision++;
        }));
        if (names.size() > 100) emptyRow(g, t, names.size() + " names saved", "Search to find names beyond the first 100.");
        cursor += PAD;
    }

    private void addListNames() {
        var typed = ChatClan.typed(listField.getValue());
        var old = blocklistEditing ? tools.autoTpyBlocklist() : tools.autoTpyWhitelist();
        var updated = ChatClan.add(old, typed.names());
        if (blocklistEditing) tools.setAutoTpyBlocklist(updated); else tools.setAutoTpyWhitelist(updated);
        importRevision++;
        listField.setValue("");
        hostFocus(listField);
        notice("Added " + (updated.size() - old.size()) + " name" + (updated.size() - old.size() == 1 ? "" : "s") + (typed.invalid() > 0 ? "; skipped " + typed.invalid() + " invalid." : "."));
    }

    private void autoTiming(GuiGraphics g, UiTheme t) {
        toggleRow(g, t, "Adapt to the server", "Learn from confirmed replies; rate limits pause sending", tools.autoTpyAdaptive(), tools::toggleAutoTpyAdaptive);
        menuRow(g, t, "Reset learned speed", "Back to the configured interval", false, () -> { tools.resetAutoTpyLearning(); notice("Learned speed reset."); });
        cursor += PAD;
        noteRow(g, t, "Pacing and buffer", "Intervals and the buffer size are in Settings, in the Auto TPY card.");
        menuRow(g, t, "Open Auto TPY settings", null, true, () -> select(Page.SETTINGS));
    }

    // ---- Server ---------------------------------------------------------------------------------------------------

    private void server(GuiGraphics g, UiTheme t) {
        float x = contentX(), w = contentWidth();
        if (serverPath.isEmpty() && tools.commandAvailable("hotspot")) {
            float y = cursor;
            cursor += CONTROL + PAD;
            if (visible(y, CONTROL)) {
                new Actions(g, t, x + w, y)
                    .add("Refresh", "reset", Tone.GHOST, true, tools::refreshHotspot)
                    .add("End hotspot", null, Tone.DANGER, true, () -> confirm("End your hotspot?", "Everyone visiting is sent away. You can't undo this.",
                        "End hotspot", "/hotspot kill", () -> command("/hotspot kill")));
                button(g, t, "Go to hotspot", "star", x, y, buttonWidth("Go to hotspot", "star"), Tone.PRIMARY, true, () -> command("/hotspot"));
            }
            caption(g, t, "Hotspot", null, null, null);
            var details = tools.hotspotDetails();
            if (details.isEmpty()) emptyRow(g, t, "Waiting for details", "Refresh to ask the server for the latest hotspot information.");
            else {
                List<String> lines = new ArrayList<>();
                for (String line : details) lines.addAll(UiDraw.wrap(line, w - 12, UiDraw.SMALL));
                float hy = cursor, hh = lines.size() * 9 + 10;
                cursor += hh + PAD;
                if (visible(hy, hh)) {
                    UiDraw.rounded(g, x - 4, hy, w + 8, hh, 6, UiDraw.alpha(t.mantle(), .8f));
                    for (int i = 0; i < lines.size(); i++) UiDraw.text(g, lines.get(i), x + 4, hy + 9 + i * 9, UiDraw.SMALL, t.muted());
                }
            }
        }
        // Going into a submenu slides the list in from the right; going back slides it in from the left.
        if (serverPath.size() != serverDepth) { serverDir = serverPath.size() > serverDepth ? 1 : -1; serverDepth = serverPath.size(); serverDepthChanged = System.nanoTime(); }
        float slide = serverDepthChanged == 0 ? 1 : UiAnim.eased(serverDepthChanged, .22f), slideBefore = UiRender.opacity;
        UiRender.opacity = slideBefore * slide;
        g.pose().pushMatrix();
        g.pose().translate(serverDir * (1 - slide) * 14, 0);
        try {
        List<ChatServerSettings.Entry> entries = serverPath.isEmpty()
            ? ChatServerSettings.available(ChatServerSettings.groups(), this::commandAvailable) : serverPath.getLast().children();
        if (!serverPath.isEmpty()) {
            float y = cursor;
            cursor += CONTROL + PAD;
            if (visible(y, CONTROL)) {
                iconButton(g, t, "chevron_left", x, y, CONTROL, t.muted(), "Back (Esc)", () -> serverPath.removeLast());
                List<String> trail = new ArrayList<>();
                for (var entry : serverPath) trail.add(entry.label());
                UiDraw.bold(g, UiDraw.fit(String.join(" / ", trail), w - 30), x + 24, y + CONTROL / 2, UiDraw.TEXT, t.text());
            }
        } else caption(g, t, "Server settings", null, null, null);
        var shown = entries.stream().filter(entry -> !("/homecancel".equals(entry.command()) && !tools.canCancelHome())
            && !("/tpacancel".equals(entry.command()) && !tools.canCancelTpa())).toList();
        if (shown.isEmpty()) emptyRow(g, t, "No controls available", "This server hasn't offered settings for this category.");
        for (var entry : shown) menuRow(g, t, entry.label(), entry.menu() ? null : entry.command().strip(), entry.menu(), () -> {
            if (entry.menu()) { serverPath.add(entry); scroll = 0; }
            else if (entry.needsInput()) request(entry.label(), "Value", entry.command(), "Send");
            else if (entry.command().equals("/tpacancel")) tools.cancelTpa();
            else if (entry.command().equals("/homecancel")) tools.cancelHome();
            else command(entry.command());
        });
        } finally { g.pose().popMatrix(); UiRender.opacity = slideBefore; }
        cursor += PAD;
    }

    // ---- dialog ---------------------------------------------------------------------------------------------------

    private void dialogBox(GuiGraphics g, int mx, int my, float delta, UiTheme t) {
        Dialog dialog = shownDialog;
        boolean live = this.dialog != null;
        dialogDrawing = true;
        float before = UiRender.opacity, shown = UiAnim.easeOutCubic(dialogShown);
        g.pose().pushMatrix();
        try {
            UiDraw.rect(g, 0, 0, width, height, UiDraw.alpha(0xff000000, .35f * shown));
            UiRender.opacity = before * shown;
            float w = Math.min(300, panelWidth - 40), x = (width - w) / 2;
            List<String> description = dialog.description() == null ? List.of() : UiDraw.wrap(dialog.description(), w - 28, UiDraw.SMALL);
            String preview = dialog.preview() == null ? null : dialog.preview().apply(dialogField.getValue().strip());
            String problem = dialogTouched ? dialogProblem() : null;
            // The box eases to a new height when a preview or problem line appears.
            float h = UiAnim.smooth("ct.dialog.h", 34 + description.size() * 9 + (dialog.field() != null ? 34 : 0) + (preview != null ? 14 : 0) + (problem != null ? 12 : 0) + 34, 18);
            float y = (height - h) / 2;
            float scale = UiAnim.lerp(.96f, 1, shown);
            g.pose().translate(width / 2f, height / 2f);
            g.pose().scale(scale, scale);
            g.pose().translate(-width / 2f, -height / 2f);
            UiDraw.shadow(g, x, y, w, h, 10, 14, UiDraw.alpha(0xff000000, .4f));
            UiDraw.rounded(g, x, y, w, h, 10, t.base());
            UiDraw.outline(g, x, y, w, h, 10, .7f, UiDraw.alpha(t.overlay(), .7f));
            float cy = y + 18;
            UiDraw.bold(g, UiDraw.fit(dialog.title(), w - 28), x + 14, cy, UiDraw.TITLE, t.text());
            cy += 14;
            for (String line : description) { UiDraw.text(g, line, x + 14, cy, UiDraw.SMALL, t.muted()); cy += 9; }
            if (dialog.field() != null) {
                cy += 4;
                UiDraw.text(g, dialog.field(), x + 14, cy, UiDraw.SMALL, t.muted());
                dialogField.place(Math.round(x + 14), Math.round(cy + 10), Math.round(w - 28));
                if (live) dialogField.render(g, mx, my, delta);
                cy += 30;
            }
            if (preview != null) { UiDraw.text(g, UiDraw.fit(preview, w - 28), x + 14, cy + 4, UiDraw.SMALL, UiDraw.alpha(t.accent(), .9f)); cy += 14; }
            if (problem != null) { UiDraw.text(g, UiDraw.fit(problem, w - 28), x + 14, cy + 3, UiDraw.SMALL, t.red()); cy += 12; }
            float by = y + h - CONTROL - 10;
            float cw = buttonWidth(dialog.confirm(), null);
            button(g, t, dialog.confirm(), null, x + w - 14 - cw, by, cw, dialog.danger() ? Tone.DANGER : Tone.PRIMARY, live, this::confirmDialog);
            float cancel = buttonWidth("Cancel", null);
            button(g, t, "Cancel", null, x + w - 14 - cw - GAP - cancel, by, cancel, Tone.GHOST, live, this::closeDialog);
        } finally { dialogDrawing = false; g.pose().popMatrix(); UiRender.opacity = before; }
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (dialog != null) {
            if (dialog.field() != null && dialogField.isMouseOver(mx, my)) { hostFocus(dialogField); return dialogField.mouseClicked(event, doubled); }
            if (event.button() == 0) for (int i = dialogHits.size() - 1; i >= 0; i--) {
                Hit hit = dialogHits.get(i);
                if (UiDraw.over(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) { hit.action().run(); return true; }
            }
            return true;
        }
        if (event.button() == 0) {
            List<Page> shown = pages();
            for (int i = 0; i < shown.size(); i++)
                if (UiDraw.over(mx, my, left + 8, itemY(i), SIDE, ITEM)) { if (shown.get(i) != page || recipient != null) select(shown.get(i)); return true; }
        }
        if (page == Page.SETTINGS && settingsPage != null && settingsPage.mouseClicked(event, doubled)) return true;
        for (UiInput input : List.of(composer, search, listField))
            if (children().contains(input) && input.isMouseOver(mx, my)) { hostFocus(input); return input.mouseClicked(event, doubled); }
        if (event.button() == 0) for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (UiDraw.over(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) { hit.action().run(); return true; }
        }
        if (overTranscript(mx, my)) {
            long id = lineAt(my);
            if (event.button() == 1 && id >= 0) { copyLine(id); return true; }
            if (event.button() == 0) {
                // A click opens Chat (from Overview) or picks a line; movement turns it into a drag that selects.
                transcriptPressed = true; transcriptDragged = false; pressX = mx; pressY = my;
                if ((event.modifiers() & 1) != 0 && selectAnchor >= 0 && id >= 0) { selectEnd = id; transcriptDragged = true; }
                return true;
            }
        } else if (event.button() == 0 && selectAnchor >= 0) clearSelection();
        return super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (transcriptPressed) {
            if (!transcriptDragged && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) {
                transcriptDragged = true;
                selectAnchor = lineAt(pressY);
            }
            if (transcriptDragged && selectAnchor >= 0) {
                selectEnd = lineAt(event.y());
                // Dragging past the edge scrolls to reach more lines.
                var area = transcriptArea;
                if (area != null) {
                    float edge = event.y() < area.y() ? 6 : event.y() > area.y() + area.h() ? -6 : 0;
                    if (area.dock()) dockScroll = Math.max(0, dockScroll + edge); else chatScroll = Math.max(0, chatScroll + edge);
                }
            }
            return true;
        }
        if (dialog == null && page == Page.SETTINGS && settingsPage != null && settingsPage.mouseDragged(event)) return true;
        return super.mouseDraggedUi(event, dx, dy);
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) {
        if (transcriptPressed) {
            transcriptPressed = false;
            if (!transcriptDragged) {
                if (page == Page.OVERVIEW) openChatPage();
                else {
                    // A click picks one line, or clears it when it was the only one picked.
                    long id = lineAt(pressY);
                    if (id >= 0 && selectAnchor == id && selectEnd == id) clearSelection(); else { selectAnchor = selectEnd = id; }
                }
            }
            return true;
        }
        if (dialog == null && page == Page.SETTINGS && settingsPage != null && settingsPage.mouseReleased()) return true;
        return super.mouseReleasedUi(event);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (dialog != null) return true;
        if (page == Page.SETTINGS) return settingsPage != null && settingsPage.mouseScrolled(mx, my, vertical);
        float amount = (float) vertical * 30;
        if (page == Page.OVERVIEW && overTranscript(mx, my)) { dockScroll = Math.max(0, dockScroll + amount); return true; }
        if (!UiDraw.over(mx, my, contentX() - 6, bodyTop(), contentWidth() + 12, bodyBottom() - bodyTop())) return false;
        if (page == Page.CHAT) chatScroll = Math.max(0, chatScroll + amount);
        else if (page == Page.MESSAGES && recipient != null) conversationScroll = Math.max(0, conversationScroll + amount);
        else scroll = Math.max(0, scroll - amount);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean enter = key == 257 || key == 335, escape = key == 256;
        if (dialog != null) {
            if (escape) { closeDialog(); return true; }
            if (enter) { confirmDialog(); return true; }
            return dialogField.keyPressed(event) || true;
        }
        if (page == Page.SETTINGS && settingsPage != null && settingsPage.keyPressed(event)) return true;
        // Ctrl+C copies selected chat lines, unless text is highlighted in a field.
        if (key == 67 && (event.modifiers() & 2) != 0 && !selectedLines().isEmpty()
            && !(getFocused() instanceof UiInput input && !input.getHighlighted().isEmpty())) { copySelection(); return true; }
        if (escape && selectAnchor >= 0) { clearSelection(); return true; }
        if (enter && getFocused() == composer) { sendComposer(); return true; }
        if (enter && getFocused() == listField) { addListNames(); return true; }
        if (enter && getFocused() == search) {
            String name = search.getValue().strip();
            if (ChatMessages.validName(name)) selectedPlayer = name;
            return true;
        }
        if (escape && page == Page.MESSAGES && recipient != null) { saveDraft(); recipient = null; composer.setValue(drafts.getOrDefault("", "")); placeInputs(); return true; }
        if (escape && page == Page.SERVER && !serverPath.isEmpty()) { serverPath.removeLast(); return true; }
        return super.keyPressed(event);
    }

    @Override protected String activeSection() { return null; }

    @Override public void removed() { saveDraft(); super.removed(); }
}

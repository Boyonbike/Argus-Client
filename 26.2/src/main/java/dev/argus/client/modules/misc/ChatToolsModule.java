package dev.argus.client.modules.misc;

import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.argus.client.ClientContext;
import dev.argus.client.chat.AutoTpyController;
import dev.argus.client.chat.AutoTpyPolicy;
import dev.argus.client.chat.AutoTpyQueue;
import dev.argus.client.chat.ChatClan;
import dev.argus.client.chat.ChatCommandTemplate;
import dev.argus.client.chat.ChatCountdowns;
import dev.argus.client.chat.ChatFriendHighlight;
import dev.argus.client.chat.ChatHomeEdit;
import dev.argus.client.chat.ChatHomeFavorites;
import dev.argus.client.chat.ChatHomeNames;
import dev.argus.client.chat.ChatMessages;
import dev.argus.client.chat.ChatModuleLink;
import dev.argus.client.chat.ChatPublicQueue;
import dev.argus.client.chat.ChatRecentPlayers;
import dev.argus.client.chat.ChatSavedCooldowns;
import dev.argus.client.chat.ChatSender;
import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.chat.ChatToolsStore;
import dev.argus.client.chat.ChatTpaRequests;
import dev.argus.client.chat.ChatWhisperQueue;
import dev.argus.client.chat.DiscordIgn;
import dev.argus.client.chat.DiscordRoles;
import dev.argus.client.events.ChatMessageEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.gui.mixin.EntityArgumentAccessor;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.KeyBindSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.settings.options.SocialRole;
import dev.argus.client.events.InputPressEvent;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Messages, contacts, clan, homes, teleport requests, Auto TPY and server settings in one place. The logic lives in
 * {@code dev.argus.client.chat}; this module feeds it chat lines and packets, sends what it asks for, and keeps its
 * data in {@link ChatToolsStore} so settings presets never swap out homes or contacts.
 *
 * <p>Friends are Argus friends ({@code SocialRegistry}); friend and enemy colours in chat belong to BetterChat, so this
 * module only colours clan members.
 */
public final class ChatToolsModule extends Module {
    private static final Pattern HOME_CLICK = Pattern.compile("^/(?:[a-z0-9_.-]+:)?home\\s+\\S+\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final SystemToast.SystemToastId DM_TOAST = new SystemToast.SystemToastId(5_000);
    public static final List<String> DEFAULT_COMMAND_OPTIONS =
        List.of("Message | /msg {player} ", "Whisper | /w {player} ", "Teleport Request | /tpa {player}");
    private static final int HIGHLIGHT_REFRESH_TICKS = 20;
    private static final long SAVE_EVERY_MS = 5_000;
    public static final int CHAT_LINES = 300, LONG_CHAT_LINES = 1000;

    // ---- settings -------------------------------------------------------------------------------------------------

    private final KeyBindSetting openKey = add(new KeyBindSetting("Open window key", "Not bound", v -> true));
    private final BooleanSetting detectCommands = add(new BooleanSetting("Detect player commands", true));
    private final BooleanSetting runOnClick = add(new BooleanSetting("Run complete commands", false));
    private final NumberSetting<Integer> requestTimeout = add(new NumberSetting<>("Request timeout", 120, 5, 3600).withUnit("s"));

    private final Setting<Boolean> messagesGroup = group("Messages");
    private final BooleanSetting hideMessages = add(new BooleanSetting("Hide private messages", false));
    private final BooleanSetting messageToasts = add(new BooleanSetting("Message notifications", true));
    private final StringSetting messageCommand = add(new StringSetting("Message command", "/msg {player}"));
    private final BooleanSetting queueChat = add(new BooleanSetting("Queue chat", true));
    private final NumberSetting<Integer> chatCooldown = add(new NumberSetting<>("Chat cooldown", 5000, 0, 30000).withUnit("ms"));
    private final NumberSetting<Integer> whisperCooldown = add(new NumberSetting<>("Message cooldown", 2500, 0, 30000).withUnit("ms"));

    private final Setting<Boolean> teleportsGroup = group("Teleports");
    // Each kind has its own length. Manual pins yours; otherwise it's learned from the server's "wait" replies.
    private final BooleanSetting homeManual = add(new BooleanSetting("Manual home cooldown", false));
    private final NumberSetting<Integer> homeCooldown = add(new NumberSetting<>("Home cooldown", 30, 0, 3600, value -> homeManual.getValue()).withUnit("s"));
    private final BooleanSetting tpaManual = add(new BooleanSetting("Manual teleport cooldown", false));
    private final NumberSetting<Integer> tpaCooldown = add(new NumberSetting<>("Teleport cooldown", 30, 0, 3600, value -> tpaManual.getValue()).withUnit("s"));

    private final Setting<Boolean> autoTpyGroup = group("Auto TPY");
    private final BooleanSetting autoTpyEnabled = add(new BooleanSetting("Accept automatically", false));
    private final BooleanSetting autoWhitelistOnly = autoTpy(new BooleanSetting("Whitelist only", true));
    private final BooleanSetting autoAllowFriends = autoTpy(new BooleanSetting("Allow friends", false));
    private final BooleanSetting autoToMe = autoTpy(new BooleanSetting("Accept teleports to me", true));
    private final BooleanSetting autoToThem = autoTpy(new BooleanSetting("Accept teleports to them", false));
    private final NumberSetting<Integer> autoInterval = autoTpy(new NumberSetting<>("Accept interval", 500, 0, 60000).withUnit("ms"));
    private final BooleanSetting autoAdaptive = autoTpy(new BooleanSetting("Adaptive pacing", false));
    private final NumberSetting<Integer> autoMinimum = autoTpy(new NumberSetting<>("Fastest interval", 50, 0, 60000).withUnit("ms"));
    private final NumberSetting<Integer> autoMaximum = autoTpy(new NumberSetting<>("Slowest interval", 5000, 0, 60000).withUnit("ms"));
    private final NumberSetting<Integer> autoCapacity = autoTpy(new NumberSetting<>("Buffer limit", 1024, 64, 16384));
    private final NumberSetting<Integer> autoInFlight = autoTpy(new NumberSetting<>("Awaiting replies", 4, 1, 32));
    private final NumberSetting<Integer> autoAttempts = autoTpy(new NumberSetting<>("Max attempts", 3, 1, 10));
    private final NumberSetting<Integer> autoReplyTimeout = autoTpy(new NumberSetting<>("Reply timeout", 3000, 500, 30000).withUnit("ms"));
    private final NumberSetting<Integer> autoBackoff = autoTpy(new NumberSetting<>("Retry backoff", 1000, 0, 30000).withUnit("ms"));

    private final Setting<Boolean> clanGroup = group("Clan");
    private final BooleanSetting highlightClanChat = add(new BooleanSetting("Highlight clan in chat", true));
    private final ColorSetting clanChatColor = add(new ColorSetting("Clan chat colour", new Color(255, 170, 60), v -> highlightClanChat.getValue()));
    private final BooleanSetting highlightClanTab = add(new BooleanSetting("Highlight clan in tab", true));
    private final ColorSetting clanTabColor = add(new ColorSetting("Clan tab colour", new Color(255, 170, 60), v -> highlightClanTab.getValue()));
    private final BooleanSetting highlightClanRows = add(new BooleanSetting("Highlight clan rows", false));
    private final ColorSetting clanRowColor = add(new ColorSetting("Clan row colour", new Color(255, 170, 60, 96), v -> highlightClanRows.getValue()));

    /** A card header on the settings page; the settings after it, up to the next header, are its members. */
    private Setting<Boolean> group(String name) {
        Setting<Boolean> header = add(new BooleanSetting(name, false)).markAsGroup();
        header.setExcludedFromConfig(true);
        return header;
    }

    private <S extends Setting<?>> S autoTpy(S setting) {
        add(setting);
        setting.dependsOn(autoTpyEnabled);
        return setting;
    }

    // ---- hooks other parts of the client fill in ------------------------------------------------------------------

    /** The chat window, attached by the GUI. */
    public interface Window {
        boolean showing();
        boolean reading(String player);
        void open(String player);
        void close();
    }
    /** The Discord IGN sync, attached by its module: the relay it is set to and its current problem, if any. */
    public interface DiscordSync {
        String configuredRelay();
        String problem();
    }
    private Window window;
    private DiscordSync discordSync;
    public void attachWindow(Window window) { this.window = window; }
    public void attachDiscordSync(DiscordSync sync) { this.discordSync = sync; refreshDiscordCache(); syncAutoTpy(); }

    // ---- state ----------------------------------------------------------------------------------------------------

    private final Minecraft mc = Minecraft.getInstance();
    private final ChatToolsStore store;
    private final String storeProblem;
    private long nextSave;

    private final ChatMessages messages = new ChatMessages();
    private final ChatRecentPlayers recentPlayers = new ChatRecentPlayers();
    private final ChatWhisperQueue whispers = new ChatWhisperQueue();
    private final ChatPublicQueue chats = new ChatPublicQueue();
    private final ChatTeleportState teleports = new ChatTeleportState();
    private final ChatTpaRequests incomingRequests = new ChatTpaRequests();
    private final AutoTpyController autoTpy = new AutoTpyController();
    private final List<String> hotspotDetails = new ArrayList<>();
    private final List<String> discoveredHomes = new ArrayList<>();
    private final List<String> suggestedHomes = new ArrayList<>();
    private final ArrayDeque<ChatLine> chatLines = new ArrayDeque<>();
    private long chatLineId;
    private String replyPlayer;
    private long hotspotReadingUntil;
    private Connection session;
    private Map<String, List<DiscordRoles.Role>> autoRoleRoster;
    private String autoRoleSource;
    private Set<String> autoRoleSelection = Set.of();
    private Set<String> autoRoleMembers = Set.of();
    private List<DiscordRoles.Role> autoAvailableRoles = List.of();
    private String autoAccount;
    private long autoTick;
    private volatile int homeRequest;
    private boolean homesRequested;
    private int nextHomeRequest = -1_400_000_000;
    private long homeRequestedAt;
    private ChatHomeEdit pendingHomeEdit;
    private long homeEditExpires;
    private Set<String> clanNames = Set.of();
    private int clanColor;
    private boolean clanInChat;
    private int highlightRefreshTicks;
    private String savedKey, recentKey;
    private ChatSavedCooldowns.Saved saved = ChatSavedCooldowns.Saved.NONE;
    private String cachedRoleData, cachedUsernameData;
    private Map<String, List<DiscordRoles.Role>> cachedRoles = Map.of();
    private Map<String, String> cachedUsernames = Map.of();
    /** Set by a press of the window key and handled on the next tick, so even a tap shorter than a tick is caught. */
    private boolean openRequested;
    /** How far (squared, in blocks) the server must move the player for it to count as a teleport. */
    private static final double TELEPORT_JUMP_SQ = 16 * 16;
    private Waiting waitingNow = new Waiting(0, 0, null);

    public ChatToolsModule() {
        super("ChatTools", "Messages, friends, clan, homes, teleport requests and Auto TPY in one window.", ModuleCategory.MISC,
            "chat", "better chat", "dm", "messages", "tpa", "homes", "clan");
        ChatToolsStore loaded;
        String problem = null;
        try { loaded = new ChatToolsStore(dataFile()); }
        catch (IOException | RuntimeException failure) {
            loaded = ChatToolsStore.inMemory();
            problem = "Chat data couldn't be read, so changes this session won't be saved: " + failure.getMessage();
            logger.warn("[Argus] {}", problem, failure);
        }
        store = loaded;
        storeProblem = problem;
        if (!store.has(ChatToolsStore.Lists.COMMAND_OPTIONS)) store.set(ChatToolsStore.Lists.COMMAND_OPTIONS, DEFAULT_COMMAND_OPTIONS);
        refreshMessagePatterns();
        for (var setting : List.of(autoTpyEnabled, autoWhitelistOnly, autoAllowFriends, autoToMe, autoToThem, autoAdaptive))
            setting.setChangeCallback(this::syncAutoTpy);
        for (var setting : List.of(autoInterval, autoCapacity, autoInFlight, autoAttempts, autoReplyTimeout, autoBackoff))
            setting.setChangeCallback(this::syncAutoTpy);
        autoMinimum.setChangeCallback(() -> { autoBounds(true); syncAutoTpy(); });
        autoMaximum.setChangeCallback(() -> { autoBounds(false); syncAutoTpy(); });
        for (var setting : List.of(homeManual, tpaManual, homeCooldown, tpaCooldown)) setting.setChangeCallback(this::refreshCooldowns);
    }

    private static Path dataFile() { return FabricLoader.getInstance().getGameDir().resolve("argusclient/chat.json"); }

    public static ChatToolsModule get() {
        return ClientContext.moduleRegistry == null ? null : ClientContext.getModule(ChatToolsModule.class);
    }

    /** Why chat data isn't being saved, or null when it is. */
    public String storeProblem() { return storeProblem; }
    /** The chat data, for importing from another client. Call {@link #dataImported()} after changing it. */
    public ChatToolsStore store() { return store; }
    /** Re-reads everything cached from the store after a successful import. */
    public void dataImported() {
        refreshMessagePatterns();
        recentKey = null;
        savedKey = null;
        refreshDiscordCache();
        refreshHighlights();
        syncAutoTpy();
    }

    // ---- BetterChat, the hidden engine ----------------------------------------------------------------------------

    private static final String MERGED_KEY = "MergedBetterChat";
    /** Whether an earlier session already carried BetterChat's on/off state over to this module. */
    private boolean mergedBetterChat;
    private final ChatModuleLink link = new ChatModuleLink();

    @Override public JsonElement toJson() {
        JsonElement json = super.toJson();
        if (json != null && json.isJsonObject()) json.getAsJsonObject().addProperty(MERGED_KEY, mergedBetterChat);
        return json;
    }
    @Override public void fromJson(JsonElement json) {
        // A partial preset without the flag never undoes the merge.
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has(MERGED_KEY)
            && json.getAsJsonObject().get(MERGED_KEY).getAsBoolean()) mergedBetterChat = true;
        super.fromJson(json);
        migrateCooldowns(json);
    }

    /**
     * Before the Manual toggles existed, these lengths were only a fallback. A length someone changed from the old
     * default of 30s was a deliberate choice, so it carries over as their manual length.
     */
    private void migrateCooldowns(JsonElement json) {
        if (json == null || !json.isJsonObject() || !(json.getAsJsonObject().get("settings") instanceof com.google.gson.JsonObject settings)) return;
        if (settings.has(homeManual.getConfigName()) || settings.has(tpaManual.getConfigName())) return;
        if (settings.has(homeCooldown.getConfigName()) && homeCooldown.getValue() != 30) homeManual.setValue(true);
        if (settings.has(tpaCooldown.getConfigName()) && tpaCooldown.getValue() != 30) tpaManual.setValue(true);
    }

    /** Runs every client tick, whether or not either module is on, so both always switch together. */
    public static void syncBetterChat() {
        ChatToolsModule tools = get();
        BetterChatModule betterChat = ClientContext.moduleRegistry == null ? null : ClientContext.getModule(BetterChatModule.class);
        if (tools == null || betterChat == null) return;
        boolean wasMerged = tools.mergedBetterChat;
        var decision = tools.link.decide(wasMerged, tools.isToggled(), betterChat.isToggled());
        tools.mergedBetterChat = decision.merged();
        boolean changed = !wasMerged;
        if (tools.isToggled() != decision.on()) { if (decision.on()) tools.enable(); else tools.disable(); changed = true; }
        if (betterChat.isToggled() != decision.on()) { if (decision.on()) betterChat.enable(); else betterChat.disable(); changed = true; }
        if (changed && !wasMerged && ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
    }

    /** BetterChat's settings, shown on this module's page. */
    public static List<Setting<?>> betterChatSettings() {
        BetterChatModule betterChat = ClientContext.moduleRegistry == null ? null : ClientContext.getModule(BetterChatModule.class);
        return betterChat == null ? List.of() : betterChat.getSettings();
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------------

    @Override public void onEnable() {
        resetSession();
        refreshMessagePatterns();
        refreshCooldowns();
        highlightRefreshTicks = 0;
        refreshHighlights();
        syncAutoTpy();
    }

    @Override public void onDisable() {
        if (window != null && window.showing()) window.close();
        resetSession();
        clanNames = Set.of();
        clanInChat = false;
        autoTpy.enabled(false);
        if (mc.gui != null) mc.gui.getChat().rescaleChat();
        saveNow();
    }

    @EventHandler private void onTick(MinecraftTickEvent event) {
        if (openRequested) {
            openRequested = false;
            if (mc.screen == null) openWindow(null);
        }
        checkSession();
        applyCooldowns();
        waitingNow = computeWaiting();
        sendWhispers();
        sendChats();
        if (mc.player != null && mc.getConnection() != null) {
            String account = serverKey();
            if (autoAccount != null && !autoAccount.equals(account)) { autoTpy.reset(); incomingRequests.clear(); }
            autoAccount = account;
            syncAutoTpy();
            autoTick = mc.player.tickCount;
            autoTpy.tick(now(), autoTick, command -> mc.getConnection().sendCommand(withoutSlash(command)));
        }
        // Clan and colour edits are rare; a one-second refresh keeps highlighting current without per-tick work.
        if (++highlightRefreshTicks >= HIGHLIGHT_REFRESH_TICKS) {
            highlightRefreshTicks = 0;
            refreshHighlights();
        }
        if (store.dirty() && now() >= nextSave) saveNow();
    }

    /**
     * The window key works like any other bind: only in game, with no screen open. It latches the press itself rather
     * than polling whether the key is held once a tick, so a tap shorter than a tick still opens the window. Opening
     * waits for the tick so the key's typed character can't land in the new window's text field.
     */
    @EventHandler private void onInputPress(InputPressEvent event) {
        if (mc.screen == null && claimsKeyPress(event.getCode(), event.isMouse())) openRequested = true;
    }

    /**
     * The window key. When the module's own bind is the same key, the press opens the window rather than switching the
     * module off, which used to take a second press to undo.
     */
    @Override public boolean claimsKeyPress(int code, boolean mouse) {
        if (openKey.followsDefault()) return false;
        var key = openKey.getValue();
        return key.mouse() == mouse && key.code() == code;
    }

    @EventHandler private void onDisconnect(MinecraftDisconnectEvent event) {
        left();
        saveNow();
    }

    /**
     * Leaving a server, or a network moving you to another of its servers (how 6b6t completes many teleports). Messages
     * and timers survive a move; a new connection still starts a new session through {@link #checkSession()}.
     */
    private void left() {
        autoTpy.reset();
        incomingRequests.clear();
        teleports.transferred(now());
        syncSavedCooldowns();
    }

    private void saveNow() {
        nextSave = now() + SAVE_EVERY_MS;
        try { store.save(); }
        catch (IOException failure) { logger.warn("[Argus] Couldn't save chat data", failure); }
    }

    private void resetSession() {
        if (window != null && window.showing()) window.close();
        messages.clear();
        chatLines.clear();
        recentPlayers.clear();
        recentKey = null;
        whispers.clear();
        chats.clear();
        replyPlayer = null;
        hotspotDetails.clear();
        hotspotReadingUntil = 0;
        session = null;
        homeRequest = 0;
        homesRequested = false;
        discoveredHomes.clear();
        suggestedHomes.clear();
        teleports.clear();
        refreshCooldowns();
        savedKey = null;
        incomingRequests.clear();
        autoTpy.reset();
        autoAccount = null;
        pendingHomeEdit = null;
    }

    private void checkSession() {
        var listener = mc.getConnection();
        if (listener == null || listener.getConnection() == session) return;
        resetSession();
        session = listener.getConnection();
    }

    public static long now() { return System.nanoTime() / 1_000_000; }

    // ---- window ---------------------------------------------------------------------------------------------------

    /** Opens the window, on a conversation when {@code player} is a username. @return whether it opened */
    public boolean openWindow(String player) {
        if (!isToggled() || mc.player == null || mc.getConnection() == null || window == null) return false;
        checkSession();
        window.open(player != null && ChatMessages.validName(player) ? player : null);
        return true;
    }

    public ChatMessages messages() { checkSession(); return messages; }

    // ---- settings helpers -----------------------------------------------------------------------------------------

    private void refreshMessagePatterns() {
        messages.patterns(store.get(ChatToolsStore.Lists.INCOMING_PATTERNS), store.get(ChatToolsStore.Lists.OUTGOING_PATTERNS));
    }
    /** Hands the manual lengths to the tracker; a kind left on Auto uses only what it learned from the server. */
    private void refreshCooldowns() {
        teleports.manual(ChatTeleportState.Kind.Home, homeManual.getValue() ? homeCooldown.getValue() * 1_000L : null, now());
        teleports.manual(ChatTeleportState.Kind.TPA, tpaManual.getValue() ? tpaCooldown.getValue() * 1_000L : null, now());
    }
    private void autoBounds(boolean minimumChanged) {
        if (autoMinimum.getValue() > autoMaximum.getValue()) {
            if (minimumChanged) autoMaximum.setValue(autoMinimum.getValue()); else autoMinimum.setValue(autoMaximum.getValue());
        }
    }
    private void applyCooldowns() {
        whispers.minimum(whisperCooldown.getValue());
        chats.minimum(chatCooldown.getValue());
    }

    public List<String> commandOptions() { return store.get(ChatToolsStore.Lists.COMMAND_OPTIONS); }
    public void setCommandOptions(List<String> options) { store.set(ChatToolsStore.Lists.COMMAND_OPTIONS, options); }
    public List<String> incomingPatterns() { return store.get(ChatToolsStore.Lists.INCOMING_PATTERNS); }
    public List<String> outgoingPatterns() { return store.get(ChatToolsStore.Lists.OUTGOING_PATTERNS); }
    public void setMessagePatterns(List<String> incoming, List<String> outgoing) {
        store.set(ChatToolsStore.Lists.INCOMING_PATTERNS, incoming);
        store.set(ChatToolsStore.Lists.OUTGOING_PATTERNS, outgoing);
        refreshMessagePatterns();
    }
    public List<String> extraHomes() { return store.get(ChatToolsStore.Lists.HOMES); }
    public void setExtraHomes(List<String> homes) { store.set(ChatToolsStore.Lists.HOMES, homes); }

    public boolean messageNotifications() { return messageToasts.getValue(); }
    public boolean hidingMessages() { return hideMessages.getValue(); }
    public int chatCooldownMs() { return chatCooldown.getValue(); }
    public int whisperCooldownMs() { return whisperCooldown.getValue(); }
    public KeyBindSetting openKeySetting() { return openKey; }

    // ---- Auto TPY -------------------------------------------------------------------------------------------------

    private void syncAutoTpy() {
        if (autoTpy == null || autoBackoff == null || store == null) return;
        Set<String> friends = new HashSet<>(friends());
        refreshAutoTpyRoles();
        autoTpy.configure(new AutoTpyPolicy(Set.copyOf(store.get(ChatToolsStore.Lists.AUTO_TPY_WHITELIST)),
                Set.copyOf(store.get(ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST)), autoWhitelistOnly.getValue(), autoAllowFriends.getValue(),
                autoToMe.getValue(), autoToThem.getValue(), autoRoleMembers, !autoRoleSelection.isEmpty()),
            friends, autoCapacity.getValue(), autoInFlight.getValue(), autoAttempts.getValue(), autoReplyTimeout.getValue(),
            autoBackoff.getValue(), autoInterval.getValue(), autoAdaptive.getValue(), autoMinimum.getValue(), autoMaximum.getValue());
        incomingRequests.capacity(autoCapacity.getValue());
        boolean on = isToggled() && autoTpyEnabled.getValue();
        boolean starting = on && !autoTpy.enabled();
        autoTpy.enabled(on);
        if (starting) for (var request : incomingRequests.pending(now())) offerAutoTpy(request);
    }
    private void offerAutoTpy(ChatTpaRequests.Request request) {
        autoTpy.offer(new AutoTpyQueue.Request(request.player(), request.toThem(), request.expires(), acceptCommand(request)), now());
    }
    /** Role access follows the live connection, so a changed relay can't keep old permissions. */
    private void refreshAutoTpyRoles() {
        Map<String, List<DiscordRoles.Role>> roster = discordRoleRoster();
        Set<String> selected = Set.copyOf(store.get(ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS));
        String relay = configuredRelay();
        String source = DiscordRoles.source(relay);
        boolean rosterChanged = roster != autoRoleRoster;
        if (rosterChanged) autoAvailableRoles = DiscordRoles.availableRoles(roster);
        if (rosterChanged || !selected.equals(autoRoleSelection) || !source.equals(autoRoleSource)) {
            autoRoleMembers = DiscordRoles.authorizedMembers(roster, selected, store.get(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE), relay);
            autoRoleSelection = selected;
            autoRoleRoster = roster;
            autoRoleSource = source;
        }
    }
    public boolean autoTpyEnabled() { return autoTpyEnabled.getValue(); }
    public void autoTpyEnabled(boolean enabled) { autoTpyEnabled.setValue(enabled); syncAutoTpy(); }
    public boolean autoTpyToMe() { return autoToMe.getValue(); }
    public void toggleAutoTpyToMe() { autoToMe.setValue(!autoToMe.getValue()); syncAutoTpy(); }
    public boolean autoTpyToThem() { return autoToThem.getValue(); }
    public void toggleAutoTpyToThem() { autoToThem.setValue(!autoToThem.getValue()); syncAutoTpy(); }
    public boolean autoTpyWhitelistOnly() { return autoWhitelistOnly.getValue(); }
    public void toggleAutoTpyWhitelistOnly() { autoWhitelistOnly.setValue(!autoWhitelistOnly.getValue()); syncAutoTpy(); }
    public boolean autoTpyAllowsFriends() { return autoAllowFriends.getValue(); }
    public void toggleAutoTpyFriends() { autoAllowFriends.setValue(!autoAllowFriends.getValue()); syncAutoTpy(); }
    public boolean autoTpyAdaptive() { return autoAdaptive.getValue(); }
    public void toggleAutoTpyAdaptive() { autoAdaptive.setValue(!autoAdaptive.getValue()); syncAutoTpy(); }

    /** The chat log's time format: BetterChat's while its Timestamps setting is on, otherwise hours and minutes. */
    public String chatTimeFormat() {
        boolean timestamps = false;
        String format = "HH:mm";
        for (Setting<?> setting : betterChatSettings()) {
            if (setting.getName().equals("Timestamps") && setting.getValue() instanceof Boolean on) timestamps = on;
            if (setting.getName().equals("Format") && setting.getValue() instanceof Enum<?> choice)
                format = switch (choice.name()) { case "HOURS" -> "HH"; case "SECONDS" -> "HH:mm:ss"; default -> "HH:mm"; };
        }
        return timestamps ? format : "HH:mm";
    }
    public List<DiscordRoles.Role> autoTpyAvailableRoles() { refreshAutoTpyRoles(); return autoAvailableRoles; }
    public List<String> autoTpyRoleIds() { return store.get(ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS); }
    public void toggleAutoTpyRole(String id) {
        if (id == null || !id.matches("[0-9]+")) return;
        List<String> ids = new ArrayList<>(autoTpyRoleIds());
        if (ids.contains(id)) ids.removeIf(id::equals); else ids.add(id);
        store.set(ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS, ids);
        syncAutoTpy();
    }
    public void clearAutoTpyRoles() { store.set(ChatToolsStore.Lists.AUTO_TPY_ROLE_IDS, List.of()); syncAutoTpy(); }
    public int autoTpyRoleMemberCount() { refreshAutoTpyRoles(); return autoRoleMembers.size(); }
    public List<AutoTpyQueue.Entry> autoTpyEntries() { return autoTpy.queue().entries(now()); }
    public AutoTpyQueue.Stats autoTpyStats() {
        var stats = autoTpy.queue().stats(now());
        return new AutoTpyQueue.Stats(stats.live(), stats.queued(), stats.awaiting(), stats.retries(),
            stats.overflow() + incomingRequests.rejected(), stats.latest());
    }
    public long autoTpyIntervalMs() { return autoTpy.pacer().intervalMs(); }
    public void cancelAutoTpy(String player) { autoTpy.queue().cancel(player, now()); }
    public void clearAutoTpy() { autoTpy.queue().clear(now()); }
    public void resetAutoTpyLearning() { autoTpy.pacer().resetLearning(); }
    public List<String> autoTpyWhitelist() { return ChatClan.members(store.get(ChatToolsStore.Lists.AUTO_TPY_WHITELIST)); }
    public List<String> autoTpyBlocklist() { return ChatClan.members(store.get(ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST)); }
    public void setAutoTpyWhitelist(List<String> names) {
        List<String> updated = ChatClan.members(names);
        retainOwnership(ChatToolsStore.Lists.AUTO_TPY_SYNCED, autoTpyWhitelist(), updated);
        store.set(ChatToolsStore.Lists.AUTO_TPY_WHITELIST, updated);
        syncAutoTpy();
    }
    public void setAutoTpyBlocklist(List<String> names) { store.set(ChatToolsStore.Lists.AUTO_TPY_BLOCKLIST, ChatClan.members(names)); syncAutoTpy(); }
    /** The lists Discord sync owns entries in, so it can remove only the names it added itself. */
    public List<String> syncedEntries(ChatToolsStore.Lists list) { return store.get(list); }
    public void setSyncedEntries(ChatToolsStore.Lists list, List<String> names) { store.set(list, names); }
    /** Manual changes keep ownership only for entries present both before and after the edit. */
    private void retainOwnership(ChatToolsStore.Lists list, List<String> before, List<String> after) {
        Set<String> previous = ChatClan.keys(before);
        Set<String> kept = ChatClan.keys(after);
        setSyncedEntries(list, syncedEntries(list).stream().filter(name -> {
            String key = name.toLowerCase(Locale.ROOT);
            return previous.contains(key) && kept.contains(key);
        }).toList());
    }
    /** Whether the synced-name lists only hold names a sync added, so they're safe to remove again. */
    public boolean discordOwnershipTrusted() { return "1".equals(store.get(ChatToolsStore.Texts.OWNERSHIP_VERSION)); }
    public void trustDiscordOwnership() { store.set(ChatToolsStore.Texts.OWNERSHIP_VERSION, "1"); }

    // ---- friends (Argus SocialRegistry) ---------------------------------------------------------------------------

    public boolean isFriend(String player) {
        return ClientContext.socialRegistry != null && player != null && ClientContext.socialRegistry.isFriendName(player);
    }
    public boolean isEnemy(String player) {
        return ClientContext.socialRegistry != null && player != null && ClientContext.socialRegistry.isEnemyName(player);
    }
    public void toggleFriend(String player) { toggleSocial(player, SocialRole.FRIEND); }
    public void toggleEnemy(String player) { toggleSocial(player, SocialRole.ENEMY); }
    private void toggleSocial(String player, SocialRole role) {
        var social = ClientContext.socialRegistry;
        if (social == null || !ChatMessages.validName(player) || isSelf(player)) return;
        if (social.getRole(player) == role) social.removeSocial(player);
        else {
            social.removeSocial(player);
            if (role == SocialRole.FRIEND) social.addFriend(player); else social.addEnemy(player);
        }
        if (ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
        syncAutoTpy();
    }
    /** Argus friends, without yourself, alphabetically. */
    public List<String> friends() {
        List<String> names = new ArrayList<>();
        if (ClientContext.socialRegistry != null) for (Object object : ClientContext.socialRegistry.getRegistry())
            if (object instanceof SocialEntry entry && entry.role() == SocialRole.FRIEND && ChatMessages.validName(entry.name()) && !isSelf(entry.name()))
                names.add(entry.name());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    // ---- clan and Discord -----------------------------------------------------------------------------------------

    /** Clan members to list, in saved order, without yourself. */
    public List<String> clanMembers() { return savedClanMembers().stream().filter(name -> !isSelf(name)).toList(); }
    /** Every saved clan member, including yourself, for whole-list rewrites. */
    public List<String> savedClanMembers() { return ChatClan.members(store.get(ChatToolsStore.Lists.CLAN_MEMBERS)); }
    public boolean inClan(String player) { return ChatClan.contains(store.get(ChatToolsStore.Lists.CLAN_MEMBERS), player); }
    /** Adds the usernames not already in the clan and returns how many that was. */
    public int addToClan(List<String> names) {
        List<String> current = store.get(ChatToolsStore.Lists.CLAN_MEMBERS);
        List<String> updated = ChatClan.add(current, names);
        int added = updated.size() - current.size();
        if (added > 0) {
            retainOwnership(ChatToolsStore.Lists.CLAN_SYNCED, current, updated);
            store.set(ChatToolsStore.Lists.CLAN_MEMBERS, updated);
            refreshHighlights();
        }
        return added;
    }
    public void removeFromClan(String player) {
        if (!inClan(player)) return;
        store.set(ChatToolsStore.Lists.CLAN_MEMBERS, ChatClan.remove(store.get(ChatToolsStore.Lists.CLAN_MEMBERS), player));
        setSyncedEntries(ChatToolsStore.Lists.CLAN_SYNCED, ChatClan.remove(syncedEntries(ChatToolsStore.Lists.CLAN_SYNCED), player));
        refreshHighlights();
    }
    /** Replaces the whole clan list, for a sync that keeps names added by hand but removes ones it added itself. */
    public void setClanMembers(List<String> names) {
        store.set(ChatToolsStore.Lists.CLAN_MEMBERS, ChatClan.members(names));
        refreshHighlights();
    }
    public void toggleClan(String player) {
        if (inClan(player)) removeFromClan(player);
        else if (ChatMessages.validName(player) && !isSelf(player)) addToClan(List.of(player));
    }
    public ChatClan.Groups clanGroups() { return ChatClan.groups(clanMembers(), this::isFriend, this::online); }
    public DiscordRoles.Groups discordClanGroups() { return DiscordRoles.groups(clanMembers(), this::isFriend, this::online, discordRoleRoster()); }
    public String discordRoleLabel(String player) { return DiscordRoles.label(discordRoleRoster(), player); }
    public String discordUsername(String player) {
        refreshDiscordCache();
        return cachedUsernames.getOrDefault(player.toLowerCase(Locale.ROOT), "");
    }
    public void setDiscordUsernames(Map<String, String> usernames) {
        store.set(ChatToolsStore.Texts.DISCORD_USERNAMES, DiscordIgn.encodeUsernames(usernames));
    }
    public void setDiscordRoles(Map<String, List<DiscordRoles.Role>> roster, String source) {
        store.set(ChatToolsStore.Texts.DISCORD_ROLES, DiscordRoles.encode(roster));
        store.set(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE, source == null ? "" : source);
        syncAutoTpy();
    }
    /** Re-evaluate Auto TPY admission after the Discord connection changes. */
    public void refreshAutoTpyPolicy() { syncAutoTpy(); }
    private void refreshDiscordCache() {
        String roles = store.get(ChatToolsStore.Texts.DISCORD_ROLES), usernames = store.get(ChatToolsStore.Texts.DISCORD_USERNAMES);
        if (!roles.equals(cachedRoleData)) { cachedRoles = roles.isEmpty() ? Map.of() : DiscordRoles.decode(roles); cachedRoleData = roles; }
        if (!usernames.equals(cachedUsernameData)) {
            cachedUsernames = usernames.isEmpty() ? Map.of() : DiscordIgn.decodeUsernames(usernames);
            cachedUsernameData = usernames;
        }
    }
    /** Saved roles describe one relay; a different connection hides them until the next sync. */
    private Map<String, List<DiscordRoles.Role>> discordRoleRoster() {
        refreshDiscordCache();
        return store.get(ChatToolsStore.Texts.DISCORD_ROLE_SOURCE).equals(DiscordRoles.source(configuredRelay())) ? cachedRoles : Map.of();
    }
    private String configuredRelay() { return discordSync == null ? "" : discordSync.configuredRelay(); }

    // ---- recent players, online players ---------------------------------------------------------------------------

    public void rememberPlayer(String player) {
        checkSession();
        if (isSelf(player) || !syncRecentPlayers()) return;
        recentPlayers.touch(player);
        store.set(ChatToolsStore.Lists.RECENT_PLAYERS,
            ChatRecentPlayers.write(store.get(ChatToolsStore.Lists.RECENT_PLAYERS), recentKey, recentPlayers.names()));
    }
    public List<String> recentPlayers() { checkSession(); syncRecentPlayers(); return recentPlayers.names(); }
    /** Loads this server/account's saved recents into a fresh session; false until the account is known. */
    private boolean syncRecentPlayers() {
        if (mc.player == null) return false;
        String key = serverKey();
        if (!key.equals(recentKey)) {
            recentKey = key;
            recentPlayers.restore(ChatRecentPlayers.read(store.get(ChatToolsStore.Lists.RECENT_PLAYERS), key));
        }
        return true;
    }
    public List<String> onlinePlayers() {
        if (mc.getConnection() == null) return List.of();
        return mc.getConnection().getOnlinePlayers().stream().map(info -> info.getProfile().name())
            .filter(name -> ChatMessages.validName(name) && !isSelf(name)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public boolean onlineContact(String player) { return online(player); }
    public String replyPlayer() { return replyPlayer; }

    /** Server address (or singleplayer world folder) and account, so saved data never leaks between them. */
    private String serverKey() {
        String server;
        if (mc.getCurrentServer() != null) server = mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else if (mc.getSingleplayerServer() != null)
            server = "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else server = "local";
        return server + "/" + (mc.player == null ? "" : mc.player.getGameProfile().id());
    }
    public String sessionKey() { return serverKey(); }

    // ---- homes ----------------------------------------------------------------------------------------------------

    public List<String> favoriteHomes() { return ChatHomeFavorites.names(store.get(ChatToolsStore.Lists.FAVORITE_HOMES), serverKey()); }
    public boolean favoriteHome(String home) { return favoriteHomes().stream().anyMatch(name -> name.equalsIgnoreCase(home)); }
    public void toggleFavoriteHome(String home) {
        store.set(ChatToolsStore.Lists.FAVORITE_HOMES, ChatHomeFavorites.toggle(store.get(ChatToolsStore.Lists.FAVORITE_HOMES), serverKey(), home));
    }
    public List<String> homes() {
        checkSession();
        return ChatHomeNames.merge(suggestedHomes, discoveredHomes, extraHomes(), favoriteHomes());
    }
    public boolean loadingHomes() { return homeRequest != 0 && now() - homeRequestedAt < 5_000; }
    /** Asks the server once per connection, so Homes is full however the window reached it, not only after a tab click. */
    public void loadHomes() {
        checkSession();
        if (!homesRequested) refreshHomes();
    }
    public void refreshHomes() {
        checkSession();
        var listener = mc.getConnection();
        if (listener == null || loadingHomes()) return;
        homeRequest = nextHomeRequest--;
        homeRequestedAt = now();
        homesRequested = true;
        // A private transaction ID, so opening Homes never replaces chat's own tab-completion request.
        listener.send(new ServerboundCommandSuggestionPacket(homeRequest, "/home "));
    }
    private void confirmHomeEdit(String message) {
        if (pendingHomeEdit == null || now() > homeEditExpires || !pendingHomeEdit.confirmedBy(message)) return;
        ChatHomeEdit edit = pendingHomeEdit;
        pendingHomeEdit = null;
        store.set(ChatToolsStore.Lists.FAVORITE_HOMES, ChatHomeFavorites.edit(store.get(ChatToolsStore.Lists.FAVORITE_HOMES), serverKey(), edit));
        List<String> updatedDiscovered = edit.apply(discoveredHomes), updatedSuggested = edit.apply(suggestedHomes);
        discoveredHomes.clear();
        discoveredHomes.addAll(updatedDiscovered);
        suggestedHomes.clear();
        suggestedHomes.addAll(updatedSuggested);
        // New server homes belong to this connection, not to the extra names shared by every server.
        if (edit.oldName() != null) store.set(ChatToolsStore.Lists.HOMES, edit.apply(extraHomes()));
        if (edit.newName() != null) addHome(edit.newName());
        // A reply to an earlier suggestion request must not bring the old name back.
        homeRequest = 0;
        refreshHomes();
    }
    private void collectHomes(Component component) {
        ClickEvent click = component.getStyle().getClickEvent();
        String command = click instanceof ClickEvent.RunCommand run ? run.command()
            : click instanceof ClickEvent.SuggestCommand suggest ? suggest.command() : null;
        if (command != null && HOME_CLICK.matcher(command).matches()) addHome(WHITESPACE.split(command.strip())[1]);
        for (Component sibling : component.getSiblings()) collectHomes(sibling);
    }
    private void addHome(String name) {
        String valid = ChatHomeNames.valid(name);
        if (valid != null && discoveredHomes.stream().noneMatch(existing -> existing.equalsIgnoreCase(valid))) discoveredHomes.add(valid);
    }

    // ---- hotspot --------------------------------------------------------------------------------------------------

    public List<String> hotspotDetails() { return List.copyOf(hotspotDetails); }
    public void refreshHotspot() {
        if (now() < hotspotReadingUntil) return;
        hotspotDetails.clear();
        hotspotReadingUntil = now() + 3_000;
        send("/hotspot info");
    }

    // ---- teleports ------------------------------------------------------------------------------------------------

    public List<ChatTeleportState.Timer> timers() { checkSession(); return teleports.timers(now()); }
    public List<ChatTpaRequests.Request> requests() { checkSession(); return incomingRequests.pending(now()); }
    public List<String> countdowns() { return ChatCountdowns.lines(timers(), requests(), now()); }
    /** The HUD line for one teleport kind, or "" while none is running. */
    public String countdown(ChatTeleportState.Kind kind) { return ChatCountdowns.hud(kind, timers(), now()); }
    /** Incoming teleport requests as one HUD line, or "" with none. */
    public String requestCountdown() { return ChatCountdowns.requests(requests(), now()); }
    public void requestTeleport(String player) {
        if (!ChatMessages.validName(player) || isSelf(player)) return;
        rememberPlayer(player);
        send("/tpa " + player);
    }
    public void answer(ChatTpaRequests.Request request, boolean yes) {
        checkSession();
        if (incomingRequests.isPending(request, now())) send(yes ? acceptCommand(request) : denyCommand(request));
    }
    /** The server's own accept command from its prompt, otherwise the first one its command tree offers. */
    public String acceptCommand(ChatTpaRequests.Request request) {
        return request.verbsKnown() ? request.yesCommand() : "/" + serverVerb("tpy", "tpaccept", "tpyes") + " " + request.player();
    }
    public String denyCommand(ChatTpaRequests.Request request) {
        return request.verbsKnown() ? request.noCommand() : "/" + serverVerb("tpn", "tpdeny", "tpno") + " " + request.player();
    }
    public boolean canCancelHome() { checkSession(); return teleports.canCancel(ChatTeleportState.Kind.Home, now()); }
    public boolean canCancelTpa() { checkSession(); return teleports.canCancel(ChatTeleportState.Kind.TPA, now()); }
    public void cancelHome() { if (canCancelHome()) send("/homecancel"); }
    public void cancelTpa() { if (canCancelTpa()) send(cancelTpaCommand()); }
    public String cancelTpaCommand() {
        if (mc.getConnection() != null) {
            var root = mc.getConnection().getCommands().getRoot();
            if (root.getChild("tpacancel") == null && root.getChild("tpcancel") != null) return "/tpcancel";
        }
        return "/tpacancel";
    }

    /** The length a cooldown runs after a teleport: your manual length when set, else the one learned from the server. */
    public record CooldownLength(Long manual, Long learned) {
        public long effective() { return manual != null ? manual : learned != null ? learned : 0; }
    }
    public CooldownLength cooldownLength(ChatTeleportState.Kind kind) {
        checkSession();
        syncSavedCooldowns();
        Long manual = switch (kind) {
            case Home -> homeManual.getValue() ? homeCooldown.getValue() * 1_000L : null;
            case TPA -> tpaManual.getValue() ? tpaCooldown.getValue() * 1_000L : null;
            case Hotspot -> null;
        };
        return new CooldownLength(manual, teleports.learned().get(kind));
    }
    /** Sets a manual length (the same setting as in the module's settings), or with null goes back to Auto. */
    public void manualCooldown(ChatTeleportState.Kind kind, Long millis) {
        BooleanSetting manual = kind == ChatTeleportState.Kind.Home ? homeManual : kind == ChatTeleportState.Kind.TPA ? tpaManual : null;
        NumberSetting<Integer> length = kind == ChatTeleportState.Kind.Home ? homeCooldown : tpaCooldown;
        if (manual == null) return;
        if (millis != null) length.setValue((int) Math.clamp(millis / 1_000, 0, 3_600));
        manual.setValue(millis != null);
        refreshCooldowns();
    }
    /**
     * Loads this server/account's learned cooldowns into a fresh session and saves changes, so the first
     * teleport of a session already uses the real cooldown instead of the fallback estimate.
     */
    private void syncSavedCooldowns() {
        if (mc.player == null) return;
        String key = serverKey();
        if (!key.equals(savedKey)) {
            savedKey = key;
            saved = ChatSavedCooldowns.read(store.get(ChatToolsStore.Lists.SAVED_COOLDOWNS), key);
            teleports.learned(saved.learned());
        }
        // Only learned lengths are kept per server; manual lengths live in the module's settings.
        var current = new ChatSavedCooldowns.Saved(teleports.learned(), java.util.Map.of());
        if (current.equals(saved)) return;
        saved = current;
        store.set(ChatToolsStore.Lists.SAVED_COOLDOWNS, ChatSavedCooldowns.write(store.get(ChatToolsStore.Lists.SAVED_COOLDOWNS), key, current));
    }

    // ---- sending --------------------------------------------------------------------------------------------------

    /**
     * Outgoing DMs enter history only when the server echoes them, so rejected sends never look delivered. Until then
     * they wait in the whisper queue, which holds them back while the server's whisper delay runs.
     */
    public boolean sendPrivate(String player, String text) {
        if (!ChatMessages.validName(player) || text.isBlank() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return false;
        String template = messageCommand.getValue().strip();
        if (!template.contains("{player}") || template.indexOf('\n') >= 0 || template.indexOf('\r') >= 0) return false;
        String command = template.replace("{player}", player) + " " + text;
        if (withoutSlash(command).length() > 256) return false;
        return queueWhisper(new ChatWhisperQueue.Whisper(player, text, command));
    }
    /** Runs a command typed in the window. Whispers with an explicit recipient join the DM queue. */
    public boolean sendCommand(String command) {
        var whisper = ChatWhisperQueue.parse(command, messageCommand.getValue());
        // The server resolves /reply using its live conversation. Delaying or retrying it can send to someone else.
        if (whisper != null && whisper.player() != null) return queueWhisper(whisper);
        send(command);
        return true;
    }
    public boolean isWhisper(String command) { return ChatWhisperQueue.parse(command, messageCommand.getValue()) != null; }
    private boolean queueWhisper(ChatWhisperQueue.Whisper whisper) {
        checkSession();
        applyCooldowns();
        if (mc.getConnection() == null || !whispers.add(whisper)) return false;
        if (whisper.player() != null) rememberPlayer(whisper.player());
        sendWhispers();
        return true;
    }
    /** Sends the next queued whisper once the server's delay allows it; never mid-transfer, when there's no world. */
    private void sendWhispers() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        var next = whispers.poll(now());
        if (next != null) send(next.command());
    }
    /**
     * Sends public chat through the queue, or straight away when queueing is off. The text goes out through the normal
     * chat path when its turn comes, so BetterChat adds its prefix and suffix then, exactly once.
     */
    public boolean sendPublic(String text) {
        checkSession();
        if (text == null || text.isBlank() || mc.getConnection() == null) return false;
        if (!queueChat.getValue()) { sendChatNow(text); return true; }
        applyCooldowns();
        if (!chats.add(text)) return false;
        sendChats();
        return true;
    }
    private void sendChats() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        String next = chats.poll(now());
        if (next != null) sendChatNow(next);
    }
    /** The plain text this module last handed to Minecraft to send, until its packet goes out. */
    private volatile String sentRaw;
    private void sendChatNow(String text) {
        mc.gui.getChat().addRecentChat(text);
        sentRaw = text;
        mc.getConnection().sendChat(text);
    }
    public void send(String command) {
        if (mc.getConnection() == null) return;
        mc.gui.getChat().addRecentChat(command);
        mc.getConnection().sendCommand(withoutSlash(command));
    }

    /** One message waiting in either queue, for the window's send tray. */
    public record Outgoing(boolean whisper, long id, String target, String text, long eta) {}
    /** Everything still waiting to send, soonest first. */
    public List<Outgoing> outgoing() {
        checkSession();
        applyCooldowns();
        long now = now();
        List<Outgoing> result = new ArrayList<>();
        for (var entry : chats.entries(now)) result.add(new Outgoing(false, entry.id(), null, entry.text(), entry.eta()));
        for (var entry : whispers.entries(now)) result.add(new Outgoing(true, entry.id(),
            entry.whisper().player() == null ? "Reply" : entry.whisper().player(), entry.whisper().text(), entry.eta()));
        result.sort(java.util.Comparator.comparingLong(Outgoing::eta));
        return result;
    }
    public void cancelOutgoing(Outgoing outgoing) {
        if (outgoing.whisper()) whispers.cancel(outgoing.id()); else chats.cancel(outgoing.id());
    }
    /** How long queued whispers still wait for the server's delay, or 0 when none are waiting. */
    public long whisperWait() { checkSession(); return whispers.waiting(now()); }
    /** Texts of whispers to this player that are queued or not yet echoed by the server, oldest first. */
    public List<String> pendingWhispers(String player) {
        checkSession();
        return whispers.pending(player, now()).stream().map(ChatWhisperQueue.Whisper::text).toList();
    }

    // ---- what's waiting -------------------------------------------------------------------------------------------

    /** What is waiting for the player: teleport requests to answer, unread messages and a Discord sync problem. */
    public record Waiting(int requests, int unread, String problem) {
        public int total() { return requests + unread + (problem == null ? 0 : 1); }
    }
    /** Refreshed every tick, so the on-screen notice never recomputes it while drawing. */
    public Waiting waiting() { return waitingNow; }
    private Waiting computeWaiting() {
        String problem = discordSync == null ? null : discordSync.problem();
        return new Waiting(mc.getConnection() == null ? 0 : requests().size(), messages().unread(), problem == null || problem.isBlank() ? null : problem);
    }

    // ---- incoming chat --------------------------------------------------------------------------------------------

    /** Whether a chat line is a private message in a format this module recognises. */
    public boolean isPrivateMessage(String text) {
        return mc.player != null && messages.parse(text, mc.player.getGameProfile().name(), now()) != null;
    }
    /** Whether a chat line is a private message someone sent you; BetterChat's DM sound asks this. */
    public boolean isIncomingPrivateMessage(String text) {
        if (mc.player == null || text == null) return false;
        ChatMessages.Message message = messages.parse(text, mc.player.getGameProfile().name(), now());
        return message != null && message.incoming();
    }
    /** How many chat lines the window keeps: more while BetterChat's long chat history is on. */
    public int chatLineLimit() {
        BetterChatModule betterChat = ClientContext.moduleRegistry == null ? null : ClientContext.getModule(BetterChatModule.class);
        return betterChat != null && betterChat.longChatHistorySetting.getValue() ? LONG_CHAT_LINES : CHAT_LINES;
    }

    /** One chat line exactly as in-game chat shows it, for the Chat page and the Overview. */
    public record ChatLine(long id, long time, Component message, boolean dm) {}
    /** Recent chat for this session, oldest first. */
    public List<ChatLine> chatLines() { checkSession(); return List.copyOf(chatLines); }

    /**
     * Runs after BetterChat (priority 0), so its DM and mention sounds still play for a DM this then hides. Reads the
     * original text, because BetterChat has already put its timestamp in front of the shown message.
     */
    @EventHandler(priority = -100) private void onMessage(ChatMessageEvent event) {
        if (!event.getStage().isPre() || event.isCancelled() || mc.player == null || mc.getConnection() == null) return;
        checkSession();
        String text = event.getOriginalText();
        if (text == null) text = event.getMessage().getString();
        // The refused whisper goes again once the wait is over, so the server's refusal would only be noise.
        if (whispers.refused(text, now()) || chats.refused(text, now())) { event.cancel(); return; }
        ChatMessages.Message message = messages.parse(text, mc.player.getGameProfile().name(), now());
        if (message == null) return;
        if (!message.incoming()) whispers.delivered(message.player(), message.text());
        rememberPlayer(message.player());
        if (message.incoming()) replyPlayer = message.player();
        boolean read = window != null && window.showing() && window.reading(message.player());
        messages.add(message, read);
        if (message.incoming() && !read && messageToasts.getValue())
            SystemToast.addOrUpdate(mc.getToastManager(), DM_TOAST,
                Component.literal("Message from " + message.player()), Component.literal("Open Chat Tools to reply."));
        if (hideMessages.getValue()) event.cancel();
    }

    /** Runs after every other listener, so lines hidden from chat (hidden DMs, resent whispers) stay hidden here too. */
    @EventHandler(priority = -10_000) private void onShownMessage(ChatMessageEvent event) {
        if (!event.getStage().isPre() || event.isCancelled() || mc.player == null || mc.getConnection() == null) return;
        checkSession();
        // The window shows its own time column, so it keeps the line without BetterChat's timestamp.
        Component message = event.getOriginalMessage() != null ? event.getOriginalMessage() : event.getMessage();
        chatLines.addLast(new ChatLine(++chatLineId, System.currentTimeMillis(), message, isPrivateMessage(message.getString())));
        int limit = chatLineLimit();
        while (chatLines.size() > limit) chatLines.removeFirst();
    }

    @EventHandler private void onSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundChatPacket chat) {
            // BetterChat may have added its prefix and suffix; match the packet back to the text the queue sent.
            String text = ChatPublicQueue.queuedText(sentRaw, chat.message());
            sentRaw = null;
            mc.execute(() -> { if (isToggled()) { checkSession(); chats.sent(text, now()); } });
            return;
        }
        String command = event.getPacket() instanceof ServerboundChatCommandPacket packet ? packet.command()
            : event.getPacket() instanceof ServerboundChatCommandSignedPacket packet ? packet.command() : null;
        if (command == null) return;
        mc.execute(() -> {
            if (!isToggled() || mc.getConnection() == null) return;
            checkSession();
            String[] parts = WHITESPACE.split(command.strip());
            String verb = parts[0].substring(parts[0].lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
            if (parts.length > 1 && Set.of("msg", "w", "whisper", "tell", "tpa", "tpahere").contains(verb)) rememberPlayer(parts[1]);
            if (ChatWhisperQueue.isWhisper(command, messageCommand.getValue())) whispers.sent(command, now());
            syncSavedCooldowns();
            autoTpy.commandSent(command, now(), autoTick);
            teleports.sent(command, now());
            incomingRequests.sent(command, now());
            ChatHomeEdit edit = ChatHomeEdit.parse(command);
            if (edit != null) {
                pendingHomeEdit = edit;
                homeEditExpires = now() + 10_000;
            }
        });
    }

    @EventHandler private void onReceive(PacketEvent.Receive event) {
        if (!isToggled()) return;
        if (event.getPacket() instanceof ClientboundCommandSuggestionsPacket packet && packet.id() == homeRequest && homeRequest != 0) {
            event.cancel();
            mc.execute(() -> {
                if (!isToggled() || mc.getConnection() == null || packet.id() != homeRequest) return;
                suggestedHomes.clear();
                suggestedHomes.addAll(ChatHomeNames.merge(packet.suggestions().stream().map(ClientboundCommandSuggestionsPacket.Entry::text).toList()));
                homeRequest = 0;
            });
        } else if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            mc.execute(() -> {
                if (!isToggled() || mc.getConnection() == null) return;
                checkSession();
                String text = packet.content().getString();
                // Whispers, including echoes to offline recipients, must never change server command state. Nor may the
                // whisper delay: "Please wait 2 seconds before..." would otherwise read as a home or TP cooldown.
                String self = mc.player == null ? null : mc.player.getGameProfile().name();
                if (messages.parse(text, self, now()) != null || ChatWhisperQueue.isRefusal(text)) return;
                // Another player's chat must never move timers, requests or homes, even if it imitates a server reply.
                if (ChatSender.fromPlayer(text, this::online)) return;
                if (now() < hotspotReadingUntil || text.toLowerCase(Locale.ROOT).contains("hotspot")) {
                    hotspotDetails.add(ChatSender.plain(text));
                    if (hotspotDetails.size() > 12) hotspotDetails.removeFirst();
                }
                syncSavedCooldowns();
                teleports.message(text, now());
                syncSavedCooldowns();
                collectRequests(packet.content());
                confirmHomeEdit(text);
                collectHomes(packet.content());
                for (String name : ChatHomeNames.fromMessage(text)) addHome(name);
            });
        } else if (event.getPacket() instanceof ClientboundSetActionBarTextPacket packet) {
            mc.execute(() -> {
                if (!isToggled() || mc.getConnection() == null) return;
                checkSession();
                syncSavedCooldowns();
                teleports.message(packet.text().getString(), now());
                syncSavedCooldowns();
            });
        } else if (event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
            // A long jump is a teleport happening: it ends a pending /home, /tpa or /hotspot and starts its cooldown.
            var player = mc.player;
            if (player != null && packet.relatives().isEmpty() && packet.change().position().distanceToSqr(player.position()) >= TELEPORT_JUMP_SQ)
                mc.execute(() -> { if (isToggled() && mc.getConnection() != null) { checkSession(); teleports.teleported(now()); } });
        } else if (event.getPacket() instanceof ClientboundLoginPacket) {
            // A login while already in a world is a network moving you to another of its servers. Read the world now,
            // before the login is handled and replaces it.
            boolean inWorld = mc.level != null;
            mc.execute(() -> { if (isToggled() && inWorld) left(); });
        }
    }

    private void collectRequests(Component message) {
        List<String> commands = new ArrayList<>();
        message.visit((Style style, String text) -> {
            ClickEvent click = style.getClickEvent();
            if (click instanceof ClickEvent.RunCommand run) commands.add(run.command());
            else if (click instanceof ClickEvent.SuggestCommand suggest) commands.add(suggest.command());
            return Optional.empty();
        }, Style.EMPTY);
        String self = mc.player == null ? null : mc.player.getGameProfile().name();
        syncAutoTpy();
        autoTpy.message(message.getString(), now());
        incomingRequests.message(message.getString(), commands, self, now(), requestTimeout.getValue());
        for (var change : incomingRequests.drainChanges()) {
            autoTpy.lifecycle(change, now());
            if (change.kind() == ChatTpaRequests.ChangeKind.Upsert) {
                offerAutoTpy(change.request());
                rememberPlayer(change.request().player());
            }
        }
    }

    // ---- clan highlighting ----------------------------------------------------------------------------------------

    private void refreshHighlights() {
        Set<String> clan = ChatClan.keys(store.get(ChatToolsStore.Lists.CLAN_MEMBERS));
        int rgb = clanChatColor.getValue().getRGB() & 0xffffff;
        boolean shown = isToggled() && highlightClanChat.getValue();
        // The tab list reads these names every frame; only chat lines are wrapped once and need rebuilding.
        boolean chatChanged = shown != clanInChat || shown && (!clan.equals(clanNames) || rgb != clanColor);
        clanNames = clan;
        clanColor = rgb;
        clanInChat = shown;
        if (chatChanged && mc.gui != null) mc.gui.getChat().rescaleChat();
    }

    /**
     * Colours clan members in a chat line. Called after BetterChat has coloured friends and enemies, and leaves those
     * names alone so a name is never coloured twice.
     */
    public Component highlightClan(Component message) {
        if (!clanInChat || clanNames.isEmpty()) return message;
        return ChatFriendHighlight.apply(message, name -> {
            String key = name.toLowerCase(Locale.ROOT);
            return clanNames.contains(key) && !isFriend(name) && !isEnemy(name) ? Integer.valueOf(clanColor) : null;
        });
    }
    /** Colours a clan member's username inside their tab-list name, keeping prefixes; a nickname is coloured whole. */
    public Component highlightTabName(PlayerInfo info, Component name) {
        String player = info.getProfile().name();
        if (!isToggled() || !highlightClanTab.getValue() || player == null || !clanNames.contains(player.toLowerCase(Locale.ROOT))
            || isFriend(player) || isEnemy(player)) return name;
        int color = clanTabColor.getValue().getRGB() & 0xffffff;
        Component result = ChatFriendHighlight.apply(name, found -> found.equalsIgnoreCase(player) ? Integer.valueOf(color) : null);
        return result != name ? result : ChatFriendHighlight.recolor(name, color);
    }
    /** The bar to draw behind a clan member's tab-list row, with its colour's own alpha, or 0 for none. */
    public int tabHighlight(PlayerInfo info) {
        String player = info.getProfile().name();
        if (!isToggled() || !highlightClanRows.getValue() || player == null || !clanNames.contains(player.toLowerCase(Locale.ROOT))) return 0;
        return clanRowColor.getValue().getRGB();
    }

    // ---- player actions -------------------------------------------------------------------------------------------

    public record Option(String label, String command, boolean complete, boolean detected) {}

    public boolean runsImmediately(Option option) {
        return runOnClick.getValue() && option.complete() && !Character.isWhitespace(option.command().charAt(option.command().length() - 1));
    }
    /** Commands for a player: ones found in the server's command tree, then the configured ones. */
    public List<Option> options(String player) {
        Map<String, Option> result = new LinkedHashMap<>();
        if (detectCommands.getValue() && mc.getConnection() != null) {
            for (CommandNode<ClientSuggestionProvider> root : mc.getConnection().getCommands().getRoot().getChildren())
                if (root instanceof LiteralCommandNode<ClientSuggestionProvider> literal) discover(literal, List.of(literal.getLiteral()), player, 0, result);
        }
        for (String entry : commandOptions()) {
            ChatCommandTemplate template = ChatCommandTemplate.parse(entry);
            if (!template.valid()) continue;
            String command = template.expand(player);
            boolean complete = !Character.isWhitespace(command.charAt(command.length() - 1));
            put(result, new Option(template.label(), command, complete, false));
        }
        return new ArrayList<>(result.values());
    }
    private void discover(CommandNode<ClientSuggestionProvider> node, List<String> path, String player, int depth, Map<String, Option> result) {
        if (depth > 2) return;
        for (CommandNode<ClientSuggestionProvider> child : node.getChildren()) {
            if (child instanceof ArgumentCommandNode<ClientSuggestionProvider, ?> argument && acceptsPlayers(argument.getType())) {
                String command = "/" + String.join(" ", path) + " " + player;
                boolean complete = argument.getCommand() != null;
                put(result, new Option("/" + String.join(" ", path), command + (complete ? "" : " "), complete, true));
            } else if (child instanceof LiteralCommandNode<ClientSuggestionProvider> literal) {
                List<String> next = new ArrayList<>(path);
                next.add(literal.getLiteral());
                discover(literal, next, player, depth + 1, result);
            }
        }
    }
    private static boolean acceptsPlayers(ArgumentType<?> type) {
        if (type instanceof GameProfileArgument) return true;
        return type instanceof EntityArgument && ((EntityArgumentAccessor) type).argus$playersOnly();
    }
    private static void put(Map<String, Option> options, Option option) {
        options.putIfAbsent(option.command().stripTrailing().toLowerCase(Locale.ROOT), option);
    }

    /** The player a piece of chat refers to (insertion, hover or click), if they're online. */
    public String playerFor(Style style) {
        if (mc.getConnection() == null || style == null) return null;
        String insertion = onlineName(style.getInsertion());
        if (insertion != null) return insertion;
        if (style.getHoverEvent() instanceof HoverEvent.ShowEntity show && show.entity().type == EntityType.PLAYER) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(show.entity().uuid);
            if (info != null && !isSelf(info.getProfile().name())) return info.getProfile().name();
            if (show.entity().name.isPresent()) {
                String hovered = onlineName(show.entity().name.get().getString());
                if (hovered != null) return hovered;
            }
        }
        String clicked = null;
        if (style.getClickEvent() instanceof ClickEvent.SuggestCommand suggest) clicked = suggest.command();
        else if (style.getClickEvent() instanceof ClickEvent.RunCommand run) clicked = run.command();
        if (clicked != null) {
            String[] tokens = WHITESPACE.split(clicked.strip());
            for (int i = 1; i < tokens.length; i++) {
                String name = onlineName(tokens[i]);
                if (name != null) return name;
            }
        }
        return null;
    }

    // ---- server ---------------------------------------------------------------------------------------------------

    private String serverVerb(String... candidates) {
        for (String candidate : candidates) if (hasCommand(candidate)) return candidate;
        return candidates[0];
    }
    private boolean hasCommand(String root) {
        return mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild(root) != null;
    }
    /**
     * Whether this server offers a top-level command. Before the server sends its command tree (or if it sends an
     * empty one) nothing is known, so server-specific controls stay available rather than vanishing.
     */
    public boolean commandAvailable(String root) {
        if (mc.getConnection() == null) return true;
        var tree = mc.getConnection().getCommands().getRoot();
        return tree.getChildren().isEmpty() || tree.getChild(root) != null;
    }

    private boolean online(String name) {
        return mc.getConnection() != null && mc.getConnection().getPlayerInfoIgnoreCase(name) != null;
    }
    private String onlineName(String candidate) {
        if (candidate == null || !USERNAME.matcher(candidate).matches() || mc.getConnection() == null) return null;
        PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(candidate);
        return info == null || isSelf(info.getProfile().name()) ? null : info.getProfile().name();
    }
    private boolean isSelf(String name) {
        return mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(name);
    }
    private static String withoutSlash(String command) { return command.startsWith("/") ? command.substring(1) : command; }
}

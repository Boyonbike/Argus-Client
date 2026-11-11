package dev.argus.client.modules.client;

import dev.argus.client.ClientContext;
import dev.argus.client.chat.ChatToolsStore;
import dev.argus.client.chat.DiscordConnection;
import dev.argus.client.chat.DiscordIgn;
import dev.argus.client.chat.DiscordIgnClient;
import dev.argus.client.chat.DiscordIgnSyncState;
import dev.argus.client.chat.DiscordRoles;
import dev.argus.client.chat.DiscordSyncApply;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Keeps the Chat Tools clan list and Auto TPY whitelist in step with the Minecraft names posted in a Discord #ign
 * channel, read through the clan's relay (which holds the bot token, so none is stored here). Every eligible guild
 * member counts; Discord roles supply labels and ordering rather than filtering. The sync never empties a list.
 *
 * <p>This is separate from the Discord module, which only shows Rich Presence.
 */
public final class DiscordSyncModule extends Module {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Argus Discord IGN sync");
        thread.setDaemon(true);
        return thread;
    });

    private final StringSetting relayUrl = add(new StringSetting("Relay URL", ""));
    private final BooleanSetting applyToClan = add(new BooleanSetting("Apply to clan", true));
    private final BooleanSetting applyToAutoTpy = add(new BooleanSetting("Apply to Auto TPY", true));
    private final NumberSetting<Integer> autoSyncHours = add(new NumberSetting<>("Auto sync", 6, 0, 24).withUnit("h"));

    private final Minecraft mc = Minecraft.getInstance();
    private final DiscordIgnSyncState syncState = new DiscordIgnSyncState();
    private Future<?> task;
    private String status = "Not synced yet.";
    private long successfulSync;
    private int accountCount;
    /** The last finished read failed or couldn't be applied, so there's something to fix. */
    private boolean failed;
    /** Last completed read or rejected attempt, in monotonic milliseconds. */
    private long lastSync;
    /** Only the first sync of a game launch happens on its own; every client's sync reaches the shared relay. */
    private boolean syncedThisLaunch, startSyncPending;
    private final AtomicLong retryNotBefore = new AtomicLong();

    public DiscordSyncModule() {
        super("DiscordSync", "Keeps the clan list and Auto TPY whitelist in step with the Minecraft names in your Discord's #ign channel.",
            ModuleCategory.CLIENT, "discord ign", "ign sync", "clan sync", "relay");
        relayUrl.setChangeCallback(() -> {
            ChatToolsModule tools = ChatToolsModule.get();
            // Role access is bound to its relay: a changed relay stops admitting the old roster at once.
            if (tools != null) tools.refreshAutoTpyPolicy();
            if (!syncState.busy()) status = DiscordConnection.problem(relayUrl.getValue()) == null ? "Connection saved. Ready to sync." : problem();
        });
    }

    public static DiscordSyncModule get() {
        return ClientContext.moduleRegistry == null ? null : ClientContext.getModule(DiscordSyncModule.class);
    }

    /** Hands Chat Tools the relay and current problem, for role access and its "waiting" notice. */
    public void attach(ChatToolsModule tools) {
        tools.attachDiscordSync(new ChatToolsModule.DiscordSync() {
            @Override public String configuredRelay() { return DiscordSyncModule.this.configuredRelay(); }
            @Override public String problem() { return isToggled() && failed ? status : null; }
        });
    }

    @Override public void onEnable() {
        String problem = problem();
        if (problem != null) {
            status = problem;
            error(problem);
            disable();
            status = problem;
            return;
        }
        // Modules switch on while the config loads; the first sync waits for the first game tick.
        if (!syncedThisLaunch) startSyncPending = true;
    }

    @Override public void onDisable() {
        syncState.cancel();
        if (task != null) task.cancel(true);
        task = null;
        startSyncPending = false;
        status = "Discord sync is off.";
    }

    @EventHandler private void onTick(MinecraftTickEvent event) {
        if (startSyncPending) { startSyncPending = false; syncNow(); return; }
        int hours = autoSyncHours.getValue();
        if (hours <= 0 || syncState.busy() || now() < retryNotBefore.get()) return;
        if (now() - lastSync < hours * 3_600_000L) return;
        syncNow();
    }

    public String configuredRelay() { return DiscordConnection.relay(relayUrl.getValue()); }
    /** The relay exactly as typed, for importing. */
    public String relaySetting() { return relayUrl.getValue(); }
    public void setRelay(String relay) { relayUrl.setValue(relay == null ? "" : relay.strip()); }
    public boolean hasRelay() { return !configuredRelay().isEmpty(); }
    public boolean syncBusy() { return syncState.busy(); }
    public String syncStatus() { return status; }
    /** Monotonic milliseconds; zero until a readable, non-empty roster has been applied. */
    public long lastSuccessfulSync() { return successfulSync; }
    public boolean syncFailed() { return failed; }
    public int syncedAccounts() { return accountCount; }

    /** Reads Discord now and applies the result, off the game thread. Safe to call from a button. */
    public void syncNow() {
        if (!mc.isSameThread()) { mc.execute(this::syncNow); return; }
        if (syncState.busy()) { warning("Still reading Discord; wait for the current sync to finish."); return; }
        if (now() < retryNotBefore.get()) {
            status = "Discord is rate limiting the sync. Wait before trying again.";
            warning("Discord is rate limiting the sync. Wait " + (retryNotBefore.get() - now() + 999) / 1000 + " seconds.");
            return;
        }
        lastSync = now();
        String problem = problem();
        if (problem != null) { status = problem; error(problem); return; }
        ChatToolsModule tools = ChatToolsModule.get();
        if (tools == null) { status = "Chat Tools isn't available, so nothing was changed."; error(status); return; }
        var config = configuration();
        var ticket = syncState.begin(config, tools.savedClanMembers(), tools.autoTpyWhitelist());
        status = "Reading Discord members and #ign…";
        info(status);
        task = WORKER.submit(() -> {
            try {
                DiscordIgnClient.Sync sync = new DiscordIgnClient(DiscordIgnClient.live(), config.relay(), this::deferRetry).fetch();
                mc.execute(() -> finished(ticket, sync));
            } catch (DiscordIgnClient.ApiException exception) {
                mc.execute(() -> failed(ticket, exception.explanation(), exception.retryDelayMillis()));
            } catch (IOException exception) {
                logger.warn("[Argus] Reading Discord IGNs failed", exception);
                mc.execute(() -> failed(ticket, exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Couldn't reach the relay; the clan list and whitelist were left unchanged."
                    : "Couldn't read Discord: " + exception.getMessage() + " The lists were left unchanged.", 0));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                mc.execute(() -> failed(ticket, "The Discord sync was interrupted; the lists were left unchanged.", 0));
            } catch (RuntimeException exception) {
                logger.warn("[Argus] Reading Discord IGNs failed", exception);
                mc.execute(() -> failed(ticket, "Discord returned data Argus couldn't read; the lists were left unchanged.", 0));
            }
        });
    }

    private DiscordIgnSyncState.Settings configuration() {
        return new DiscordIgnSyncState.Settings(configuredRelay(), applyToClan.getValue(), applyToAutoTpy.getValue());
    }

    /** Only the matching, unchanged read may finish; an old callback can't release a newer read. */
    private boolean accept(DiscordIgnSyncState.Ticket ticket) {
        if (!syncState.isCurrent(ticket)) return false;
        task = null;
        lastSync = now();
        ChatToolsModule tools = ChatToolsModule.get();
        if (tools == null) {
            syncState.cancel();
            status = "Chat Tools isn't available, so nothing was changed.";
            error(status);
            return false;
        }
        boolean accepted = syncState.complete(ticket, configuration(), tools.savedClanMembers(), tools.autoTpyWhitelist());
        if (!accepted) {
            status = "Discord settings or the lists changed during the read, so the result was discarded. Sync again to apply the new settings.";
            warning(status);
        }
        return accepted;
    }

    private void finished(DiscordIgnSyncState.Ticket ticket, DiscordIgnClient.Sync sync) {
        if (!accept(ticket)) return;
        syncedThisLaunch = true;
        ChatToolsModule tools = ChatToolsModule.get();
        DiscordIgn.Result result = sync.result();
        var before = new DiscordSyncApply.Lists(tools.savedClanMembers(), tools.syncedEntries(ChatToolsStore.Lists.CLAN_SYNCED),
            tools.autoTpyWhitelist(), tools.syncedEntries(ChatToolsStore.Lists.AUTO_TPY_SYNCED));
        var outcome = DiscordSyncApply.apply(result, before, applyToClan.getValue(), applyToAutoTpy.getValue(), tools.discordOwnershipTrusted());
        String source = DiscordRoles.source(ticket.settings().relay());
        switch (outcome.kind()) {
            case UNREADABLE -> {
                failed = true;
                status = "One or more #ign posts have no readable text. Turn on Message Content Intent for the bot and remove empty or attachment-only posts; the lists were left unchanged.";
                error(status);
                return;
            }
            case EMPTY -> {
                // Readable and complete, but nobody is linked any more: drop role access now, keep the lists.
                tools.setDiscordRoles(Map.of(), source);
                status = "No IGNs found in #ign; the lists were left unchanged and Discord role access was cleared.";
                warning(status);
                reportSkipped(result);
                return;
            }
            case APPLIED -> { }
        }
        tools.setDiscordRoles(sync.roles(), source);
        tools.setDiscordUsernames(result.usernames());
        if (applyToClan.getValue()) tools.setClanMembers(outcome.lists().clan());
        if (applyToAutoTpy.getValue()) tools.setAutoTpyWhitelist(outcome.lists().whitelist());
        tools.setSyncedEntries(ChatToolsStore.Lists.CLAN_SYNCED, outcome.lists().clanSynced());
        tools.setSyncedEntries(ChatToolsStore.Lists.AUTO_TPY_SYNCED, outcome.lists().whitelistSynced());
        tools.trustDiscordOwnership();
        if (outcome.upgradedOwnership()) warning("Updated how Discord sync tracks names: existing entries were kept as hand-added because the old tracking couldn't tell them apart. Check the clan list and Auto TPY whitelist for old members.");
        successfulSync = now();
        failed = false;
        accountCount = result.names().size();
        status = "Synced " + accountCount + " account" + plural(accountCount) + ".";
        info("Discord IGN sync: " + accountCount + " IGN" + plural(accountCount) + " from " + result.members() + " guild member"
            + plural(result.members()) + " (" + String.join("; ", outcome.changes()) + ").");
        reportSkipped(result);
    }

    private void reportSkipped(DiscordIgn.Result result) {
        if (!result.conflicts().isEmpty()) warning("Skipped names claimed by more than one Discord user: " + String.join(", ", result.conflicts()) + ".");
        if (result.invalid() > 0) info("Ignored " + result.invalid() + " message" + plural(result.invalid()) + " that weren't Java username lists.");
    }

    private void failed(DiscordIgnSyncState.Ticket ticket, String message, long retryMillis) {
        // Even a discarded read must not allow another request before Discord's retry deadline.
        if (retryMillis > 0) deferRetry(retryMillis);
        if (!accept(ticket)) return;
        failed = true;
        status = message;
        error(status);
    }

    private void deferRetry(long millis) { retryNotBefore.accumulateAndGet(now() + millis, Math::max); }

    /** What stops a sync, or null when the settings are usable. */
    private String problem() {
        String problem = DiscordConnection.problem(relayUrl.getValue());
        if (problem != null) return problem;
        if (!applyToClan.getValue() && !applyToAutoTpy.getValue())
            return "Turn on Apply to clan, Apply to Auto TPY, or both; otherwise there's nowhere to put the names.";
        return null;
    }

    private void info(String text) { print(text, ChatFormatting.GRAY); }
    private void warning(String text) { print(text, ChatFormatting.YELLOW); }
    private void error(String text) { print(text, ChatFormatting.RED); }
    /** Chat messages only while in a world; the status line on the settings page always shows the latest. */
    private void print(String text, ChatFormatting colour) {
        if (mc.player == null || mc.gui == null) return;
        ChatUtils.printChat(Component.literal(text).withStyle(colour), ChatUtils.moduleSignature(this));
    }

    private static String plural(int count) { return count == 1 ? "" : "s"; }
    private static long now() { return System.nanoTime() / 1_000_000L; }
}

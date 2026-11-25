/*
 * Comet highway travel helpers.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.argus.client.modules.misc;

import dev.argus.client.chat.ChatHomeNames;
import dev.argus.client.chat.ChatSender;
import dev.argus.client.chat.HomeOverride;
import dev.argus.client.chat.HomeOverrideDialog;
import dev.argus.client.chat.SetHomeScheduler;
import dev.argus.client.chat.SetHomeStore;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

/**
 * Comet's standalone home scheduler. When the server answers /sethome with an override dialog, the module reads the
 * show_dialog packet, never opens the screen, and sends the Override button's action straight back (for Paper this is a
 * custom_click_action carrying the dialog's one-time callback id).
 */
public final class AutoSetHomeModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoSetHomeModule.class);

    private enum HomeTiming implements NamedEntry {
        AUTO("Auto Detect"), FIXED("Fixed Timer");

        private final String name;

        HomeTiming(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Setting<String> home = add(new StringSetting("Home", "test"));
    private final Setting<HomeTiming> timing = add(new EnumSetting<>("Timing", HomeTiming.AUTO));
    private final Setting<Integer> intervalSeconds = add(new NumberSetting<>("Interval", 60, 5, 3600).withUnit("s"));
    private final Setting<Integer> cooldownBufferSeconds = add(new NumberSetting<>("Cooldown Buffer", 10, 0, 120).withUnit("s"));
    private final Setting<Integer> retryDelaySeconds = add(new NumberSetting<>("Retry Delay", 60, 0, 3600).withUnit("s"));
    private final Setting<Boolean> override = add(new BooleanSetting("Override", true));
    private final Setting<String> dialogMatch = add(new StringSetting("Dialog Match", "home", value -> override.getValue()));
    private final Setting<String> buttonMatch = add(new StringSetting("Button Match", "override", value -> override.getValue()));
    private final Setting<Integer> timeoutSeconds = add(new NumberSetting<>("Dialog Timeout", 10, 1, 60).withUnit("s"));

    private SetHomeStore store;
    private SetHomeScheduler scheduler;
    private String account;
    private String homeServer;
    private boolean storeWarning;
    private boolean invalidHomeWarning;
    private ClientPacketListener connectionSession;
    /** Mirrors scheduler.waitingForDialog() for the netty thread, which decides whether to swallow a dialog. */
    private volatile boolean expectingDialog;

    public AutoSetHomeModule() {
        super("AutoSetHome", "Automatically updates a home using server cooldowns or a fixed timer.", ModuleCategory.MISC);
    }

    @Override
    public void onDisable() {
        expectingDialog = false;
        connectionSession = null;
        if (scheduler != null) scheduler.cancel();
    }

    @EventHandler
    private void onTick(MinecraftTickEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.getCurrentServer() == null) {
            onDisable();
            return;
        }
        checkSession();
        scheduler.expire();
        if (scheduler.waiting() && !home.getValue().strip().equals(scheduler.requestedHome())) scheduler.cancel();
        expectingDialog = scheduler.waitingForDialog() && override.getValue();
        if (scheduler.waiting()) return;
        if (mc.screen != null || !scheduler.ready(timing.getValue() == HomeTiming.FIXED, intervalMillis(), bufferMillis())) return;
        String name = ChatHomeNames.valid(home.getValue());
        if (name == null) {
            if (!invalidHomeWarning) {
                mc.gui.getChat().addClientSystemMessage(Component.literal("[AutoSetHome] Choose a valid single home name."));
            }
            invalidHomeWarning = true;
            return;
        }
        invalidHomeWarning = false;
        // Record and persist before dispatch so even a crash after sending retains the attempt timestamp.
        scheduler.sent(name, location(), override.getValue(), timeoutSeconds.getValue() * 1_000L, intervalMillis());
        save();
        expectingDialog = override.getValue();
        mc.getConnection().sendCommand(HomeOverride.command(name));
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundShowDialogPacket dialogPacket) {
            onDialog(event, dialogPacket);
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet) || packet.overlay()) return;
        ClientPacketListener connection = mc.getConnection();
        mc.execute(() -> {
            if (!isToggled() || connection == null || connection != mc.getConnection()
                || mc.player == null || mc.level == null || mc.getCurrentServer() == null) return;
            checkSession();
            String text = packet.content().getString();
            if (ChatSender.fromPlayer(text, name -> connection.getPlayerInfoIgnoreCase(name) != null)) return;
            if (scheduler.message(text, retryMillis()) == SetHomeScheduler.Reply.IGNORED) return;
            expectingDialog = scheduler.waitingForDialog() && override.getValue();
            save();
        });
    }

    private void checkSession() {
        if (store == null) {
            store = new SetHomeStore(FabricLoader.getInstance().getGameDir().resolve("argusclient/auto-set-home-state.json"));
            if (store.loadError() != null) LOGGER.warn("Auto set home: saved state could not be loaded; learning again.");
        }
        String currentAccount = mc.player.getUUID().toString();
        String currentServer = SetHomeStore.normaliseServer(mc.getCurrentServer().ip);
        if (scheduler == null || !currentAccount.equals(account) || !currentServer.equals(homeServer)) {
            account = currentAccount;
            homeServer = currentServer;
            scheduler = new SetHomeScheduler(System::currentTimeMillis, store.load(account, homeServer));
            expectingDialog = false;
        }
        ClientPacketListener current = mc.getConnection();
        if (current != connectionSession) {
            connectionSession = current;
            scheduler.cancel();
            expectingDialog = false;
        }
    }

    private void save() {
        try {
            store.save(account, homeServer, scheduler.state());
            storeWarning = false;
        } catch (IOException | RuntimeException e) {
            if (!storeWarning) LOGGER.warn("Auto set home: saving failed; timing may be lost after restart.");
            storeWarning = true;
            LOGGER.warn("Saving auto set home state failed", e);
        }
    }

    private SetHomeScheduler.Location location() {
        return new SetHomeScheduler.Location(mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimensionId());
    }

    /**
     * Runs on the netty thread. A dialog that arrives while a /sethome is waiting for one, and that matches the
     * configured phrases, is swallowed here and answered on the client thread.
     */
    private void onDialog(PacketEvent.Receive event, ClientboundShowDialogPacket packet) {
        if (!isToggled() || !expectingDialog || !override.getValue()) return;
        Dialog dialog = packet.dialog().value();
        ActionButton button = overrideButton(dialog);
        if (button == null || button.action().isEmpty()) return;
        event.cancel();
        expectingDialog = false;
        ClientPacketListener connection = mc.getConnection();
        Holder<Dialog> holder = packet.dialog();
        mc.execute(() -> {
            boolean live = isToggled() && connection != null && connection == mc.getConnection()
                && mc.player != null && scheduler != null && scheduler.waitingForDialog();
            if (!live || !press(connection, button)) {
                // Not ours to answer after all: show it as the server intended.
                if (connection != null && connection == mc.getConnection()) connection.showDialog(holder, mc.screen);
                return;
            }
            scheduler.clicked(location());
        });
    }

    /** The button whose label matches Button Match, if the dialog's title, body or buttons match Dialog Match. */
    private ActionButton overrideButton(Dialog dialog) {
        return HomeOverrideDialog.overrideButton(dialog, dialogMatch.getValue(), buttonMatch.getValue());
    }

    private boolean press(ClientPacketListener connection, ActionButton button) {
        return HomeOverrideDialog.press(connection, button);
    }

    private long intervalMillis() {
        return Math.max(1, intervalSeconds.getValue() * 1_000L);
    }

    private long bufferMillis() {
        return Math.max(0, cooldownBufferSeconds.getValue() * 1_000L);
    }

    private long retryMillis() { return Math.max(0, retryDelaySeconds.getValue() * 1_000L); }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        onDisable();
    }

    private String dimensionId() {
        return mc.level.dimension().identifier().toString();
    }
}

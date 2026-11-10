/*
 * Comet highway travel helpers.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.argus.client.modules.misc;

import dev.argus.client.chat.ChatHomeNames;
import dev.argus.client.chat.ChatSender;
import dev.argus.client.chat.HomeOverride;
import dev.argus.client.chat.SetHomeScheduler;
import dev.argus.client.chat.SetHomeStore;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.ScreenChangeEvent;
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
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Comet's standalone home scheduler and dialog confirmation, using Argus settings and events. */
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
    private Screen confirmationScreen;

    public AutoSetHomeModule() {
        super("AutoSetHome", "Automatically updates a home using server cooldowns or a fixed timer.", ModuleCategory.MISC);
    }

    @Override
    public void onDisable() {
        confirmationScreen = null;
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
        if (scheduler.waiting()) {
            if (!home.getValue().strip().equals(scheduler.requestedHome())) {
                scheduler.cancel();
                confirmationScreen = null;
                return;
            }
            if (scheduler.waitingForDialog() && override.getValue() && tryOverride()) {
                scheduler.clicked(location());
                confirmationScreen = null;
            }
            return;
        }
        confirmationScreen = null;
        if (mc.screen != null || !scheduler.ready(timing.getValue() == HomeTiming.FIXED, intervalMillis(), bufferMillis())) return;
        String name = ChatHomeNames.valid(home.getValue());
        if (name == null) {
            if (!invalidHomeWarning) {
                mc.player.displayClientMessage(Component.literal("[AutoSetHome] Choose a valid single home name."), false);
            }
            invalidHomeWarning = true;
            return;
        }
        invalidHomeWarning = false;
        // Record and persist before dispatch so even a crash after sending retains the attempt timestamp.
        scheduler.sent(name, location(), override.getValue(), timeoutSeconds.getValue() * 1_000L, intervalMillis());
        save();
        mc.getConnection().sendCommand(HomeOverride.command(name));
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet) || packet.overlay()) return;
        ClientPacketListener connection = mc.getConnection();
        mc.execute(() -> {
            if (!isToggled() || connection == null || connection != mc.getConnection()
                || mc.player == null || mc.level == null || mc.getCurrentServer() == null) return;
            checkSession();
            String text = packet.content().getString();
            if (ChatSender.fromPlayer(text, name -> connection.getPlayerInfoIgnoreCase(name) != null)) return;
            if (scheduler.message(text, retryMillis()) == SetHomeScheduler.Reply.IGNORED) return;
            confirmationScreen = null;
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
            confirmationScreen = null;
        }
        ClientPacketListener current = mc.getConnection();
        if (current != connectionSession) {
            connectionSession = current;
            scheduler.cancel();
            confirmationScreen = null;
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

    private boolean tryOverride() {
        Screen screen = mc.screen;
        if (!(screen instanceof DialogScreen<?>) || screen != confirmationScreen) return false;

        String phrase = dialogMatch.getValue();
        String buttonPhrase = buttonMatch.getValue();
        if (phrase.isBlank() || buttonPhrase.isBlank()) return false;

        List<String> labels = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) labels.add(widget.getMessage().getString());
        }
        if (!HomeOverride.isHomeDialog(screen.getTitle().getString(), labels, phrase)) return false;

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton button
                && button.active && button.visible
                && HomeOverride.isOverrideButton(button.getMessage().getString(), buttonPhrase)) {
                button.onClick(new MouseButtonEvent(
                    button.getX() + button.getWidth() / 2.0,
                    button.getY() + button.getHeight() / 2.0,
                    new MouseButtonInfo(0, 0)
                ), false);
                return true;
            }
        }
        return false;
    }

    private void onScreen(Screen screen) {
        if (scheduler != null && scheduler.waitingForDialog() && confirmationScreen == null && screen instanceof DialogScreen<?>) confirmationScreen = screen;
    }

    private long intervalMillis() {
        return Math.max(1, intervalSeconds.getValue() * 1_000L);
    }

    private long bufferMillis() {
        return Math.max(0, cooldownBufferSeconds.getValue() * 1_000L);
    }

    private long retryMillis() { return Math.max(0, retryDelaySeconds.getValue() * 1_000L); }

    @EventHandler
    private void onScreenChange(ScreenChangeEvent.AfterInit event) {
        onScreen(event.getNewScreen());
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        onDisable();
    }

    private String dimensionId() {
        return mc.level.dimension().identifier().toString();
    }
}

package dev.argus.client.gui;

import net.fabricmc.api.ClientModInitializer;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.modules.player.AutoRekitModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * Registers /argus (module GUI) and /social, /chattools and /friends (the Social menu, /friends on its Players page). The screen opens
 * on the next client tick because the chat screen closes itself right after a
 * command runs and would otherwise replace our window.
 */
public final class FriendsMod implements ClientModInitializer {

    private enum Request { NONE, MODULES, FRIENDS, CHAT }
    private static volatile Request request = Request.NONE;

    @Override
    public void onInitializeClient() {
        DescriptionCatalog.ensureApplied();
        ScreenEvents.AFTER_INIT.register(MultiplayerProfiles::install);
        ScreenEvents.AFTER_INIT.register(AutoRekitModule::addContainerButton);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("argus").executes(ctx -> {
                request = Request.MODULES;
                return 1;
            }));
            dispatcher.register(ClientCommands.literal("social").executes(ctx -> {
                request = Request.CHAT;
                return 1;
            }));
            dispatcher.register(ClientCommands.literal("chattools").executes(ctx -> {
                request = Request.CHAT;
                return 1;
            }));
            dispatcher.register(ClientCommands.literal("friends").executes(ctx -> {
                request = Request.FRIENDS;
                return 1;
            }));
        });

        // Singleplayer worlds load their bound presets on join (servers are handled by the core).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> Presets.onJoin(client)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GuiNavigation.tick();
            ChatToolsModule.syncBetterChat();
            ChatToolsScreen.attach();
            AutoRekitModule rekit = AutoRekitModule.get();
            if (rekit != null) rekit.tick(client);
            Request r = request;
            if (r == Request.NONE) return;
            request = Request.NONE;
            Minecraft mc = Minecraft.getInstance();
            if (r == Request.MODULES) mc.gui.setScreen(new ModulesScreen(null));
            else if (r == Request.CHAT) mc.gui.setScreen(new ChatToolsScreen(null, null));
            else mc.gui.setScreen(ChatToolsScreen.on(null, ChatToolsScreen.Page.PLAYERS));
        });
    }
}

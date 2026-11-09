package dev.argus.client.gui;

import net.fabricmc.api.ClientModInitializer;
import dev.argus.client.modules.player.AutoRekitModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * Registers /argus (module GUI) and /friends (friends window). The screen opens
 * on the next client tick because the chat screen closes itself right after a
 * command runs and would otherwise replace our window.
 */
public final class FriendsMod implements ClientModInitializer {

    private enum Request { NONE, MODULES, FRIENDS }
    private static volatile Request request = Request.NONE;

    @Override
    public void onInitializeClient() {
        DescriptionCatalog.ensureApplied();
        ScreenEvents.AFTER_INIT.register(MultiplayerProfiles::install);
        ScreenEvents.AFTER_INIT.register(AutoRekitModule::addContainerButton);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("argus").executes(ctx -> {
                request = Request.MODULES;
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("friends").executes(ctx -> {
                request = Request.FRIENDS;
                return 1;
            }));
        });

        // Singleplayer worlds load their bound presets on join (servers are handled by the core).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> Presets.onJoin(client)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GuiNavigation.tick();
            AutoRekitModule rekit = AutoRekitModule.get();
            if (rekit != null) rekit.tick(client);
            Request r = request;
            if (r == Request.NONE) return;
            request = Request.NONE;
            Minecraft mc = Minecraft.getInstance();
            if (r == Request.MODULES) mc.setScreen(new ModulesScreen(null));
            else mc.setScreen(new SettingsScreen(null, SettingsScreen.FRIENDS, null));
        });
    }
}

package dev.randomstuff.client.gui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Registers /randomstuff (module GUI) and /friends (friends window). The screen opens
 * on the next client tick because the chat screen closes itself right after a
 * command runs and would otherwise replace our window.
 */
public final class FriendsMod implements ClientModInitializer {

    private enum Request { NONE, MODULES, FRIENDS }
    private static volatile Request request = Request.NONE;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("randomstuff").executes(ctx -> {
                request = Request.MODULES;
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("friends").executes(ctx -> {
                request = Request.FRIENDS;
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GuiNavigation.tick();
            Request r = request;
            if (r == Request.NONE) return;
            request = Request.NONE;
            Minecraft mc = Minecraft.getInstance();
            if (r == Request.MODULES) mc.setScreen(new ModulesScreen(null));
            else mc.setScreen(new FriendsScreen(null));
        });
    }
}

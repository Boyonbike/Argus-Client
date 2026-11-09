package dev.argus.client.gui;

import dev.argus.client.network.ProfileRuntime;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/** Meteor's default multiplayer placement, using Argus buttons and typography. */
public final class MultiplayerProfiles {
    private MultiplayerProfiles() {}
    public static void install(Minecraft client, Screen screen, int width, int height) {
        if (!(screen instanceof JoinMultiplayerScreen)) return;
        var buttons=Screens.getButtons(screen);
        buttons.add(new UiButton(width-155,3,75,20,"Proxies",false,"shield",()->client.setScreen(new ProxiesScreen(screen))));
        buttons.add(new UiButton(width-78,3,75,20,"Accounts",false,"player",()->client.setScreen(new AccountsScreen(screen))));
        ScreenEvents.beforeRender(screen).register((s,g,mx,my,delta)-> {
            UiAnim.frame(); UiRender.opacity=1; UiDraw.density=(float)client.getWindow().getGuiScale();
        });
        ScreenEvents.afterRender(screen).register((s,g,mx,my,delta)-> {
            var theme=GuiState.theme();
            int available=Math.max(40,width-163);
            UiDraw.text(g,UiDraw.fit("Logged in as " + client.getUser().getName(),available,UiDraw.SMALL),3,9,UiDraw.SMALL,theme.text());
            var proxy=ProfileRuntime.activeProxy();
            String status=ProfileRuntime.error().isEmpty()?(proxy==null?"Not using a proxy":"Using proxy " + proxy.address()):"Couldn't load accounts/proxies";
            UiDraw.text(g,UiDraw.fit(status,available,UiDraw.SMALL),3,21,UiDraw.SMALL,theme.muted());
        });
    }
}

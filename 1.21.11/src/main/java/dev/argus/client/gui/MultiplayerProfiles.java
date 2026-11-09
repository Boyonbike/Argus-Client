package dev.argus.client.gui;

import dev.argus.client.network.ProfileRuntime;
import dev.argus.client.network.SessionRecovery;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/** Meteor's default multiplayer placement, using Argus buttons and typography. */
public final class MultiplayerProfiles {
    /**
     * Fabric fires AFTER_INIT twice when a screen is resized (once from the nested init, once from resize itself)
     * without clearing widgets in between. Fabric creates fresh event objects on every real re-init, so if this
     * screen's render event is the one we already used, our buttons are still there and must not be added again.
     */
    private static final Map<Screen, Object> INSTALLED = new WeakHashMap<>();
    private static final int PAD = 4, SIZE = 20;
    private MultiplayerProfiles() {}
    public static void install(Minecraft client, Screen screen, int width, int height) {
        if (!(screen instanceof JoinMultiplayerScreen)) return;
        var renderEvent = ScreenEvents.afterRender(screen);
        if (INSTALLED.get(screen) == renderEvent) return;
        INSTALLED.put(screen, renderEvent);
        SessionRecovery.checkSoon(client);
        var buttons=Screens.getButtons(screen);
        // The base client's "Vanilla" checkbox lives in the Accounts menu as "Appear as vanilla" now.
        buttons.removeIf(b -> b instanceof Checkbox && "Vanilla".equals(b.getMessage().getString()));
        buttons.add(new UiIconButton(PAD,PAD,SIZE,"shield","Proxies",()->client.setScreen(new ProxiesScreen(screen))));
        buttons.add(new UiIconButton(width-PAD-SIZE,PAD,SIZE,"player","Accounts",()->client.setScreen(new AccountsScreen(screen))));
        ScreenEvents.beforeRender(screen).register((s,g,mx,my,delta)-> {
            UiAnim.frame(); UiRender.opacity=1; UiDraw.density=(float)client.getWindow().getGuiScale();
        });
        ScreenEvents.afterRender(screen).register((s,g,mx,my,delta)-> {
            var theme=GuiState.theme();
            int textX=PAD+SIZE+5, available=Math.max(40,width-textX*2);
            UiDraw.text(g,UiDraw.fit("Logged in as " + client.getUser().getName(),available,UiDraw.SMALL),textX,PAD+5,UiDraw.SMALL,theme.text());
            var proxy=ProfileRuntime.activeProxy();
            String status = SessionRecovery.status(client);
            if (status.isEmpty()) {
                status = ProfileRuntime.error().isEmpty()
                    ? (proxy == null ? "Not using a proxy" : "Using proxy " + proxy.address()) : "Couldn't load accounts/proxies";
            }
            UiDraw.text(g,UiDraw.fit(status,available,UiDraw.SMALL),textX,PAD+15,UiDraw.SMALL,theme.muted());
        });
    }
}

package dev.argus.client.gui;

import dev.argus.client.network.ProfileRuntime;
import dev.argus.client.network.SessionRecovery;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import dev.argus.client.network.ProxyEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/** Account, proxy and profile buttons on the multiplayer screen, using Argus buttons and typography. */
public final class MultiplayerProfiles {
    /**
     * The extract events we already hooked, per screen. Fabric fires AFTER_INIT on every init <em>and</em> resize
     * (sometimes twice per resize), handing out fresh event objects on a real re-init but not in between, so this
     * keeps the render hooks to one set per event object.
     */
    private static final Map<Screen, Object> HOOKED = new WeakHashMap<>();
    private static final int PAD = 4, SIZE = 20;
    private MultiplayerProfiles() {}
    public static void install(Minecraft client, Screen screen, int width, int height) {
        // Catch an expired launcher token as soon as the game reaches its menus, not only once you open Multiplayer.
        if (screen instanceof TitleScreen) { SessionRecovery.checkSoon(client); return; }
        if (!(screen instanceof JoinMultiplayerScreen)) return;
        var buttons=Screens.getButtons(screen);
        // Coming back to this screen (closing Accounts, or a resize such as entering fullscreen) only repositions
        // the existing widgets, so ours are still there from last time, at the old size. Swap them for fresh ones
        // placed for the current size rather than stacking a second pair on top.
        buttons.removeIf(b -> b instanceof UiStatusButton<?>);
        // The base client's "Vanilla" checkbox lives in the Accounts menu as "Appear as vanilla" now.
        buttons.removeIf(b -> b instanceof Checkbox && "Vanilla".equals(b.getMessage().getString()));
        buttons.add(new UiStatusButton<ProxyEntry>(PAD,PAD,SIZE,"proxy",ProfileRuntime::activeProxy,
                MultiplayerProfiles::drawShield,
                (proxy,t)->proxy==null?null:t.green(),
                proxy->proxy==null?"Proxies (none active)":"Proxies (using "+proxy.address()+")",
                ()->client.screen==screen,
                ()->client.setScreen(new ProxiesScreen(screen))));
        buttons.add(new UiStatusButton<UUID>(width-PAD-SIZE,PAD,SIZE,"account",()->client.getUser().getProfileId(),
                MultiplayerProfiles::drawFace,
                (id,t)->null,
                id->"Accounts ("+client.getUser().getName()+")",
                ()->client.screen==screen,
                ()->client.setScreen(new AccountsScreen(screen))));
        var renderEvent = ScreenEvents.afterRender(screen);
        if (HOOKED.get(screen) == renderEvent) return;
        HOOKED.put(screen, renderEvent);
        SessionRecovery.checkSoon(client);
        ScreenEvents.beforeRender(screen).register((s,g,mx,my,delta)-> {
            UiAnim.frame(); UiRender.opacity=1; UiDraw.density=(float)client.getWindow().getGuiScale();
        });
        ScreenEvents.afterRender(screen).register((s,g,mx,my,delta)-> {
            // Only problems get a line of text now; the buttons themselves show the account and proxy.
            String status = SessionRecovery.status(client);
            if (status.isEmpty() && !ProfileRuntime.error().isEmpty()) status = "Couldn't load accounts/proxies";
            if (status.isEmpty()) return;
            var theme=GuiState.theme();
            int textX=PAD+SIZE+5, available=Math.max(40,s.width-textX*2);
            UiDraw.text(g,UiDraw.fit(status,available,UiDraw.SMALL),textX,PAD+SIZE/2f,UiDraw.SMALL,theme.muted());
        });
    }

    /**
     * Face lookups by account, so the outgoing face can still be drawn during the swap animation. Filled while
     * drawing rather than from a screen event: those don't fire when a dialog draws this screen underneath itself.
     */
    private static final Map<UUID, Supplier<PlayerSkin>> FACES = new HashMap<>();

    private static void drawShield(GuiGraphics g, ProxyEntry proxy, float cx, float cy, float r, float scale, float opacity, float hover, UiTheme t) {
        float size = 10 * scale;
        int colour = proxy == null ? UiDraw.mix(t.text(), t.accent(), hover) : UiDraw.mix(t.green(), t.text(), hover * .35f);
        UiDraw.icon(g, proxy == null ? "shield_off" : "shield_live", cx - size / 2, cy - size / 2, size, UiDraw.alpha(colour, opacity));
    }

    private static void drawFace(GuiGraphics g, UUID id, float cx, float cy, float r, float scale, float opacity, float hover, UiTheme t) {
        Supplier<PlayerSkin> lookup;
        if (id.equals(Minecraft.getInstance().getUser().getProfileId())) FACES.put(id, lookup = UiDraw.skin());
        else lookup = FACES.get(id);
        if (lookup == null) return;
        float size = (r * 2 - 4 + hover) * scale;
        UiDraw.face(g, lookup, cx - size / 2, cy - size / 2, size, opacity);
    }
}

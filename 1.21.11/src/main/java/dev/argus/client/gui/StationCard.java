package dev.argus.client.gui;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.movement.elytratrip.ChestRole;
import dev.argus.client.modules.movement.elytratrip.ElytraTripModule;
import dev.argus.client.modules.movement.elytratrip.TripLoadout;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraTrip's station card: switch setup mode on, see which chests hold which supply, and test the refill or Discord.
 *
 * <pre>
 *  Setup mode   Open a chest and click its role beside it      [Turn on]
 *  Loadout      7 elytras, 4 totems, 16 gapples, 64 food   [Clear] [Save inventory]   (setup mode only)
 *  ● Elytras    2 chests, nearest 4 blocks away
 *  ● Dump       None tagged. Worn gear stays in your inventory.
 *  ...
 *  [Test refill] [Test Discord]
 * </pre>
 */
final class StationCard extends InlineCard {
    private static final float SETUP = 34, LOADOUT = 34, ROLE = 20, ACTIONS = 30;

    private final ElytraTripModule trip;

    StationCard(UiScreen host, ElytraTripModule trip) {
        super(host);
        this.trip = trip;
        // A new station needs setting up first, so the card starts open with nothing tagged.
        open = true;
    }

    @Override String title() { return "Station"; }
    @Override String icon() { return "player"; }

    @Override String subtitle() {
        String server = trip.serverKey();
        if (server == null) return "Join the server to see its station";
        int tagged = trip.stations().all(server).size();
        boolean noElytras = trip.stations().missing(server).contains(ChestRole.ELYTRAS);
        if (tagged == 0) return "No chests tagged yet: turn on Setup mode and open each chest";
        return (tagged == 1 ? "1 chest tagged" : tagged + " chests tagged") + (noElytras ? ", no Elytras chest yet" : "");
    }

    @Override float bodyHeight() { return SETUP + (trip.setupModeSetting().getValue() ? LOADOUT : 0) + ChestRole.values().length * ROLE + ACTIONS + 4; }

    private static String missingText(ChestRole role) {
        return switch (role) {
            case ELYTRAS -> "None tagged. Trips can't start without it.";
            case DUMP -> "None tagged. Worn gear stays in your inventory.";
            default -> "None tagged. " + role.label() + " won't be restocked.";
        };
    }

    private String rowText(String server, ChestRole role) {
        LocalPlayer player = ClientAccess.argusclientMinecraft.player;
        List<BlockPos> chests = trip.stations().chests(server, role, player == null ? Vec3.ZERO : player.position());
        if (chests.isEmpty()) return missingText(role);
        String count = chests.size() == 1 ? "1 chest" : chests.size() + " chests";
        if (player == null) return count;
        long blocks = Math.round(Math.sqrt(Vec3.atCenterOf(chests.getFirst()).distanceToSqr(player.position())));
        return count + ", nearest " + (blocks >= 10_000 ? String.format("%.1fk", blocks / 1000.0) : Long.toString(blocks)) + " blocks away";
    }

    @Override void draw(GuiGraphics g, int mx, int my, UiTheme t, float left, float y, float w) {
        // Setup mode.
        boolean setup = trip.setupModeSetting().getValue();
        float cy = y + SETUP / 2;
        String toggle = setup ? "Turn off" : "Turn on";
        float toggleW = buttonWidth(toggle) + 14;
        UiDraw.text(g, "Setup mode", left + 4, cy - 6, t.text());
        UiDraw.text(g, UiDraw.fit(setup ? "On: open a chest and click its role in the buttons beside it" : "Turn on, then open each station chest to tag it",
                w - toggleW - 24, UiDraw.SMALL), left + 4, cy + 7, UiDraw.SMALL, t.textDim());
        button(g, mx, my, t, "setup", left + w - toggleW - 4, cy - 9, toggleW, 18, toggle, !setup,
                "Show role buttons on chest screens and outline tagged chests", () -> attempt(() -> {
                    trip.setupModeSetting().setValue(!setup);
                    if (!setup && !trip.isToggled()) trip.enable();
                }, setup ? "Setup mode is off." : "Setup mode is on. Open a station chest."));
        UiDraw.rect(g, left + 4, y + SETUP - .3f, w - 8, .6f, UiDraw.alpha(t.line(), .3f));

        // Loadout: while setting up, save what you carry as the amounts to restock to and the slot each item goes back in.
        float ry = y + SETUP;
        if (setup) {
            TripLoadout saved = trip.savedLoadout();
            float lcy = ry + LOADOUT / 2;
            String save = saved == null ? "Save inventory" : "Save again";
            float saveW = buttonWidth(save) + 6, clearW = saved == null ? 0 : buttonWidth("Clear") + 6;
            UiDraw.text(g, "Loadout", left + 4, lcy - 6, t.text());
            UiDraw.text(g, UiDraw.fit(saved == null ? "Lay out your inventory, then save it: refills restock to it and put items back in place"
                    : saved.summary() + ", slots remembered", w - saveW - clearW - 28, UiDraw.SMALL), left + 4, lcy + 7, UiDraw.SMALL, t.textDim());
            button(g, mx, my, t, "saveLoadout", left + w - saveW - 4, lcy - 9, saveW, 18, save, saved == null,
                    "Save how many elytras, totems, gapples and food you carry now, and which slot each item is in",
                    () -> attempt(() -> trip.saveLoadout(), "Inventory saved."));
            if (saved != null) button(g, mx, my, t, "clearLoadout", left + w - saveW - clearW - 8, lcy - 9, clearW, 18, "Clear", false,
                    "Go back to the targets in the Refill tab", () -> attempt(trip::clearLoadout, "Loadout cleared."));
            UiDraw.rect(g, left + 4, ry + LOADOUT - .3f, w - 8, .6f, UiDraw.alpha(t.line(), .3f));
            ry += LOADOUT;
        }

        // One row per role.
        String server = trip.serverKey();
        for (ChestRole role : ChestRole.values()) {
            float rcy = ry + ROLE / 2;
            boolean missing = server == null || trip.stations().chests(server, role, Vec3.ZERO).isEmpty();
            UiDraw.circle(g, left + 8, rcy, 3, missing ? UiDraw.alpha(t.textDim(), .5f) : role.colour());
            UiDraw.text(g, role.label(), left + 16, rcy, UiDraw.SMALL, t.text());
            String detail = server == null ? "Join the server to see this" : rowText(server, role);
            int colour = missing && role == ChestRole.ELYTRAS ? 0xFFEF4444 : t.textDim();
            UiDraw.text(g, UiDraw.fit(detail, w - 90, UiDraw.SMALL), left + 76, rcy, UiDraw.SMALL, colour);
            ry += ROLE;
        }

        // Tests.
        cy = ry + ACTIONS / 2 + 2;
        float refillW = buttonWidth("Test refill") + 6, discordW = buttonWidth("Test Discord") + 6;
        button(g, mx, my, t, "testRefill", left + 4, cy - 9, refillW, 18, "Test refill", false,
                "Dump, restock and equip from where you stand, with no teleports", () -> attempt(trip::testRefill, ""));
        button(g, mx, my, t, "testDiscord", left + 8 + refillW, cy - 9, discordW, 18, "Test Discord", false,
                "Send a test message to the Discord link in the Discord tab", () -> attempt(trip::testDiscord, ""));
    }
}

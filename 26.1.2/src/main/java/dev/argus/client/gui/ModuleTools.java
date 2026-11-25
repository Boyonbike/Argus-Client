package dev.argus.client.gui;

import dev.argus.client.modules.movement.elytratrip.ElytraTripModule;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.misc.AutoLobbyModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.modules.movement.BoatPhaseModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.utils.movement.VolytraElytraFlyHandler;
import dev.argus.client.modules.player.AutoRekitModule;
import dev.argus.client.modules.render.ESPModule;
import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.modules.render.TracersModule;
import dev.argus.client.modules.render.WaypointsModule;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.utils.world.TypedCoordinates;
import dev.argus.client.utils.world.Waypoint;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import dev.argus.client.modules.render.targets.TargetLinks;
import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Style;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Which tools a module's page offers besides its settings, and where. This is the one place to look (and to add
 * a module) so every module page works the same way:
 * <ul>
 *   <li>A tool is a card on the module's page, built from {@link InlineCard.Rows} (shortcuts, preferences) or
 *       {@link InlineCard.Items} (things the player manages). It folds like any settings card.</li>
 *   <li>It sits where it makes sense: {@link Placement#first()} when the page exists for it,
 *       {@link Placement#after(String)} next to the settings group it works with, {@link Placement#last()} for data
 *       the settings act on.</li>
 *   <li>A separate screen is only for something too big to fit in a card (an editor, a picker). It is opened
 *       from a clearly labelled button in the card that owns it, never from the top of the page.</li>
 * </ul>
 */
final class ModuleTools {
    /** What the host screen provides: where opened pages return to, and the top-bar tabs only the settings window has. */
    interface Hooks {
        Screen back();
        default boolean topBar() { return false; }
        default boolean socialOpensMenu() { return true; }
        default void flipSocialTab() {}
        default boolean hudOpensEditor() { return true; }
        default void flipHudTab() {}
        default boolean inHudEditor() { return false; }
        default void openHudEditor() {}
    }

    private ModuleTools() {}

    static void attach(ModulePage page, UiScreen host, Module module, Hooks hooks) {
        if (module instanceof AutoRekitModule rekit) page.inline(rekitPresets(host, rekit), Placement.last());
        if (module instanceof AutoLobbyModule lobby) page.inline(autoLobbyPresets(host, lobby), Placement.first());
        if (module instanceof TooltipsModule tooltips) page.inline(shulkerCombos(host, tooltips), Placement.after("ShulkerIcons"));
        if (module instanceof ChatToolsModule) page.inline(social(host, hooks), Placement.first());
        if (module instanceof DiscordSyncModule sync) page.inline(discordSync(host, sync), Placement.last());
        if (module instanceof WaypointsModule) page.inline(waypoints(host), Placement.first());
        if (module instanceof BoatPhaseModule boat) page.inline(boatControls(host, boat), Placement.first());
        if (module instanceof ElytraTripModule elytraTrip) {
            page.inline(new ElytraTripCard(host, elytraTrip), Placement.first());
            page.inline(new StationCard(host, elytraTrip), Placement.after("Trip"));
        }
        if (module instanceof ElytraFlyModule fly && fly.volytra() != null) page.inline(autonavTrip(host, fly.volytra()), Placement.after("Autonav"));
        if (module instanceof HUDModule && hooks.topBar()) page.inline(hud(host, hooks), Placement.first());
        if ((module instanceof ESPModule || module instanceof TracersModule) && TargetLinks.get() != null)
            page.inline(new TargetsCard(host, module, TargetLinks.get(), module instanceof ESPModule), Placement.first());
    }

    // ---- Waypoints ---------------------------------------------------------------------------

    private static boolean inWorld() { return Minecraft.getInstance().player != null && Minecraft.getInstance().level != null; }

    private static String coords(Waypoint w) {
        return String.format(Locale.ROOT, "%.0f, %.0f, %.0f", w.getX(), w.getY(), w.getZ());
    }

    /** The waypoints on the server you are on, or every waypoint (with its server) when you are not in a world. */
    private static List<Waypoint> shownWaypoints() {
        List<Waypoint> list = new ArrayList<>();
        boolean inWorld = inWorld();
        String server = inWorld ? WaypointRegistry.currentServerKey() : null;
        for (Waypoint w : ClientContext.waypointRegistry.getRegistry())
            if (w != null && w.getName() != null && w.getServer() != null && (server == null || server.equalsIgnoreCase(w.getServer()))) list.add(w);
        if (inWorld) {
            Vec3 at = Minecraft.getInstance().player.position();
            list.sort(java.util.Comparator.comparingDouble(w -> w.distanceTo(at)));
        }
        return list;
    }

    /**
     * Adds a waypoint from the Name, X, Y and Z boxes. With X and Z blank it goes where you stand; with only Y blank
     * it takes your height. Any whole or decimal number works, with or without a + or - sign. For older habits,
     * "name x y z" or "name x z" typed into the Name box still works.
     */
    private static void addWaypoint(List<String> values) {
        if (!inWorld()) throw new IllegalStateException("Join a world to add a waypoint.");
        Vec3 here = Minecraft.getInstance().player.position();
        String name = values.get(0);
        String xText = values.get(1), yText = values.get(2), zText = values.get(3);
        if (xText.isEmpty() && yText.isEmpty() && zText.isEmpty()) {
            // Coordinates typed after the name, as the old single box took them.
            String[] parts = name.split("\\s+");
            int count = parts.length >= 4 && TypedCoordinates.parse(parts[parts.length - 3]) != null ? 3 : parts.length >= 3 ? 2 : 0;
            if (count > 0 && java.util.Arrays.stream(parts, parts.length - count, parts.length).allMatch(part -> TypedCoordinates.parse(part) != null)) {
                xText = parts[parts.length - count];
                yText = count == 3 ? parts[parts.length - 2] : "";
                zText = parts[parts.length - 1];
                name = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - count));
            }
        }
        if (name.isEmpty()) throw new IllegalArgumentException("Type a name for the waypoint.");
        Vec3 position = here;
        if (!xText.isEmpty() || !yText.isEmpty() || !zText.isEmpty()) {
            if (xText.isEmpty() || zText.isEmpty()) throw new IllegalArgumentException("Type both X and Z, or leave all three blank to use where you stand.");
            Double x = TypedCoordinates.parse(xText), z = TypedCoordinates.parse(zText);
            Double y = yText.isEmpty() ? Double.valueOf(here.y) : TypedCoordinates.parse(yText);
            if (x == null) throw new IllegalArgumentException(badCoordinate("X", xText));
            if (y == null) throw new IllegalArgumentException(badCoordinate("Y", yText));
            if (z == null) throw new IllegalArgumentException(badCoordinate("Z", zText));
            position = new Vec3(x, y, z);
        }
        String dimension = WorldUtils.getDimension().getDisplayName().toLowerCase(Locale.ROOT);
        if (!ClientContext.waypointRegistry.registerWaypoint(new Waypoint(name, position, dimension, WaypointRegistry.currentServerKey())))
            throw new IllegalArgumentException("There is already a waypoint called " + name + " on this server.");
        ClientContext.configurationManager.saveAsync();
    }

    private static String badCoordinate(String axis, String text) {
        return axis + " must be a number from -30,000,000 to 30,000,000, such as 120 or -45 (you typed " + text + ").";
    }

    /** Fills the coordinate boxes with where you stand, rounded to the block. */
    private static void fillHere(List<UiInput> boxes) {
        if (!inWorld()) throw new IllegalStateException("Join a world first.");
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        boxes.get(0).setValue(Integer.toString(player.getBlockX()));
        boxes.get(1).setValue(Integer.toString(player.getBlockY()));
        boxes.get(2).setValue(Integer.toString(player.getBlockZ()));
    }

    /**
     * " in the Nether" for the dimensions Argus knows (stored as "overworld", "nether", "end" or as ids such as
     * "minecraft:the_nether"); anything else is shown as it is after a comma.
     */
    private static String dimensionPhrase(String dimension) {
        if (dimension == null || dimension.isBlank()) return "";
        String key = dimension.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        return switch (key) {
            case "overworld" -> " in the Overworld";
            case "nether", "the_nether" -> " in the Nether";
            case "end", "the_end" -> " in the End";
            default -> ", " + dimension;
        };
    }

    private static InlineCard waypoints(UiScreen host) {
        List<UiInput> coords = new ArrayList<>();
        for (String axis : List.of("X", "Y", "Z")) {
            UiInput box = new UiInput(0, 0, 60, 16, axis.equals("Y") ? "Optional" : "");
            box.setMaxLength(12);
            box.setFilter(TypedCoordinates::typing);
            coords.add(box);
        }
        return new InlineCard.Items(host, "Waypoints", "compass",
                () -> {
                    int n = shownWaypoints().size();
                    String count = n == 0 ? "No waypoints" : n + (n == 1 ? " waypoint" : " waypoints");
                    String sync = dev.argus.client.integrations.xaero.XaeroWaypointSync.enabled() ? ", synced with Xaero" : "";
                    return inWorld() ? count + " on " + WaypointRegistry.currentServerKey() + sync : count + " on all servers" + sync + " (join a world to add one)";
                },
                inWorld() ? "No waypoints on this server yet." : "No waypoints yet.",
                () -> {
                    boolean inWorld = inWorld();
                    List<InlineCard.Items.Entry> entries = new ArrayList<>();
                    for (Waypoint w : shownWaypoints()) {
                        String detail = coords(w) + dimensionPhrase(w.getDimension()) + (inWorld ? "" : " on " + w.getServer());
                        entries.add(new InlineCard.Items.Entry(w.getName(), detail, false, null, null, null, List.of(
                                InlineCard.Items.Action.on(w.isToggled() ? "Shown" : "Hidden", "Show or hide this waypoint", w.isToggled(), () -> {
                                    w.setToggled(!w.isToggled());
                                    ClientContext.configurationManager.saveAsync();
                                }),
                                InlineCard.Items.Action.danger("Delete", "Remove this waypoint", "Deleted " + w.getName() + ".", () -> {
                                    ClientContext.waypointRegistry.unregisterWaypoint(w);
                                    ClientContext.configurationManager.saveAsync();
                                }))));
                    }
                    return entries;
                })
                .field("Name", 64, "Add", "Adds the waypoint at X, Y and Z, or where you stand if they are blank (Enter)",
                        text -> "Added waypoint " + text.trim() + ".", text -> {})
                .extraFields(List.of("X", "Y", "Z"), coords, ModuleTools::addWaypoint)
                .header(InlineCard.Items.Action.of("Here", "Fill X, Y and Z with where you stand", "", () -> fillHere(coords)));
    }

    // ---- Boat Phase --------------------------------------------------------------------------

    private static InlineCard boatControls(UiScreen host, BoatPhaseModule module) {
        return new InlineCard.Rows(host, "Boat controls", "star", "Act on the boat you are driving; the same actions can be bound to keys below")
                .open("Quick dive", "Start a dive, or cancel the one in progress", "Dive / cancel", module::toggleDive)
                .open("Cruise", "Keep flying along your heading until you press WASD", "Cruise / cancel", module::toggleCruise)
                .open("Rise to a clear pocket", "Rise to the next gap with room for the boat and its passengers", "Rise", module::surface)
                .open("Return to entry height", "Go back to the height where you boarded", "Return", module::returnHeight)
                .open("Resume adaptive phase", "Clear the slow-step fallback after a correction", "Resume", module::resumeAdaptivePhase)
                .open("Retry fast air", "Clear speed fallbacks and try the configured speed again", "Retry", module::retryFastAir);
    }

    // ---- Elytra Fly (Volytra Autonav) ---------------------------------------------------------

    /** Type or pick where to go, Start/Pause/Stop the trip, and keep a list of saved destinations. */
    private static InlineCard autonavTrip(UiScreen host, VolytraElytraFlyHandler volytra) {
        return new AutonavCard(host, volytra);
    }

    // ---- Auto Rekit --------------------------------------------------------------------------

    private static InlineCard rekitPresets(UiScreen host, AutoRekitModule module) {
        return new InlineCard.Items(host, "Rekit presets", "star",
                () -> module.activePreset.getValue().isEmpty() ? "Save your inventory, armour and offhand, then pick a preset" : "In use: " + module.activePreset.getValue(),
                "No presets yet. Arrange your inventory, name it and save.",
                () -> {
                    List<InlineCard.Items.Entry> entries = new ArrayList<>();
                    for (String name : module.presetNames()) entries.add(new InlineCard.Items.Entry(name, "", name.equals(module.activePreset.getValue()), null,
                            () -> module.selectPreset(name), "Active preset: " + name, List.of(
                            InlineCard.Items.Action.of("Update", "Overwrite this preset with your current inventory", "Updated and activated " + name + ".", () -> module.capturePreset(name)),
                            InlineCard.Items.Action.danger("Delete", "Remove this preset", "Deleted " + name + ".", () -> module.deletePreset(name)))));
                    return entries;
                })
                .field("Name a new preset…", 32, "Save inventory", "Saves what you are carrying and wearing under this name",
                        name -> "Saved and activated " + name + ".", module::capturePreset);
    }

    // ---- AutoLobby ---------------------------------------------------------------------------

    private static InlineCard autoLobbyPresets(UiScreen host, AutoLobbyModule module) {
        String name = AutoLobbyModule.PRESET_6B6T;
        return new InlineCard.Items(host, "AutoLobby presets", "star",
                () -> module.matches6b6tPreset() ? "In use: " + name + ", " + lobbyPasswords() : "Click a preset to apply it (" + lobbyPasswords() + ")",
                "No presets.",
                () -> List.of(new InlineCard.Items.Entry(name, "Comet's defaults for 6b6t.org, AutoReconnect 3.5s", module.matches6b6tPreset(), null,
                        module::apply6b6tPreset, "Applied the " + name + " preset. Account passwords are unchanged.", List.of())))
                .hint("Each cracked account's /login password is set in Accounts")
                .header(InlineCard.Items.Action.of("Accounts", "Set a lobby password for each cracked account; AutoLobby uses the one you are playing as",
                        null, () -> Minecraft.getInstance().setScreen(new AccountsScreen(host))));
    }

    private static String lobbyPasswords() {
        int n = AutoLobbyModule.accountsWithLobbyPassword();
        return n == 0 ? "no account passwords set" : n + (n == 1 ? " account password set" : " account passwords set");
    }

    // ---- Tooltips: shulker icon combos -------------------------------------------------------

    private static InlineCard shulkerCombos(UiScreen host, TooltipsModule module) {
        return new InlineCard.Items(host, "Shulker icon combos", "star",
                () -> { int n = module.getIconRules().size(); return (n == 0 ? "No combos yet" : n + (n == 1 ? " combo" : " combos") + ", first match wins")
                        + (module.shulkerIconsSetting.getValue() ? "" : ". Needs Overlay on"); },
                "No combos yet. Without one, the most common item is shown.",
                () -> {
                    List<Rule<Item>> rules = module.getIconRules();
                    List<InlineCard.Items.Entry> entries = new ArrayList<>();
                    for (int i = 0; i < rules.size(); i++) {
                        int index = i;
                        Rule<Item> rule = rules.get(i);
                        int required = rule.required().size();
                        entries.add(new InlineCard.Items.Entry((i + 1) + ". " + rule.name(), required + (required == 1 ? " required item" : " required items"), false,
                                (g, x, y) -> {
                                    if (rule.pixels() == null) ShulkerIconRenderer.drawAt(g, rule.icon().getDefaultInstance(), rule.style() == Style.SolidColour ? rule.colour() : null, x, y, 1);
                                    else ShulkerIconRenderer.drawPixels(g, rule.pixels(), x, y, 1);
                                }, null, null, List.of(
                                InlineCard.Items.Action.on(rule.enabled() ? "On" : "Off", "Turn this combo on or off", rule.enabled(), () -> {
                                    List<Rule<Item>> next = new ArrayList<>(rules);
                                    next.set(index, rule.withEnabled(!rule.enabled()));
                                    module.setIconRules(next);
                                }),
                                InlineCard.Items.Action.of("Edit", "Change the name, matching items and icon", null, () -> host.hostOpen(new ShulkerComboEditScreen(host, module, index))),
                                InlineCard.Items.Action.of("^", "Move up", null, () -> move(module, index, index - 1)),
                                InlineCard.Items.Action.of("v", "Move down", null, () -> move(module, index, index + 1)),
                                InlineCard.Items.Action.danger("Delete", "Remove this combo", null, () -> {
                                    List<Rule<Item>> next = new ArrayList<>(rules);
                                    next.remove(index);
                                    module.setIconRules(next);
                                }))));
                    }
                    return entries;
                })
                .hint("Match shulkers by name or contents")
                .header(InlineCard.Items.Action.danger("Remove all", "Delete every combo", null, () -> module.setIconRules(List.of())))
                .header(InlineCard.Items.Action.main("Add combo", "Create a combo and edit its icon", null, () -> {
                    List<Rule<Item>> rules = new ArrayList<>(module.getIconRules());
                    rules.add(new Rule<>("Combo " + (rules.size() + 1), true, List.of(), Items.DIAMOND_CHESTPLATE));
                    module.setIconRules(rules);
                    host.hostOpen(new ShulkerComboEditScreen(host, module, rules.size() - 1));
                }));
    }

    private static void move(TooltipsModule module, int from, int to) {
        List<Rule<Item>> rules = new ArrayList<>(module.getIconRules());
        if (to < 0 || to >= rules.size()) return;
        Collections.swap(rules, from, to);
        module.setIconRules(rules);
    }

    // ---- Chat Tools (Social) -----------------------------------------------------------------

    private static InlineCard social(UiScreen host, Hooks hooks) {
        InlineCard.Rows rows = new InlineCard.Rows(host, "Social menu", "people", "Messages, players, clan, homes, teleports and Auto TPY")
                .open("Social menu", "Messages, players, clan, homes, teleports and Auto TPY", "Open menu",
                        () -> host.hostOpen(new ChatToolsScreen(hooks.back(), null)));
        if (hooks.topBar()) rows.choice("Social tab opens", "What the Social tab in the top bar shows", new String[]{"Menu", "Settings"},
                () -> hooks.socialOpensMenu() ? 0 : 1, choice -> { if ((choice == 0) != hooks.socialOpensMenu()) hooks.flipSocialTab(); });
        return rows;
    }

    // ---- Discord Sync ------------------------------------------------------------------------

    private static InlineCard discordSync(UiScreen host, DiscordSyncModule sync) {
        return new InlineCard.Rows(host, "Sync", "people", "Keep the clan list and Auto TPY whitelist up to date")
                .open("Read Discord now", "Updates the clan list and Auto TPY whitelist. Details appear in chat.",
                        () -> sync.syncBusy() ? "Syncing…" : sync.syncFailed() ? "Try again"
                                : sync.lastSuccessfulSync() > 0 ? "Sync again" : "Sync now", sync::syncNow);
    }

    // ---- HUD ---------------------------------------------------------------------------------

    private static InlineCard hud(UiScreen host, Hooks hooks) {
        return new InlineCard.Rows(host, "HUD editor", "hud", "")
                .open("HUD editor", "Add, remove and arrange HUD elements on your screen",
                        () -> hooks.inHudEditor() ? "Back to editor" : "Open editor", hooks::openHudEditor)
                .choice("HUD tab opens", "What the HUD tab in the top bar shows", new String[]{"Editor", "Settings"},
                        () -> hooks.hudOpensEditor() ? 0 : 1, choice -> { if ((choice == 0) != hooks.hudOpensEditor()) hooks.flipHudTab(); });
    }
}

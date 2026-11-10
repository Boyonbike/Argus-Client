package dev.argus.client.gui;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.modules.movement.BoatPhaseModule;
import dev.argus.client.modules.player.AutoRekitModule;
import dev.argus.client.modules.render.ESPModule;
import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.modules.render.TracersModule;
import dev.argus.client.modules.render.targets.TargetLinks;
import dev.argus.client.modules.render.targets.TargetSettings;
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
        if (module instanceof TooltipsModule tooltips) page.inline(shulkerCombos(host, tooltips), Placement.after("ShulkerIcons"));
        if (module instanceof ChatToolsModule) page.inline(social(host, hooks), Placement.first());
        if (module instanceof DiscordSyncModule sync) page.inline(discordSync(host, sync), Placement.last());
        if (module instanceof BoatPhaseModule boat) page.inline(boatControls(host, boat), Placement.first());
        if (module instanceof HUDModule && hooks.topBar()) page.inline(hud(host, hooks), Placement.first());
        if (module instanceof ESPModule) page.inline(pairing(host, true), Placement.first());
        if (module instanceof TracersModule) page.inline(pairing(host, false), Placement.first());
    }

    // ---- ESP / Tracers pairing ---------------------------------------------------------------------------------

    /**
     * One row per target card. "Toggle in X" switches the card on or off in the other module (switching on copies this
     * page's settings for it once); "Mirror" keeps both cards identical until "Break link".
     */
    private static InlineCard pairing(UiScreen host, boolean fromEsp) {
        TargetLinks links = TargetLinks.get();
        String other = links == null ? (fromEsp ? "Tracers" : "ESP") : links.otherName(fromEsp);
        return new InlineCard.Items(host, "Pair with " + other, "star",
                () -> {
                    if (links == null) return "Unavailable";
                    long mirrored = java.util.Arrays.stream(TargetSettings.Kind.values()).filter(links::mirrored).count();
                    return mirrored == 0 ? "Copy a card to " + other + ", or mirror it so both stay the same"
                            : mirrored + (mirrored == 1 ? " card mirrored" : " cards mirrored") + " with " + other;
                },
                "Pairing isn't available.",
                () -> {
                    List<InlineCard.Items.Entry> entries = new ArrayList<>();
                    if (links == null) return entries;
                    for (TargetSettings.Kind kind : TargetSettings.Kind.values()) {
                        boolean mirrored = links.mirrored(kind);
                        boolean on = links.onInOther(fromEsp, kind);
                        String detail = mirrored ? "Mirrored · changes on either page apply to both"
                                : on ? "Separate · on in " + other : "Separate · off in " + other;
                        entries.add(new InlineCard.Items.Entry(kind.title(), detail, mirrored, null, null, null, List.of(
                                InlineCard.Items.Action.on("Toggle in " + other,
                                        on ? "Turn " + kind.title() + " off in " + other
                                           : "Turn " + kind.title() + " on in " + other + (mirrored ? "" : ", starting from this page's settings"),
                                        on, () -> links.toggleInOther(fromEsp, kind)),
                                mirrored
                                        ? InlineCard.Items.Action.danger("Break link", "Stop mirroring; both keep their current settings",
                                                kind.title() + " is no longer mirrored.", () -> links.breakLink(kind))
                                        : InlineCard.Items.Action.main("Mirror", "Copy this page's " + kind.title() + " settings to " + other + " and keep them the same",
                                                kind.title() + " mirrored with " + other + ".", () -> links.mirror(fromEsp, kind)))));
                    }
                    return entries;
                });
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

    // ---- Tooltips: shulker icon combos -------------------------------------------------------

    private static InlineCard shulkerCombos(UiScreen host, TooltipsModule module) {
        return new InlineCard.Items(host, "Shulker icon combos", "star",
                () -> { int n = module.getIconRules().size(); return (n == 0 ? "No combos yet" : n + (n == 1 ? " combo" : " combos")) + " · first match wins · needs Overlay on"; },
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
                        () -> sync.syncBusy() ? "Syncing…" : sync.syncFailed() ? "Failed · try again"
                                : sync.lastSuccessfulSync() > 0 ? "Synced " + sync.syncedAccounts() + " · again" : "Sync now", sync::syncNow);
    }

    // ---- HUD ---------------------------------------------------------------------------------

    private static InlineCard hud(UiScreen host, Hooks hooks) {
        return new InlineCard.Rows(host, "HUD editor", "hud", "Arrange HUD elements on your screen")
                .open("HUD editor", "Add, remove and arrange HUD elements on your screen",
                        () -> hooks.inHudEditor() ? "Back to editor" : "Open editor", hooks::openHudEditor)
                .choice("HUD tab opens", "What the HUD tab in the top bar shows", new String[]{"Editor", "Settings"},
                        () -> hooks.hudOpensEditor() ? 0 : 1, choice -> { if ((choice == 0) != hooks.hudOpensEditor()) hooks.flipHudTab(); });
    }
}

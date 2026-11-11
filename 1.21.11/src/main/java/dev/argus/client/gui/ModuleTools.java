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
import dev.argus.client.modules.render.targets.RuleSettings;
import dev.argus.client.modules.render.targets.SpecificRules;
import dev.argus.client.modules.render.targets.SpecificTargets;
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
import net.minecraft.world.item.ItemStack;
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
        if (module instanceof ESPModule) targets(page, host, true);
        if (module instanceof TracersModule) targets(page, host, false);
    }

    // ---- ESP / Tracers targets --------------------------------------------------------------------------------

    /**
     * ESP's and Tracers' target tools:
     * <ul>
     *   <li>a "Specific targets" card on top: search for a block, entity, dropped item or player and add it; one row per
     *       added target, with On/Off, "Toggle in X", "Mirror"/"Break link" and remove. Clicking a row opens its settings;</li>
     *   <li>one settings card per added target, right under that list, with the settings that suit its kind and a note
     *       saying whether it's linked with the other module;</li>
     *   <li>"Toggle in X" and "Mirror" on the Players/Mobs/Items/Blocks cards' headers, with the same note.</li>
     * </ul>
     */
    private static void targets(ModulePage page, UiScreen host, boolean fromEsp) {
        TargetLinks links = TargetLinks.get();
        if (links == null) return;
        String other = links.otherName(fromEsp);
        SpecificTargets specific = links.specific(fromEsp);
        InlineCard.Items card = specificTargets(page, host, links, specific, fromEsp, other);
        page.inline(card, Placement.first());
        page.dynamic(card, () -> {
            List<SettingsCards.Dynamic> cards = new ArrayList<>();
            for (SpecificRules.Rule rule : specific.rules().all()) {
                RuleSettings settings = specific.settings(rule);
                SpecificTargets.Candidate what = specific.describe(rule);
                ItemStack icon = what.icon();
                cards.add(new SettingsCards.Dynamic(settings, what.name(),
                        icon == null ? null : (g, x, y) -> ShulkerIconRenderer.drawAt(g, icon, null, x, y, .75f),
                        settings.settings(),
                        new SettingsCards.Decoration(() -> rule.type.title() + " target" + (rule.enabled ? "" : " (off)") + " · "
                                + (rule.mirror ? "Linked with " + other + ": changes apply to both" : "Only this page"), null)));
            }
            return cards;
        });
        for (TargetSettings.Kind kind : TargetSettings.Kind.values()) {
            page.decorate(kind.title(), new SettingsCards.Decoration(
                    () -> links.mirrored(kind) ? "Linked with " + other + ": changes apply to both"
                            : links.onInOther(fromEsp, kind) ? "Also on in " + other : "",
                    () -> {
                        boolean on = links.onInOther(fromEsp, kind);
                        boolean mirrored = links.mirrored(kind);
                        return List.of(
                                new SettingsCards.HeaderButton("Toggle in " + other,
                                        on ? "Turn " + kind.title() + " off in " + other
                                           : "Turn " + kind.title() + " on in " + other + (mirrored ? "" : ", starting from this card's settings"),
                                        on, () -> links.toggleInOther(fromEsp, kind)),
                                mirrored
                                        ? new SettingsCards.HeaderButton("Break link", "Stop linking; both keep their current settings", true, () -> links.breakLink(kind))
                                        : new SettingsCards.HeaderButton("Mirror", "Copy this card to " + other + " and keep both the same", false, () -> links.mirror(fromEsp, kind)));
                    }));
        }
    }

    private static InlineCard.Items specificTargets(ModulePage page, UiScreen host, TargetLinks links, SpecificTargets specific, boolean fromEsp, String other) {
        InlineCard.Items card = new InlineCard.Items(host, "Specific targets", "search",
                () -> {
                    List<SpecificRules.Rule> rules = specific.rules().all();
                    if (rules.isEmpty()) return "Search to add one block, mob, item or player with its own settings";
                    long linked = rules.stream().filter(rule -> rule.mirror).count();
                    return rules.size() + (rules.size() == 1 ? " target" : " targets") + (linked == 0 ? "" : " · " + linked + " linked with " + other)
                            + " · settings in the cards below";
                },
                "Nothing added yet. Search above for a block, mob, item or player.",
                () -> {
                    List<InlineCard.Items.Entry> entries = new ArrayList<>();
                    for (SpecificRules.Rule rule : specific.rules().all()) entries.add(ruleEntry(page, links, fromEsp, other, specific, rule));
                    return entries;
                });
        card.tags((entry, hover) -> hover ? "Show settings" : "");
        card.field("Search blocks, mobs, items, players...", 64, "Add",
                "Adds the first match. Type block:, mob:, item: or player: first to search one kind only.", text -> "", text -> {});
        card.suggest(text -> {
            List<InlineCard.Items.Suggestion> found = new ArrayList<>();
            for (SpecificTargets.Candidate candidate : SpecificTargets.search(text, 6)) {
                boolean added = specific.rules().find(candidate.type(), candidate.id()) != null;
                ItemStack icon = candidate.icon();
                found.add(new InlineCard.Items.Suggestion(candidate.name(), candidate.type().title() + (added ? " · added" : ""),
                        icon == null ? null : (g, x, y) -> ShulkerIconRenderer.drawAt(g, icon, null, x, y, 1),
                        () -> {
                            SpecificRules.Rule rule = specific.rules().find(candidate.type(), candidate.id());
                            if (rule == null) {
                                rule = specific.rules().add(candidate.type(), candidate.id());
                                specific.changed();
                            }
                            page.reveal(specific.settings(rule));
                        },
                        added ? candidate.name() + " is already added." : "Added " + candidate.name() + ". Its settings are open below."));
            }
            return found;
        }, "No block, mob, item or player matches. Online players show up by name; use player:Name for anyone else.");
        return card;
    }

    private static InlineCard.Items.Entry ruleEntry(ModulePage page, TargetLinks links, boolean fromEsp, String other,
                                                    SpecificTargets specific, SpecificRules.Rule rule) {
        SpecificTargets.Candidate what = specific.describe(rule);
        String name = what.name();
        boolean mirrored = rule.mirror;
        boolean on = links.onInOther(fromEsp, rule);
        RuleSettings settings = specific.settings(rule);
        String detail = rule.type.title() + " · " + settings.rangeLabel() + " · "
                + (mirrored ? "Linked with " + other : on ? "Also on in " + other : "Only this page");
        ItemStack icon = what.icon();
        SpecificRules.Type type = rule.type;
        String id = rule.id;
        Runnable toggle = () -> {
            SpecificRules.Rule current = specific.rules().find(type, id);
            if (current == null) return;
            current.enabled = !current.enabled;
            specific.changed();
            links.syncRules();
        };
        return new InlineCard.Items.Entry(name, detail, rule.enabled,
                icon == null ? (g, x, y) -> UiDraw.rounded(g, x + 2, y + 2, 12, 12, 3, settings.swatch() | 0xFF000000)
                             : (g, x, y) -> ShulkerIconRenderer.drawAt(g, icon, null, x, y, 1),
                () -> page.reveal(settings), null, List.of(
                InlineCard.Items.Action.on(rule.enabled ? "On" : "Off", rule.enabled ? "Turn " + name + " off on this page" : "Turn " + name + " on on this page",
                        rule.enabled, toggle),
                InlineCard.Items.Action.on("Toggle in " + other,
                        on ? "Turn " + name + " off in " + other
                           : "Add " + name + " to " + other + (mirrored ? "" : ", starting from these settings"),
                        on, () -> links.toggleInOther(fromEsp, rule)),
                mirrored
                        ? InlineCard.Items.Action.main("Break link", "Stop linking; both keep their current settings",
                                name + " is no longer linked.", () -> links.breakLink(rule))
                        : InlineCard.Items.Action.of("Mirror", "Add " + name + " to " + other + " with these settings and keep both the same",
                                name + " linked with " + other + ".", () -> links.mirror(fromEsp, rule)),
                InlineCard.Items.Action.danger("X", mirrored ? "Remove " + name + " from both pages" : "Remove " + name + " from this page",
                        "Removed " + name + ".", () -> links.remove(fromEsp, rule))));
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

package dev.argus.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a finished Discord read does to the clan list and Auto TPY whitelist. The sync never empties a list: an
 * unreadable or empty read leaves both as they were. Names you added by hand always stay; only names an earlier sync
 * added are removed again when Discord stops listing them.
 */
public final class DiscordSyncApply {
    /** The two destination lists and, for each, the names a sync added (so it may remove them again). */
    public record Lists(List<String> clan, List<String> clanSynced, List<String> whitelist, List<String> whitelistSynced) {
        public Lists {
            clan = List.copyOf(clan); clanSynced = List.copyOf(clanSynced);
            whitelist = List.copyOf(whitelist); whitelistSynced = List.copyOf(whitelistSynced);
        }
    }

    public enum Kind {
        /** The names were applied. */
        APPLIED,
        /** Some #ign posts had no readable text; nothing changed. */
        UNREADABLE,
        /** Readable, but nobody is linked; lists unchanged and Discord role access cleared. */
        EMPTY
    }

    /**
     * @param upgradedOwnership the old tracking couldn't tell synced names from manual ones, so they were all kept as manual
     * @param changes one summary per list changed, such as "clan list +2/-1"
     */
    public record Outcome(Kind kind, Lists lists, boolean upgradedOwnership, List<String> changes) {}

    private DiscordSyncApply() {}

    /**
     * @param trustedOwnership whether the synced-name lists were written by a version that only records names the sync
     *                         itself added; older data also listed manual entries and can't be trusted to remove them
     */
    public static Outcome apply(DiscordIgn.Result result, Lists before, boolean toClan, boolean toWhitelist, boolean trustedOwnership) {
        if (result.unreadable()) return new Outcome(Kind.UNREADABLE, before, false, List.of());
        if (result.names().isEmpty()) return new Outcome(Kind.EMPTY, before, false, List.of());
        List<String> clan = before.clan(), clanSynced = before.clanSynced();
        List<String> whitelist = before.whitelist(), whitelistSynced = before.whitelistSynced();
        List<String> changes = new ArrayList<>();
        if (toClan) {
            DiscordIgn.Update update = DiscordIgn.update(clan, clanSynced, result.names(), trustedOwnership);
            changes.add("clan list +" + added(clan, update.names()) + "/-" + removed(clan, update.names()));
            clan = update.names();
            clanSynced = update.managed();
        } else if (!trustedOwnership) clanSynced = List.of();
        if (toWhitelist) {
            DiscordIgn.Update update = DiscordIgn.update(whitelist, whitelistSynced, result.names(), trustedOwnership);
            changes.add("Auto TPY whitelist +" + added(whitelist, update.names()) + "/-" + removed(whitelist, update.names()));
            whitelist = update.names();
            whitelistSynced = update.managed();
        } else if (!trustedOwnership) whitelistSynced = List.of();
        boolean upgraded = !trustedOwnership && (!before.clanSynced().isEmpty() || !before.whitelistSynced().isEmpty());
        return new Outcome(Kind.APPLIED, new Lists(clan, clanSynced, whitelist, whitelistSynced), upgraded, List.copyOf(changes));
    }

    static int added(List<String> before, List<String> after) {
        Set<String> had = ChatClan.keys(before);
        int count = 0;
        for (String name : after) if (!had.contains(name.toLowerCase(Locale.ROOT))) count++;
        return count;
    }

    static int removed(List<String> before, List<String> after) {
        Set<String> has = ChatClan.keys(after);
        int count = 0;
        for (String name : before) if (!has.contains(name.toLowerCase(Locale.ROOT))) count++;
        return count;
    }
}

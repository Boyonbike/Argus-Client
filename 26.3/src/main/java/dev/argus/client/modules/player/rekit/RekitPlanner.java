package dev.argus.client.modules.player.rekit;

import java.util.List;
import java.util.Objects;

/** Pure transfer decisions. Preset identity and Minecraft stackability are deliberately separate. */
public final class RekitPlanner {
    private RekitPlanner() {}

    public record Stack(Object matchKey, Object mergeKey, int count, int maximum, int durability) {
        public static final Stack EMPTY = new Stack(null, null, 0, 64, -1);
        boolean empty() { return count <= 0 || matchKey == null; }
        boolean matches(Stack other) { return !empty() && !other.empty() && Objects.equals(matchKey, other.matchKey); }
        boolean merges(Stack other) { return matches(other) && Objects.equals(mergeKey, other.mergeKey); }
    }

    public record Move(int source, int amount, boolean replacing) {}

    public static Move choose(Stack saved, Stack current, List<Stack> sources,
                              boolean fullStacks, boolean largerStacks, boolean betterDurability) {
        return choose(saved, current, sources, fullStacks, largerStacks, betterDurability, true);
    }

    public static Move choose(Stack saved, Stack current, List<Stack> sources,
                              boolean fullStacks, boolean largerStacks, boolean betterDurability, boolean canSplitReplacement) {
        if (saved.empty()) return null;
        int target = Math.min(saved.maximum, fullStacks ? saved.maximum : saved.count);
        boolean matches = saved.matches(current);
        if (matches && current.durability < 0 && current.count >= target) return null;
        // Fill using a stack Minecraft can actually combine before considering a replacement.
        if (matches && current.count < target) {
            for (int i = 0; i < sources.size(); i++) {
                Stack source = sources.get(i);
                if (current.merges(source)) return new Move(i, Math.min(source.count, target - current.count), false);
            }
        }
        int best = -1, score = -1;
        for (int i = 0; i < sources.size(); i++) {
            Stack source = sources.get(i);
            if (!saved.matches(source)) continue;
            if (!current.empty() && source.count > target && !canSplitReplacement) continue;
            if (matches) {
                if (current.durability >= 0) {
                    if (!betterDurability || source.durability <= current.durability) continue;
                } else if (!largerStacks || current.merges(source) || Math.min(source.count, target) <= current.count) continue;
            }
            int candidate = source.durability >= 0 ? source.durability : Math.min(source.count, target);
            if (candidate > score) { best = i; score = candidate; }
        }
        return best < 0 ? null : new Move(best, Math.min(sources.get(best).count, target), !current.empty());
    }
}

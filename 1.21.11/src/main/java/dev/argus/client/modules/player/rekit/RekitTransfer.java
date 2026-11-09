package dev.argus.client.modules.player.rekit;

import java.util.ArrayList;
import java.util.List;

/** One atomic pickup transaction, with no cursor stack left between ticks. */
public final class RekitTransfer {
    private RekitTransfer() {}
    public record Click(int slot, int button, boolean swap) {
        public Click(int slot, int button) { this(slot, button, false); }
    }

    public static List<Click> offhandPlan(int source, int hotbar, int sourceCount, int amount, boolean replacing, int spare) {
        List<Click> transfer = plan(source, hotbar, sourceCount, amount, replacing, spare);
        if (transfer.isEmpty()) return List.of();
        List<Click> clicks = new ArrayList<>();
        clicks.add(new Click(hotbar, 40, true));
        clicks.addAll(transfer);
        clicks.add(new Click(hotbar, 40, true));
        return List.copyOf(clicks);
    }

    public static List<Click> plan(int source, int destination, int sourceCount, int amount,
                                   boolean replacing, int spare) {
        if (amount <= 0 || amount > sourceCount || source == destination) return List.of();
        List<Click> clicks = new ArrayList<>();
        if (replacing && amount < sourceCount) {
            if (spare < 0 || spare == source || spare == destination) return List.of();
            clicks.add(new Click(destination, 0));
            clicks.add(new Click(spare, 0));
        }
        clicks.add(new Click(source, 0));
        if (amount == sourceCount) {
            clicks.add(new Click(destination, 0));
            if (replacing) clicks.add(new Click(source, 0));
        } else {
            for (int i = 0; i < amount; i++) clicks.add(new Click(destination, 1));
            clicks.add(new Click(source, 0));
        }
        return List.copyOf(clicks);
    }
}

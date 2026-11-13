package dev.argus.client.chat;

import java.util.ArrayList;
import java.util.List;

/** Shared wall-clock countdown text for the chat tools window and HUD. */
public final class ChatCountdowns {
    private ChatCountdowns() {}
    public static String request(ChatTpaRequests.Request request, long now) {
        return (request.toThem() ? "Travel to " : "Request from ") + request.player() + ": "
            + ChatTeleportState.remaining(request.expires() - now);
    }
    public static List<String> lines(List<ChatTeleportState.Timer> timers, List<ChatTpaRequests.Request> requests, long now) {
        List<String> result = new ArrayList<>();
        for (var timer : timers) result.add(timer.label(now));
        for (var request : requests) result.add(request(request, now));
        return List.copyOf(result);
    }
    /**
     * One kind's HUD line: its warmup ("Teleporting to base in 5s"), then its cooldown ("Home teleportation cooldown: 30s"),
     * or "" when neither is running. Home and player each have their own line, so both cooldowns can show at once.
     */
    public static String hud(ChatTeleportState.Kind kind, List<ChatTeleportState.Timer> timers, long now) {
        return timers.stream().filter(t -> t.kind() == kind).map(t -> t.hud(now)).filter(s -> !s.isEmpty()).findFirst().orElse("");
    }
    /** Incoming requests in one line: the soonest to time out, and how many are waiting. */
    public static String requests(List<ChatTpaRequests.Request> requests, long now) {
        if (requests.isEmpty()) return "";
        var soonest = requests.stream().min(java.util.Comparator.comparingLong(ChatTpaRequests.Request::expires)).orElseThrow();
        String first = (soonest.toThem() ? "Travel to " : "Request from ") + soonest.player() + " " + ChatTeleportState.remaining(soonest.expires() - now);
        return requests.size() == 1 ? first : first + " (+" + (requests.size() - 1) + " more)";
    }
    public static String player(String player, List<ChatTeleportState.Timer> timers, List<ChatTpaRequests.Request> requests, long now) {
        List<String> result = new ArrayList<>();
        for (var timer : timers) if (timer.kind() == ChatTeleportState.Kind.TPA && timer.target().equalsIgnoreCase(player)) result.add(timer.status(now));
        for (var request : requests) if (request.player().equalsIgnoreCase(player)) result.add("Incoming request " + ChatTeleportState.remaining(request.expires() - now));
        return String.join(" \u00b7 ", result);
    }
    public static String home(String home, List<ChatTeleportState.Timer> timers, long now) {
        return timers.stream().filter(t -> t.kind() == ChatTeleportState.Kind.Home && t.target().equalsIgnoreCase(home))
            .map(t -> t.status(now)).findFirst().orElse("");
    }
}

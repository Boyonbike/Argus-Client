package dev.argus.client.chat;

/**
 * Keeps BetterChat (hidden, the engine) switched on and off together with Chat Tools (shown). Whichever of the two was
 * switched since the last check wins, so a keybind, the classic GUI or a loaded preset can still turn chat on or off.
 * The first check after BetterChat was merged turns Chat Tools on if an older config had BetterChat on.
 */
public final class ChatModuleLink {
    /** What both modules should be, and whether the one-time merge has now happened. */
    public record Decision(boolean on, boolean merged) {}

    private Boolean lastChatTools, lastBetterChat;

    /**
     * @param merged whether a previous session already merged BetterChat's state into Chat Tools
     */
    public Decision decide(boolean merged, boolean chatTools, boolean betterChat) {
        boolean on;
        if (!merged) on = chatTools || betterChat;
        else if (lastChatTools != null && chatTools != lastChatTools) on = chatTools;
        else if (lastBetterChat != null && betterChat != lastBetterChat) on = betterChat;
        else on = chatTools;
        lastChatTools = on;
        lastBetterChat = on;
        return new Decision(on, true);
    }
}

package dev.argus.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Splits BetterChat's settings (a flat list in the closed base client) into the cards shown on the Chat Tools page. A
 * card starts at the first setting of a known name and runs until the next one, so settings added in later versions
 * land in the card of the setting before them instead of disappearing.
 */
final class ChatSettingsSections {
    record Span(String title, String icon, int from, int to) {}

    /** The first setting of each card, in BetterChat's order. */
    private static final Map<String, String[]> STARTS = Map.of(
        "Timestamps", new String[] {"Chat style", "chat"},
        "MentionSound", new String[] {"Sounds", "bell"},
        "Animation", new String[] {"Animation", "render"},
        "Message", new String[] {"Prefix & suffix", "font"},
        "Highlight", new String[] {"Highlights", "people"},
        "LongChatHistory", new String[] {"History", "sliders"});

    private ChatSettingsSections() {}

    static List<Span> split(List<String> names) {
        List<Span> spans = new ArrayList<>();
        String title = "Chat style", icon = "chat";
        int from = 0;
        for (int i = 0; i < names.size(); i++) {
            String[] start = STARTS.get(names.get(i));
            if (start == null || i == from) { if (start != null) { title = start[0]; icon = start[1]; } continue; }
            spans.add(new Span(title, icon, from, i));
            title = start[0]; icon = start[1]; from = i;
        }
        if (from < names.size()) spans.add(new Span(title, icon, from, names.size()));
        return spans;
    }
}

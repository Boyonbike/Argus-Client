package dev.argus.client.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatSettingsSectionsTest {
    /** BetterChat's settings in the base client's order. */
    private static final List<String> BETTER_CHAT = List.of("Timestamps", "OnlyServer", "Format", "TextColor", "BracketsColor",
        "Rainbow", "RainbowAmount", "LeftBracket", "RightBracket", "MentionSound", "SoundMode", "Volume", "PMSound", "SoundMode",
        "Volume", "Animation", "Type", "Speed", "Alpha", "Always", "Message", "Prefix", "Suffix", "Highlight", "Self", "Friends",
        "Enemies", "Color", "LongChatHistory", "NoReset");

    @Test void betterChatSplitsIntoSixCards() {
        var spans = ChatSettingsSections.split(BETTER_CHAT);
        assertEquals(List.of("Chat style", "Sounds", "Animation", "Prefix & suffix", "Highlights", "History"),
            spans.stream().map(ChatSettingsSections.Span::title).toList());
        assertEquals(new ChatSettingsSections.Span("Chat style", "chat", 0, 9), spans.get(0));
        assertEquals(new ChatSettingsSections.Span("Sounds", "bell", 9, 15), spans.get(1));
        assertEquals(new ChatSettingsSections.Span("History", "sliders", 28, 30), spans.getLast());
    }

    @Test void everySettingLandsInExactlyOneCard() {
        var spans = ChatSettingsSections.split(BETTER_CHAT);
        int expected = 0;
        for (var span : spans) { assertEquals(expected, span.from()); expected = span.to(); }
        assertEquals(BETTER_CHAT.size(), expected);
    }

    @Test void unknownSettingsJoinTheCardBeforeThem() {
        var spans = ChatSettingsSections.split(List.of("Timestamps", "NewThing", "MentionSound", "Volume"));
        assertEquals(List.of(new ChatSettingsSections.Span("Chat style", "chat", 0, 2), new ChatSettingsSections.Span("Sounds", "bell", 2, 4)), spans);
        assertEquals(List.of(new ChatSettingsSections.Span("Chat style", "chat", 0, 1)), ChatSettingsSections.split(List.of("Brand new")));
        assertEquals(List.of(), ChatSettingsSections.split(List.of()));
    }
}

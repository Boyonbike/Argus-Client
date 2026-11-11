package dev.argus.client.chat;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** BetterChat adds its prefix and suffix as the queued message is sent; the queue must still recognise its own message. */
class ChatPublicQueuePrefixTest {
    @Test void aDecoratedPacketStillMatchesTheQueuedText() {
        assertEquals("hello there", ChatPublicQueue.queuedText("hello there", "> hello there ⋆ suffix"));
        assertEquals("hello there", ChatPublicQueue.queuedText("hello there", "hello there"));
    }

    @Test void otherChatIsLeftAsItWas() {
        assertEquals("typed by hand", ChatPublicQueue.queuedText(null, "typed by hand"));
        assertEquals("something else", ChatPublicQueue.queuedText("hello", "something else"));
        assertEquals("anything", ChatPublicQueue.queuedText("   ", "anything"));
    }

    @Test void aRefusalAfterADecoratedSendRequeuesTheMessageOnce() {
        var queue = new ChatPublicQueue();
        queue.minimum(0);
        assertTrue(queue.add("hello there"));
        String text = queue.poll(1_000);
        assertEquals("hello there", text);
        // What the server received carries BetterChat's prefix and suffix; the queue is told about its own text.
        queue.sent(ChatPublicQueue.queuedText(text, "> hello there ⋆"), 1_001);
        assertTrue(queue.refused("Please wait 2 seconds before sending another message.", 1_050),
            "The refusal belongs to the queued message, so it is sent again");
        assertEquals(List.of("hello there"), queue.entries(1_050).stream().map(ChatPublicQueue.Queued::text).toList(),
            "The plain text goes back, so the prefix is added once when it is resent");
    }
}

package dev.argus.client.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatModuleLinkTest {
    @Test void anOldConfigWithBetterChatOnTurnsChatToolsOnOnce() {
        var link = new ChatModuleLink();
        assertEquals(new ChatModuleLink.Decision(true, true), link.decide(false, false, true));
        assertEquals(new ChatModuleLink.Decision(false, true), new ChatModuleLink().decide(false, false, false));
        assertEquals(new ChatModuleLink.Decision(true, true), new ChatModuleLink().decide(false, true, false),
            "Chat Tools already on keeps BetterChat on too");
    }

    @Test void afterTheMergeChatToolsDecidesAtStartup() {
        assertFalse(new ChatModuleLink().decide(true, false, true).on(), "A stale BetterChat toggle doesn't win on the first check");
        assertTrue(new ChatModuleLink().decide(true, true, false).on());
    }

    @Test void switchingEitherModuleSwitchesBoth() {
        var link = new ChatModuleLink();
        assertTrue(link.decide(true, true, true).on());
        assertFalse(link.decide(true, false, true).on(), "Turning Chat Tools off turns BetterChat off");
        assertTrue(link.decide(true, false, true).on(), "Turning BetterChat on (keybind, classic GUI) turns Chat Tools on");
        assertTrue(link.decide(true, true, true).on(), "Nothing changed, nothing moves");
        assertFalse(link.decide(true, true, false).on(), "Turning BetterChat off turns Chat Tools off");
    }

    @Test void chatToolsWinsWhenBothChangeAtOnce() {
        var link = new ChatModuleLink();
        link.decide(true, true, true);
        assertFalse(link.decide(true, false, false).on());
        assertTrue(link.decide(true, true, false).on());
    }
}

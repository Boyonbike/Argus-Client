package dev.argus.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UiTextTest {
    @Test
    void stripsMinecraftFormattingCodes() {
        assertEquals("Primarily used for NewChunks", UiText.stripFormatting("§fPrimarily used for NewChunks"));
        assertEquals("Makes sure there are no ghost blocks.\nNot recommended on 2b2t.",
                UiText.stripFormatting("Makes sure there are no ghost blocks.\n§cNot recommended on 2b2t."));
        assertEquals("Red", UiText.stripFormatting("§x§F§F§0§0§0§0Red"));
    }

    @Test
    void handlesMissingAndPlainText() {
        assertEquals("", UiText.stripFormatting(null));
        assertEquals("Plain description.", UiText.stripFormatting("Plain description."));
        assertEquals("trailing", UiText.stripFormatting("trailing§"));
    }
}

package dev.argus.client.gui;

/** Text helpers shared by the custom screens. */
final class UiText {
    private UiText() {}

    /**
     * Removes legacy Minecraft formatting codes ({@code §} followed by one character). The
     * built-in client descriptions still contain these colour escapes; this GUI draws plain
     * text, so they would otherwise appear as stray section signs.
     */
    static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        StringBuilder plain = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00a7') {
                if (i + 1 < text.length()) i++;
                continue;
            }
            plain.append(ch);
        }
        return plain.toString();
    }
}

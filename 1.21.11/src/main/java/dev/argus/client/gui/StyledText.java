package dev.argus.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Chat text drawn with the menu's own font instead of Minecraft's bitmap font, so it is rasterised at the exact screen
 * size and stays sharp. Keeps each part's colour, bold, underline and strikethrough, and wraps on spaces.
 */
final class StyledText {
    private StyledText() {}

    /** A piece of a line in one style. {@code colour} is 0 when the text uses the default colour. */
    record Run(String text, int colour, boolean bold, boolean underline, boolean strike) {
        boolean sameStyle(Run other) {
            return colour == other.colour && bold == other.bold && underline == other.underline && strike == other.strike;
        }
    }

    /** A wrapped line: its runs and where each starts, in logical units from the line's left edge. */
    record Line(List<Run> runs, float[] offsets, float width) {}

    /** A chat component's runs, with any legacy § formatting codes in its text applied. */
    static List<Run> runs(Component text) { return runs(text.getVisualOrderText()); }

    static List<Run> runs(FormattedCharSequence text) {
        List<Run> runs = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        Style[] current = {null};
        text.accept((index, style, codePoint) -> {
            if (current[0] != null && !current[0].equals(style)) { add(runs, pending.toString(), current[0]); pending.setLength(0); }
            current[0] = style;
            pending.appendCodePoint(codePoint);
            return true;
        });
        if (current[0] != null && !pending.isEmpty()) add(runs, pending.toString(), current[0]);
        return runs;
    }

    private static void add(List<Run> runs, String text, Style style) {
        TextColor colour = style.getColor();
        Run run = new Run(text, colour == null ? 0 : 0xff000000 | colour.getValue(), style.isBold(), style.isUnderlined(), style.isStrikethrough());
        if (!runs.isEmpty() && runs.getLast().sameStyle(run)) runs.set(runs.size() - 1, new Run(runs.getLast().text() + text, run.colour, run.bold, run.underline, run.strike));
        else runs.add(run);
    }

    static float width(Run run, float size) {
        float width = UiDraw.width(run.text(), size);
        // Bold glyphs advance a little further (see UiFont.drawCentred).
        return run.bold() ? width + run.text().codePointCount(0, run.text().length()) * .35f / UiDraw.density : width;
    }

    /** Wraps runs into lines no wider than {@code available}, breaking at spaces and inside words that don't fit. */
    static List<Line> wrap(List<Run> runs, float available, float size) {
        List<Line> lines = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        float used = 0;
        for (Run run : runs) {
            String text = run.text();
            int start = 0;
            while (start < text.length()) {
                if (text.charAt(start) == '\n') {
                    lines.add(line(line, size)); line = new ArrayList<>(); used = 0; start++;
                    continue;
                }
                // The next token: a run of spaces, or a word.
                boolean space = text.charAt(start) == ' ';
                int end = start;
                while (end < text.length() && text.charAt(end) != '\n' && (text.charAt(end) == ' ') == space) end++;
                Run token = with(run, text.substring(start, end));
                float tokenWidth = width(token, size);
                if (used + tokenWidth <= available || (line.isEmpty() && space)) {
                    if (!(line.isEmpty() && space && !lines.isEmpty())) { line.add(token); used += tokenWidth; }
                } else if (space) {
                    lines.add(line(line, size)); line = new ArrayList<>(); used = 0;
                } else if (tokenWidth <= available) {
                    lines.add(line(line, size)); line = new ArrayList<>();
                    line.add(token); used = tokenWidth;
                } else {
                    // A word longer than a whole line: break it where it runs out of room.
                    String rest = token.text();
                    while (!rest.isEmpty()) {
                        int fit = fit(with(run, rest), available - used, size);
                        if (fit == 0 && line.isEmpty()) fit = Character.charCount(rest.codePointAt(0));
                        if (fit == 0) { lines.add(line(line, size)); line = new ArrayList<>(); used = 0; continue; }
                        Run piece = with(run, rest.substring(0, fit));
                        line.add(piece); used += width(piece, size);
                        rest = rest.substring(fit);
                        if (!rest.isEmpty()) { lines.add(line(line, size)); line = new ArrayList<>(); used = 0; }
                    }
                }
                start = end;
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) lines.add(line(line, size));
        return lines;
    }

    private static Run with(Run style, String text) { return new Run(text, style.colour(), style.bold(), style.underline(), style.strike()); }

    /** How many chars of {@code run} fit in {@code available}. */
    private static int fit(Run run, float available, float size) {
        String text = run.text();
        int fit = 0;
        for (int i = 0; i < text.length(); ) {
            int next = i + Character.charCount(text.codePointAt(i));
            if (width(with(run, text.substring(0, next)), size) > available) break;
            fit = next; i = next;
        }
        return fit;
    }

    /** Joins neighbouring tokens of the same style and records where each run starts. */
    private static Line line(List<Run> tokens, float size) {
        List<Run> merged = new ArrayList<>();
        for (Run token : tokens) {
            if (!merged.isEmpty() && merged.getLast().sameStyle(token)) merged.set(merged.size() - 1, with(token, merged.getLast().text() + token.text()));
            else merged.add(token);
        }
        float[] offsets = new float[merged.size()];
        float pen = 0;
        for (int i = 0; i < merged.size(); i++) { offsets[i] = pen; pen += width(merged.get(i), size); }
        return new Line(List.copyOf(merged), offsets, pen);
    }

    /** Draws a line with its cap height centred on {@code centreY}. */
    static void draw(net.minecraft.client.gui.GuiGraphics g, Line line, float x, float centreY, float size, int defaultColour) {
        for (int i = 0; i < line.runs().size(); i++) {
            Run run = line.runs().get(i);
            int colour = run.colour() == 0 ? defaultColour : (run.colour() & 0x00ffffff) | (defaultColour & 0xff000000);
            float rx = x + line.offsets()[i];
            UiFont.drawCentred(g, run.text(), rx, centreY, size, colour, run.bold());
            if (run.underline() || run.strike()) {
                float w = width(run, size), thickness = Math.max(.5f, 1 / UiDraw.density);
                if (run.underline()) UiDraw.rect(g, rx, centreY + size * .45f, w, thickness, colour);
                if (run.strike()) UiDraw.rect(g, rx, centreY, w, thickness, colour);
            }
        }
    }
}

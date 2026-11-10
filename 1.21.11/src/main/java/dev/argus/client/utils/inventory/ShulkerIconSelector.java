package dev.argus.client.utils.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Item identity is supplied by the caller; components such as damage and enchantments are ignored. */
public final class ShulkerIconSelector {
    private ShulkerIconSelector() {}

    public record Entry<T>(T item, int count) {}

    public enum Style { Normal, SolidColour }

    public enum NameMode { Contains, Exact, StartsWith, EndsWith }
    public enum ContentsMode { All, Any, Exact }

    public record Match(String name, NameMode nameMode, ContentsMode contentsMode, int minimum) {
        public static final Match DEFAULT = new Match("", NameMode.Contains, ContentsMode.All, 1);

        public Match {
            name = name.strip();
            minimum = Math.max(1, minimum);
        }

        private boolean matchesName(String customName) {
            if (name.isEmpty()) return true;
            String actual = customName.toLowerCase(Locale.ROOT);
            String expected = name.toLowerCase(Locale.ROOT);
            return switch (nameMode) {
                case Contains -> actual.contains(expected);
                case Exact -> actual.equals(expected);
                case StartsWith -> actual.startsWith(expected);
                case EndsWith -> actual.endsWith(expected);
            };
        }

        private <T> boolean matchesContents(List<T> required, Map<T, Long> counts) {
            if (required.isEmpty()) return !name.isEmpty();
            return switch (contentsMode) {
                case All -> required.stream().allMatch(item -> counts.getOrDefault(item, 0L) >= minimum);
                case Any -> required.stream().anyMatch(item -> counts.getOrDefault(item, 0L) >= minimum);
                case Exact -> required.size() == counts.size()
                    && required.stream().allMatch(item -> counts.getOrDefault(item, 0L) >= minimum);
            };
        }
    }

    public record Icon<T>(T item, Style style, int colour, PixelIcon pixels) {}

    public record Rule<T>(String name, boolean enabled, List<T> required, T icon, Style style, int colour,
                          Match match, PixelIcon pixels) {
        public Rule(String name, boolean enabled, List<T> required, T icon) {
            this(name, enabled, required, icon, Style.Normal, 0xffffffff);
        }

        public Rule(String name, boolean enabled, List<T> required, T icon, Style style, int colour) {
            this(name, enabled, required, icon, style, colour, Match.DEFAULT, null);
        }

        public Rule {
            required = required.stream().distinct().toList();
            if (style == null) style = Style.Normal;
        }

        public Rule<T> withEnabled(boolean enabled) {
            return new Rule<>(name, enabled, required, icon, style, colour, match, pixels);
        }
    }

    /** First matching combo wins. Otherwise count quantities, breaking ties by the first occupied slot. */
    public static <T> T select(Iterable<Entry<T>> contents, List<Rule<T>> rules) {
        Icon<T> icon = selectIcon(contents, rules);
        return icon == null ? null : icon.item();
    }

    public static <T> Icon<T> selectIcon(Iterable<Entry<T>> contents, List<Rule<T>> rules) {
        return selectIcon(contents, "", rules);
    }

    public static <T> Icon<T> selectIcon(Iterable<Entry<T>> contents, String customName, List<Rule<T>> rules) {
        Map<T, Long> counts = new LinkedHashMap<>();
        for (Entry<T> entry : contents) {
            if (entry.item() != null && entry.count() > 0) counts.merge(entry.item(), (long) entry.count(), Long::sum);
        }
        for (Rule<T> rule : rules) {
            if (rule.enabled() && rule.icon() != null
                && rule.match().matchesName(customName) && rule.match().matchesContents(rule.required(), counts)) {
                return new Icon<>(rule.icon(), rule.style(), rule.colour(), rule.pixels());
            }
        }
        T best = null;
        long largest = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > largest) {
                best = entry.getKey();
                largest = entry.getValue();
            }
        }
        return best == null ? null : new Icon<>(best, Style.Normal, 0xffffffff, null);
    }
}

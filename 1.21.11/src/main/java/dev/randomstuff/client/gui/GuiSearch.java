package dev.randomstuff.client.gui;

import java.util.*;

/** A small standalone index: exact/alias matches first, then substrings and typing errors. */
public final class GuiSearch {
    private GuiSearch() {}
    public record Entry(String module, String category, List<String> aliases, List<String> settings) {}
    public record Hit(String module, String category, String setting, String alias, int score) {
        public boolean isSetting() { return setting != null; }
    }
    public static List<Hit> search(List<Entry> entries, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (Entry entry : entries) {
            int best = score(entry.module, q); String alias = null;
            for (String candidate : entry.aliases) {
                int value = score(candidate, q);
                if (value < best) { best = value; alias = candidate; }
            }
            if (best != Integer.MAX_VALUE) hits.add(new Hit(entry.module, entry.category, null, alias, best));
            for (String setting : entry.settings) {
                int value = score(setting, q);
                if (value != Integer.MAX_VALUE) hits.add(new Hit(entry.module, entry.category, setting, null, value + 2));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).thenComparing(Hit::module, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(h -> h.setting == null ? "" : h.setting, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(hits);
    }
    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    private static int score(String text, String query) {
        String value = normalize(text);
        if (value.equals(query)) return 0;
        if (value.startsWith(query)) return 5 + Math.min(8, value.length() - query.length());
        if (value.contains(query)) return 16 + Math.min(8, value.length() - query.length());
        if (query.length() < 3 || value.isEmpty()) return Integer.MAX_VALUE;
        int allowed = Math.max(1, Math.min(3, query.length() / 4));
        int distance = distance(value, query);
        return distance <= allowed ? 30 + distance * 4 : Integer.MAX_VALUE;
    }
    private static int distance(String first, String second) {
        int[] row = new int[second.length() + 1];
        for (int j = 0; j < row.length; j++) row[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            int diagonal = row[0]; row[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int old = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), diagonal + (first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1));
                diagonal = old;
            }
        }
        return row[second.length()];
    }
}

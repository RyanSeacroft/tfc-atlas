package dev.ryan.tfcatlas.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Complete the comma-separated token at the caret without replacing other entries. */
public final class SearchCompletion {
    public record Match(int start, int end, String text, String suffix) {
        public String apply(String value) {
            return value.substring(0, start) + text + value.substring(end);
        }

        public int cursor() {
            return start + text.length();
        }
    }

    public static Match suggest(
            String value, int cursor, Collection<String> options, boolean focused) {
        return focused ? suggest(value, cursor, options) : null;
    }

    private static boolean separator(char character) {
        return character == ',' || character == '\n' || character == '\r';
    }

    public static Match suggest(String value, int cursor, Collection<String> options) {
        cursor = Math.max(0, Math.min(value.length(), cursor));
        int start = cursor, end = cursor;
        while (start > 0 && !separator(value.charAt(start - 1))) {
            start--;
        }
        while (end < value.length() && !separator(value.charAt(end))) {
            end++;
        }
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        if (cursor <= start) {
            return null;
        }
        if (!value.substring(cursor, end).isBlank()) {
            return null;
        }
        String prefix = value.substring(start, cursor).toLowerCase(Locale.ROOT).replace('_', ' ');
        Set<String> selected =
                new HashSet<>(
                        SearchQuery.names(
                                value.substring(0, Math.max(0, start))
                                        + ","
                                        + value.substring(end)));
        String best = null;
        for (String option : options) {
            String text = option.toLowerCase(Locale.ROOT);
            // An exact token is complete even when a longer name shares its prefix.
            if (text.equals(prefix)) {
                return null;
            }
            if (text.startsWith(prefix)
                    && !text.equals(prefix)
                    && !selected.contains(text.replace(' ', '_'))) {
                if (best == null
                        || text.length() < best.length()
                        || text.length() == best.length() && text.compareTo(best) < 0) {
                    best = text;
                }
            }
        }
        return best == null ? null : new Match(start, end, best, best.substring(prefix.length()));
    }
}

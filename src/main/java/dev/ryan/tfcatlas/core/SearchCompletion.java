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

    public static Match suggest(String value, int cursor, Collection<String> options) {
        cursor = Math.max(0, Math.min(value.length(), cursor));
        int start = value.lastIndexOf(',', Math.max(-1, cursor - 1)) + 1,
                end = value.indexOf(',', cursor);
        if (end < 0) {
            end = value.length();
        }
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        if (cursor <= start) {
            return null;
        }
        String prefix = value.substring(start, cursor).toLowerCase(Locale.ROOT).replace('_', ' ');
        Set<String> selected =
                new HashSet<>(SearchQuery.names(value.substring(0, Math.max(0, start))));
        for (String option : options) {
            String text = option.toLowerCase(Locale.ROOT);
            if (text.startsWith(prefix)
                    && !text.equals(prefix)
                    && !selected.contains(text.replace(' ', '_'))) {
                return new Match(start, end, text, text.substring(prefix.length()));
            }
        }
        return null;
    }
}

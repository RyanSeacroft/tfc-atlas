package dev.ryan.tfcatlas.core;

import java.util.List;

/** Fits complete rows and reserves room for an overflow hint; never cuts a label in half. */
public final class KeyLayout {
    public record Fit(int count, int hidden, int height) {}

    public static Fit fit(List<Integer> rows, int header, int available, int footer) {
        int total = header + rows.stream().mapToInt(Integer::intValue).sum();
        if (total <= available) {
            return new Fit(rows.size(), 0, total);
        }
        int used = header, count = 0;
        while (count < rows.size() && used + rows.get(count) + footer <= available) {
            used += rows.get(count++);
        }
        return new Fit(count, rows.size() - count, Math.min(available, used + footer));
    }
}

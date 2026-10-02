package dev.ryan.tfcatlas.core;

/** One stable phase label per search, with a bounded rate of visible counter changes. */
public final class SearchProgress {
    private final String phase;
    private boolean published;
    private long lastMillis;
    private int percent;

    public SearchProgress(boolean underground, boolean height) {
        phase =
                underground
                        ? "Searching underground rock"
                        : height ? "Predicting surface Y" : "Searching";
    }

    public String initial() {
        return phase + " · 0% · 0 matching cells";
    }

    public String update(long completed, long total, long matches, long nowMillis) {
        if (published && nowMillis - lastMillis < 150 && completed < total) {
            return null;
        }
        percent = Math.max(percent, (int) Math.min(100, completed * 100 / Math.max(1, total)));
        published = true;
        lastMillis = nowMillis;
        return phase + " · " + percent + "% · " + matches + " matching cells";
    }
}

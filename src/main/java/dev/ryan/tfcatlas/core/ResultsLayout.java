package dev.ryan.tfcatlas.core;

/** Compact space for five result rows and the two optional footer messages. */
public final class ResultsLayout {
    public static int rowPitch(int height) {
        return Math.max(14, (height - 144) / 5);
    }

    public static int statusY(int height, boolean hasAdvice) {
        return height - (hasAdvice ? 64 : 67);
    }

    private ResultsLayout() {}
}

package dev.ryan.tfcatlas.core;

/** Equal-width family and custom columns share one list height and footer. */
public record GroupMenuLayout(
        int left,
        int width,
        int columnWidth,
        int right,
        int dividerX,
        int listY,
        int listHeight,
        int actionsY) {
    public static GroupMenuLayout of(int width, int height) {
        int content = Math.min(width - 24, 720);
        int column = (content - 12) / 2, left = (width - content) / 2;
        return new GroupMenuLayout(
                left,
                content,
                column,
                left + column + 12,
                left + column + 5,
                60,
                Math.max(24, height - 124),
                height - 56);
    }
}

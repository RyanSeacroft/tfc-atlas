package dev.ryan.tfcatlas.core;

/** Tooltip bounds are calculated in physical GUI pixels, including the vanilla border. */
public record TooltipLayout(float scale, int x, int y) {
    public static TooltipLayout fit(
            int screenW, int screenH, int mouseX, int mouseY, int textW, int textH) {
        float scale =
                Math.min(
                        .75f,
                        Math.min((screenW - 8f) / (textW + 8f), (screenH - 8f) / (textH + 8f)));
        scale = Math.max(.01f, scale);
        int w = (int) Math.ceil((textW + 8) * scale), h = (int) Math.ceil((textH + 8) * scale);
        int left = mouseX + 10;
        if (left + w > screenW - 4) {
            left = mouseX - 10 - w;
        }
        left = Math.max(4, Math.min(screenW - 4 - w, left));
        int top = Math.max(4, Math.min(screenH - 4 - h, mouseY + 10));
        return new TooltipLayout(
                scale, (int) Math.ceil(left / scale) + 4, (int) Math.ceil(top / scale) + 4);
    }
}

package dev.ryan.tfcatlas.core;

/** Leave at least half the reported CPU capacity to the game; cap terrain work at four threads. */
public final class GenerationBudget {
    public static int workers(int processors) {
        return Math.max(1, Math.min(4, processors / 2));
    }

    private GenerationBudget() {}
}

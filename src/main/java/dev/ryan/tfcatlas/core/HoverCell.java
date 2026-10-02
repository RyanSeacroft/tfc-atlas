package dev.ryan.tfcatlas.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Debounced point sampling independent of map LOD, GPU pages and the tile-generation queue. */
public final class HoverCell {
    public record Result(Cell cell, boolean failed, long retryAfter) {}

    private final Executor executor;
    private final BiFunction<Integer, Integer, Cell> sample;
    private final Consumer<RuntimeException> error;
    private final LongSupplier clock;
    private final Map<Long, Result> cache = new LinkedHashMap<>(128, .75f, true);
    private long target, changedAt;
    private boolean hasTarget, busy, closed;

    public HoverCell(
            Executor executor,
            BiFunction<Integer, Integer, Cell> sample,
            Consumer<RuntimeException> error,
            LongSupplier clock) {
        this.executor = executor;
        this.sample = sample;
        this.error = error;
        this.clock = clock;
    }

    public synchronized Result at(int x, int z) {
        long now = clock.getAsLong(), key = ((long) x << 32) | (z & 0xffffffffL);
        Result found = cache.get(key);
        if (closed || found != null && (!found.failed || now < found.retryAfter)) {
            return found;
        }
        if (!hasTarget || target != key) {
            target = key;
            hasTarget = true;
            changedAt = now;
            return null;
        }
        if (!busy && now - changedAt >= 80) {
            busy = true;
            try {
                executor.execute(
                        () -> {
                            Result result;
                            try {
                                result =
                                        new Result(
                                                Objects.requireNonNull(
                                                        sample.apply(x, z),
                                                        "Missing cursor sample"),
                                                false,
                                                0);
                            } catch (RuntimeException ex) {
                                result = new Result(null, true, clock.getAsLong() + 2000);
                                synchronized (this) {
                                    if (!closed && !Thread.currentThread().isInterrupted()) {
                                        error.accept(ex);
                                    }
                                }
                            }
                            synchronized (this) {
                                if (!closed) {
                                    cache.put(key, result);
                                    while (cache.size() > 1024) {
                                        cache.remove(cache.keySet().iterator().next());
                                    }
                                }
                                busy = false;
                            }
                        });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                busy = false;
            }
        }
        return found;
    }

    public synchronized void close() {
        closed = true;
        cache.clear();
    }
}

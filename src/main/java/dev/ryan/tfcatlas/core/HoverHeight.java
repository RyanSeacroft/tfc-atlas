package dev.ryan.tfcatlas.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.IntBinaryOperator;

/** One background calculation at a time; results are keyed to the exact cursor column. */
public final class HoverHeight {
    public record Result(Integer y, boolean failed) {}

    private final Executor executor;
    private final IntBinaryOperator sample;
    private final Map<Long, Result> cache = new LinkedHashMap<>(128, .75f, true);
    private long target, changedAt;
    private boolean hasTarget, busy, closed;

    public HoverHeight(Executor executor, IntBinaryOperator sample) {
        this.executor = executor;
        this.sample = sample;
    }

    public synchronized Result at(int x, int z, long nowMillis) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        Result result = cache.get(key);
        if (result != null || closed) {
            return result;
        }
        if (!hasTarget || target != key) {
            hasTarget = true;
            target = key;
            changedAt = nowMillis;
            return null;
        }
        if (!busy && nowMillis - changedAt >= 80) {
            busy = true;
            try {
                executor.execute(
                        () -> {
                            Result value;
                            try {
                                value = new Result(sample.applyAsInt(x, z), false);
                            } catch (RuntimeException ex) {
                                value = new Result(null, true);
                            }
                            synchronized (this) {
                                if (!closed) {
                                    cache.put(key, value);
                                    while (cache.size() > 1024) {
                                        cache.remove(cache.keySet().iterator().next());
                                    }
                                }
                                busy = false;
                            }
                        });
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                busy = false;
            }
        }
        return null;
    }

    public synchronized void close() {
        closed = true;
        cache.clear();
    }
}

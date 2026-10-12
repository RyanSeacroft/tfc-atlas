package dev.ryan.tfcatlas.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small exact-sample cache. Mutable samplers keep one instance per owner thread. */
public final class SampleCache<T> {
    private final int limit;
    private final Map<Long, T> values = new LinkedHashMap<>(128, .75f, true);

    public SampleCache(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Cache limit must be positive");
        }
        this.limit = limit;
    }

    public T get(long key) {
        return values.get(key);
    }

    public void put(long key, T value) {
        values.put(key, value);
        if (values.size() > limit) {
            values.remove(values.keySet().iterator().next());
        }
    }

    public int size() {
        return values.size();
    }

    public static long position(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}

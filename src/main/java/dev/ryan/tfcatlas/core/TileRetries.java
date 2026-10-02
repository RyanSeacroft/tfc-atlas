package dev.ryan.tfcatlas.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Failed tiles wait between retries rather than becoming permanently blacklisted. */
public final class TileRetries {
    private record Failure(int attempts, long due, String reason) {}

    private final Map<Tile.Key, Failure> failures = new LinkedHashMap<>();
    private final LongSupplier clock;

    public TileRetries() {
        this(() -> System.nanoTime() / 1_000_000);
    }

    public TileRetries(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void failed(Tile.Key key, Exception error) {
        Failure old = failures.get(key);
        int attempts = old == null ? 1 : Math.min(7, old.attempts + 1);
        failures.put(
                key,
                new Failure(
                        attempts,
                        clock.getAsLong() + Math.min(60_000, 1000L << (attempts - 1)),
                        error.getClass().getSimpleName()));
        while (failures.size() > 2048) {
            failures.remove(failures.keySet().iterator().next());
        }
    }

    public synchronized boolean deferred(Tile.Key key) {
        Failure f = failures.get(key);
        return f != null && clock.getAsLong() < f.due;
    }

    public synchronized void succeeded(Tile.Key key) {
        failures.remove(key);
    }

    public synchronized int count() {
        return failures.size();
    }

    public synchronized String status() {
        if (failures.isEmpty()) {
            return "";
        }
        Failure first = failures.values().iterator().next();
        return failures.size()
                + " tile(s) awaiting retry · "
                + first.reason
                + " · details in latest.log";
    }

    public static String describe(Throwable error) {
        String type = error.getClass().getSimpleName(), message = error.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        // Keep the panel readable; the complete exception and tile coordinates go to the log.
        return type + ": " + (message.length() > 72 ? message.substring(0, 69) + "…" : message);
    }
}

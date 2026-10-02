package dev.ryan.tfcatlas.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Bounded opportunistic jobs; visible-map and search work always take priority. */
public final class BackgroundPrecache implements AutoCloseable {
    @FunctionalInterface
    public interface Loader {
        boolean load(Tile.Key key, BooleanSupplier obsolete) throws Exception;
    }

    private final Executor executor;
    private final Predicate<Tile.Key> cached;
    private final Loader loader;
    private final BooleanSupplier busy;
    private final BiConsumer<Tile.Key, Exception> error;
    private final TileRetries failures;
    private volatile PrecacheArea area;
    private volatile boolean enabled, closed;
    private Iterator<Tile.Key> cursor = Collections.emptyIterator();
    private final Deque<Tile.Key> retry = new ArrayDeque<>();
    private int scheduled;
    private final Set<Tile.Key> delayed = new LinkedHashSet<>();
    private long completed, reused;

    public record Progress(
            long completed, long reused, int queued, int retrying, boolean finished) {}

    public synchronized Progress progress() {
        return new Progress(
                completed,
                reused,
                scheduled,
                delayed.size(),
                area != null
                        && !cursor.hasNext()
                        && retry.isEmpty()
                        && delayed.isEmpty()
                        && scheduled == 0);
    }

    private final int parallelism;

    public BackgroundPrecache(
            Executor executor,
            Predicate<Tile.Key> cached,
            Loader loader,
            BooleanSupplier busy,
            Consumer<Exception> error) {
        this(executor, cached, loader, busy, error, 1);
    }

    public BackgroundPrecache(
            Executor executor,
            Predicate<Tile.Key> cached,
            Loader loader,
            BooleanSupplier busy,
            Consumer<Exception> error,
            int parallelism) {
        this(
                executor,
                cached,
                loader,
                busy,
                (key, ex) -> error.accept(ex),
                parallelism,
                new TileRetries());
    }

    public BackgroundPrecache(
            Executor executor,
            Predicate<Tile.Key> cached,
            Loader loader,
            BooleanSupplier busy,
            BiConsumer<Tile.Key, Exception> error,
            int parallelism,
            TileRetries failures) {
        this.executor = executor;
        this.cached = cached;
        this.loader = loader;
        this.busy = busy;
        this.error = error;
        this.parallelism = Math.max(1, parallelism);
        this.failures = failures;
    }

    public synchronized void update(int playerX, int playerZ, boolean enabled) {
        if (closed) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            return;
        }
        PrecacheArea next = PrecacheArea.at(playerX, playerZ);
        if (!next.equals(area)) {
            area = next;
            cursor = next.iterator();
            retry.clear();
            delayed.removeIf(k -> !next.contains(k));
        }
        schedule();
    }

    private synchronized void schedule() {
        while (!closed && enabled && scheduled < parallelism && !busy.getAsBoolean() && hasWork()) {
            scheduled++;
            try {
                executor.execute(this::run);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                scheduled--;
                break;
            }
        }
    }

    private boolean hasWork() {
        return !retry.isEmpty()
                || cursor.hasNext()
                || delayed.stream().anyMatch(k -> !failures.deferred(k));
    }

    private Tile.Key nextKey() {
        if (!retry.isEmpty()) {
            return retry.removeFirst();
        }
        for (var it = delayed.iterator(); it.hasNext(); ) {
            var key = it.next();
            if (!failures.deferred(key)) {
                it.remove();
                return key;
            }
        }
        return cursor.hasNext() ? cursor.next() : null;
    }

    private boolean obsolete(PrecacheArea plan) {
        return closed || !enabled || area != plan || busy.getAsBoolean();
    }

    private void run() {
        PrecacheArea plan = area;
        Tile.Key key = null;
        try {
            synchronized (this) {
                if (obsolete(plan)) {
                    return;
                }
                for (int checked = 0; checked < 256 && hasWork(); checked++) {
                    Tile.Key candidate = nextKey();
                    if (candidate == null) {
                        break;
                    }
                    if (failures.deferred(candidate)) {
                        delayed.add(candidate);
                        continue;
                    }
                    if (!cached.test(candidate)) {
                        key = candidate;
                        break;
                    }
                    reused++;
                }
            }
            if (key != null) {
                if (!loader.load(key, () -> obsolete(plan))) {
                    synchronized (this) {
                        if (area == plan) {
                            retry.addFirst(key);
                        }
                    }
                } else {
                    failures.succeeded(key);
                    synchronized (this) {
                        completed++;
                    }
                }
            }
        } catch (Exception ex) {
            if (!closed && !Thread.currentThread().isInterrupted()) {
                if (key != null) {
                    failures.failed(key, ex);
                    synchronized (this) {
                        if (area == plan) {
                            delayed.add(key);
                        }
                    }
                }
                error.accept(key, ex);
            }
        } finally {
            synchronized (this) {
                scheduled--;
                schedule();
            }
        }
    }

    public synchronized void close() {
        closed = true;
        enabled = false;
        retry.clear();
        delayed.clear();
        cursor = Collections.emptyIterator();
    }
}

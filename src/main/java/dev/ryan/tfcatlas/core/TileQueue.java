package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Bounded map work; saved-tile reads have their own queue so generation cannot hold them up. */
public final class TileQueue extends ThreadPoolExecutor {
    public TileQueue(int threads, String name) {
        super(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(512),
                job -> {
                    Thread thread = new Thread(job, name);
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public void prioritize(Predicate<Runnable> obsolete, Comparator<Runnable> order) {
        getQueue().removeIf(obsolete);
        List<Runnable> queued = new ArrayList<>();
        getQueue().drainTo(queued);
        queued.sort(order);
        getQueue().addAll(queued);
    }
}

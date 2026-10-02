package dev.ryan.tfcatlas.core;

public final class OverviewTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        progress();
        for (int width : new int[] {320, 854, 1920, 3840}) {
            for (int height : new int[] {240, 480, 1080, 2160}) {
                for (double scale : new double[] {.007, .003, .001, .0001, .000001, .00000001}) {
                    int step = Sampling.mapStep(scale, width, height);
                    check(
                            step <= 256 / Tile.GRID && Integer.bitCount(step) == 1,
                            "Wide views never use the rejected ultra-coarse samples");
                    int pixels = Sampling.texturePixels(step, scale);
                    check(
                            pixels >= 1 && pixels <= 32 && 32 % pixels == 0,
                            "Overview textures remain bounded with nonzero sample groups");
                }
            }
        }
        for (double scale : new double[] {.02, .1, .25, .5, 1, 8}) {
            int expected = scale >= .5 ? 1 : scale >= .25 ? 2 : 4;
            check(
                    Sampling.stableMapStep(scale, 854, 480, 4) == expected,
                    "Established close detail remains 32, 16, 8 blocks");
        }
        check(
                Sampling.mapStep(.002, 854, 480) == 16 && Sampling.mapStep(.0005, 854, 480) == 32,
                "Wide views select 128 then 256 blocks");
        check(
                Sampling.stableMapStep(1. / 64 * .95, 854, 480, 4) == 4,
                "Slight zoom-out does not oscillate at a level edge");
        check(
                Sampling.stableMapStep(1. / 64 * .85, 854, 480, 4) == 8,
                "A sustained zoom-out changes one level");
        check(
                Sampling.stableMapStep(1. / 64 * 1.05, 854, 480, 8) == 8
                        && Sampling.stableMapStep(1. / 64 * 1.15, 854, 480, 8) == 4,
                "Reverse transition has a hysteresis band");
        check(
                Sampling.stableMapStep(.0005, 854, 480, 4) == 32,
                "Fast large zoom still obeys the 256 cap");
        for (int step = 1; step <= Sampling.MAX_MAP_STEP; step *= 2) {
            for (double scale : new double[] {1e-8, .0001, .01, 1, 100}) {
                int pixels = Sampling.texturePixels(step, scale);
                check(
                        pixels >= 1 && pixels <= 32 && 32 % pixels == 0 && pixels >= step / 4,
                        "Fine composite coverage fits every texture scale");
            }
        }
        check(
                ColourTransition.blend(0, 0xff987654, 0) == 0xff987654,
                "New coverage appears immediately without transparent fading");
        check(
                ColourTransition.blend(0xff000000, 0xffffffff, 0) == 0xff000000
                        && ColourTransition.blend(0xff000000, 0xffffffff, 250) == 0xffffffff,
                "Fade retains old colour and reaches the exact new colour");
        int last = -1;
        for (int time = 0; time <= 250; time++) {
            int c = ColourTransition.blend(0xff000000, 0xffffffff, time);
            check(
                    (c >>> 24) == 255 && (c & 255) >= last,
                    "Colour fades stay opaque and monotonic without stacking opacity");
            last = c & 255;
        }
        precache();
        System.out.println(
                "PASS: "
                        + checks
                        + " 128/256 overview, precache, transition and stable-progress checks");
    }

    private static void precache() {
        for (int anchor : new int[] {-29_999_000, -8193, -1, 0, 8191, 123456, 29_999_000}) {
            var area = PrecacheArea.at(anchor, anchor);
            java.util.Set<Tile.Key> keys = new java.util.HashSet<>();
            double ring = -1;
            for (var key : area) {
                double nextRing =
                        Math.pow(key.x() - area.tileX(), 2) + Math.pow(key.z() - area.tileZ(), 2);
                check(
                        area.contains(key) && keys.add(key) && nextRing >= ring,
                        "Finite precache traverses unique centre-first tiles, including world edges");
                ring = nextRing;
            }
            check(
                    keys.size() < 16000,
                    "Precache plan has bounded work and no all-world allocation");
            for (int degrees = 0; degrees < 360; degrees++) {
                int x = anchor + (int) (PrecacheArea.RADIUS * Math.cos(Math.toRadians(degrees))),
                        z =
                                anchor
                                        + (int)
                                                (PrecacheArea.RADIUS
                                                        * Math.sin(Math.toRadians(degrees)));
                if (Math.abs(x) <= 29_999_900 && Math.abs(z) <= 29_999_900) {
                    check(
                            keys.contains(Tile.Key.at(x, z, PrecacheArea.STEP)),
                            "Precache includes the full requested 512,000-block circle around the actual player");
                }
            }
        }
        java.util.ArrayDeque<Runnable> jobs = new java.util.ArrayDeque<>();
        java.util.List<Tile.Key> loaded = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean busy =
                new java.util.concurrent.atomic.AtomicBoolean();
        java.util.Set<Tile.Key> cached = new java.util.HashSet<>();
        var cache =
                new BackgroundPrecache(
                        jobs::add,
                        cached::contains,
                        (key, obsolete) -> {
                            check(
                                    !obsolete.getAsBoolean(),
                                    "Visible/search work takes priority before generation");
                            loaded.add(key);
                            cached.add(key);
                            return true;
                        },
                        busy::get,
                        e -> {
                            throw new AssertionError(e);
                        });
        cache.update(100, 100, true);
        for (int i = 0; i < 50; i++) {
            cache.update(101, 101, true);
        }
        check(jobs.size() == 1, "Ticks and small player moves cannot flood the background queue");
        jobs.remove().run();
        check(
                jobs.size() == 1 && loaded.size() == 1,
                "Completed background tile schedules just one continuation");
        busy.set(true);
        jobs.remove().run();
        check(
                jobs.isEmpty() && loaded.size() == 1,
                "Foreground map/search work pauses the precache");
        busy.set(false);
        cache.update(100, 100, true);
        jobs.remove().run();
        check(
                loaded.size() == 2 && !loaded.get(0).equals(loaded.get(1)),
                "Precache resumes without restarting or rereading completed disk tiles");
        cache.update(500000, -500000, true);
        jobs.remove().run();
        check(
                loaded.get(2).equals(Tile.Key.at(500000, -500000, PrecacheArea.STEP)),
                "A distant player move reprioritizes the new local area");
        cache.close();
        int before = loaded.size();
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        cache.update(0, 0, true);
        check(loaded.size() == before && jobs.isEmpty(), "Disconnect/close stops background work");
        java.util.List<Tile.Key> attempted = new java.util.ArrayList<>();
        var retry =
                new BackgroundPrecache(
                        jobs::add,
                        k -> false,
                        (key, obsolete) -> {
                            attempted.add(key);
                            if (attempted.size() == 1) {
                                busy.set(true);
                                check(
                                        obsolete.getAsBoolean(),
                                        "New foreground work cancels an in-flight background tile");
                                return false;
                            }
                            return true;
                        },
                        busy::get,
                        e -> {
                            throw new AssertionError(e);
                        });
        retry.update(-10, -10, true);
        jobs.remove().run();
        check(jobs.isEmpty(), "Preempted background work waits for foreground completion");
        busy.set(false);
        retry.update(-10, -10, true);
        jobs.remove().run();
        check(
                attempted.get(0).equals(attempted.get(1)),
                "Preempted tile retries instead of leaving a permanent hole");
        retry.close();
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        var warm =
                new BackgroundPrecache(
                        jobs::add,
                        k -> true,
                        (key, obsolete) -> {
                            throw new AssertionError("Warm all-layer cache regenerated");
                        },
                        () -> false,
                        e -> {
                            throw new AssertionError(e);
                        });
        warm.update(0, 0, true);
        int scans = 0;
        while (!jobs.isEmpty()) {
            check(jobs.size() == 1 && ++scans < 65, "Warm-cache scan stays bounded and finishes");
            jobs.remove().run();
        }
        check(scans > 1, "Large cached areas are scanned in bounded batches");
        warm.close();
    }

    private static void progress() {
        for (int kind = 0; kind < 3; kind++) {
            String phase =
                    kind == 2
                            ? "Searching underground rock"
                            : kind == 1 ? "Predicting surface Y" : "Searching";
            SearchProgress p = new SearchProgress(kind == 2, kind == 1);
            check(p.initial().startsWith(phase + " · 0%"), "Initial phase is correct");
            int updates = 0, last = -1;
            long time = 1000, lastTime = Long.MIN_VALUE;
            for (int done = 0; done <= 4096; done += 32) {
                String text = p.update(done, 4096, done / 8, time);
                if (text != null) {
                    check(
                            text.startsWith(phase + " · "),
                            "Row and tile boundaries never switch the phase label");
                    int percent =
                            Integer.parseInt(
                                    text.substring(text.indexOf(" · ") + 3, text.indexOf('%')));
                    check(percent >= last, "Progress never goes backwards");
                    last = percent;
                    check(
                            lastTime == Long.MIN_VALUE || time - lastTime >= 150 || done == 4096,
                            "Counters change at most once per 150 ms, except completion");
                    lastTime = time;
                    updates++;
                }
                time += 2;
            }
            check(
                    updates == 3 && last == 100,
                    "Rapid row/tile changes coalesce but completion is immediate");
            check(
                    p.update(1000, 4096, 512, time + 200).contains("100%"),
                    "A repeated earlier work boundary cannot move progress backwards");
        }
    }
}

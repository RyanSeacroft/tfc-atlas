package dev.ryan.tfcatlas.core;

import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.client.Profile;
import dev.ryan.tfcatlas.client.Profiles;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SearchUiTest {
    private static int checks;

    private static void check(boolean value, String text) {
        checks++;
        if (!value) {
            throw new AssertionError(text);
        }
    }

    private static Cell cell(int type, int flags) {
        return new Cell("tfc:rock/raw/granite", "tfc:plains", type, 20, 20, 3, 2, 1, flags);
    }

    private static SearchQuery.Result pin(int x, int z) {
        return new SearchQuery.Result(x, z, Math.hypot(x, z), cell(2, 1));
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void run() {
        var q =
                new SearchQuery(
                        Set.of(),
                        Set.of(),
                        SearchQuery.types("land, UPLIFT"),
                        20,
                        30,
                        20,
                        30,
                        SearchQuery.terrain("river, lake"),
                        0,
                        4);
        check(
                q.matchesRegion(cell(2, 3)) && q.matchesRegion(cell(3, 5)),
                "Category and feature lists accept either entry");
        check(
                !q.matchesRegion(cell(1, 3)) && !q.matchesRegion(cell(2, 1)),
                "Different fields still combine with AND");
        check(
                SearchQuery.types("Any, ").isEmpty() && SearchQuery.terrain("").isEmpty(),
                "Blank or Any remains unrestricted");
        rejects(
                () -> SearchQuery.types("land, typo"),
                "Unknown category must not silently become Any");
        rejects(
                () -> SearchQuery.types("dikes"),
                "Dikes cannot falsely select a regional category");
        rejects(
                () -> SearchQuery.terrain("river, typo"),
                "Unknown feature must not silently become Any");
        var names = List.of("Granite", "Gneiss", "Basalt", "Rolling hills", "Plains");
        for (String input : List.of("gr", "basalt, gr", "basalt, gneiss, gr")) {
            var match = SearchCompletion.suggest(input, input.length(), names);
            check(
                    match != null && match.suffix().equals("anite"),
                    "Suggestion for every comma token: " + input);
            check(
                    match.apply(input).equals(input.substring(0, input.length() - 2) + "granite"),
                    "Tab preserves preceding entries: " + input);
        }
        String middle = "granite, ba, gneiss";
        var match = SearchCompletion.suggest(middle, 11, names);
        check(
                match != null
                        && match.apply(middle).equals("granite, basalt, gneiss")
                        && match.cursor() == 15,
                "Caret completion preserves following entries");
        check(
                SearchCompletion.suggest("  rolling_h", 11, names)
                        .apply("  rolling_h")
                        .equals("  rolling hills"),
                "Spaces and underscore names complete");
        check(
                SearchCompletion.suggest("granite, gr", 11, names) == null,
                "Do not suggest a duplicate previous entry");
        check(
                SearchCompletion.suggest("granite", 7, names) == null
                        && SearchCompletion.suggest("granite, ", 9, names) == null,
                "Exact or blank token leaves Tab navigation alone");
        var old =
                Profiles.decode(
                        JsonParser.parseString(
                                        "{\"type\":3,\"feature\":\"River\",\"uiScale\":0.5,\"keyScale\":0,\"legend\":false}")
                                .getAsJsonObject());
        check(
                old.categories.equals("Uplift") && old.query().features().equals(Set.of("river")),
                "Old profiles and presets migrate category and feature filters");
        check(
                !old.legend && old.keyScale == .5 && old.keyWidth == 0 && old.keyHeight == 0,
                "Preserve key toggle and initialise automatic full-list dimensions");
        old.keyWidth = 78;
        old.keyHeight = 153;
        old.categories = "Land, Uplift";
        old.feature = "River, Lake";
        var saved = Profiles.decode(Profiles.JSON.toJsonTree(old).getAsJsonObject());
        check(
                saved.keyWidth() == 0
                        && saved.keyHeight() == 153
                        && saved.query().equals(old.query()),
                "Viewport dimensions and multiple filters survive reconnect");
        check(
                !Profiles.JSON.toJsonTree(saved).getAsJsonObject().has("uiScale"),
                "Removed UI scale is no longer saved");
        for (var layer : List.of(Layer.ALTITUDE, Layer.INLAND)) {
            check(
                    layer.legend(false, Map.of()).stream()
                            .noneMatch(e -> e.label().matches(".*[0-9].*")),
                    "Keys use terrain names without unexplained indices");
        }
        for (int size : new int[] {20, 30}) {
            List<Integer> rows = Collections.nCopies(size, 11);
            int header = 17, total = header + size * 11;
            check(
                    KeyLayout.fit(rows, header, total, 22).count() == size,
                    "Full-height key includes every entry");
            check(
                    KeyLayout.fit(rows, header, header + 10 * 11 + 22, 22).count() == 10,
                    "Shortening key to ten rows includes overflow hint");
            int last = 0;
            for (int height = 40; height <= total; height++) {
                var fit = KeyLayout.fit(rows, header, height, 22);
                check(
                        fit.count() >= last
                                && fit.height() <= height
                                && fit.count() + fit.hidden() == size,
                        "Key grows monotonically within viewport");
                last = fit.count();
            }
        }
        check(
                KeyLayout.fit(List.of(11, 31, 11), 17, 69, 22).count() == 1,
                "Wrapped labels are kept whole");
        var safe = HudLayout.safeArea(320, 240);
        var drag = new HudLayout.Drag(new HudLayout.Box(32, 50, 100, 20), .5, 40, 55, false);
        check(
                drag.move(40, 900, safe).bottom() == 232,
                "Panels can reach eight pixels from screen bottom");
        Map<Tile.Key, BitSet> bits = new HashMap<>();
        BitSet all = new BitSet(1024);
        all.set(0, 1024);
        var negative = new Tile.Key(-1, -1, 1);
        bits.put(negative, all);
        var overlay = new SearchOverlay(bits, List.of(pin(-64, -64)));
        all.clear();
        bits.clear();
        check(
                overlay.count() == 1024
                        && overlay.matches(negative, 1023)
                        && overlay.candidates().size() == 1,
                "All matches persist independently of mutable builder and result limit");
        check(
                !overlay.matches(new Tile.Key(0, 0, 1), 0) && overlay.matches(negative, 0),
                "Negative tiles and tile boundaries retain exact match bits");
        check(
                overlay.bounds().equals(new SearchOverlay.Bounds(-256, -256, 32, 32)),
                "Search texture aligns to negative world coordinates");
        check(
                SearchOverlay.EMPTY.bounds().pixelWidth() == 0
                        && SearchOverlay.EMPTY.bounds().pixelHeight() == 0,
                "Empty search has no texture");
        BitSet edge = new BitSet();
        edge.set(0);
        var crossing =
                new SearchOverlay(Map.of(negative, edge, new Tile.Key(0, 0, 1), edge), List.of());
        check(
                crossing.bounds().equals(new SearchOverlay.Bounds(-256, -256, 64, 64)),
                "One continuous texture spans adjacent negative/positive tiles");
        rejects(
                () ->
                        new SearchOverlay(
                                Map.of(
                                        new Tile.Key(0, 0, 2),
                                        new BitSet(),
                                        new Tile.Key(1, 1, 1),
                                        new BitSet()),
                                List.of()),
                "Mixed resolutions cannot corrupt search coverage");
        check(
                overlay.edges().size() == 4,
                "A solid tile produces one continuous four-edge outline");
        check(
                overlay.edges().stream().allMatch(e -> e.x0() == e.x1() || e.z0() == e.z1()),
                "Outline edges follow region boundaries");
        BitSet row = new BitSet();
        row.set(0, 2);
        var joined = new SearchOverlay(Map.of(new Tile.Key(0, 0, 1), row), List.of());
        check(
                joined.edges().size() == 4,
                "Adjacent matching cells merge without an internal box border");
        BitSet ring = new BitSet();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                if (x != 1 || z != 1) {
                    ring.set(x + z * 32);
                }
            }
        }
        check(
                new SearchOverlay(Map.of(new Tile.Key(0, 0, 1), ring), List.of()).edges().size()
                        == 8,
                "Nonmatching holes keep their own inner boundary");
        saved.layer = "INLAND";
        check(
                saved.keyWidth() == 0 && saved.keyHeight() == 0,
                "Another layer starts at its compact natural size");
        saved.keySize(64, 60);
        saved.layer = "ROCKS";
        check(
                saved.keyWidth() == 0 && saved.keyHeight() == 153,
                "Switching layers restores the original key size");
        var roundtrip = Profiles.decode(Profiles.JSON.toJsonTree(saved).getAsJsonObject());
        roundtrip.layer = "INLAND";
        check(
                roundtrip.keyWidth() == 0 && roundtrip.keyHeight() == 60,
                "Each layer's dimensions survive saving");
        roundtrip.resetHud();
        check(roundtrip.keySizes.isEmpty(), "Reset UI clears every layer's custom size");
        check(roundtrip.searchCircle, "Radius circle is enabled by default");
        roundtrip.searchCircle = false;
        check(
                !Profiles.decode(Profiles.JSON.toJsonTree(roundtrip).getAsJsonObject())
                        .searchCircle,
                "Radius toggle persists");
        var details = new SearchDetails(q, -512, 256, 16384, 50, 512);
        var criteria = details.lines();
        check(
                criteria.contains("Surface rock region: Land, Uplift")
                        && criteria.contains("Terrain: Lake, River"),
                "Summary lists every submitted category and feature in stable order");
        check(
                criteria.contains("Rocks: Any") && criteria.contains("Biomes: Any"),
                "Unrestricted name filters are explicit");
        check(
                criteria.contains("Rain: 20–30 mm · Temp: 20–30 °C")
                        && criteria.contains("Surface Y: 0–4 (predicted)"),
                "Summary preserves exact climate and altitude constraints with units");
        check(
                criteria.contains("Centre: X -512, Z 256")
                        && criteria.contains("Search radius: 16384 blocks")
                        && criteria.contains(
                                "Closest matches: up to 50 · Min distance: 512 blocks"),
                "Summary records the actual search centre and result settings");
        Profile edited = new Profile();
        var captured = new SearchDetails(edited.query(), 0, 0, 128, 10, 0);
        var submitted = captured.lines();
        edited.rocks = "granite";
        edited.minTemp = 25;
        check(
                captured.lines().equals(submitted),
                "Later form changes cannot misrepresent an earlier search");
        edited.criteriaX = 75;
        edited.criteriaY = 100;
        edited.criteriaScale = .7;
        var restored = Profiles.decode(Profiles.JSON.toJsonTree(edited).getAsJsonObject());
        check(
                restored.criteriaX == 75
                        && restored.criteriaY == 100
                        && restored.criteriaScale == .7,
                "Criteria panel position and resize survive save/load");
        restored.resetHud();
        check(
                restored.criteriaX == -1
                        && restored.criteriaY == -1
                        && restored.criteriaScale == .5,
                "Reset UI restores the new panel too");
        var yProfile = new Profile();
        yProfile.minY = 100;
        yProfile.maxY = 130;
        var heightQuery = yProfile.query();
        check(
                heightQuery.matches(cell(2, 1), 100) && heightQuery.matches(cell(2, 1), 130),
                "Surface Y bounds are inclusive despite biome altitude index 3");
        check(
                !heightQuery.matches(cell(2, 1), 99) && !heightQuery.matches(cell(2, 1), 131),
                "Y bounds reject lower and higher terrain");
        check(
                heightQuery.matchesRegion(cell(2, 1)),
                "Cheap region filtering does not confuse altitude index with Y");
        check(
                !new Profile().query().heightRestricted(),
                "Default world-height range avoids unnecessary height sampling");
        rejects(
                () -> new SearchQuery(Set.of(), Set.of(), -1, 0, 500, -30, 40, "", 130, 100),
                "Reversed Y bounds rejected");
        var oldHeight =
                Profiles.decode(
                        JsonParser.parseString(
                                        "{\"minAltitude\":2,\"maxAltitude\":8,\"rocks\":\"granite\"}")
                                .getAsJsonObject());
        check(
                oldHeight.minY == -64 && oldHeight.maxY == 319 && oldHeight.rocks.equals("granite"),
                "Old terrain-index bounds reset rather than becoming incorrect Y bounds");
        var restoredHeight = Profiles.decode(Profiles.JSON.toJsonTree(yProfile).getAsJsonObject());
        check(restoredHeight.minY == 100 && restoredHeight.maxY == 130, "New Y bounds persist");
        Profile mode = new Profile();
        for (String expected :
                List.of(
                        "Explored only",
                        "Off",
                        "Full map",
                        "Unexplored only",
                        "Explored only",
                        "Off")) {
            mode.cycleCoverage();
            check(
                    mode.mode.equals(expected) && mode.overlayVisible() != expected.equals("Off"),
                    "One coverage cycle controls visibility and mode");
            check(
                    Profiles.decode(Profiles.JSON.toJsonTree(mode).getAsJsonObject())
                            .mode
                            .equals(expected),
                    "Cycle state persists");
        }
        mode =
                Profiles.decode(
                        JsonParser.parseString("{\"enabled\":false,\"mode\":\"Full map\"}")
                                .getAsJsonObject());
        check(
                mode.mode.equals("Off")
                        && !Profiles.JSON.toJsonTree(mode).getAsJsonObject().has("enabled"),
                "Legacy disabled state migrates to the single Off state");
        mode.cycleCoverage();
        check(
                Profiles.decode(Profiles.JSON.toJsonTree(mode).getAsJsonObject()).overlayVisible(),
                "Legacy disable cannot override a later Full map selection");
        for (int height : new int[] {100, 200, 600}) {
            var fit = KeyLayout.fit(List.of(11, 11, 11), 15, height, 11);
            check(
                    fit.height() == 48 && fit.count() == 3,
                    "Oversized key stops exactly after the final row");
        }
        var tiny = KeyLayout.fit(Collections.nCopies(20, 11), 15, 40, 11);
        check(
                tiny.count() == 1 && tiny.hidden() == 19 && tiny.height() == 37,
                "Small key reserves just one overflow line without spare bottom space");
        System.out.println(
                "PASS: "
                        + checks
                        + " completion, list-query, key-viewport and stable search-overlay checks");
    }
}

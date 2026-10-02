package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.HudLayout.Box;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.MapLabels;
import dev.ryan.tfcatlas.core.SearchLayout;
import dev.ryan.tfcatlas.core.Tile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DisplayTest {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) {
            throw new AssertionError(message);
        }
    }

    private static Cell cell(float rain, float temp, int altitude, int inland, int flags) {
        return new Cell(
                "tfc:rock/raw/granite",
                "tfc:rolling_hills",
                3,
                rain,
                temp,
                altitude,
                inland,
                0,
                flags);
    }

    public static void run() {
        climateContinents();
        Cell sample = cell(150, 15, 8, 10, 1);
        for (Layer layer : Layer.values()) {
            boolean eligible = layer == Layer.ROCKS || layer == Layer.BIOMES;
            check(
                    MapLabels.visible("Active layer", layer, .5, 1) == eligible,
                    "Names follow the selected layer: " + layer);
            check(!MapLabels.visible("Off", layer, 1, 1), "Off hides names: " + layer);
            check(
                    !MapLabels.visible("Active layer", layer, .09, 1),
                    "Zoomed-out names hidden: " + layer);
            check(
                    !MapLabels.visible("Active layer", layer, 1, 16),
                    "Overview samples never supply labels: " + layer);
            var first = layer.legend(false, Map.of());
            check(
                    !first.isEmpty() && first.equals(layer.legend(false, Map.of())),
                    "Stable shared key ordering: " + layer);
            check(
                    first.stream().noneMatch(e -> e.label().startsWith("Index")),
                    "Readable key entries: " + layer);
        }
        check(MapLabels.text(Layer.ROCKS, sample).equals("Granite"), "Rocks use rock names");
        check(
                MapLabels.text(Layer.BIOMES, sample).equals("Rolling hills"),
                "Biomes use biome names");
        check(
                MapLabels.visible("Active layer", Layer.ROCKS, .09375, 1),
                "12-pixel threshold shows labels four times farther out");
        check(
                MapLabels.fits(70, .5, 6) && !MapLabels.fits(100, .5, 6),
                "Text must fit inside the exact coloured cell");
        for (int index : new int[] {-300, -2, -1, 0, 1, 200}) {
            check(
                    Math.floorDiv(MapLabels.cellCentre(index), Tile.GRID) == index,
                    "Label anchor stays inside its own cell at " + index);
        }
        Cell neighbour = new Cell("tfc:rock/raw/basalt", "tfc:plains", 0, 100, 10, 0, 0, 0, 1);
        check(
                MapLabels.fitsRegion(Layer.ROCKS, sample, 100, .5, .09375, 0, 0, (x, z) -> sample),
                "Long names fit matching regions at wider zoom");
        check(
                !MapLabels.fitsRegion(
                        Layer.ROCKS,
                        sample,
                        100,
                        .5,
                        .09375,
                        0,
                        0,
                        (x, z) -> x == 2 ? neighbour : sample),
                "Names cannot cross a rock boundary");
        check(
                !MapLabels.fitsRegion(
                        Layer.BIOMES,
                        sample,
                        100,
                        .5,
                        .09375,
                        -1,
                        -1,
                        (x, z) -> x == -3 ? neighbour : sample),
                "Biome boundaries work at negative coordinates");
        check(
                !MapLabels.fitsRegion(
                        Layer.ROCKS,
                        sample,
                        100,
                        .5,
                        .09375,
                        0,
                        0,
                        (x, z) -> x == 1 ? null : sample),
                "Missing neighbour tiles cannot justify a label");
        Cell sameRock = new Cell(sample.rock(), neighbour.biome(), 0, 100, 10, 0, 0, 0, 1);
        check(
                MapLabels.fitsRegion(
                        Layer.ROCKS, sample, 100, .5, .09375, 0, 0, (x, z) -> sameRock),
                "Rock labels may cross biome boundaries of the same rock");
        check(
                !MapLabels.fitsRegion(
                        Layer.BIOMES, sample, 100, .5, .09375, 0, 0, (x, z) -> sameRock),
                "Biome labels use biome identity independently");
        check(
                Layer.RAINFALL.legend(false, Map.of()).stream()
                        .map(Layer.LegendEntry::label)
                        .toList()
                        .equals(
                                List.of(
                                        "0–100 mm",
                                        "100–200 mm",
                                        "200–300 mm",
                                        "300–400 mm",
                                        "400–500 mm",
                                        "500+ mm")),
                "Rainfall legend has ordered data ranges");
        check(
                Layer.TEMPERATURE.legend(false, Map.of()).stream()
                        .map(Layer.LegendEntry::label)
                        .toList()
                        .equals(
                                List.of(
                                        "Below -20 °C",
                                        "-20–-10 °C",
                                        "-10–0 °C",
                                        "0–10 °C",
                                        "10–20 °C",
                                        "20–30 °C",
                                        "30+ °C")),
                "Temperature ranges are numeric, not alphabetically sorted");
        var bands = Layer.ALTITUDE.legend(false, Map.of());
        check(
                bands.stream()
                        .map(Layer.LegendEntry::label)
                        .toList()
                        .equals(
                                List.of(
                                        "Ocean",
                                        "Near island",
                                        "Lowland",
                                        "Midland",
                                        "Highland",
                                        "Mountains")),
                "Actual TFC four-unit altitude bands");
        Set<Integer> colours = new HashSet<>();
        for (int altitude : new int[] {0, 4, 8, 12}) {
            colours.add(Layer.ALTITUDE.color(cell(0, 0, altitude, 0, 1), false, Map.of()));
        }
        check(
                colours.size() == 4,
                "Midland, highland and mountains no longer saturate to the same colour");
        for (int altitude : new int[] {-1, 0, 3, 4, 7, 8, 11, 12}) {
            Cell c = cell(0, 0, altitude, 0, 1);
            var entry =
                    bands.stream()
                            .filter(e -> e.colourKey().equals(Layer.ALTITUDE.colourKey(c)))
                            .findFirst()
                            .orElseThrow();
            check(
                    entry.colourAt(.5) == Layer.ALTITUDE.color(c, false, Map.of()),
                    "Terrain key exactly agrees with map: " + altitude);
        }
        var override = Map.of("RAINFALL:100–200 mm", 0x123456);
        for (float rain : new float[] {100, 150, 199.9f}) {
            check(
                    Layer.RAINFALL.color(cell(rain, 0, 0, 0, 1), false, override) == 0x123456,
                    "Range edit covers all values: " + rain);
        }
        for (float rain : new float[] {99.9f, 200}) {
            check(
                    Layer.RAINFALL.color(cell(rain, 0, 0, 0, 1), false, override) != 0x123456,
                    "Range edit excludes adjacent values: " + rain);
        }
        check(
                Layer.RAINFALL.legend(false, override).get(1).swatch().stream()
                        .allMatch(c -> c == 0x123456),
                "Range key reflects its edited map colour");
        Map<String, Integer> oldColours =
                new HashMap<>(
                        Map.of(
                                "RAINFALL:100.0 mm",
                                0x112233,
                                "ALTITUDE:Index 4",
                                0x223344,
                                "ROCKS:Granite",
                                0x334455));
        Layer.migrateColourRanges(oldColours);
        check(
                oldColours.equals(
                        Map.of(
                                "RAINFALL:100–200 mm",
                                0x112233,
                                "ALTITUDE:Midland (4–7)",
                                0x223344,
                                "ROCKS:Granite",
                                0x334455)),
                "Existing numeric key edits upgrade to real ranges without altering rock edits");
        Profile old =
                Profiles.decode(
                        JsonParser.parseString(
                                        "{\"seed\":\"-42\",\"uiScale\":0.75,\"mapLabels\":\"Both\",\"mode\":\"Unexplored only\",\"colors\":{\"ROCKS:Granite\":123},\"savedSearches\":{\"test\":\"preset\"}}")
                                .getAsJsonObject());
        check(
                old.toolbarScale == .5 && old.mapLabels.equals("Active layer"),
                "Previous defaults and labels migrate");
        check(
                old.seed.equals("-42")
                        && old.mode.equals("Unexplored only")
                        && old.colors.get("ROCKS:Granite") == 123
                        && old.savedSearches.get("test").equals("preset"),
                "Migration preserves the world, coverage, colours and searches");
        for (double scale : new double[] {.2, .5, .75, 1, 1.5}) {
            Profile p =
                    Profiles.decode(
                            JsonParser.parseString("{\"uiScale\":" + scale + ",\"uiRevision\":3}")
                                    .getAsJsonObject());
            check(
                    p.toolbarScale == scale && p.infoScale == scale,
                    "Current scale retained: " + scale);
        }
        for (int width : new int[] {320, 426, 640, 854, 1280}) {
            for (int height : new int[] {240, 360, 480}) {
                for (double scale : new double[] {.2, .5, .75, 1, 1.5}) {
                    Box safe = HudLayout.safeArea(width, height);
                    List<Box> nativeUi =
                            List.of(
                                    new Box(0, 0, 30, 30),
                                    new Box(0, height - 120, 20, 120),
                                    new Box(width - 20, height - 180, 20, 180),
                                    new Box(20, height - 100, 75, 20));
                    Box key =
                            HudLayout.place(
                                    safe,
                                    (int) Math.ceil(Math.min(174 * scale, safe.width() * .4)),
                                    (int) Math.ceil(85 * scale),
                                    safe.right()
                                            - (int)
                                                    Math.ceil(
                                                            Math.min(
                                                                    174 * scale,
                                                                    safe.width() * .4)),
                                    safe.y(),
                                    nativeUi);
                    check(
                            key != null
                                    && safe.contains(key)
                                    && nativeUi.stream().noneMatch(key::overlaps),
                            "Key avoids Xaero at " + width + "x" + height + " / " + scale);
                    List<Box> occupied = new ArrayList<>(nativeUi);
                    occupied.add(key);
                    Box panel =
                            HudLayout.place(
                                    safe,
                                    (int) Math.min(348 * scale, safe.width() * .57),
                                    (int) Math.ceil(46 * scale),
                                    8,
                                    height - 32 - (int) (46 * scale),
                                    occupied);
                    check(
                            panel != null
                                    && safe.contains(panel)
                                    && occupied.stream().noneMatch(panel::overlaps),
                            "Readout avoids native buttons and key at "
                                    + width
                                    + "x"
                                    + height
                                    + " / "
                                    + scale);
                }
            }
        }
        Set<String> expected =
                Set.of(
                        "rocks",
                        "biomes",
                        "categories",
                        "feature",
                        "minRain",
                        "maxRain",
                        "minTemp",
                        "maxTemp",
                        "radius",
                        "resultSpacing",
                        "highlights",
                        "minY",
                        "maxY",
                        "precision",
                        "searchRockLayer");
        for (int width : new int[] {320, 426, 640, 854, 1280}) {
            for (int height : new int[] {240, 260, 360, 480}) {
                var groups = SearchLayout.groups(width, height);
                Set<String> keys = new HashSet<>();
                List<Box> controls = new ArrayList<>();
                for (var group : groups) {
                    for (int i = 0; i < group.keys().size(); i++) {
                        check(keys.add(group.keys().get(i)), "No duplicated search filter");
                        Box field = group.field(i);
                        check(
                                field.y() >= 61
                                        && field.bottom() + 1 < height - 72
                                        && field.x() >= 12
                                        && field.right() < width - 12
                                        && field.width() >= 35
                                        && field.height() >= 12,
                                "Search field fits above actions at " + width + "x" + height);
                        check(
                                controls.stream()
                                        .noneMatch(b -> b.padded(1).overlaps(field.padded(1))),
                                "Compact fields and edit-box borders do not overlap");
                        controls.add(field);
                    }
                }
                check(
                        keys.equals(expected),
                        "All search parameters remain on one page at " + width + "x" + height);
            }
        }
        Box safe = new Box(32, 40, 300, 200), start = new Box(50, 70, 100, 40);
        HudLayout.Drag move = new HudLayout.Drag(start, .5, 60, 80, false);
        check(
                move.move(90, 100, safe).equals(new Box(80, 90, 100, 40)),
                "Dragging preserves grab offset");
        check(
                safe.contains(move.move(-900, -900, safe))
                        && safe.contains(move.move(900, 900, safe)),
                "Dragging cannot leave the safe viewport");
        HudLayout.Drag resize = new HudLayout.Drag(start, .5, 150, 110, true);
        check(
                Math.abs(resize.resizedScale(200, 130) - .75) < 1e-9,
                "Corner drag resizes proportionally");
        check(
                resize.resizedScale(-1000, -1000) == .2 && resize.resizedScale(10000, 10000) == 1.5,
                "Resize scale respects both limits");
        Profile moved = new Profile();
        moved.toolbarX = 80;
        moved.toolbarY = 90;
        moved.keyX = 210;
        moved.keyY = 55;
        moved.infoX = 40;
        moved.infoY = 160;
        moved.toolbarScale = .3;
        moved.keyScale = .7;
        moved.infoScale = .6;
        Profile loaded = Profiles.decode(Profiles.JSON.toJsonTree(moved).getAsJsonObject());
        check(
                loaded.toolbarX == 80
                        && loaded.keyX == 210
                        && loaded.infoY == 160
                        && loaded.toolbarScale == .3
                        && loaded.keyScale == .7
                        && loaded.infoScale == .6,
                "All independent panel positions and sizes survive save/load");
        loaded.resetHud();
        check(
                loaded.toolbarX == 32
                        && loaded.keyX == -1
                        && loaded.infoY == -1
                        && loaded.keyScale == .5
                        && loaded.infoScale == .5
                        && loaded.keyWidth == 0
                        && loaded.keyHeight == 0,
                "Reset UI restores automatic placement and default dimensions");
        check(
                old.keyX == -1 && old.infoX == -1 && old.keyScale == .5,
                "Old profiles acquire HUD defaults without losing preferences");
        check(
                HudLayout.place(new Box(0, 0, 20, 20), 21, 10, 0, 0, List.of()) == null,
                "Insufficient space cannot spill over native UI");
        System.out.println(
                "PASS: " + checks + " label, legend, scale-migration and HUD-layout checks");
    }

    private static void climateContinents() {
        Profile p = new Profile();
        check(p.climateContinents, "New profiles show continent shapes by default");
        check(
                Profiles.decode(JsonParser.parseString("{}").getAsJsonObject()).climateContinents,
                "Existing profiles acquire default-on continent fill");
        p.climateContinents = false;
        check(
                !Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject()).climateContinents,
                "Explicit Off survives save/reload");
        int previous = p.mapStyleHash();
        p.climateContinents = true;
        check(previous != p.mapStyleHash(), "Toggling continents invalidates rendered pages");
        Cell ocean = cell(150, 15, 0, 0, 0), land = cell(150, 15, 0, 0, 1);
        for (Layer layer : Layer.values()) {
            p.layer = layer.name();
            p.colors.clear();
            int plain = layer.color(ocean, false, Map.of());
            check(
                    layer.mapColor(ocean, false, Map.of(), false) == plain,
                    "Off restores the original ocean climate/palette: " + layer);
            check(
                    p.mapColor(land) == layer.color(land, false, Map.of()),
                    "Land palette stays unchanged: " + layer);
            check(
                    LegendScreen.entries(p).equals(layer.legend(false, Map.of(), true)),
                    "Both keys use the active display policy: " + layer);
            if (!layer.continentFill()) {
                check(p.mapColor(ocean) == plain, "No change to non-climate ocean: " + layer);
                check(
                        layer.legend(false, Map.of(), true).equals(layer.legend(false, Map.of())),
                        "No extra row on other keys: " + layer);
                continue;
            }
            var ranges = layer.legend(false, Map.of());
            var filled = LegendScreen.entries(p);
            check(
                    filled.get(0).label().equals("Ocean")
                            && filled.get(0).colourAt(.5) == p.mapColor(ocean),
                    "Ocean key matches the map/export: " + layer);
            check(
                    filled.subList(1, filled.size()).equals(ranges),
                    "Existing climate ranges/order are preserved: " + layer);
            for (int flags : new int[] {1, 3, 5, 9}) {
                check(
                        p.mapColor(cell(150, 15, 0, 0, flags))
                                == layer.color(land, false, Map.of()),
                        "Land, rivers, lakes and mountains retain climate colours");
            }
            int oceanColour = p.mapColor(ocean);
            for (float rain : new float[] {0, 100, 500, 1000}) {
                for (float temp : new float[] {-50, 0, 30, 60}) {
                    Cell wetOcean = cell(rain, temp, 0, 0, 0);
                    check(
                            p.mapColor(wetOcean) == oceanColour,
                            "Ocean silhouette does not change with climate");
                    p.climateContinents = false;
                    check(
                            p.mapColor(wetOcean) == layer.color(wetOcean, false, Map.of()),
                            "Off shows every ocean climate value");
                    p.climateContinents = true;
                }
            }
            p.colors.put(layer.name() + ":" + layer.colourKey(land), 0xABCDE0);
            p.colors.put(layer.name() + ":Ocean", 0x123456);
            check(
                    p.mapColor(land) == 0xABCDE0 && p.mapColor(ocean) == 0x123456,
                    "Ocean and climate range edits remain independent");
            check(
                    LegendScreen.entries(p).get(0).colourAt(.5) == 0x123456,
                    "Ocean colour edit updates cached legends");
            p.climateContinents = false;
            check(
                    LegendScreen.entries(p).equals(layer.legend(false, p.colors)),
                    "Disabling the option removes the ocean row immediately");
            check(
                    p.mapColor(ocean) == 0xABCDE0,
                    "Climate range edit applies to oceans when disabled");
            p.climateContinents = true;
            check(
                    layer.value(ocean).equals(layer.value(land)),
                    "Hover climate data remains available over ocean");
        }
    }
}

package dev.ryan.tfcatlas.core;

import java.util.List;
import java.util.Random;
import java.util.Set;

/** Selection state and scroll bounds are tested independently of the screen implementation. */
public final class SelectionUiTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        var groups =
                List.of(
                        new BiomeGroups.Group("a", "A", Set.of("one", "two")),
                        new BiomeGroups.Group("b", "B", Set.of("two", "three")),
                        new BiomeGroups.Group("all", "All", Set.of("one", "two", "three")));
        var selection = new GroupSelection(groups, "a, b", "four");
        selection.toggle("two");
        check(
                !selection.contains("two")
                        && selection.names().equals(Set.of("one", "three", "four")),
                "Unticking an overlap removes it while preserving other choices");
        Random random = new Random(26);
        for (int i = 0; i < 1000; i++) {
            if (random.nextBoolean()) {
                selection.toggle(groups.get(random.nextInt(groups.size())));
            } else {
                selection.toggle(List.of("one", "two", "three", "four").get(random.nextInt(4)));
            }
            var saved = selection.save();
            check(
                    SearchGroups.resolve(saved.names(), saved.groups(), groups)
                            .equals(selection.names()),
                    "Serialising partial overlapping groups preserves exactly the selected names");
        }
        var biomes =
                BiomeGroups.from(
                        Set.of(
                                "tfc:plains",
                                "tfc:rolling_hills",
                                "tfc:lowlands",
                                "tfc:old_mountains"),
                        Set.of());
        check(biomes.resolve("", "all").size() == 4, "All list includes basic terrain biomes");
        check(
                biomes.resolve("", "hills").equals(Set.of("tfc:rolling_hills")),
                "Basic hill group exists");
        check(
                biomes.resolve("", "plains").equals(Set.of("tfc:plains", "tfc:lowlands")),
                "Basic plains and lowlands exist");
        check(
                SearchQuery.names("plains\nrolling hills\r\nold mountains")
                        .equals(Set.of("plains", "rolling_hills", "old_mountains")),
                "Multiline pasted lists retain separate names");
        check(
                SearchCompletion.suggest(
                                "plains\nold m", 12, List.of("old mountains", "old mountains lake"))
                        .text()
                        .equals("old mountains"),
                "Completion operates on the current multiline token");
        SmoothScroll scroll = new SmoothScroll();
        scroll.range(1000, 100);
        scroll.wheel(-.25);
        scroll.tick(.016);
        check(
                scroll.position() > 0 && scroll.position() < 7,
                "Fractional wheel input animates smoothly without row jumps");
        scroll.move(99999);
        check(scroll.position() == 900, "Dragging cannot overscroll");
        scroll.range(80, 100);
        check(scroll.position() == 0, "Resize or empty content clamps scrolling");
        scroll.range(1000, 100);
        scroll.reveal(400, 410, 100);
        scroll.tick(1);
        check(
                scroll.position() > 0 && scroll.position() < 310,
                "Caret reveal respects viewport and animation");
        check(
                new dev.ryan.tfcatlas.client.Profile().mapLabels.equals("Active layer"),
                "New profiles default to map labels on");
        System.out.println(
                "PASS 1000 overlapping-selection round trips, multiline completion and smooth-scroll bounds");
    }
}

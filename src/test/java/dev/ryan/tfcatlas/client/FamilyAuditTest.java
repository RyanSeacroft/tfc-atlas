package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.BiomeGroups;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import net.dries007.tfc.world.TFCChunkGenerator;

/** Audit every family against all biomes in the installed TFC dependency. */
public final class FamilyAuditTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        Set<String> registered = new HashSet<>();
        var location =
                TFCChunkGenerator.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        try (var jar = new JarFile(java.nio.file.Path.of(location).toFile())) {
            jar.stream()
                    .map(java.util.jar.JarEntry::getName)
                    .filter(
                            name ->
                                    name.startsWith("data/tfc/worldgen/biome/")
                                            && name.endsWith(".json"))
                    .map(
                            name ->
                                    "tfc:"
                                            + name.substring(
                                                    name.lastIndexOf('/') + 1, name.length() - 5))
                    .forEach(registered::add);
        }
        var biomes = BiomeGroups.from(registered, Set.of());
        Set<String> covered = new HashSet<>();
        for (var group : biomes.groups()) {
            check(
                    registered.containsAll(group.members()),
                    "Families include only this version's real biomes");
            check(!group.id().equals("other"), "All published TFC biomes have a meaningful family");
            covered.addAll(group.members());
        }
        check(covered.equals(registered), "No published biome is omitted");
        Set<String> coast = biomes.resolve("Coastal biomes", "");
        check(
                coast.contains("tfc:shore") && coast.contains("tfc:tidal_flats"),
                "Coasts include shores and tides");
        check(
                !coast.contains("tfc:mud_flats") && !coast.contains("tfc:salt_flats"),
                "Inland flats are not beaches");
        check(
                !biomes.resolve("Ocean biomes", "").contains("tfc:oceanic_mountains"),
                "Oceanic mountains are not open ocean");
        if (registered.contains("tfc:coastal_dunes")) {
            check(
                    coast.containsAll(
                            Set.of(
                                    "tfc:coastal_dunes",
                                    "tfc:sea_stacks",
                                    "tfc:embayments",
                                    "tfc:terrace_upper",
                                    "tfc:terrace_lower",
                                    "tfc:setback_cliffs")),
                    "Named and non-named coastal variants are complete");
            Set<String> karst = biomes.resolve("Karst biomes", "");
            check(
                    karst.contains("tfc:burren_roche_moutonee")
                            && karst.contains("tfc:extreme_doline_mountains"),
                    "Karst includes burren and extreme dolines");
            for (String tag :
                    Set.of(
                            "is_karst",
                            "is_cenote",
                            "is_burren",
                            "is_shilin",
                            "is_tower_karst",
                            "is_dolines",
                            "is_extreme_dolines")) {
                check(
                        karst.containsAll(tag("tfc:" + tag, new HashSet<>())),
                        "Full TFC karst tag: " + tag);
            }
            check(
                    coast.containsAll(tag("c:is_beach", new HashSet<>())),
                    "Coastal group includes every tagged TFC beach");
        }
        for (String group :
                new String[] {
                    "hills",
                    "plains",
                    "lakes",
                    "coasts",
                    "mountains",
                    "plateaus",
                    "karst",
                    "volcanic",
                    "oceans",
                    "rivers",
                    "badlands",
                    "canyons",
                    "highlands",
                    "dry",
                    "glacial",
                    "islands",
                    "wetlands",
                    "rifts"
                }) {
            var match = biomes.groups().stream().filter(g -> g.id().equals(group)).findFirst();
            if (match.isPresent()) {
                check(
                        biomes.resolve(match.get().label(), "").equals(match.get().members()),
                        "Every menu family resolves its exact members");
            }
        }
        System.out.println(
                "PASS audit of all "
                        + registered.size()
                        + " published TFC biomes, coast inclusion and family exclusions");
    }

    private static Set<String> tag(String id, Set<String> seen) throws Exception {
        Set<String> result = new HashSet<>();
        if (!seen.add(id)) {
            return result;
        }
        String[] parts = id.split(":", 2);
        String path = "/data/" + parts[0] + "/tags/worldgen/biome/" + parts[1] + ".json";
        try (var stream = TFCChunkGenerator.class.getResourceAsStream(path)) {
            if (stream == null) {
                return result;
            }
            var json =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (var value : json.getAsJsonObject().getAsJsonArray("values")) {
                String name =
                        value.isJsonObject()
                                ? value.getAsJsonObject().get("id").getAsString()
                                : value.getAsString();
                if (name.startsWith("#")) {
                    result.addAll(tag(name.substring(1), seen));
                } else if (name.startsWith("tfc:")) {
                    result.add(name);
                }
            }
        }
        return result;
    }
}

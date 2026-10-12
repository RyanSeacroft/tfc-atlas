package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.BiomeGroups;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import net.dries007.tfc.world.TFCChunkGenerator;

/** Check group membership using the installed 1.20 distribution's real biome tag. */
public final class PublishedBiomeGroupsTest {
    private PublishedBiomeGroupsTest() {}

    public static void run() throws Exception {
        Set<String> registered = new HashSet<>();
        Path jar =
                Path.of(
                        TFCChunkGenerator.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        try (ZipFile file = new ZipFile(jar.toFile())) {
            file.stream()
                    .map(entry -> entry.getName())
                    .filter(
                            name ->
                                    name.startsWith("data/tfc/worldgen/biome/")
                                            && name.endsWith(".json"))
                    .map(
                            name ->
                                    "tfc:"
                                            + name.substring(
                                                    "data/tfc/worldgen/biome/".length(),
                                                    name.length() - 5))
                    .forEach(registered::add);
        }
        Set<String> kaolin = tag("tfc:kaolin_clay_spawns_in", new HashSet<>());
        kaolin.retainAll(registered);
        assert !kaolin.isEmpty() : "Published 1.20 kaolin tag must be available";
        var catalog = BiomeGroups.from(registered, kaolin);
        assert catalog.resolve("", "kaolin").equals(kaolin);
        for (var group : catalog.groups()) {
            assert registered.containsAll(group.members()) : "Groups cannot invent biomes";
        }
        String expanded = catalog.editableList("plains", "kaolin");
        assert catalog.resolve(expanded, "").equals(catalog.resolve("plains", "kaolin"));
        System.out.println(
                "PASS published TFC 1.20 biome groups: "
                        + kaolin.size()
                        + " kaolinite candidate biomes");
    }

    private static Set<String> tag(String id, Set<String> seen) throws Exception {
        Set<String> result = new HashSet<>();
        if (!seen.add(id)) {
            return result;
        }
        String[] parts = id.split(":", 2);
        String path = "/data/" + parts[0] + "/tags/worldgen/biome/" + parts[1] + ".json";
        try (var input = TFCChunkGenerator.class.getResourceAsStream(path)) {
            assert input != null : "Missing published tag " + id;
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (var value : json.getAsJsonObject().getAsJsonArray("values")) {
                String name =
                        value.isJsonObject()
                                ? value.getAsJsonObject().get("id").getAsString()
                                : value.getAsString();
                if (name.startsWith("#")) {
                    result.addAll(tag(name.substring(1), seen));
                } else {
                    result.add(name);
                }
            }
        }
        return result;
    }
}

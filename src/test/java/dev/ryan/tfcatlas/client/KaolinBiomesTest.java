package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.KaolinBiomes;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import net.dries007.tfc.world.TFCChunkGenerator;

/** Validate against the pinned TFC distribution, not a shared list copied from 1.20. */
public final class KaolinBiomesTest {
    private KaolinBiomesTest() {}

    public static void run() throws Exception {
        assert KaolinBiomes.containsKaolin("{\"features\":[\"#tfc:in_biome/veins/kaolin\"]}");
        assert KaolinBiomes.containsKaolin("{\"features\":[[\"tfc:vein/kaolin_disc\"]]}");
        assert !KaolinBiomes.containsKaolin("{\"features\":[\"#tfc:in_biome/veins\"]}");
        assert !KaolinBiomes.containsKaolin("{\"features\":null}");
        assert !KaolinBiomes.containsKaolin("not json");
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
        Set<String> kaolin =
                KaolinBiomes.bundled(registered, TFCChunkGenerator.class::getResourceAsStream);
        assert !kaolin.isEmpty() : "Published 1.21 kaolin list must not be empty";
        assert kaolin.contains("tfc:old_mountains");
        assert !kaolin.contains("tfc:plains");
        var catalog = BiomeGroups.from(registered, kaolin);
        assert catalog.resolve("", "kaolin").equals(kaolin);
        assert !catalog.resolve("", "karst").isEmpty()
                : "Actual 1.21 karst variants must be available";
        for (var group : catalog.groups()) {
            assert registered.containsAll(group.members()) : "Groups cannot invent biomes";
        }
        assert KaolinBiomes.bundled(
                        Set.of("tfc:plains"), TFCChunkGenerator.class::getResourceAsStream)
                .isEmpty();
        String expanded = catalog.editableList("plains", "kaolin, karst");
        assert catalog.resolve(expanded, "").equals(catalog.resolve("plains", "kaolin, karst"));
        System.out.println(
                "PASS published TFC 1.21 biome groups: "
                        + kaolin.size()
                        + " kaolinite candidate biomes");
    }
}

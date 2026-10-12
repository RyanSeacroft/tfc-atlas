package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.KaolinBiomes;
import java.util.HashSet;
import java.util.Set;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** Use the world's full generation data when local, and installed TFC candidates when remote. */
final class WorldBiomeGroups {
    private WorldBiomeGroups() {}

    static BiomeGroups current() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            return catalog(BiomeGroups.from(Set.of(), Set.of()));
        }
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Set<String> registered = new HashSet<>();
        registry.keySet().forEach(id -> registered.add(id.toString()));
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            // Minecraft's network biome codec omits generation features. Do not interpret
            // that empty data as an intentionally empty kaolin list on a multiplayer server.
            return catalog(
                    BiomeGroups.from(
                            registered,
                            KaolinBiomes.bundled(
                                    registered, TFCChunkGenerator.class::getResourceAsStream)));
        }
        Set<String> kaolin = new HashSet<>();
        var tag =
                TagKey.create(
                        Registries.PLACED_FEATURE,
                        ResourceLocation.fromNamespaceAndPath("tfc", "in_biome/veins/kaolin"));
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var entry : biomes.entrySet()) {
            boolean hasKaolin =
                    entry.getValue().getGenerationSettings().features().stream()
                            .flatMap(set -> set.stream())
                            .anyMatch(
                                    holder ->
                                            holder.is(tag)
                                                    || holder.unwrapKey()
                                                            .map(
                                                                    key ->
                                                                            key.location()
                                                                                    .toString()
                                                                                    .equals(
                                                                                            "tfc:vein/kaolin_disc"))
                                                            .orElse(false));
            if (hasKaolin) {
                kaolin.add(entry.getKey().location().toString());
            }
        }
        return catalog(BiomeGroups.from(registered, kaolin));
    }

    private static BiomeGroups catalog(BiomeGroups groups) {
        return groups.withGroups(SavedGroups.catalog("biomes", groups.groups()));
    }

    static String kaolinHelp() {
        return "Select biomes containing the kaolin feature. Singleplayer uses world generation settings; multiplayer uses installed TFC defaults. Server data packs can differ. Climate requirements still apply.";
    }
}

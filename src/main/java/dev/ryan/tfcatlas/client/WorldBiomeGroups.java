package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** Read the real synced biome tag, including changes supplied by the world's data packs. */
final class WorldBiomeGroups {
    private WorldBiomeGroups() {}

    static BiomeGroups current() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return catalog(BiomeGroups.from(Set.of(), Set.of()));
        }
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Set<String> registered = new HashSet<>(), kaolin = new HashSet<>();
        registry.keySet().forEach(id -> registered.add(id.toString()));
        var tag =
                TagKey.create(
                        Registries.BIOME, new ResourceLocation("tfc", "kaolin_clay_spawns_in"));
        registry.getTag(tag)
                .ifPresent(
                        holders ->
                                holders.forEach(
                                        holder ->
                                                holder.unwrapKey()
                                                        .ifPresent(
                                                                key ->
                                                                        kaolin.add(
                                                                                key.location()
                                                                                        .toString()))));
        return catalog(BiomeGroups.from(registered, kaolin));
    }

    private static BiomeGroups catalog(BiomeGroups groups) {
        return groups.withGroups(SavedGroups.catalog("biomes", groups.groups()));
    }

    static String kaolinHelp() {
        return "Select biomes allowed by this world's kaolin biome tag, including server data-pack changes. Climate and other deposit requirements still apply.";
    }
}

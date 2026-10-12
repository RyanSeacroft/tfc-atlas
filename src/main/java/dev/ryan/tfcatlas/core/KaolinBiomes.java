package dev.ryan.tfcatlas.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** TFC 1.21 assigns kaolin through biome features rather than the old biome tag. */
public final class KaolinBiomes {
    private KaolinBiomes() {}

    public static Set<String> bundled(
            Set<String> registered, Function<String, InputStream> resources) {
        Set<String> members = new TreeSet<>();
        for (String id : registered) {
            if (!id.startsWith("tfc:")) {
                continue;
            }
            String path = "/data/tfc/worldgen/biome/" + id.substring(4) + ".json";
            try (InputStream input = resources.apply(path)) {
                if (input != null
                        && containsKaolin(
                                new String(input.readAllBytes(), StandardCharsets.UTF_8))) {
                    members.add(id);
                }
            } catch (IOException ignored) {
                // Unreadable optional resources must not crash a menu or widen a search.
            }
        }
        return Set.copyOf(members);
    }

    public static boolean containsKaolin(String biomeJson) {
        try {
            JsonElement root = JsonParser.parseString(biomeJson);
            return root.isJsonObject() && feature(root.getAsJsonObject().get("features"));
        } catch (JsonParseException ignored) {
            return false;
        }
    }

    private static boolean feature(JsonElement value) {
        if (value == null) {
            return false;
        }
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                if (feature(child)) {
                    return true;
                }
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String id = value.getAsString();
            return id.equals("#tfc:in_biome/veins/kaolin") || id.equals("tfc:vein/kaolin_disc");
        }
        return false;
    }
}

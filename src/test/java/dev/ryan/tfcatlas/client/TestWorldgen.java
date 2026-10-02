package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Objects;
import net.dries007.tfc.world.settings.Settings;

final class TestWorldgen {
    static Settings defaults() {
        try (var in =
                Settings.class.getResourceAsStream(
                        "/data/tfc/worldgen/world_preset/overworld.json")) {
            var data =
                    JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in)))
                            .getAsJsonObject()
                            .getAsJsonObject("dimensions")
                            .getAsJsonObject("minecraft:overworld")
                            .getAsJsonObject("generator")
                            .getAsJsonObject("tfc_settings");
            var rocks = data.getAsJsonObject("rock_layer_settings").getAsJsonObject("rocks");
            for (var entry : new ArrayList<>(rocks.entrySet())) {
                String[] id = entry.getValue().getAsString().split(":");
                try (var raw =
                        Settings.class.getResourceAsStream(
                                "/data/"
                                        + id[0]
                                        + "/tfc/worldgen/rock_settings/"
                                        + id[1]
                                        + ".json")) {
                    rocks.add(
                            entry.getKey(),
                            JsonParser.parseReader(
                                    new InputStreamReader(Objects.requireNonNull(raw))));
                }
            }
            return Settings.CODEC
                    .codec()
                    .parse(JsonOps.INSTANCE, data)
                    .getOrThrow(IllegalArgumentException::new);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

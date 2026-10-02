package dev.ryan.tfcatlas.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;

public final class Profiles {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/tfcatlas");
    }

    public static String hash(String s) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(s.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject out = new JsonObject();
            value.getAsJsonObject().keySet().stream()
                    .sorted()
                    .forEach(k -> out.add(k, canonical(value.getAsJsonObject().get(k))));
            return out;
        }
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            value.getAsJsonArray().forEach(v -> out.add(canonical(v)));
            return out;
        }
        return value;
    }

    public static Profile load(String world) {
        Path p = root().resolve("profiles/" + hash(world) + ".json");
        try {
            if (Files.exists(p)) {
                Profile profile =
                        decode(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
                profile.startSession();
                // Rewrite legacy seed-bearing profiles/presets when this world is opened.
                save(world, profile);
                return profile;
            }
        } catch (Exception e) {
            AtlasClient.message("Profile could not be read: " + e.getMessage());
        }
        return new Profile();
    }

    public static Profile decode(JsonObject stored) {
        Profile profile = JSON.fromJson(stored, Profile.class);
        if (!stored.has("cacheRevision")) {
            if (profile.memoryTiles == 512) {
                profile.memoryTiles = 2048;
            }
            if (profile.diskMB == 256) {
                profile.diskMB = 1024;
            }
        }
        profile.cacheRevision = 1;
        // The old independent enable switch is now the Off state of the coverage cycle.
        if (stored.has("enabled") && !stored.get("enabled").getAsBoolean()) {
            profile.mode = "Off";
        }
        double previous = stored.has("uiScale") ? stored.get("uiScale").getAsDouble() : .5;
        if (!stored.has("uiRevision") && previous == .75) {
            previous = .5;
        }
        if (stored.has("uiScale")) {
            if (!stored.has("toolbarScale") || profile.toolbarScale == 0) {
                profile.toolbarScale = previous;
            }
            if (!stored.has("infoScale") || profile.infoScale == 0) {
                profile.infoScale = previous;
            }
            if (!stored.has("labelScale")) {
                profile.labelScale = previous;
            }
        }
        if (!stored.has("categories") && stored.has("type")) {
            int type = stored.get("type").getAsInt();
            if (type >= 0 && type < dev.ryan.tfcatlas.core.Cell.TYPE_NAMES.length) {
                profile.categories = dev.ryan.tfcatlas.core.Cell.TYPE_NAMES[type];
            }
        }
        if (!stored.has("searchRevision")) {
            if (profile.minRain == 0 && profile.maxRain == 500) {
                profile.minRain = -Float.MAX_VALUE;
                profile.maxRain = Float.MAX_VALUE;
            }
            if (profile.minTemp == -30 && profile.maxTemp == 40) {
                profile.minTemp = -Float.MAX_VALUE;
                profile.maxTemp = Float.MAX_VALUE;
            }
        }
        if (!stored.has("searchRockLayer")
                && stored.has("rockMode")
                && stored.get("rockMode").getAsString().equals("Y range")) {
            profile.searchRockLayer = "Any layer";
        }
        // One-time upgrade of the former full-map default; keep an intentional Off state.
        if (!stored.has("coverageRevision") && profile.mode.equals("Full map")) {
            profile.mode = "Unexplored only";
        }
        profile.coverageRevision = 1;
        profile.searchRevision = 2;
        profile.climateZones =
                dev.ryan.tfcatlas.core.ClimateZones.displayList(profile.climateZones);
        profile.uiRevision = 6;
        profile.validate();
        return profile;
    }

    /** Presets contain search inputs only, never the session seed or nested presets. */
    public static String preset(Profile profile) {
        return JSON.toJson(searchFields(JSON.toJsonTree(profile).getAsJsonObject()));
    }

    private static JsonObject searchFields(JsonObject source) {
        JsonObject result = new JsonObject();
        for (String key :
                new String[] {
                    "climateZones",
                    "minGroundwater",
                    "maxGroundwater",
                    "rocks",
                    "biomes",
                    "categories",
                    "feature",
                    "minRain",
                    "maxRain",
                    "minTemp",
                    "maxTemp",
                    "radius",
                    "searchX",
                    "searchZ",
                    "resultSpacing",
                    "highlights",
                    "minY",
                    "maxY",
                    "precision",
                    "searchRockLayer",
                    "searchOrigin",
                    "searchRevision"
                }) {
            if (source.has(key)) {
                result.add(key, source.get(key).deepCopy());
            }
        }
        return result;
    }

    public static String stored(Profile profile, boolean multiplayer) {
        JsonObject data = JSON.toJsonTree(profile).getAsJsonObject();
        if (multiplayer) {
            data.remove("seed");
        }
        JsonObject searches = new JsonObject();
        profile.savedSearches.forEach(
                (name, value) -> {
                    try {
                        searches.addProperty(
                                name,
                                JSON.toJson(
                                        searchFields(
                                                JsonParser.parseString(value).getAsJsonObject())));
                    } catch (RuntimeException ignored) {
                    } // Do not preserve unreadable legacy blobs that may contain seeds.
                });
        data.add("savedSearches", searches);
        return JSON.toJson(data);
    }

    public static void save(String world, Profile profile) {
        Path p = root().resolve("profiles/" + hash(world) + ".json");
        try {
            Files.createDirectories(p.getParent());
            Path temp = Files.createTempFile(p.getParent(), "profile-", ".tmp");
            Files.writeString(temp, stored(profile, ClientSeed.hidden()));
            try {
                Files.move(
                        temp,
                        p,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            AtlasClient.message("Could not save profile: " + e.getMessage());
        }
    }
}

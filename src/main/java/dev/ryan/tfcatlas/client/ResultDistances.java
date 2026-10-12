package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SearchQuery;
import net.minecraft.client.Minecraft;

/** Display distances follow the player; search ordering remains tied to its submitted centre. */
final class ResultDistances {
    static double toPlayer(SearchQuery.Result result) {
        var player = Minecraft.getInstance().player;
        return player == null
                ? result.distance()
                : Math.hypot(result.x() - player.getX(), result.z() - player.getZ());
    }

    private ResultDistances() {}
}

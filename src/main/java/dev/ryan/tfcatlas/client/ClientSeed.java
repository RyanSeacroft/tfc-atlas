package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SeedRecovery;
import dev.ryan.tfcatlas.mixin.BiomeManagerAccessor;
import io.netty.buffer.Unpooled;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.OverworldClimateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

final class ClientSeed {
    static String status = "Waiting for TFC climate data; or enter the seed in World";

    static boolean hidden() {
        return Minecraft.getInstance().getSingleplayerServer() == null;
    }

    static boolean detect(Profile profile) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            status = "Seed detection: join the Overworld";
            return false;
        }
        try {
            var model = Climate.get(level);
            if (model.getClass() != OverworldClimateModel.class) {
                status = "Seed detection: waiting for standard TFC climate data";
                return false;
            }
            if (!(level.getBiomeManager() instanceof BiomeManagerAccessor access)) {
                status = "Seed detection unavailable; enter the seed in World";
                return false;
            }
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(12));
            try {
                // Serialize the already-received local model. No packet or request is sent.
                OverworldClimateModel.STREAM_CODEC.encode(buffer, (OverworldClimateModel) model);

                long climateSeed = buffer.readVarLong();
                float temperatureScale = buffer.readFloat();
                var recovered = SeedRecovery.recover(climateSeed, access.tfcatlas$biomeZoomSeed());
                if (recovered.isEmpty()) {
                    status = "Seed not verified; enter the seed in World";
                    return false;
                }
                profile.seed = Long.toString(recovered.getAsLong());
                if (Float.isFinite(temperatureScale)
                        && temperatureScale >= 0
                        && temperatureScale < Integer.MAX_VALUE) {
                    profile.temperatureScale = Math.round(temperatureScale);
                }
                status = "Seed detected and verified from TFC climate data";
                return true;
            } finally {
                buffer.release();
            }
        } catch (RuntimeException | LinkageError ex) {
            status = "Seed detection unavailable; enter the seed in World";
            return false;
        }
    }
}

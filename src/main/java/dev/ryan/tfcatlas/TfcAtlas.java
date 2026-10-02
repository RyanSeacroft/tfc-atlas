package dev.ryan.tfcatlas;

import dev.ryan.tfcatlas.client.AtlasClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod("tfcatlas")
public final class TfcAtlas {
    public TfcAtlas() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> AtlasClient::init);
    }
}

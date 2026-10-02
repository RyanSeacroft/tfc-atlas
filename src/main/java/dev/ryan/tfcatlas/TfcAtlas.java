package dev.ryan.tfcatlas;

import dev.ryan.tfcatlas.client.AtlasClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = "tfcatlas", dist = Dist.CLIENT)
public final class TfcAtlas {
    public TfcAtlas(IEventBus bus) {
        AtlasClient.init(bus);
    }
}

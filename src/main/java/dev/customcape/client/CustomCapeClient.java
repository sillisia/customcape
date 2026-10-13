
package dev.customcape.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class CustomCapeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LocalState.init(FabricLoader.getInstance().getConfigDir().resolve("customcape"));
        WebSync.start();
    }
}

package net.bdew.generators.fabric;

import net.bdew.generators.Generators;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.fabric.platform.TransferPlatformImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class GeneratorsFabric implements ModInitializer {
    @Override public void onInitialize() {
        TransferPlatform.install(TransferPlatformImpl.provider());
        Generators.init();
        FabricTransfers.register();
        if (FabricLoader.getInstance().isModLoaded("team_reborn_energy"))
            FabricTransfers.installEnergyIntegration(new FabricEnergyTransfers());
    }
}

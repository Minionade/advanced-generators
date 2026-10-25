package net.bdew.generators.fabric;

import net.bdew.generators.Generators;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.platform.TransferPlatformImpl;
import net.fabricmc.api.ModInitializer;

public final class GeneratorsFabric implements ModInitializer {
    @Override public void onInitialize() { TransferPlatform.install(TransferPlatformImpl.provider()); Generators.init(); FabricTransfers.register(); }
}

package net.bdew.generators.neoforge;

import net.bdew.generators.Generators;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.neoforge.platform.TransferPlatformImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Generators.MOD_ID)
public final class GeneratorsNeoForge {
    public GeneratorsNeoForge(IEventBus modBus) {
        TransferPlatform.install(TransferPlatformImpl.provider()); Generators.init(); modBus.addListener(TransferPlatformImpl::registerCapabilities);
    }
}

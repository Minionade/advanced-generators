package net.bdew.generators.neoforge;

import net.bdew.generators.Generators;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.integration.MekanismGasInput;
import net.bdew.generators.integration.SingularityIterationIntegration;
import net.bdew.generators.neoforge.platform.TransferPlatformImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;

@Mod(Generators.MOD_ID)
public final class GeneratorsNeoForge {
    public GeneratorsNeoForge(IEventBus modBus) {
        TransferPlatform.install(TransferPlatformImpl.provider());
        Generators.init();
        modBus.addListener(TransferPlatformImpl::registerCapabilities);
        if (ModList.get().isLoaded("mekanism")) modBus.addListener(MekanismGasInput::register);
        if (ModList.get().isLoaded("mio_icif")) modBus.addListener(SingularityIterationIntegration::register);
    }
}

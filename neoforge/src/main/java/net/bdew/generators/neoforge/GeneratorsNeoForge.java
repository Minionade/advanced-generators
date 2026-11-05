package net.bdew.generators.neoforge;

import net.bdew.generators.Generators;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.integration.MekanismGasInput;
import net.bdew.generators.integration.ModernIndustrializationIntegration;
import net.bdew.generators.neoforge.platform.TransferPlatformImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;

@Mod(Generators.MOD_ID)
public final class GeneratorsNeoForge {
    public GeneratorsNeoForge(IEventBus modBus) {
        TransferPlatform.install(TransferPlatformImpl.provider());
        if (ModList.get().isLoaded("create")) net.bdew.generators.neoforge.create.CreateIntegration.registerContent();
        Generators.init();
        modBus.addListener(TransferPlatformImpl::registerCapabilities);
        if (ModList.get().isLoaded("create")) modBus.addListener(net.bdew.generators.neoforge.create.CreateIntegration::registerCapabilities);
        if (ModList.get().isLoaded("mekanism")) modBus.addListener(MekanismGasInput::register);
        if (ModList.get().isLoaded("modern_industrialization")) modBus.addListener(ModernIndustrializationIntegration::register);
    }
}

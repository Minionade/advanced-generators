package net.bdew.generators.fabric;

import dev.architectury.registry.menu.MenuRegistry;
import net.bdew.generators.ModContent;
import net.bdew.generators.menu.MachineScreen;
import net.fabricmc.api.ClientModInitializer;

public final class GeneratorsFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() { MenuRegistry.registerScreenFactory(ModContent.MACHINE_MENU.get(), MachineScreen::new); }
}

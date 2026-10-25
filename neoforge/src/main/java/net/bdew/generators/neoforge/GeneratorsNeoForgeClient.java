package net.bdew.generators.neoforge;

import dev.architectury.registry.menu.MenuRegistry;
import net.bdew.generators.ModContent;
import net.bdew.generators.menu.MachineScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="advgenerators", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class GeneratorsNeoForgeClient {
    private GeneratorsNeoForgeClient() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new);
    }
}

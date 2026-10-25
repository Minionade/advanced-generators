package net.bdew.generators.neoforge;

import dev.architectury.registry.menu.MenuRegistry;
import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineBorderRenderer;
import net.bdew.generators.menu.MachineScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="advgenerators", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class GeneratorsNeoForgeClient {
    private GeneratorsNeoForgeClient() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new);
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY.get(), MachineBorderRenderer::new);
    }
    @SubscribeEvent public static void registerDeveloperTextures(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addPackFinders(
            Generators.id("resourcepacks/developer_textures"),
            PackType.CLIENT_RESOURCES,
            Component.literal("Advanced Generators: Developer Textures"),
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP
        );
    }
}

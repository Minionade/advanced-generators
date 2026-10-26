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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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
    @SubscribeEvent public static void registerFluidClientExtensions(RegisterClientExtensionsEvent event) {
        ModContent.GASES.forEach((name, definition) -> {
            var still = Generators.id("block/" + name + "/still");
            var flowing = Generators.id("block/" + name + "/flowing");
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return still; }
                @Override public ResourceLocation getFlowingTexture() { return flowing; }
                @Override public int getTintColor() { return 0xffffffff; }
            }, definition.source.get().getFluidType());
        });
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

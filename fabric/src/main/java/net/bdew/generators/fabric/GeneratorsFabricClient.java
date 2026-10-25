package net.bdew.generators.fabric;

import dev.architectury.registry.menu.MenuRegistry;
import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineBorderRenderer;
import net.bdew.generators.menu.MachineScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class GeneratorsFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        FabricFluidBucketModel.register();
        MenuRegistry.registerScreenFactory(ModContent.MACHINE_MENU.get(), MachineScreen::new);
        BlockEntityRendererRegistry.register(ModContent.MACHINE_ENTITY.get(), MachineBorderRenderer::new);
        var container = FabricLoader.getInstance().getModContainer(Generators.MOD_ID).orElseThrow();
        ResourceManagerHelper.registerBuiltinResourcePack(
            Generators.id("developer_textures"),
            container,
            Component.literal("Advanced Generators: Developer Textures"),
            ResourcePackActivationType.NORMAL
        );
    }
}

package net.bdew.generators;

import dev.architectury.platform.Platform;
import net.bdew.generators.config.GeneratorConfig;
import net.minecraft.resources.ResourceLocation;

public final class Generators {
    public static final String MOD_ID = "advgenerators";
    private Generators() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
    public static void init() {
        GeneratorConfig.load(Platform.getConfigFolder().resolve("advgenerators.json"));
        ModContent.register();
    }
}

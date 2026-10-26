package net.bdew.generators.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

/** Registers the shared dynamic model used for the mod's fluid buckets. */
public final class FabricFluidBucketModel {
    private static final ResourceLocation FLUID_FILL_MODEL = ResourceLocation.fromNamespaceAndPath(
        "advgenerators", "item/bucket_fluid_fill");

    private FabricFluidBucketModel() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            context.addModels(FLUID_FILL_MODEL);
            context.resolveModel().register(modelContext -> {
                ResourceLocation id = modelContext.id();
                if (isBucketModel(id, "steam_bucket")) return new FabricDynamicBucketModel("steam");
                if (isBucketModel(id, "syngas_bucket")) return new FabricDynamicBucketModel("syngas");
                return null;
            });
        });
    }

    private static boolean isBucketModel(ResourceLocation id, String itemPath) {
        return id.getNamespace().equals("advgenerators")
            && (id.getPath().equals(itemPath) || id.getPath().equals("item/" + itemPath));
    }
}

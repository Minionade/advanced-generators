package net.bdew.generators.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

/** Registers the shared dynamic model used for the mod's fluid buckets. */
public final class FabricFluidBucketModel {
    private FabricFluidBucketModel() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            context.addModels(
                ResourceLocation.fromNamespaceAndPath("advgenerators", "item/steam_bucket_fill"),
                ResourceLocation.fromNamespaceAndPath("advgenerators", "item/syngas_bucket_fill"));
            context.resolveModel().register(modelContext -> {
                String fluid = bucketFluid(modelContext.id());
                return fluid == null ? null : new FabricDynamicBucketModel(fluid);
            });
            context.modifyModelOnLoad().register((original, modelContext) -> {
                ResourceLocation id = modelContext.resourceId();
                if (id == null && modelContext.topLevelId() != null) id = modelContext.topLevelId().id();
                if (id == null) return original;
                String fluid = bucketFluid(id);
                if (fluid != null) return new FabricDynamicBucketModel(fluid);
                return original;
            });
        });
    }

    private static String bucketFluid(ResourceLocation id) {
        if (!id.getNamespace().equals("advgenerators")) return null;
        String path = id.getPath();
        if (path.equals("steam_bucket") || path.equals("item/steam_bucket")) return "steam";
        if (path.equals("syngas_bucket") || path.equals("item/syngas_bucket")) return "syngas";
        return null;
    }
}

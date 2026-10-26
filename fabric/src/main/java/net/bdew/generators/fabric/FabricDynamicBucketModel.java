package net.bdew.generators.fabric;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Bakes the vanilla bucket frame together with the matching animated fluid texture. */
public final class FabricDynamicBucketModel implements UnbakedModel {
    private static final ResourceLocation EMPTY_BUCKET_MODEL = ResourceLocation.withDefaultNamespace("item/bucket");
    private final String fluidPath;

    public FabricDynamicBucketModel(String fluidPath) { this.fluidPath = fluidPath; }

    @Override public Collection<ResourceLocation> getDependencies() {
        return List.of(EMPTY_BUCKET_MODEL, fillModelId());
    }

    @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> modelLoader) { }

    @Override public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return new FabricDynamicBucketBakedModel(
            baker.bake(EMPTY_BUCKET_MODEL, state), baker.bake(fillModelId(), state));
    }

    private ResourceLocation fillModelId() {
        return ResourceLocation.fromNamespaceAndPath("advgenerators", "item/" + fluidPath + "_bucket_fill");
    }
}

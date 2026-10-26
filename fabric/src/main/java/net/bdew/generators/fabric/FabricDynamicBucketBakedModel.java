package net.bdew.generators.fabric;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Vanilla-rendered composition of the bucket frame and a baked fluid fill layer. */
public final class FabricDynamicBucketBakedModel implements BakedModel {
    private final BakedModel bucketModel;
    private final BakedModel fillModel;

    public FabricDynamicBucketBakedModel(BakedModel bucketModel, BakedModel fillModel) {
        this.bucketModel = bucketModel;
        this.fillModel = fillModel;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        List<BakedQuad> quads = new ArrayList<>();
        quads.addAll(bucketModel.getQuads(state, direction, random));
        quads.addAll(fillModel.getQuads(state, direction, random));
        return quads;
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return bucketModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return bucketModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return bucketModel.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return bucketModel.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return bucketModel.getOverrides(); }
}

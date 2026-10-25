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

import java.util.List;

record FluidBucketBakedModel(List<BakedQuad> quads, ItemTransforms transforms, ItemOverrides overrides,
                             TextureAtlasSprite particle) implements BakedModel {
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return direction == null ? quads : List.of();
    }
    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return false; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return particle; }
    @Override public ItemTransforms getTransforms() { return transforms; }
    @Override public ItemOverrides getOverrides() { return overrides; }
}

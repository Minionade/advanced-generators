package net.bdew.generators.fabric;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Uses Minecraft's bucket sprite and overlays the bucket fluid with its live Fabric fluid sprite. */
public final class FabricDynamicBucketBakedModel implements BakedModel, FabricBakedModel {
    private final String fluidPath;
    private final BakedModel bucketModel;
    private final BakedModel fillModel;

    public FabricDynamicBucketBakedModel(String fluidPath, BakedModel bucketModel, BakedModel fillModel) {
        this.fluidPath = fluidPath;
        this.bucketModel = bucketModel;
        this.fillModel = fillModel;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return List.of();
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return bucketModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return bucketModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return bucketModel.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return bucketModel.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public boolean isVanillaAdapter() { return false; }

    @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                                         Supplier<RandomSource> random, RenderContext context) { }

    @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        var emitter = context.getEmitter();
        for (BakedQuad quad : bucketModel.getQuads(null, null, randomSupplier.get())) {
            emitter.fromVanilla(quad.getVertices(), 0);
            emitter.emit();
        }

        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("advgenerators", fluidPath));
        if (fluid == Fluids.EMPTY) return;

        FluidState fluidState = fluid.defaultFluidState();
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        var client = Minecraft.getInstance();
        TextureAtlasSprite sprite = null;
        int tint = -1;
        if (handler != null) {
            TextureAtlasSprite[] sprites = handler.getFluidSprites(client.level, BlockPos.ZERO, fluidState);
            if (sprites.length > 0) sprite = sprites[0];
            tint = handler.getFluidColor(client.level, BlockPos.ZERO, fluidState);
        }
        if (sprite == null) {
            ResourceLocation stillTexture = ResourceLocation.fromNamespaceAndPath(
                "advgenerators", "block/" + fluidPath + "/still");
            sprite = client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        }

        final TextureAtlasSprite fluidSprite = sprite;
        final int fluidTint = tint;
        context.pushTransform(quad -> {
            quad.color(fluidTint, fluidTint, fluidTint, fluidTint);
            quad.spriteBake(fluidSprite, MutableQuadView.BAKE_LOCK_UV);
            return true;
        });
        for (BakedQuad quad : fillModel.getQuads(null, null, randomSupplier.get())) {
            emitter.fromVanilla(quad.getVertices(), 0);
            emitter.emit();
        }
        context.popTransform();
    }
}

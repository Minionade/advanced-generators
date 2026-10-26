package net.bdew.generators.fabric;

import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.fabric.mixin.ItemModelGeneratorAccessor;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;

/**
 * Bakes the vanilla bucket frame with the registered fluid sprite clipped to vanilla's fill mask.
 * The mask geometry approach is adapted from BucketLib by cech12 (MIT); see THIRD_PARTY_LICENSES.md.
 */
public final class FabricFluidBucketModel extends BlockModel implements UnbakedModel {
    private static final float MASK_OFFSET = 0.002F;
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private static final ItemModelGeneratorAccessor GENERATOR = (ItemModelGeneratorAccessor) ITEM_MODEL_GENERATOR;
    private static final Map<ResourceLocation, FabricFluidBucketModel> MODELS = new HashMap<>();
    private static final ResourceLocation BASE_MODEL_ID = Generators.id("item/fluid_bucket");

    private final Fluid fluid;

    private FabricFluidBucketModel(BlockModel source, Fluid fluid) {
        super(((net.bdew.generators.fabric.mixin.BlockModelAccessor) source).advgen$getParentLocation(),
            source.getElements(),
            ((net.bdew.generators.fabric.mixin.BlockModelAccessor) source).advgen$getTextureMap(),
            source.hasAmbientOcclusion(), source.getGuiLight(), source.getTransforms(), source.getOverrides());
        this.fluid = fluid;
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyModelOnLoad().register((original, modelContext) -> {
            ResourceLocation id = modelContext.resourceId();
            if (id == null && modelContext.topLevelId() != null)
                id = modelContext.topLevelId().id();
            if (id == null || !(original instanceof BlockModel blockModel)) return original;
            String path = id.getPath();
            Fluid fluid = path.equals("steam_bucket") || path.equals("item/steam_bucket")
                ? ModContent.GASES.get("steam").source.get()
                : path.equals("syngas_bucket") || path.equals("item/syngas_bucket")
                    ? ModContent.GASES.get("syngas").source.get()
                    : id.equals(BASE_MODEL_ID) ? Fluids.EMPTY : null;
            if (fluid == null) return original;
            FabricFluidBucketModel model = new FabricFluidBucketModel(blockModel, fluid);
            if (id.equals(BASE_MODEL_ID)) MODELS.put(id, model);
            return model;
        }));
    }

    @Override
    public BlockModel getRootModel() {
        resolveParents(MODELS::get);
        return this;
    }

    @Override
    public BakedModel bake(ModelBaker baker, BlockModel model, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, boolean useAmbientOcclusion) {
        resolveParents(MODELS::get);
        Material baseMaterial = getMaterial("layer0");
        Material maskMaterial = getMaterial("fluidMask");
        TextureAtlasSprite baseSprite = spriteGetter.apply(baseMaterial);
        TextureAtlasSprite maskSprite = spriteGetter.apply(maskMaterial);
        FluidRenderHandler handler = fluid == Fluids.EMPTY ? null : FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        TextureAtlasSprite fluidSprite = null;
        if (handler != null) {
            TextureAtlasSprite[] sprites = handler.getFluidSprites(null, null, fluid.defaultFluidState());
            if (sprites.length > 0) fluidSprite = sprites[0];
        }

        if (fluid != Fluids.EMPTY && FluidVariantAttributes.isLighterThanAir(FluidVariant.of(fluid))) {
            modelState = BlockModelRotation.X180_Y0;
        }

        ItemOverrides overrides = ItemOverrides.EMPTY;
        List<BakedQuad> quads = new ArrayList<>();
        quads.addAll(bakeElements(this, overrides,
            GENERATOR.advgen$processFrames(0, "layer0", baseSprite.contents()), baseSprite, modelState));
        if (fluidSprite != null && maskSprite != null) {
            quads.addAll(bakeMaskElements(this, overrides, maskSprite, fluidSprite, modelState));
        }
        return new FluidBucketBakedModel(quads, getTransforms(), overrides, baseSprite);
    }

    private static List<BakedQuad> bakeMaskElements(BlockModel model, ItemOverrides overrides,
                                                     TextureAtlasSprite mask, TextureAtlasSprite fluid,
                                                     ModelState state) {
        List<BlockElement> elements = new ArrayList<>(GENERATOR.advgen$processFrames(1, "fluid", mask.contents()));
        if (!elements.isEmpty()) elements.remove(0);
        int width = mask.contents().width();
        int height = mask.contents().height();
        BitSet opaque = new BitSet(width * height);
        mask.contents().getUniqueFrames().forEach(frame -> {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                if (!mask.contents().isTransparent(frame, x, y)) opaque.set(x + y * width);
        });

        for (int y = 0; y < height; y++) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                boolean isOpaque = x < width && opaque.get(x + y * width);
                if (isOpaque && start < 0) start = x;
                if (!isOpaque && start >= 0) {
                    int xEnd = x;
                    int yEnd = y + 1;
                    outer: while (yEnd < height) {
                        for (int xx = start; xx < xEnd; xx++) if (!opaque.get(xx + yEnd * width)) break outer;
                        yEnd++;
                    }
                    for (int yy = y; yy < yEnd; yy++) for (int xx = start; xx < xEnd; xx++) opaque.clear(xx + yy * width);
                    Map<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
                    for (Direction direction : Direction.values()) faces.put(direction,
                        new BlockElementFace(null, 1, "fluid", new BlockFaceUV(null, 0)));
                    elements.add(new BlockElement(
                        new Vector3f(16f * start / width, 16f - 16f * yEnd / height, 7.5f - MASK_OFFSET),
                        new Vector3f(16f * xEnd / width, 16f - 16f * y / height, 8.5f + MASK_OFFSET),
                        faces, null, true));
                    start = -1;
                }
            }
        }
        return bakeElements(model, overrides, elements, fluid, state);
    }

    private static List<BakedQuad> bakeElements(BlockModel model, ItemOverrides overrides, List<BlockElement> elements,
                                                 TextureAtlasSprite sprite, ModelState state) {
        if (elements.isEmpty()) return List.of();
        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(model, overrides, false).particle(sprite);
        for (BlockElement element : elements) element.faces.forEach((direction, face) -> {
            BakedQuad quad = FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, direction, state, element.rotation, element.shade);
            if (face.cullForDirection() == null) builder.addUnculledFace(quad);
            else builder.addCulledFace(Direction.rotate(state.getRotation().getMatrix(), face.cullForDirection()), quad);
        });
        return builder.build().getQuads(null, null, RandomSource.create());
    }
}

package net.bdew.generators.fabric.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BlockModel.class)
public interface BlockModelAccessor {
    @Accessor("parent") BlockModel advgen$getParent();
    @Accessor("parentLocation") ResourceLocation advgen$getParentLocation();
    @Accessor("textureMap") Map<String, Either<Material, String>> advgen$getTextureMap();
}

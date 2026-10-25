package net.bdew.generators.fabric.mixin;

import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ItemModelGenerator.class)
public interface ItemModelGeneratorAccessor {
    @Invoker("processFrames")
    List<BlockElement> advgen$processFrames(int tintIndex, String textureName, SpriteContents contents);
}

package net.bdew.generators.machine;

import net.bdew.generators.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class UpgradeKitItem extends Item {
    public UpgradeKitItem() { super(new Properties()); }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        var player = ctx.getPlayer(); var level = ctx.getLevel();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof MachineBlockEntity machine)) return InteractionResult.PASS;
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModContent.RECIPE_TYPES.get("upgrade_kit").get())) {
            var recipe = holder.value();
            if (!recipe.id("item").equals(BuiltInRegistries.ITEM.getKey(this))) continue;
            var target = machine.findUpgradeTarget(recipe.id("from"));
            if (target == null) continue;
            if (!(BuiltInRegistries.BLOCK.get(recipe.id("to")) instanceof MachineBlock replacement)) return InteractionResult.FAIL;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(level.getBlockEntity(target) instanceof MachineBlockEntity oldMachine)) return InteractionResult.FAIL;
            var core = oldMachine.controller();
            var old = level.getBlockState(target);
            var replacementState = replacement.defaultBlockState();
            if (replacement.kind.hasFacing() && old.hasProperty(BlockStateProperties.FACING))
                replacementState = replacementState.setValue(BlockStateProperties.FACING, old.getValue(BlockStateProperties.FACING));
            // Restore the link before notifying neighbours: the upgraded module may be a bridge
            // to the rest of the machine. The Scala UpgradeKit explicitly reconnects it too.
            if (!level.setBlock(target, replacementState, 2)) return InteractionResult.FAIL;
            if (level.getBlockEntity(target) instanceof MachineBlockEntity upgraded && core != null && core != oldMachine)
                upgraded.connectTo(core);
            level.updateNeighborsAt(target, replacement);
            level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1, 1);
            if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}

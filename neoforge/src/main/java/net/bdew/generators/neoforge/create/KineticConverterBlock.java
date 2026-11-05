package net.bdew.generators.neoforge.create;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;

/** Turns FE into Create rotation. A standalone block, not a multiblock module: it takes FE from whatever feeds it. */
public class KineticConverterBlock extends KineticBlock implements IBE<KineticConverterBlockEntity> {
    public KineticConverterBlock() {
        super(Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(2, 8));
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.AXIS);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(BlockStateProperties.AXIS); }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.AXIS, context.getClickedFace().getAxis());
    }

    /** A wrench turns the shaft: Y, then Z, then X. */
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            Direction.Axis next = switch (state.getValue(BlockStateProperties.AXIS)) {
                case X -> Direction.Axis.Y;
                case Y -> Direction.Axis.Z;
                default -> Direction.Axis.X;
            };
            BlockPos pos = context.getClickedPos();
            KineticBlockEntity.switchToBlockState(level, pos, state.setValue(BlockStateProperties.AXIS, next));
            if (level.getBlockEntity(pos) instanceof KineticConverterBlockEntity converter) {
                converter.updateGeneratedRotation();
                converter.sendData();
            }
        }
        IWrenchable.playRotateSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override public Class<KineticConverterBlockEntity> getBlockEntityClass() { return KineticConverterBlockEntity.class; }
    @Override public BlockEntityType<? extends KineticConverterBlockEntity> getBlockEntityType() { return CreateIntegration.BLOCK_ENTITY.get(); }
}

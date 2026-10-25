package net.bdew.generators.machine;

import com.mojang.serialization.MapCodec;
import dev.architectury.registry.menu.MenuRegistry;
import net.bdew.generators.platform.TransferPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class MachineBlock extends BaseEntityBlock {
    public final MachineKind kind;
    public MachineBlock(MachineKind kind) {
        super(Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH).setValue(BlockStateProperties.POWERED, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new MachineBlock(kind)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.FACING, BlockStateProperties.POWERED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(BlockStateProperties.FACING, context.getNearestLookingDirection().getOpposite()); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, net.bdew.generators.ModContent.MACHINE_ENTITY.get(), MachineBlockEntity::tick);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof UpgradeKitItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && TransferPlatform.interact(machine, player, hand, hit.getDirection()))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) return InteractionResult.PASS;
        var target = kind.controller() || kind == MachineKind.CONTROL || kind == MachineKind.SENSOR || kind.output() ? machine : machine.controller();
        if (target == null) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("advgenerators.status." + machine.network().error()), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) MenuRegistry.openExtendedMenu(serverPlayer, target, buf -> buf.writeBlockPos(target.getBlockPos()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (oldState.getBlock() != this) MultiblockNetwork.invalidate(level);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock()) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.dropItems();
            MultiblockNetwork.invalidate(level);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected boolean isSignalSource(BlockState state) { return kind == MachineKind.SENSOR; }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return kind == MachineKind.CONTROL || kind == MachineKind.SENSOR || kind.output();
    }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == MachineKind.SENSOR && state.getValue(BlockStateProperties.POWERED) && direction == state.getValue(BlockStateProperties.FACING).getOpposite() ? 15 : 0;
    }
}

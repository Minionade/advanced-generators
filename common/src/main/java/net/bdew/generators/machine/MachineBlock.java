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

public class MachineBlock extends BaseEntityBlock {
    public final MachineKind kind;
    public MachineBlock(MachineKind kind) {
        super(Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());
        this.kind = kind;
        var state = stateDefinition.any();
        if (kind.hasFacing()) state = state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        if (kind.hasPoweredState()) state = state.setValue(BlockStateProperties.POWERED, false);
        registerDefaultState(state);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> create(kind)); }
    // Block invokes this override from its constructor, before this.kind has
    // been assigned. Use concrete property-shape subclasses so these methods
    // never read instance fields during superclass construction.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}

    public static MachineBlock create(MachineKind kind) {
        if (kind.hasFacing() && kind.hasPoweredState()) return new FacingPowered(kind);
        if (kind.hasFacing()) return new Facing(kind);
        if (kind.hasPoweredState()) return new Powered(kind);
        return new MachineBlock(kind);
    }

    private static final class Facing extends MachineBlock {
        private Facing(MachineKind kind) { super(kind); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.FACING); }
    }
    private static final class Powered extends MachineBlock {
        private Powered(MachineKind kind) { super(kind); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.POWERED); }
    }
    private static final class FacingPowered extends MachineBlock {
        private FacingPowered(MachineKind kind) { super(kind); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.FACING, BlockStateProperties.POWERED); }
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!kind.controller() && MultiblockNetwork.findConnections(context.getLevel(), context.getClickedPos(), kind).size() > 1) return null;
        return kind.hasFacing() ? defaultBlockState().setValue(BlockStateProperties.FACING, context.getNearestLookingDirection().getOpposite()) : defaultBlockState();
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if (!kind.controller() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.tryConnect();
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state,level,pos,block,fromPos,moving);
        if (!kind.controller() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.tryConnect();
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return TransferPlatform.createMachineBlockEntity(pos, state); }
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
        if (player.isCrouching()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) return InteractionResult.PASS;
        // Controllers own their main GUI; control/sensor modules have their
        // own per-module configuration. All other modules open the controller.
        var target = kind.controller() || kind == MachineKind.CONTROL || kind == MachineKind.SENSOR ? machine : machine.controller();
        if (target == null) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("advgenerators.status." + machine.network().error()), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && target.kind().controller()) target.refreshOutputFaces();
        if (kind.controller() && !machine.hasRequiredModules()) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("bdlib.multiblock.incomplete"), false);
                machine.missingRequiredModules().forEach((module,count) -> {
                    String moduleName = switch (module) { case "turbine" -> "Turbine"; case "mixingChamber" -> "MixingChamber"; default -> "HeatExchanger"; };
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("bdlib.multiblock.incomplete.entry",
                        net.minecraft.network.chat.Component.literal(Integer.toString(count)).withStyle(net.minecraft.ChatFormatting.RED),
                        net.minecraft.network.chat.Component.translatable("advgenerators.module."+moduleName+".name").withStyle(net.minecraft.ChatFormatting.RED)),false);
                });
            }
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
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
                if (kind.controller()) machine.detachModules();
                machine.dropItems();
            }
            MultiblockNetwork.invalidate(level);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected boolean isSignalSource(BlockState state) {
        // Redstone wire treats signal sources as connectable endpoints. These
        // blocks can accept control/output configuration signals, while only
        // sensors actually emit a non-zero signal in getSignal below.
        return kind == MachineKind.CONTROL || kind == MachineKind.SENSOR || kind.output();
    }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == MachineKind.SENSOR && state.getValue(BlockStateProperties.POWERED) && direction == state.getValue(BlockStateProperties.FACING).getOpposite() ? 15 : 0;
    }
}

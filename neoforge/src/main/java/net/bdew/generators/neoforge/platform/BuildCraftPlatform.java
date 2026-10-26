package net.bdew.generators.neoforge.platform;

import buildcraft.lib.internal.mj.IMjConnector;
import buildcraft.lib.internal.mj.IMjPassiveProvider;
import buildcraft.lib.internal.mj.IMjReadable;
import buildcraft.lib.internal.mj.MjCapabilities;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Isolates optional BCCE class loading from installs without BuildCraft. */
final class BuildCraftPlatform {
    private BuildCraftPlatform() {}

    static MachineBlockEntity createMachineBlockEntity(BlockPos pos, BlockState state) {
        return BuildCraftMachineBlockEntity.create(pos, state);
    }

    static boolean canOutputOn(MachineBlockEntity machine, Direction side) {
        return BuildCraftMachineBlockEntity.canOutputOn(machine, side);
    }

    static void push(MachineBlockEntity machine) {
        if (machine instanceof BuildCraftMachineBlockEntity buildCraftMachine) {
            buildCraftMachine.pushMjOutput();
        }
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<MachineBlockEntity> type) {
        event.registerBlockEntity(MjCapabilities.CAP_CONNECTOR, type, (machine, side) ->
            machine instanceof BuildCraftMachineBlockEntity buildCraftMachine
                ? buildCraftMachine.buildCraftCapability(MjCapabilities.CAP_CONNECTOR, side) : null);
        event.registerBlockEntity(MjCapabilities.CAP_PASSIVE_PROVIDER, type, (machine, side) ->
            machine instanceof BuildCraftMachineBlockEntity buildCraftMachine
                ? buildCraftMachine.buildCraftCapability(MjCapabilities.CAP_PASSIVE_PROVIDER, side) : null);
        event.registerBlockEntity(MjCapabilities.CAP_READABLE, type, (machine, side) ->
            machine instanceof BuildCraftMachineBlockEntity buildCraftMachine
                ? buildCraftMachine.buildCraftCapability(MjCapabilities.CAP_READABLE, side) : null);
    }
}

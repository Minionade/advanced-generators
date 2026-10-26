package net.bdew.generators.neoforge.platform;

import net.bdew.generators.machine.MachineBlockEntity;

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
}

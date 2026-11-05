package net.bdew.generators.neoforge.platform;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergyTile;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What an EU output module does as an IC2 energy source. IC2 asks the tile what it offers every tick and then draws
 * from it, so the numbers are worked out from the controller's current state each time. The two machine entity
 * classes only forward here, so the logic exists once.
 */
final class Ic2Source {
    /** Same rate as the other EU outputs: one EU is worth four FE. */
    private static final double FE_PER_EU = 4;

    private Ic2Source() {}

    static Direction facing(MachineBlockEntity module) {
        return module.getBlockState().getValue(BlockStateProperties.FACING);
    }

    static int tier(MachineBlockEntity module) {
        return switch (module.kind()) {
            case EU_OUTPUT_LV -> 1;
            case EU_OUTPUT_MV -> 2;
            case EU_OUTPUT_HV -> 3;
            case EU_OUTPUT_EV -> 4;
            case EU_OUTPUT_IV -> 5;
            default -> 0;
        };
    }

    static double offered(MachineBlockEntity module) {
        if (!module.kind().isEuOutput()) return 0;
        Direction side = facing(module);
        MachineBlockEntity core = module.controller();
        if (core == null || !module.outputEnabled(side)) return 0;
        double limitEu = Math.max(0, core.outputLimit(module, side)) / FE_PER_EU;
        return Math.max(0, Math.min(module.kind().euOutputRate(), Math.min(limitEu, core.energy / FE_PER_EU)));
    }

    static void draw(MachineBlockEntity module, double amountEu) {
        MachineBlockEntity core = module.controller();
        if (core == null || amountEu <= 0) return;
        core.extractEnergy((long) Math.ceil(amountEu * FE_PER_EU), false);
        core.recordOutput(module, facing(module), amountEu);
    }

    static boolean emitsTo(MachineBlockEntity module, Direction side) {
        return module.kind().isEuOutput() && side == facing(module) && module.controller() != null;
    }

    /** Joins or leaves IC2's energy net; only EU output modules have anything to put on it. */
    static <T extends BlockEntity & IEnergyTile> void join(T module, MachineBlockEntity machine) {
        if (machine.getLevel() != null && !machine.getLevel().isClientSide && machine.kind().isEuOutput()) EnergyNet.instance.addBlockEntityTile(module);
    }

    static void leave(IEnergyTile tile, MachineBlockEntity machine) {
        if (machine.getLevel() != null && !machine.getLevel().isClientSide && machine.kind().isEuOutput()) EnergyNet.instance.removeTile(tile);
    }
}

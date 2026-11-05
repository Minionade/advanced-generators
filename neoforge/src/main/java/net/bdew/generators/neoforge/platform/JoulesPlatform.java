package net.bdew.generators.neoforge.platform;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.Direction;

/** Pushes the controller's energy into neighbouring Mekanism machines as Joules. Isolates Mekanism classes. */
final class JoulesPlatform {
    private JoulesPlatform() {}

    private static IStrictEnergyHandler target(MachineBlockEntity module, Direction side) {
        var level = module.getLevel();
        var pos = module.getBlockPos().relative(side);
        if (level == null || !level.hasChunkAt(pos)) return null;
        return level.getCapability(Capabilities.STRICT_ENERGY.block(), pos, side.getOpposite());
    }

    static boolean canOutputOn(MachineBlockEntity module, Direction side) {
        return target(module, side) != null;
    }

    static void push(MachineBlockEntity module) {
        var core = module.controller();
        double joulesPerFe = GeneratorConfig.INSTANCE.joulesPerFe;
        if (core == null || joulesPerFe <= 0) return;
        for (Direction side : Direction.values()) {
            if (!module.outputEnabled(side)) continue;
            IStrictEnergyHandler handler = target(module, side);
            if (handler == null) continue;
            long fe = Math.min(core.energy, Math.max(0, core.outputLimit(module, side)));
            long offered = (long) Math.floor(fe * joulesPerFe);
            // insertEnergy answers with what did not fit
            long fits = offered - handler.insertEnergy(offered, Action.SIMULATE);
            if (fits <= 0) continue;
            long accepted = fits - handler.insertEnergy(fits, Action.EXECUTE);
            if (accepted <= 0) continue;
            long spent = core.extractEnergy((long) Math.ceil(accepted / joulesPerFe), false);
            core.recordOutput(module, side, spent);
        }
    }
}

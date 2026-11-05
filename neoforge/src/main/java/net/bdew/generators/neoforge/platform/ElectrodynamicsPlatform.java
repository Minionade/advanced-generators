package net.bdew.generators.neoforge.platform;

import electrodynamics.prefab.utilities.ElectricityUtils;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.Direction;
import voltaic.api.electricity.ICapabilityElectrodynamic;
import voltaic.prefab.utilities.object.TransferPack;
import voltaic.registers.VoltaicCapabilities;

/** Pushes the controller's energy into neighbouring Electrodynamics machines as Joules. Isolates its classes. */
final class ElectrodynamicsPlatform {
    private ElectrodynamicsPlatform() {}

    static boolean canOutputOn(MachineBlockEntity module, Direction side) {
        var level = module.getLevel();
        var pos = module.getBlockPos().relative(side);
        if (level == null || !level.hasChunkAt(pos)) return false;
        var entity = level.getBlockEntity(pos);
        return entity != null && ElectricityUtils.isElectricReceiver(entity, side.getOpposite());
    }

    static void push(MachineBlockEntity module) {
        var core = module.controller();
        var config = GeneratorConfig.INSTANCE;
        var level = module.getLevel();
        if (core == null || level == null || config.electrodynamicsJoulesPerFe <= 0) return;
        for (Direction side : Direction.values()) {
            if (!module.outputEnabled(side)) continue;
            var pos = module.getBlockPos().relative(side);
            if (!level.hasChunkAt(pos)) continue;
            var entity = level.getBlockEntity(pos);
            if (entity == null || !ElectricityUtils.isElectricReceiver(entity, side.getOpposite())) continue;
            long fe = Math.min(core.energy, Math.max(0, core.outputLimit(module, side)));
            double joules = Math.floor(fe * config.electrodynamicsJoulesPerFe);
            if (joules <= 0) continue;
            // ask for the preferred voltage, kept inside what the receiver accepts
            double voltage = config.electrodynamicsVoltage;
            ICapabilityElectrodynamic receiver = level.getCapability(VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK, pos, side.getOpposite());
            if (receiver != null) voltage = Math.max(receiver.getMinimumVoltage(), Math.min(receiver.getMaximumVoltage(), voltage));
            var pack = TransferPack.joulesVoltage(joules, voltage);
            double fits = ElectricityUtils.receivePower(entity, side.getOpposite(), pack, true).getJoules();
            if (fits <= 0) continue;
            double accepted = ElectricityUtils.receivePower(entity, side.getOpposite(), TransferPack.joulesVoltage(fits, voltage), false).getJoules();
            if (accepted <= 0) continue;
            long spent = core.extractEnergy((long) Math.ceil(accepted / config.electrodynamicsJoulesPerFe), false);
            core.recordOutput(module, side, spent);
        }
    }
}

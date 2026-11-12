package net.bdew.generators.fabric;

import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

final class EnergyBufferStorage implements EnergyStorage {
    private final MachineBlockEntity controller, module;
    private final Direction side;
    private final MachineKind outputKind;
    private final MachineTransaction state;
    EnergyBufferStorage(MachineBlockEntity controller, MachineBlockEntity module, Direction side, MachineKind outputKind) {
        this.controller = controller; this.module = module; this.side = side; this.outputKind = outputKind;
        state = MachineTransaction.of(controller);
    }
    private double ratio() { return outputKind.isEuOutput() ? GeneratorConfig.fePerEu() : 1; }
    @Override public long insert(long max, TransactionContext transaction) { return 0; }
    @Override public long extract(long max, TransactionContext transaction) {
        if (max < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (max == 0 || !supportsExtraction()) return 0;
        long limit = (long) (Math.max(0, controller.outputLimit(module, side)) / ratio());
        if (outputKind.isEuOutput()) limit = Math.min(limit, outputKind.euOutputRate());
        long moved = Math.min(max, Math.min(limit, getAmount()));
        if (moved > 0) {
            state.beforeChange(transaction);
            controller.extractEnergy((long) Math.ceil(moved * ratio()), false);
            state.recordOutput(module, side, moved);
        }
        return moved;
    }
    @Override public long getAmount() { return (long) (controller.energy / ratio()); }
    @Override public long getCapacity() { return (long) (controller.energyCapacity / ratio()); }
    @Override public boolean supportsExtraction() {
        return controller.canAccessFrom(module) && module.outputEnabled(side);
    }
    @Override public boolean supportsInsertion() { return false; }
}

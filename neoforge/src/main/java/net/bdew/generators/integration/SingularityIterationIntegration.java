package net.bdew.generators.integration;

import com.miophas.singularity_iteration.core.api.energy.storage.CableTier;
import com.miophas.singularity_iteration.core.api.energy.storage.EUApi;
import com.miophas.singularity_iteration.core.api.energy.storage.IEUEnergyStorage;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Optional EU output capability for Singularity Iteration's native cable network. */
public final class SingularityIterationIntegration {
    private static final long FE_PER_EU = 4;

    private SingularityIterationIntegration() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(EUApi.SIDED, ModContent.MACHINE_ENTITY.get(),
            (machine, side) -> {
                if (!machine.kind().isEnergyOutput() || side == null || !machine.outputEnabled(side)) return null;
                if (machine.kind().hasFacing()
                    && machine.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) != side) return null;
                MachineBlockEntity core = machine.controller();
                if (core == null) return null;
                return new GeneratorEnergy(core, machine.kind(), side);
            });
    }

    private static final class GeneratorEnergy implements IEUEnergyStorage.NoInsert {
        private final MachineBlockEntity core;
        private final MachineKind kind;
        private final Direction side;

        private GeneratorEnergy(MachineBlockEntity core, MachineKind kind, Direction side) {
            this.core = core;
            this.kind = kind;
            this.side = side;
        }

        private long outputRateEu() {
            return Math.max(0, core.outputLimit[side.get3DDataValue()]) / FE_PER_EU;
        }

        private CableTier tier() {
            return switch (kind) {
                case EU_OUTPUT_LV -> CableTier.LV;
                case EU_OUTPUT_MV -> CableTier.MV;
                case EU_OUTPUT_HV -> CableTier.HV;
                case EU_OUTPUT_EV -> CableTier.EV;
                case EU_OUTPUT_IV -> CableTier.IV;
                default -> {
                    long rate = outputRateEu();
                    if (rate <= CableTier.LV.powerRating) yield CableTier.LV;
                    if (rate <= CableTier.MV.powerRating) yield CableTier.MV;
                    if (rate <= CableTier.HV.powerRating) yield CableTier.HV;
                    if (rate <= CableTier.EV.powerRating) yield CableTier.EV;
                    yield CableTier.IV;
                }
            };
        }

        @Override public boolean canConnect(CableTier cableTier) {
            return cableTier != null && cableTier.powerRating <= tier().powerRating;
        }
        @Override public long receive(long amount, boolean simulate) { return 0; }
        @Override public long extract(long maxExtract, boolean simulate) {
            if (maxExtract <= 0 || !core.kind().controller()) return 0;
            long available = Math.max(0, core.energy / FE_PER_EU);
            long movedEu = Math.min(Math.min(maxExtract, available), Math.min(outputRateEu(), tier().powerRating));
            if (!simulate && movedEu > 0) core.extractEnergy(movedEu * FE_PER_EU, false);
            return movedEu;
        }
        @Override public long getAmount() { return Math.max(0, core.energy / FE_PER_EU); }
        @Override public long getCapacity() { return Math.max(0, core.energyCapacity / FE_PER_EU); }
        @Override public boolean canReceive() { return false; }
        @Override public boolean canExtract() {
            return core.energy >= FE_PER_EU && outputRateEu() > 0 && core.kind().controller();
        }
    }
}

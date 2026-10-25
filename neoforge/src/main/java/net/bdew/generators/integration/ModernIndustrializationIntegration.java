package net.bdew.generators.integration;

import aztech.modern_industrialization.api.energy.CableTier;
import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.api.energy.MIEnergyStorage;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Exposes the generator's EU output modules to Modern Industrialization cables. */
public final class ModernIndustrializationIntegration {
    private static final long FE_PER_EU = 4;

    private ModernIndustrializationIntegration() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(EnergyApi.SIDED, ModContent.MACHINE_ENTITY.get(),
            ModernIndustrializationIntegration::storage);
    }

    private static MIEnergyStorage storage(MachineBlockEntity module, Direction side) {
        if (side == null || !module.kind().isEuOutput()) return null;
        if (module.getBlockState().getValue(BlockStateProperties.FACING) != side) return null;
        MachineBlockEntity core = module.controller();
        if (core == null || !module.outputEnabled(side)) return null;

        return new MIEnergyStorage.NoInsert() {
            private long rateEu() {
                return Math.min(module.kind().euOutputRate(),
                    Math.max(0, core.outputLimit(module, side)) / FE_PER_EU);
            }

            @Override
            public boolean canConnect(CableTier tier) {
                return rateEu() > 0 && rateEu() <= tier.eu;
            }

            @Override
            public long extract(long maximum, boolean simulate) {
                if (maximum <= 0 || !module.outputEnabled(side)) return 0;
                long extractedEu = Math.min(maximum, Math.min(rateEu(), core.energy / FE_PER_EU));
                if (!simulate && extractedEu > 0) { core.extractEnergy(extractedEu * FE_PER_EU, false); core.recordOutput(module, side, extractedEu); }
                return extractedEu;
            }

            @Override public long getAmount() { return core.energy / FE_PER_EU; }
            @Override public long getCapacity() { return core.energyCapacity / FE_PER_EU; }
            @Override public boolean canExtract() { return module.outputEnabled(side) && rateEu() > 0; }
        };
    }
}

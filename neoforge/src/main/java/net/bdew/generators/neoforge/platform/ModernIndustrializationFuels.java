package net.bdew.generators.neoforge.platform;

import aztech.modern_industrialization.api.datamaps.FluidFuel;
import net.minecraft.world.level.material.Fluid;

/** Isolates Modern Industrialization classes from installs without it. */
final class ModernIndustrializationFuels {
    private ModernIndustrializationFuels() {}

    static double fePerMb(Fluid fluid, double fePerEu) {
        return FluidFuel.getEu(fluid) * fePerEu;
    }
}

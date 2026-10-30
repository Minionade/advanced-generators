package net.bdew.generators.neoforge.platform;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.fluid.FluidVariant;
import buildcraft.lib.fluid.FuelApiBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

/** Isolates BuildCraft classes from installs without it. */
final class BuildCraftFuels {
    private BuildCraftFuels() {}

    /** A fuel's energy per bucket is its power per tick times the ticks a bucket burns for. */
    static double fePerMb(Fluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        var found = BuildCraftApi.service(BuildCraftServices.ENERGY_FLUIDS).findFuel(FluidVariant.of(id), FuelApiBridge.MATCH_CONTEXT);
        if (found.isEmpty()) return 0;
        var profile = found.get().profile();
        double microMjPerBucket = (double) profile.powerPerTickMicroMj() * profile.burnTicksPerBucket();
        long microMjPerFe = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().microMjPerFe();
        return microMjPerFe <= 0 ? 0 : microMjPerBucket / 1000.0 / microMjPerFe;
    }
}

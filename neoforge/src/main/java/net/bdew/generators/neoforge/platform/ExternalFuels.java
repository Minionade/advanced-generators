package net.bdew.generators.neoforge.platform;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;

/** Fuels other mods define: the fuel turbine burns them without needing a recipe for each. */
final class ExternalFuels {
    private ExternalFuels() {}

    static double fePerMb(Fluid fluid) {
        double result = 0;
        if (ModList.get().isLoaded("modern_industrialization")) result = ModernIndustrializationFuels.fePerMb(fluid, net.bdew.generators.config.GeneratorConfig.fePerEu());
        if (result <= 0 && ModList.get().isLoaded("buildcraftlib")) result = BuildCraftFuels.fePerMb(fluid);
        return result;
    }
}

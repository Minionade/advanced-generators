package net.bdew.generators.legacy;

import net.bdew.generators.config.GeneratorConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports the balance settings of the original mod's {@code advgenerators-common.toml} (Forge config, 1.20.1)
 * into {@link GeneratorConfig}. Settings missing from the file keep their defaults.
 */
public final class LegacyConfig {
    private LegacyConfig() {}

    /** Controller section in the old file to the controller id used by {@link GeneratorConfig#moduleLimits}. */
    private static final Map<String, String> CONTROLLERS = Map.of(
        "TurbineFuel", "fuel_turbine_controller",
        "TurbineSteam", "steam_turbine_controller",
        "SyngasProducer", "syngas_controller",
        "HeatExchanger", "exchanger_controller");

    /** The old module type ids, as written in the {@code Modules = ["Turbine:50", ...]} lists, to today's categories. */
    private static final Map<String, String> MODULE_CATEGORIES = Map.ofEntries(
        Map.entry("Turbine", "turbine"), Map.entry("FuelTank", "fuelTank"), Map.entry("PowerCapacitor", "powerCapacitor"),
        Map.entry("PowerOutput", "powerOutput"), Map.entry("FluidOutputSelect", "fluidOutput"), Map.entry("FluidInput", "fluidInput"),
        Map.entry("ItemInput", "itemInput"), Map.entry("ItemOutput", "itemOutput"), Map.entry("Sensor", "sensor"),
        Map.entry("Control", "control"), Map.entry("EfficiencyUpgradeTier1", "efficiencyUpgradeTier1"),
        Map.entry("EfficiencyUpgradeTier2", "efficiencyUpgradeTier2"), Map.entry("HeatingChamber", "heatingChamber"),
        Map.entry("MixingChamber", "mixingChamber"), Map.entry("HeatExchanger", "heatExchanger"));

    public static GeneratorConfig fromToml(String toml) {
        return apply(MiniToml.parse(toml), new GeneratorConfig());
    }

    public static GeneratorConfig apply(Map<String, Object> root, GeneratorConfig c) {
        var fuel = table(root, "TurbineFuel");
        var steam = table(root, "TurbineSteam");
        var syngas = table(root, "SyngasProducer");
        var exchanger = table(root, "HeatExchanger");
        var modules = table(root, "Modules");

        c.fuelCapacity = (int) num(fuel, "InternalFuelCapacity", c.fuelCapacity);
        // The old turbines each had their own base energy capacity; the port keeps a single value.
        c.energyCapacity = (long) num(fuel, "InternalEnergyCapacity", num(steam, "InternalEnergyCapacity", c.energyCapacity));
        var efficiency = table(fuel, "FuelEfficiency");
        c.fuelEfficiency = new double[]{num(efficiency, "Base", c.fuelEfficiency[0]), num(efficiency, "Tier1", c.fuelEfficiency[1]), num(efficiency, "Tier2", c.fuelEfficiency[2])};

        c.steamCapacity = (int) num(steam, "InternalSteamCapacity", c.steamCapacity);
        c.maxRpm = num(steam, "MaxRPM", c.maxRpm);
        c.steamEnergyDensity = num(steam, "SteamEnergyDensity", c.steamEnergyDensity);
        var inertia = table(steam, "Inertia");
        c.inertiaMultiplier = num(inertia, "InertiaFunctionMultiplier", c.inertiaMultiplier);
        c.inertiaExponent = num(inertia, "InertiaFunctionExponent", c.inertiaExponent);
        var drag = table(steam, "Drag");
        c.spinUp = num(drag, "SpinUpMultiplier", c.spinUp);
        c.baseDrag = num(drag, "BaseDragMultiplier", c.baseDrag);
        c.coilDrag = num(drag, "CoilDragMultiplier", c.coilDrag);

        c.syngasCapacity = (int) num(syngas, "InternalTankCapacity", c.syngasCapacity);
        c.syngasWorkHeat = num(syngas, "WorkHeat", c.syngasWorkHeat);
        c.syngasMaxHeat = num(syngas, "MaxHeat", c.syngasMaxHeat);
        c.carbonPerSyngas = num(syngas, "CarbonPerMBSyngas", c.carbonPerSyngas);
        c.steamPerSyngas = num(syngas, "SteamPerMBSyngas", c.steamPerSyngas);
        c.waterSteamRatio = num(syngas, "WaterSteamRatio", c.waterSteamRatio);
        c.mixingThroughput = num(syngas, "MixingChamberThroughput", c.mixingThroughput);
        c.heatingThroughput = num(syngas, "HeatingChamberThroughput", c.heatingThroughput);
        c.chamberHeating = num(syngas, "HeatingChamberHeating", c.chamberHeating);
        c.chamberLoss = num(syngas, "HeatingChamberLoss", c.chamberLoss);
        c.carbonPerHeat = num(syngas, "CarbonPerHeat", c.carbonPerHeat);

        c.exchangerCapacity = (int) num(exchanger, "InternalTankCapacity", c.exchangerCapacity);
        c.exchangerMaxHeat = num(exchanger, "MaxHeat", c.exchangerMaxHeat);
        c.exchangerStartHeat = num(exchanger, "StartHeating", c.exchangerStartHeat);
        c.exchangerDecay = num(exchanger, "HeatDecay", c.exchangerDecay);
        c.exchangerTransfer = num(table(modules, "HeatExchanger"), "HeatTransfer", c.exchangerTransfer);

        c.fuelTankCapacity = (int) num(table(modules, "FuelTank"), "Capacity", c.fuelTankCapacity);
        var turbines = table(modules, "Turbines");
        var capacitors = table(modules, "Capacitors");
        for (int tier = 0; tier < 5; tier++) {
            var t = table(turbines, "Tier" + (tier + 1));
            c.turbineOutput[tier] = (int) num(t, "MaxFEPerTick", c.turbineOutput[tier]);
            c.turbineInertia[tier] = num(t, "Inertia", c.turbineInertia[tier]);
        }
        for (int tier = 0; tier < 3; tier++)
            c.capacitorCapacity[tier] = (long) num(table(capacitors, "Tier" + (tier + 1)), "Capacity", c.capacitorCapacity[tier]);

        CONTROLLERS.forEach((section, controller) -> {
            var list = table(root, section).get("Modules");
            if (list instanceof List<?> entries) c.moduleLimits.put(controller, limits(entries));
        });
        return c;
    }

    /** {@code ["PowerOutput:6", ...]}: modules absent from the list cannot connect, as in the original. */
    private static Map<String, Integer> limits(List<?> entries) {
        var result = new LinkedHashMap<String, Integer>();
        for (Object entry : entries) {
            if (!(entry instanceof String s) || s.indexOf(':') < 0) continue;
            String category = MODULE_CATEGORIES.get(s.substring(0, s.indexOf(':')));
            try {
                if (category != null) result.put(category, Integer.parseInt(s.substring(s.indexOf(':') + 1).trim()));
            } catch (NumberFormatException ignored) {
                // an invalid entry is skipped, as the original config validator would have rejected it
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table(Map<String, Object> parent, String key) {
        return parent.get(key) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static double num(Map<String, Object> table, String key, double fallback) {
        return table.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }
}

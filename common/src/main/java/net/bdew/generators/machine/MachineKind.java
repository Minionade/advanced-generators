package net.bdew.generators.machine;

public enum MachineKind {
    FUEL_TURBINE_CONTROLLER("controller", 0), STEAM_TURBINE_CONTROLLER("controller", 0),
    SYNGAS_CONTROLLER("controller", 0), EXCHANGER_CONTROLLER("controller", 0),
    FE_OUTPUT("powerOutput", 0), EU_OUTPUT_LV("powerOutput", 1), EU_OUTPUT_MV("powerOutput", 2),
    EU_OUTPUT_HV("powerOutput", 3), EU_OUTPUT_EV("powerOutput", 4), EU_OUTPUT_IV("powerOutput", 5),
    FLUID_INPUT("fluidInput", 0), GAS_INPUT("gasInput", 0), FUEL_TANK("fuelTank", 0),
    TURBINE_TIER1("turbine", 1), TURBINE_TIER2("turbine", 2), TURBINE_TIER3("turbine", 3),
    TURBINE_TIER4("turbine", 4), TURBINE_TIER5("turbine", 5),
    POWER_CAPACITOR_TIER1("powerCapacitor", 1), POWER_CAPACITOR_TIER2("powerCapacitor", 2), POWER_CAPACITOR_TIER3("powerCapacitor", 3),
    EFFICIENCY_UPGRADE_TIER1("efficiencyUpgradeTier1", 1), EFFICIENCY_UPGRADE_TIER2("efficiencyUpgradeTier2", 2),
    FLUID_OUTPUT_SELECT("fluidOutput", 0), ITEM_INPUT("itemInput", 0), ITEM_OUTPUT("itemOutput", 0),
    HEATING_CHAMBER("heatingChamber", 0), MIXING_CHAMBER("mixingChamber", 0), HEAT_EXCHANGER("heatExchanger", 0),
    CONTROL("control", 0), SENSOR("sensor", 0);

    public final String category;
    public final int tier;
    MachineKind(String category, int tier) { this.category = category; this.tier = tier; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public boolean controller() { return category.equals("controller"); }
    public boolean output() { return isEnergyOutput() || this == FLUID_OUTPUT_SELECT || this == ITEM_OUTPUT; }
    public boolean isEnergyOutput() { return category.equals("powerOutput"); }
    /** Only these legacy blocks stored a facing direction in their block state. */
    public boolean hasFacing() { return this == SENSOR || name().startsWith("EU_OUTPUT_"); }
    public boolean hasPoweredState() { return this == CONTROL || this == SENSOR; }
}

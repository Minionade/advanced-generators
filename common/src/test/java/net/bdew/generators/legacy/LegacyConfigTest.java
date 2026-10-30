package net.bdew.generators.legacy;

import com.google.gson.Gson;
import net.bdew.generators.config.GeneratorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConfigTest {
    /** Shaped like the file Forge wrote for the original mod: comments, indented tables, one-line and wrapped arrays. */
    private static final String TOML = """
        #Internal fuel capacity (mB) - this is the base value, expandable by tanks
        [TurbineFuel]
        	#Range: 1 ~ 2147483647
        	InternalFuelCapacity = 2000
        	InternalEnergyCapacity = 250000
        	#Required modules for the machine to function
        	Required = ["Turbine:1"]
        	#Modules that can connect to the machine
        	Modules = ["PowerOutput:3", "Turbine:20", "FluidInput:5", "FuelTank:10",
        		"PowerCapacitor:10", "Sensor:10", "Control:10", "EfficiencyUpgradeTier1:1"]

        	#Fuel Efficiency
        	[TurbineFuel.FuelEfficiency]
        		Base = 1.0
        		Tier1 = 1.5
        		Tier2 = 2.0

        [TurbineSteam]
        	MaxRPM = 4000.0
        	SteamEnergyDensity = 3.0 # FE/mB
        	InternalSteamCapacity = 8000
        	[TurbineSteam.Inertia]
        		InertiaFunctionMultiplier = 0.05
        		InertiaFunctionExponent = -3.0
        	[TurbineSteam.Drag]
        		SpinUpMultiplier = 2.0
        		BaseDragMultiplier = 0.3
        		CoilDragMultiplier = 0.5

        [SyngasProducer]
        	InternalTankCapacity = 16000
        	WorkHeat = 100.0
        	MaxHeat = 250.0
        	CarbonPerMBSyngas = 40.0
        	CarbonPerHeat = 10.0
        	Modules = ["ItemInput:2", "MixingChamber:4"]

        [HeatExchanger]
        	MaxHeat = 2000.0
        	StartHeating = 200.0
        	HeatDecay = 0.1

        [Modules]
        	[Modules.FuelTank]
        		Capacity = 32000
        	[Modules.HeatExchanger]
        		HeatTransfer = 24.0
        	[Modules.Turbines]
        		[Modules.Turbines.Tier2]
        			MaxFEPerTick = 175
        			Inertia = 1.15
        	[Modules.Capacitors]
        		[Modules.Capacitors.Tier3]
        			Capacity = 30000000
        """;

    private static String json(GeneratorConfig c) { return new Gson().toJson(c); }

    @Test void parsesTablesValuesAndWrappedArrays() {
        Map<String, Object> toml = MiniToml.parse(TOML);
        @SuppressWarnings("unchecked") var fuel = (Map<String, Object>) toml.get("TurbineFuel");
        assertEquals(2000L, fuel.get("InternalFuelCapacity"));
        assertEquals(List.of("Turbine:1"), fuel.get("Required"));
        assertEquals(8, ((List<?>) fuel.get("Modules")).size());
        @SuppressWarnings("unchecked") var steam = (Map<String, Object>) toml.get("TurbineSteam");
        assertEquals(3.0, steam.get("SteamEnergyDensity"), "a trailing comment is ignored");
        @SuppressWarnings("unchecked") var drag = (Map<String, Object>) steam.get("Drag");
        assertEquals(0.3, drag.get("BaseDragMultiplier"));
    }

    @Test void importsEveryGroupOfSettings() {
        var c = LegacyConfig.fromToml(TOML);
        assertEquals(2000, c.fuelCapacity);
        assertEquals(250000, c.energyCapacity);
        assertArrayEquals(new double[]{1.0, 1.5, 2.0}, c.fuelEfficiency);
        assertEquals(4000, c.maxRpm);
        assertEquals(3, c.steamEnergyDensity);
        assertEquals(8000, c.steamCapacity);
        assertEquals(.05, c.inertiaMultiplier);
        assertEquals(-3, c.inertiaExponent);
        assertEquals(2, c.spinUp);
        assertEquals(.3, c.baseDrag);
        assertEquals(.5, c.coilDrag);
        assertEquals(16000, c.syngasCapacity);
        assertEquals(100, c.syngasWorkHeat);
        assertEquals(250, c.syngasMaxHeat);
        assertEquals(40, c.carbonPerSyngas);
        assertEquals(10, c.carbonPerHeat);
        assertEquals(2000, c.exchangerMaxHeat);
        assertEquals(200, c.exchangerStartHeat);
        assertEquals(.1, c.exchangerDecay);
        assertEquals(24, c.exchangerTransfer);
        assertEquals(32000, c.fuelTankCapacity);
        assertEquals(175, c.turbineOutput[1]);
        assertEquals(1.15, c.turbineInertia[1]);
        assertEquals(30000000L, c.capacitorCapacity[2]);
        c.validate();
    }

    @Test void settingsThatAreMissingKeepTheirDefaults() {
        var c = LegacyConfig.fromToml(TOML);
        var defaults = new GeneratorConfig();
        assertEquals(defaults.waterSteamRatio, c.waterSteamRatio);
        assertEquals(defaults.turbineOutput[0], c.turbineOutput[0]);
        assertEquals(defaults.capacitorCapacity[0], c.capacitorCapacity[0]);
        assertEquals(json(defaults), json(LegacyConfig.fromToml("")), "an empty file changes nothing");
    }

    @Test void moduleListsReplaceTheDefaultsForThatController() {
        var c = LegacyConfig.fromToml(TOML);
        var fuel = c.moduleLimits.get("fuel_turbine_controller");
        assertEquals(3, fuel.get("powerOutput"));
        assertEquals(20, fuel.get("turbine"));
        assertEquals(1, fuel.get("efficiencyUpgradeTier1"));
        assertNull(fuel.get("efficiencyUpgradeTier2"), "modules left out of the old list cannot connect");
        assertEquals(Map.of("itemInput", 2, "mixingChamber", 4), c.moduleLimits.get("syngas_controller"));
        assertEquals(new GeneratorConfig().moduleLimits.get("exchanger_controller"), c.moduleLimits.get("exchanger_controller"), "no list, no change");
    }

    @Test void aMalformedFileDoesNotThrow() {
        assertDoesNotThrow(() -> LegacyConfig.fromToml("[[[ broken\nkey = = =\n[TurbineFuel]\nInternalFuelCapacity = notanumber\n"));
    }
}

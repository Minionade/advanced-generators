package net.bdew.generators.config;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bdew.generators.legacy.LegacyConfig;
import net.bdew.generators.machine.MachineKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The same balance file is used on both loaders. Read on server startup. */
public final class GeneratorConfig {
    public static GeneratorConfig INSTANCE = new GeneratorConfig();
    public int maxConnectedBlocks = 256;
    public int fuelCapacity = 1000, fuelTankCapacity = 16000, steamCapacity = 16000, syngasCapacity = 32000, exchangerCapacity = 16000;
    public long energyCapacity = 100000;
    public long[] capacitorCapacity = {1000000, 5000000, 25000000};
    public int[] turbineOutput = {100, 150, 250, 500, 1000};
    public double[] turbineInertia = {1, 1.1, 1.2, 1.25, 1.4};
    public double[] fuelEfficiency = {1.1, 1.35, 1.85};
    public double maxRpm = 5000, inertiaMultiplier = .025, inertiaExponent = -2;
    public double spinUp = 1, baseDrag = .2, coilDrag = .75, steamEnergyDensity = 2;
    public double syngasWorkHeat = 150, syngasMaxHeat = 200, carbonPerSyngas = 50, steamPerSyngas = 10, waterSteamRatio = 3;
    public double mixingThroughput = 1, heatingThroughput = 50, chamberHeating = .1, chamberLoss = .02, carbonPerHeat = 20;
    public double exchangerMaxHeat = 1000, exchangerStartHeat = 150, exchangerDecay = .05, exchangerTransfer = 16;
    /** Mekanism Joules per FE (Mekanism's own default conversion is 1 FE = 2.5 J). */
    public double joulesPerFe = 2.5;
    /** Electrodynamics Joules per FE, and the voltage it is asked for (clamped to what the receiver accepts). */
    public double electrodynamicsJoulesPerFe = 1, electrodynamicsVoltage = 120;
    /** Create Kinetic Converter: FE used per tick while it turns, the rotation it makes, the stress it carries, its FE buffer. */
    public int createFePerTick = 100, createSpeedRpm = 256, createStressCapacity = 256, createBuffer = 10000;
    /** Multiplies every fuel value (recipes, other mods' fuels and fuelValues alike). */
    public double fuelMultiplier = 1;
    /** Extra or replacement fuel values in FE per mB: "fluid_id=fe_per_mb" or "#fluid_tag=fe_per_mb". First match wins. */
    public java.util.List<String> fuelValues = new java.util.ArrayList<>();
    public Map<String, Map<String, Integer>> moduleLimits = defaults();
    private transient java.util.List<FuelOverrides.Entry> parsedFuelValues;

    public java.util.List<FuelOverrides.Entry> fuelOverrides() {
        if (parsedFuelValues == null) {
            var errors = new java.util.ArrayList<String>();
            parsedFuelValues = FuelOverrides.parse(fuelValues, errors);
            errors.forEach(e -> LOG.warn("Ignoring fuelValues entry {}", e));
        }
        return parsedFuelValues;
    }

    private static Map<String, Map<String, Integer>> defaults() {
        var result = new LinkedHashMap<String, Map<String, Integer>>();
        for (var kind : MachineKind.values()) if (kind.controller()) {
            var limits = new LinkedHashMap<String, Integer>();
            limits.put("sensor", 10); limits.put("control", 10); limits.put("fluidInput", 5);
            switch (kind) {
                case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> {
                    limits.put("turbine", 50); limits.put("powerOutput", 6); limits.put("powerCapacitor", 10);
                    if (kind == MachineKind.FUEL_TURBINE_CONTROLLER) {
                        limits.put("fuelTank", 10); limits.put("efficiencyUpgradeTier1", 1); limits.put("efficiencyUpgradeTier2", 1);
                    }
                }
                case SYNGAS_CONTROLLER -> {
                    limits.put("itemInput", 5); limits.put("fluidOutput", 6); limits.put("heatingChamber", 10); limits.put("mixingChamber", 25);
                }
                case EXCHANGER_CONTROLLER -> {
                    limits.put("itemInput", 5); limits.put("itemOutput", 6); limits.put("fluidOutput", 6); limits.put("heatExchanger", 50);
                }
                default -> {}
            }
            result.put(kind.id(), limits);
        }
        return result;
    }
    public int limit(MachineKind controller, String category) { return moduleLimits.getOrDefault(controller.id(), Map.of()).getOrDefault(category, 0); }
    public void validate() {
        if (maxConnectedBlocks < 4 || maxConnectedBlocks > 4096) throw new IllegalArgumentException("maxConnectedBlocks must be 4..4096");
        if (turbineOutput.length != 5 || turbineInertia.length != 5 || capacitorCapacity.length != 3 || fuelEfficiency.length != 3)
            throw new IllegalArgumentException("Invalid tier array lengths");
        for (var field : getClass().getFields()) try {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            Object value = field.get(this);
            if (value instanceof Number number && (!Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0) && !field.getName().equals("inertiaExponent"))
                throw new IllegalArgumentException(field.getName() + " must be positive and finite");
            if (value instanceof double[] a) for (double x : a) if (!Double.isFinite(x) || x <= 0) throw new IllegalArgumentException(field.getName());
            if (value instanceof int[] a) for (int x : a) if (x <= 0) throw new IllegalArgumentException(field.getName());
            if (value instanceof long[] a) for (long x : a) if (x <= 0) throw new IllegalArgumentException(field.getName());
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        if (exchangerDecay >= 1 || syngasWorkHeat >= syngasMaxHeat || exchangerStartHeat >= exchangerMaxHeat || !Double.isFinite(inertiaExponent))
            throw new IllegalArgumentException("Invalid heat or inertia configuration");
        if (fuelValues == null) fuelValues = new java.util.ArrayList<>();
        parsedFuelValues = null;
        if (spinUp <= baseDrag + coilDrag) throw new IllegalArgumentException("spinUp must exceed baseDrag + coilDrag");
        for (var kind : MachineKind.values()) if (kind.controller() && !moduleLimits.containsKey(kind.id())) throw new IllegalArgumentException("Missing module limits: " + kind.id());
        moduleLimits.values().forEach(m -> m.forEach((k,v) -> { if (v == null || v < 0 || v > maxConnectedBlocks) throw new IllegalArgumentException("Invalid module limit: " + k); }));
    }
    /** Forge wrote the original mod's settings here (config/advgenerators-common.toml). */
    public static final String LEGACY_CONFIG_FILE = "advgenerators-common.toml";
    private static final Logger LOG = LoggerFactory.getLogger("advgenerators");

    /** First run after upgrading from the 1.20.1 mod: carry the old balance settings over. */
    private static void importLegacyConfig(Path legacy) {
        if (!Files.exists(legacy)) return;
        try {
            var imported = LegacyConfig.fromToml(Files.readString(legacy));
            imported.validate();
            INSTANCE = imported;
            LOG.info("Imported settings from the previous version's config {}", legacy.getFileName());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not import {}, using default settings", legacy.getFileName(), e);
        }
    }
    public static void load(Path path) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) INSTANCE = gson.fromJson(Files.readString(path), GeneratorConfig.class);
            else {
                Files.createDirectories(path.getParent());
                importLegacyConfig(path.resolveSibling(LEGACY_CONFIG_FILE));
                Files.writeString(path, gson.toJson(INSTANCE));
            }
            if (INSTANCE == null) throw new IllegalArgumentException("Empty config");
            INSTANCE.validate();
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Invalid Advanced Generators config: " + path, e); }
    }
}

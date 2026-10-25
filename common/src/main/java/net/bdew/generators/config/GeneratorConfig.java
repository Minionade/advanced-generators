package net.bdew.generators.config;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bdew.generators.machine.MachineKind;

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
    public Map<String, Map<String, Integer>> moduleLimits = defaults();

    private static Map<String, Map<String, Integer>> defaults() {
        var result = new LinkedHashMap<String, Map<String, Integer>>();
        for (var kind : MachineKind.values()) if (kind.controller()) {
            var limits = new LinkedHashMap<String, Integer>();
            limits.put("sensor", 10); limits.put("control", 10); limits.put("fluidInput", 5); limits.put("gasInput", 5);
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
        if (spinUp <= baseDrag + coilDrag) throw new IllegalArgumentException("spinUp must exceed baseDrag + coilDrag");
        for (var kind : MachineKind.values()) if (kind.controller() && !moduleLimits.containsKey(kind.id())) throw new IllegalArgumentException("Missing module limits: " + kind.id());
        moduleLimits.values().forEach(m -> m.forEach((k,v) -> { if (v == null || v < 0 || v > maxConnectedBlocks) throw new IllegalArgumentException("Invalid module limit: " + k); }));
    }
    public static void load(Path path) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) INSTANCE = gson.fromJson(Files.readString(path), GeneratorConfig.class);
            else { Files.createDirectories(path.getParent()); Files.writeString(path, gson.toJson(INSTANCE)); }
            if (INSTANCE == null) throw new IllegalArgumentException("Empty config");
            INSTANCE.validate();
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Invalid Advanced Generators config: " + path, e); }
    }
}

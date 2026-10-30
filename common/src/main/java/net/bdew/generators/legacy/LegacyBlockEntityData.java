package net.bdew.generators.legacy;

import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

import java.util.List;
import java.util.Map;

/**
 * Reads block entity data saved by the original 1.20.1 (Scala/BDLib) mod.
 *
 * <p>The original stored every machine in its own block entity type, with each BDLib data slot written as a
 * top-level key. This class translates such a tag into the layout {@code MachineBlockEntity} saves today, so
 * existing machines keep their contents and connections after a world upgrade. The formats are those written by
 * BDLib 1.27 / Advanced Generators 1.6 (see {@code DataSlot*} in BDLib).
 *
 * <p>Old-format tags are recognised by the absence of the {@code energy} key, which every current save contains.
 */
public final class LegacyBlockEntityData {
    private LegacyBlockEntityData() {}

    private static final String STEAM = "advgenerators:steam";
    private static final int MAX_OUTPUTS = 6;

    /** Legacy sensor ids, in the position they had in each controller's list (1-based, as stored today). */
    private static final Map<String, Integer> SENSOR_METRIC = Map.ofEntries(
        Map.entry("power", 1),
        Map.entry("turbine.fuel", 2), Map.entry("turbine.steam", 2), Map.entry("turbine.generation", 3), Map.entry("turbine.speed", 3),
        Map.entry("syngas.water", 1), Map.entry("syngas.syngas", 2), Map.entry("syngas.steam", 3), Map.entry("syngas.carbon", 4), Map.entry("syngas.heat", 5),
        Map.entry("exchanger.hot.input", 1), Map.entry("exchanger.hot.output", 2), Map.entry("exchanger.cold.input", 3),
        Map.entry("exchanger.cold.output", 4), Map.entry("exchanger.heat", 5));

    private static final List<String> FILL = List.of("fill.empty", "fill.not.empty", "fill.gt5", "fill.gt25", "fill.gt50", "fill.gt75", "fill.gt95", "fill.not.full", "fill.full");
    private static final List<String> RESOURCE = List.of("fill.empty", "resource.solid", "resource.fluid", "fill.gt5", "fill.gt25", "fill.gt50", "fill.gt75", "fill.gt95", "fill.not.full", "fill.full");
    private static final List<String> ANALOG_SUFFIX = List.of("stop", "low", "medium", "high");
    private static final List<String> HEAT_SUFFIX = List.of("cold", "low", "medium", "high");

    private static final Map<String, Integer> CONTROL_ACTION = Map.of(
        "disabled", 0, "useFuel", 1, "useSteam", 2, "generateEnergy", 3, "exchangeHeat", 4, "heatWater", 5, "mix", 6);
    private static final Map<String, Integer> EXCHANGER_SLOT = Map.of("BOTH", 0, "COLD", 1, "HOT", 2);

    public static boolean isLegacy(CompoundTag tag) {
        return !tag.contains("energy");
    }

    /** Returns the tag unchanged if it is already in the current format, otherwise a converted copy. */
    public static CompoundTag upgrade(CompoundTag tag, MachineKind kind) {
        return isLegacy(tag) ? convert(tag, kind) : tag;
    }

    public static CompoundTag convert(CompoundTag old, MachineKind kind) {
        // Start from a copy so vanilla and loader metadata (components, attachments) survive.
        CompoundTag out = old.copy();

        if (!kind.controller() && old.contains("connected", Tag.TAG_COMPOUND)) {
            var pos = old.getCompound("connected");
            if (pos.contains("x", Tag.TAG_INT) && pos.contains("y", Tag.TAG_INT) && pos.contains("z", Tag.TAG_INT))
                out.putLong("connectedCore", BlockPos.asLong(pos.getInt("x"), pos.getInt("y"), pos.getInt("z")));
        }

        float stored = old.getCompound("power").getFloat("stored");
        long whole = (long) Math.floor(Math.max(0, stored));
        out.putLong("energy", whole);
        out.putDouble("fraction", Math.min(.999999, Math.max(0, stored - whole)));

        out.putDouble("heat", old.getDouble("heat"));
        out.putDouble("carbon", old.getDouble("carbon"));
        out.putDouble("burnTime", old.getFloat("burnTime"));
        out.putDouble("rpm", old.getDouble("speed"));

        switch (kind) {
            case FUEL_TURBINE_CONTROLLER -> out.put("buffer0", tank(old.getCompound("fuel")));
            case STEAM_TURBINE_CONTROLLER -> out.put("buffer0", tank(old.getCompound("steam")));
            case SYNGAS_CONTROLLER -> {
                out.put("buffer0", tank(old.getCompound("waterTank")));
                out.put("buffer1", buffer(false, STEAM, old.getDouble("steam")));
                out.put("buffer2", tank(old.getCompound("syngasTank")));
                out.put("inventory", inventory(old.getList("inv", Tag.TAG_COMPOUND)));
            }
            case EXCHANGER_CONTROLLER -> {
                // heater = hot input, cooler = cold input; the hot output is the cooler's product.
                out.put("buffer0", resource(old.getCompound("heaterIn")));
                out.put("buffer1", resource(old.getCompound("coolerIn")));
                out.put("buffer2", resource(old.getCompound("coolerOut")));
                out.put("buffer3", resource(old.getCompound("heaterOut")));
                lastResource(old, "lastInputRes", out, "lastExchangerInput");
                lastResource(old, "lastOutputRes", out, "lastExchangerOutput");
            }
            case CONTROL -> {
                out.putInt("action", CONTROL_ACTION.getOrDefault(old.getString("action"), 0));
                out.putBoolean("high", old.getBoolean("mode"));
            }
            case SENSOR -> {
                var sensor = old.getCompound("sensor");
                String uid = sensor.getString("uid");
                out.putInt("metric", SENSOR_METRIC.getOrDefault(uid, 0));
                out.putInt("condition", SENSOR_METRIC.containsKey(uid) ? Math.max(0, sensorCondition(uid, sensor.getString("param"))) : 0);
            }
            default -> {}
        }

        if (kind.controller()) out.put("outputConfigs", outputConfigs(old));
        return out;
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private static CompoundTag buffer(boolean item, String id, double amount) {
        var tag = new CompoundTag();
        if (amount > 0 && id != null && !id.isEmpty()) {
            tag.putString("id", id);
            tag.putBoolean("item", item);
            tag.putDouble("amount", amount);
        }
        return tag;
    }

    /** Forge FluidTank: {FluidName, Amount}, or {Empty: ""}. */
    private static CompoundTag tank(CompoundTag tank) {
        if (tank.contains("Empty") || !tank.contains("FluidName", Tag.TAG_STRING)) return new CompoundTag();
        return buffer(false, tank.getString("FluidName"), tank.getInt("Amount"));
    }

    /** BDLib DataSlotResource: {resource: {kind: "fluid"|"item", fluid|id, amount}}. */
    private static CompoundTag resource(CompoundTag slot) {
        if (!slot.contains("resource", Tag.TAG_COMPOUND)) return new CompoundTag();
        var res = slot.getCompound("resource");
        boolean item = res.getString("kind").equals("item");
        return buffer(item, item ? res.getString("id") : res.getString("fluid"), res.getDouble("amount"));
    }

    private static void lastResource(CompoundTag old, String key, CompoundTag out, String outKey) {
        if (!old.contains(key, Tag.TAG_COMPOUND)) return;
        var kind = old.getCompound(key);
        boolean item = kind.getString("kind").equals("item");
        String id = item ? kind.getString("id") : kind.getString("fluid");
        if (id.isEmpty()) return;
        out.putString(outKey, id);
        out.putBoolean(outKey + "Item", item);
    }

    /** BDLib DataSlotInventory: [{Slot, id, Count, tag?}] becomes the current [{Slot, id, count}]. */
    private static ListTag inventory(ListTag list) {
        var result = new ListTag();
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag stack) || !stack.contains("id", Tag.TAG_STRING)) continue;
            int count = stack.getByte("Count");
            if (count <= 0) continue;
            var converted = new CompoundTag();
            converted.putByte("Slot", stack.getByte("Slot"));
            converted.putString("id", stack.getString("id"));
            converted.putInt("count", count);
            result.add(converted);
        }
        return result;
    }

    /** Parameter id to the index of that condition in the sensor's list today. */
    private static int sensorCondition(String sensorUid, String param) {
        if (sensorUid.startsWith("exchanger.") && !sensorUid.equals("exchanger.heat")) return RESOURCE.indexOf(param);
        if (sensorUid.equals("exchanger.heat") || sensorUid.equals("syngas.heat")) return indexOfSuffix(param, "heat." + sensorUid.substring(0, sensorUid.indexOf('.')) + ".", HEAT_SUFFIX);
        if (sensorUid.equals("turbine.speed")) return indexOfSuffix(param, "turbine.speed.", ANALOG_SUFFIX);
        if (sensorUid.equals("turbine.generation")) return indexOfSuffix(param, "turbine.generation.", ANALOG_SUFFIX);
        return FILL.indexOf(param);
    }

    private static int indexOfSuffix(String param, String prefix, List<String> suffixes) {
        return param.startsWith(prefix) ? suffixes.indexOf(param.substring(prefix.length())) : -1;
    }

    /**
     * BDLib CIOutputFaces: {@code outputs} is a list of int[]{index, x, y, z, faceOrdinal} and {@code cfg_<index>}
     * holds that output's config. Face ordinals are the same order as {@code Direction.get3DDataValue()}.
     */
    private static ListTag outputConfigs(CompoundTag old) {
        var result = new ListTag();
        for (Tag entry : old.getList("outputs", Tag.TAG_INT_ARRAY)) {
            if (result.size() >= MAX_OUTPUTS) break;
            int[] a = ((IntArrayTag) entry).getAsIntArray();
            if (a.length < 5 || a[4] < 0 || a[4] > 5) continue;
            var cfg = old.getCompound("cfg_" + a[0]);
            var face = new CompoundTag();
            face.putLong("position", BlockPos.asLong(a[1], a[2], a[3]));
            face.putInt("direction", a[4]);
            face.putInt("mode", Math.max(0, Math.min(3, cfg.getInt("rsMode"))));
            face.putInt("selection", EXCHANGER_SLOT.getOrDefault(cfg.getString("slot"), 0));
            face.putLong("limit", Integer.MAX_VALUE);
            face.putDouble("average", average(cfg));
            result.add(face);
        }
        return result;
    }

    private static double average(CompoundTag cfg) {
        if (cfg.contains("avg", Tag.TAG_DOUBLE)) return cfg.getDouble("avg");
        var values = cfg.getList("values", Tag.TAG_DOUBLE);
        if (values.isEmpty()) return 0;
        double sum = 0;
        for (int i = 0; i < values.size(); i++) sum += values.getDouble(i);
        return sum / values.size();
    }
}

package net.bdew.generators.legacy;

import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixtures are written the way BDLib 1.27 / Advanced Generators 1.6 saved them (DataSlot* classes), not copied from
 * a live world: primitives are top-level keys, tanks use Forge's FluidName/Amount, stacks use id/Count/tag.
 */
class LegacyBlockEntityDataTest {
    private static CompoundTag power(float stored) { var t = new CompoundTag(); t.putFloat("stored", stored); return t; }
    private static CompoundTag fluid(String name, int amount) { var t = new CompoundTag(); t.putString("FluidName", name); t.putInt("Amount", amount); return t; }
    private static CompoundTag emptyTank() { var t = new CompoundTag(); t.putString("Empty", ""); return t; }
    private static CompoundTag pos(int x, int y, int z) { var t = new CompoundTag(); t.putInt("x", x); t.putInt("y", y); t.putInt("z", z); return t; }
    private static CompoundTag fluidResource(String fluid, double amount) {
        var res = new CompoundTag(); res.putString("kind", "fluid"); res.putString("fluid", fluid); res.putDouble("amount", amount);
        var slot = new CompoundTag(); slot.put("resource", res); return slot;
    }
    private static CompoundTag itemResource(String id, double amount) {
        var res = new CompoundTag(); res.putString("kind", "item"); res.putString("id", id); res.putDouble("amount", amount);
        var slot = new CompoundTag(); slot.put("resource", res); return slot;
    }
    private static IntArrayTag output(int index, int x, int y, int z, int face) { return new IntArrayTag(new int[]{index, x, y, z, face}); }

    @Test void currentFormatIsLeftAlone() {
        var tag = new CompoundTag();
        tag.putLong("energy", 5);
        assertSame(tag, LegacyBlockEntityData.upgrade(tag, MachineKind.FE_OUTPUT));
    }

    @Test void emptyModuleTagConvertsToDefaults() {
        var out = LegacyBlockEntityData.upgrade(new CompoundTag(), MachineKind.TURBINE_TIER3);
        assertFalse(LegacyBlockEntityData.isLegacy(out));
        assertEquals(0, out.getLong("energy"));
        assertFalse(out.contains("connectedCore"));
    }

    @Test void moduleKeepsItsConnection() {
        var old = new CompoundTag();
        old.put("connected", pos(10, -60, 3));
        var out = LegacyBlockEntityData.convert(old, MachineKind.POWER_CAPACITOR_TIER2);
        assertEquals(BlockPos.asLong(10, -60, 3), out.getLong("connectedCore"));
    }

    @Test void fuelTurbineKeepsPowerFuelAndOutputs() {
        var old = new CompoundTag();
        old.put("power", power(12345.75f));
        old.put("fuel", fluid("minecraft:lava", 750));
        old.putFloat("burnTime", 3.5f);
        var outputs = new ListTag();
        outputs.add(output(0, 4, -60, 0, 5));
        outputs.add(output(2, 4, -59, 1, 0));
        old.put("outputs", outputs);
        var cfg0 = new CompoundTag(); cfg0.putString("kind", "power"); cfg0.putInt("rsMode", 1); cfg0.putDouble("avg", 12.5); cfg0.putString("unit", "fe");
        old.put("cfg_0", cfg0);

        var out = LegacyBlockEntityData.convert(old, MachineKind.FUEL_TURBINE_CONTROLLER);
        assertEquals(12345, out.getLong("energy"));
        assertEquals(.75, out.getDouble("fraction"), 1e-6);
        assertEquals(3.5, out.getDouble("burnTime"), 1e-6);
        var fuel = out.getCompound("buffer0");
        assertEquals("minecraft:lava", fuel.getString("id"));
        assertFalse(fuel.getBoolean("item"));
        assertEquals(750, fuel.getDouble("amount"), 1e-9);

        var faces = out.getList("outputConfigs", Tag.TAG_COMPOUND);
        assertEquals(2, faces.size());
        var first = faces.getCompound(0);
        assertEquals(BlockPos.asLong(4, -60, 0), first.getLong("position"));
        assertEquals(5, first.getInt("direction"));
        assertEquals(1, first.getInt("mode"));
        assertEquals(12.5, first.getDouble("average"), 1e-9);
        assertEquals(Integer.MAX_VALUE, first.getLong("limit"));
        assertEquals(0, faces.getCompound(1).getInt("mode"), "a face without a saved config falls back to Always");
    }

    @Test void steamTurbineKeepsSpeedAndSteam() {
        var old = new CompoundTag();
        old.putDouble("speed", 1234.5);
        old.put("steam", fluid("advgenerators:steam", 4000));
        old.put("power", power(10f));
        var out = LegacyBlockEntityData.convert(old, MachineKind.STEAM_TURBINE_CONTROLLER);
        assertEquals(1234.5, out.getDouble("rpm"), 1e-9);
        assertEquals("advgenerators:steam", out.getCompound("buffer0").getString("id"));
        assertEquals(4000, out.getCompound("buffer0").getDouble("amount"), 1e-9);
        assertEquals(10, out.getLong("energy"));
    }

    @Test void emptyTankBecomesEmptyBuffer() {
        var old = new CompoundTag();
        old.put("fuel", emptyTank());
        assertTrue(LegacyBlockEntityData.convert(old, MachineKind.FUEL_TURBINE_CONTROLLER).getCompound("buffer0").isEmpty());
    }

    @Test void syngasKeepsTanksBuffersAndCarbonItems() {
        var old = new CompoundTag();
        old.putDouble("carbon", 800.5); old.putDouble("steam", 120); old.putDouble("heat", 175.25);
        old.put("waterTank", fluid("minecraft:water", 9000));
        old.put("syngasTank", fluid("advgenerators:syngas", 3000));
        var inv = new ListTag();
        var coal = new CompoundTag(); coal.putByte("Slot", (byte) 1); coal.putString("id", "minecraft:coal"); coal.putByte("Count", (byte) 3);
        inv.add(coal);
        old.put("inv", inv);
        var outputs = new ListTag(); outputs.add(output(0, 1, 2, 3, 1)); old.put("outputs", outputs);
        var cfg = new CompoundTag(); cfg.putString("kind", "fluidslots_syngas"); cfg.putInt("rsMode", 3); cfg.putString("slot", "SYNGAS");
        var values = new ListTag(); values.add(DoubleTag.valueOf(10)); values.add(DoubleTag.valueOf(20)); cfg.put("values", values);
        old.put("cfg_0", cfg);

        var out = LegacyBlockEntityData.convert(old, MachineKind.SYNGAS_CONTROLLER);
        assertEquals(800.5, out.getDouble("carbon"), 1e-9);
        assertEquals(175.25, out.getDouble("heat"), 1e-9);
        assertEquals("minecraft:water", out.getCompound("buffer0").getString("id"));
        assertEquals("advgenerators:steam", out.getCompound("buffer1").getString("id"));
        assertEquals(120, out.getCompound("buffer1").getDouble("amount"), 1e-9);
        assertEquals(3000, out.getCompound("buffer2").getDouble("amount"), 1e-9);
        var stack = out.getList("inventory", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(1, stack.getByte("Slot"));
        assertEquals("minecraft:coal", stack.getString("id"));
        assertEquals(3, stack.getInt("count"));
        var face = out.getList("outputConfigs", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(3, face.getInt("mode"));
        assertEquals(15, face.getDouble("average"), 1e-9, "fluid outputs averaged their last samples");
    }

    @Test void exchangerKeepsAllFourBuffersAndLastResources() {
        var old = new CompoundTag();
        old.put("heaterIn", fluidResource("minecraft:lava", 400));
        old.put("coolerIn", fluidResource("minecraft:water", 1000));
        old.put("coolerOut", fluidResource("advgenerators:steam", 2500));
        old.put("heaterOut", itemResource("minecraft:obsidian", 0.5));
        old.putDouble("heat", 321.5);
        var lastIn = new CompoundTag(); lastIn.putString("kind", "fluid"); lastIn.putString("fluid", "minecraft:lava"); old.put("lastInputRes", lastIn);
        var lastOut = new CompoundTag(); lastOut.putString("kind", "item"); lastOut.putString("id", "minecraft:obsidian"); old.put("lastOutputRes", lastOut);
        var outputs = new ListTag(); outputs.add(output(0, 7, 8, 9, 3)); old.put("outputs", outputs);
        var cfg = new CompoundTag(); cfg.putString("kind", "fluidslots_exchanger"); cfg.putInt("rsMode", 2); cfg.putString("slot", "COLD");
        old.put("cfg_0", cfg);

        var out = LegacyBlockEntityData.convert(old, MachineKind.EXCHANGER_CONTROLLER);
        assertEquals(321.5, out.getDouble("heat"), 1e-9);
        assertEquals("minecraft:lava", out.getCompound("buffer0").getString("id"), "hot input");
        assertEquals("minecraft:water", out.getCompound("buffer1").getString("id"), "cold input");
        assertEquals("advgenerators:steam", out.getCompound("buffer2").getString("id"), "hot output");
        var obsidian = out.getCompound("buffer3");
        assertEquals("minecraft:obsidian", obsidian.getString("id"), "cold output");
        assertTrue(obsidian.getBoolean("item"));
        assertEquals(.5, obsidian.getDouble("amount"), 1e-9);
        assertEquals("minecraft:lava", out.getString("lastExchangerInput"));
        assertFalse(out.getBoolean("lastExchangerInputItem"));
        assertEquals("minecraft:obsidian", out.getString("lastExchangerOutput"));
        assertTrue(out.getBoolean("lastExchangerOutputItem"));
        var face = out.getList("outputConfigs", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(1, face.getInt("selection"), "COLD is the second option");
        assertEquals(2, face.getInt("mode"));
    }

    @Test void outputSlotsMapToTheSameOptionOrder() {
        for (var entry : java.util.Map.of("BOTH", 0, "COLD", 1, "HOT", 2).entrySet()) {
            var old = new CompoundTag();
            var outputs = new ListTag(); outputs.add(output(0, 0, 0, 0, 0)); old.put("outputs", outputs);
            var cfg = new CompoundTag(); cfg.putString("slot", entry.getKey()); old.put("cfg_0", cfg);
            var out = LegacyBlockEntityData.convert(old, MachineKind.EXCHANGER_CONTROLLER);
            assertEquals(entry.getValue(), out.getList("outputConfigs", Tag.TAG_COMPOUND).getCompound(0).getInt("selection"), entry.getKey());
        }
    }

    @Test void atMostSixOutputsAreKept() {
        var old = new CompoundTag();
        var outputs = new ListTag();
        for (int i = 0; i < 9; i++) outputs.add(output(i, i, 0, 0, 0));
        old.put("outputs", outputs);
        assertEquals(6, LegacyBlockEntityData.convert(old, MachineKind.FUEL_TURBINE_CONTROLLER).getList("outputConfigs", Tag.TAG_COMPOUND).size());
    }

    @Test void controlModuleKeepsActionAndMode() {
        var expectedActions = java.util.Map.of("disabled", 0, "useFuel", 1, "useSteam", 2, "generateEnergy", 3, "exchangeHeat", 4, "heatWater", 5, "mix", 6);
        expectedActions.forEach((uid, id) -> {
            var old = new CompoundTag();
            old.putString("action", uid);
            old.putBoolean("mode", false);
            var out = LegacyBlockEntityData.convert(old, MachineKind.CONTROL);
            assertEquals(id, out.getInt("action"), uid);
            assertFalse(out.getBoolean("high"));
        });
        var old = new CompoundTag(); old.putString("action", "mix"); old.putBoolean("mode", true);
        assertTrue(LegacyBlockEntityData.convert(old, MachineKind.CONTROL).getBoolean("high"));
    }

    private static int[] sensor(String uid, String param) {
        var old = new CompoundTag();
        var s = new CompoundTag(); s.putString("uid", uid); s.putString("param", param); old.put("sensor", s);
        var out = LegacyBlockEntityData.convert(old, MachineKind.SENSOR);
        return new int[]{out.getInt("metric"), out.getInt("condition")};
    }

    @Test void sensorSelectionsKeepTheirPositions() {
        assertArrayEquals(new int[]{1, 4}, sensor("power", "fill.gt50"));
        assertArrayEquals(new int[]{2, 8}, sensor("turbine.fuel", "fill.full"));
        assertArrayEquals(new int[]{3, 2}, sensor("turbine.generation", "turbine.generation.medium"));
        assertArrayEquals(new int[]{3, 3}, sensor("turbine.speed", "turbine.speed.high"));
        assertArrayEquals(new int[]{3, 6}, sensor("syngas.steam", "fill.gt95"));
        assertArrayEquals(new int[]{5, 1}, sensor("syngas.heat", "heat.syngas.low"));
        assertArrayEquals(new int[]{1, 2}, sensor("exchanger.hot.input", "resource.fluid"));
        assertArrayEquals(new int[]{4, 9}, sensor("exchanger.cold.output", "fill.full"));
        assertArrayEquals(new int[]{5, 3}, sensor("exchanger.heat", "heat.exchanger.high"));
    }

    @Test void disabledOrUnknownSensorStaysDisabled() {
        assertArrayEquals(new int[]{0, 0}, sensor("disabled", "disabled"));
        assertArrayEquals(new int[]{0, 0}, sensor("some.future.sensor", "x"));
    }
}

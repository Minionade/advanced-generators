package net.bdew.generators.legacy;

import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block entity tags written by the real 1.20.1 mod (Advanced Generators 1.6.0 built from the mc1.20.1 branch, Forge
 * 47.1.39, BDLib 1.27.0.6), read back with {@code /data get block} from a running server. Expected values are
 * derived from the saved tag itself, so the test does not depend on which instant of the machine was captured.
 */
class RealSaveTest {
    private static CompoundTag real(String name) throws Exception {
        try (var in = RealSaveTest.class.getResourceAsStream("/legacy/real/" + name + ".snbt")) {
            assertNotNull(in, name);
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }

    private static CompoundTag convert(String name, MachineKind kind) throws Exception {
        var out = LegacyBlockEntityData.upgrade(real(name), kind);
        assertFalse(LegacyBlockEntityData.isLegacy(out), name + " must be recognised as converted");
        return out;
    }

    private static long pos(CompoundTag tag) { return BlockPos.asLong(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")); }

    private static void assertTank(CompoundTag savedTank, CompoundTag buffer) {
        if (savedTank.getInt("Amount") <= 0) { assertTrue(buffer.isEmpty(), "empty tank, empty buffer"); return; }
        assertEquals(savedTank.getString("FluidName"), buffer.getString("id"));
        assertFalse(buffer.getBoolean("item"));
        assertEquals(savedTank.getInt("Amount"), buffer.getDouble("amount"), 1e-9);
    }

    private static void assertResource(CompoundTag saved, CompoundTag buffer) {
        if (!saved.contains("resource")) { assertTrue(buffer.isEmpty()); return; }
        var res = saved.getCompound("resource");
        boolean item = res.getString("kind").equals("item");
        assertEquals(item ? res.getString("id") : res.getString("fluid"), buffer.getString("id"));
        assertEquals(item, buffer.getBoolean("item"));
        assertEquals(res.getDouble("amount"), buffer.getDouble("amount"), 1e-9);
    }

    @Test void fuelTurbineController() throws Exception {
        var saved = real("fuel_turbine_controller");
        var out = convert("fuel_turbine_controller", MachineKind.FUEL_TURBINE_CONTROLLER);
        float stored = saved.getCompound("power").getFloat("stored");
        assertEquals((long) Math.floor(stored), out.getLong("energy"));
        assertEquals(stored - Math.floor(stored), out.getDouble("fraction"), 1e-4);
        assertEquals(saved.getFloat("burnTime"), out.getDouble("burnTime"), 1e-6);
        assertTank(saved.getCompound("fuel"), out.getCompound("buffer0"));
        assertEquals(saved.getList("outputs", Tag.TAG_INT_ARRAY).size(), out.getList("outputConfigs", Tag.TAG_COMPOUND).size());
        assertEquals("advgenerators:fuel_turbine_controller", out.getString("id"));
    }

    @Test void moduleConnection() throws Exception {
        var saved = real("turbine_tier1");
        var out = convert("turbine_tier1", MachineKind.TURBINE_TIER1);
        assertEquals(pos(saved.getCompound("connected")), out.getLong("connectedCore"));
        assertEquals(BlockPos.asLong(0, -60, 0), out.getLong("connectedCore"));
    }

    @Test void outputModuleConnection() throws Exception {
        var out = convert("fe_output", MachineKind.FE_OUTPUT);
        assertEquals(BlockPos.asLong(0, -60, 0), out.getLong("connectedCore"));
        out = convert("item_output", MachineKind.ITEM_OUTPUT);
        assertEquals(BlockPos.asLong(5, -60, 0), out.getLong("connectedCore"));
    }

    @Test void controlModule() throws Exception {
        var saved = real("control");
        var out = convert("control", MachineKind.CONTROL);
        assertEquals("useFuel", saved.getString("action"));
        assertEquals(1, out.getInt("action"));
        assertEquals(saved.getBoolean("mode"), out.getBoolean("high"));
    }

    @Test void sensorModule() throws Exception {
        var out = convert("sensor", MachineKind.SENSOR);
        assertEquals(2, out.getInt("metric"), "turbine.fuel is the second choice of a fuel turbine");
        assertEquals(4, out.getInt("condition"), "fill.gt50 is the fifth condition");
    }

    @Test void exchangerController() throws Exception {
        var saved = real("exchanger_controller");
        var out = convert("exchanger_controller", MachineKind.EXCHANGER_CONTROLLER);
        assertEquals(saved.getDouble("heat"), out.getDouble("heat"), 1e-9);
        assertResource(saved.getCompound("heaterIn"), out.getCompound("buffer0"));
        assertResource(saved.getCompound("coolerIn"), out.getCompound("buffer1"));
        assertResource(saved.getCompound("coolerOut"), out.getCompound("buffer2"));
        assertResource(saved.getCompound("heaterOut"), out.getCompound("buffer3"));
        assertEquals("minecraft:lava", out.getString("lastExchangerInput"));
        assertFalse(out.getBoolean("lastExchangerInputItem"));
        assertEquals("advgenerators:steam", out.getString("lastExchangerOutput"));

        ListTag faces = out.getList("outputConfigs", Tag.TAG_COMPOUND);
        assertEquals(1, faces.size(), "the item output facing the chest");
        var face = faces.getCompound(0);
        assertEquals(BlockPos.asLong(5, -60, 1), face.getLong("position"));
        assertEquals(3, face.getInt("direction"), "south, towards the chest");
        assertEquals(saved.getCompound("cfg_0").getInt("rsMode"), face.getInt("mode"));
    }

    @Test void syngasController() throws Exception {
        var saved = real("syngas_controller");
        var out = convert("syngas_controller", MachineKind.SYNGAS_CONTROLLER);
        assertEquals(saved.getDouble("carbon"), out.getDouble("carbon"), 1e-9);
        assertEquals(saved.getDouble("heat"), out.getDouble("heat"), 1e-9);
        assertTank(saved.getCompound("waterTank"), out.getCompound("buffer0"));
        assertEquals(saved.getDouble("steam"), out.getCompound("buffer1").getDouble("amount"), 1e-9);
        assertEquals("advgenerators:steam", out.getCompound("buffer1").getString("id"));
        assertTank(saved.getCompound("syngasTank"), out.getCompound("buffer2"));
        assertEquals(saved.getList("inv", Tag.TAG_COMPOUND).size(), out.getList("inventory", Tag.TAG_COMPOUND).size());
    }

    @Test void steamTurbineController() throws Exception {
        var saved = real("steam_turbine_controller");
        var out = convert("steam_turbine_controller", MachineKind.STEAM_TURBINE_CONTROLLER);
        assertEquals(saved.getDouble("speed"), out.getDouble("rpm"), 1e-9);
        assertTank(saved.getCompound("steam"), out.getCompound("buffer0"));
        assertEquals((long) Math.floor(saved.getCompound("power").getFloat("stored")), out.getLong("energy"));
    }
}

package net.bdew.generators;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The English block names of the original 1.20.1 mod, which this port keeps (except "Flux Generator", renamed "FE Generator"). */
class LangParityTest {
    private static final Map<String, String> ORIGINAL = Map.ofEntries(
        Map.entry("block.advgenerators.fuel_turbine_controller", "Gas Turbine controller"),
        Map.entry("block.advgenerators.fe_output", "FE Generator"),
        Map.entry("block.advgenerators.fluid_input", "Fluid Intake Valve"),
        Map.entry("block.advgenerators.fluid_output_select", "Fluid Output Valve"),
        Map.entry("block.advgenerators.item_input", "Item Input Port"),
        Map.entry("block.advgenerators.item_output", "Item Output Port"),
        Map.entry("block.advgenerators.sensor", "Sensor Module"),
        Map.entry("block.advgenerators.control", "Redstone Control Module"),
        Map.entry("block.advgenerators.efficiency_upgrade_tier1", "Fuel/Air Mixer"),
        Map.entry("block.advgenerators.efficiency_upgrade_tier2", "Gas Mix Compressor"),
        Map.entry("block.advgenerators.power_capacitor_tier1", "Basic Power Capacitor"),
        Map.entry("block.advgenerators.power_capacitor_tier2", "Advanced Power Capacitor"),
        Map.entry("block.advgenerators.power_capacitor_tier3", "High Density Power Capacitor"));

    @Test void blockNamesMatchTheOriginalMod() throws Exception {
        try (var in = LangParityTest.class.getResourceAsStream("/assets/advgenerators/lang/en_us.json")) {
            assertNotNull(in);
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            ORIGINAL.forEach((key, name) -> assertEquals(name, lang.get(key).getAsString(), key));
        }
    }
}

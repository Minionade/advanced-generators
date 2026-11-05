package net.bdew.generators.config;

import net.bdew.generators.legacy.LegacyConfig;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuelOverridesTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse(s); }

    @Test void parsesFluidsAndTagsAndSkipsBadLines() {
        var errors = new ArrayList<String>();
        var entries = FuelOverrides.parse(List.of("minecraft:lava=200", "#c:fuel_light=3000", "nonsense", "minecraft:water=-1", "a:b=x"), errors);
        assertEquals(2, entries.size());
        assertEquals(3, errors.size());
        assertFalse(entries.get(0).tag());
        assertTrue(entries.get(1).tag());
    }

    @Test void firstMatchWinsAndTagsUseTheTagTest() {
        var entries = FuelOverrides.parse(List.of("#c:fuel_light=3000", "minecraft:lava=200", "minecraft:lava=999"), new ArrayList<>());
        assertEquals(3000, FuelOverrides.find(entries, id("x:light"), t -> t.equals(id("c:fuel_light"))).getAsDouble());
        assertEquals(200, FuelOverrides.find(entries, id("minecraft:lava"), t -> false).getAsDouble());
        assertTrue(FuelOverrides.find(entries, id("minecraft:water"), t -> false).isEmpty());
    }

    @Test void reforgedConfigSectionIsImported() {
        var config = LegacyConfig.fromToml("[Fuels]\nMultiplier = 2.5\nFuelValues = [\"minecraft:lava=200\"]\n");
        assertEquals(2.5, config.fuelMultiplier);
        assertEquals(List.of("minecraft:lava=200"), config.fuelValues);
        assertEquals(1, config.fuelOverrides().size());
    }
}

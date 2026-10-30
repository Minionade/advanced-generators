package net.bdew.generators.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the heat exchanger against the inversion fixed in this change: lava (a "cooling" recipe) must go
 * to the hot input, water (a "heating" recipe) to the cold input.
 */
class ExchangerRolesTest {
    private static String recipeJson(String name, String key) throws IOException {
        try (var in = ExchangerRolesTest.class.getResourceAsStream("/data/advgenerators/recipe/exchanger/" + name + ".json")) {
            assertNotNull(in, name);
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get(key).getAsString();
        }
    }

    @Test void hotInputIsSlotZeroAndColdInputIsSlotOne() {
        assertEquals(ExchangerRoles.HOT_INPUT, ExchangerRoles.inputType(0));
        assertEquals(ExchangerRoles.COLD_INPUT, ExchangerRoles.inputType(1));
    }

    @Test void lavaRecipeIsAcceptedByTheHotInput() throws IOException {
        assertEquals("advgenerators:" + ExchangerRoles.HOT_INPUT, recipeJson("lava_to_obsidian", "type"));
    }

    @Test void waterRecipeIsAcceptedByTheColdInput() throws IOException {
        assertEquals("advgenerators:" + ExchangerRoles.COLD_INPUT, recipeJson("water_to_steam", "type"));
    }
}

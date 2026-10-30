package net.bdew.generators.machine;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A model naming a texture that is not in the jar shows up in the game as a purple and black face. */
class ModelTexturesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/advgenerators");
    private static final Pattern TEXTURE = Pattern.compile("\"advgenerators:((?:block|item)/[^\"]+)\"");

    @Test void everyTextureAModelNamesExists() throws Exception {
        var missing = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(ASSETS.resolve("models"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                var matcher = TEXTURE.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (matcher.group(1).equals("block/rotated")) continue; // a parent model, not a texture
                    if (!Files.exists(ASSETS.resolve("textures/" + matcher.group(1) + ".png")))
                        missing.add(file.getFileName() + " -> " + matcher.group(1));
                }
            }
        }
        assertEquals(new ArrayList<String>(), missing);
    }

    /** The original draws the connected-texture frame on every face of a machine item (ConnectedModelEnhancer.processItemQuads). */
    @Test void machineItemsCarryTheFrame() throws Exception {
        for (String name : new String[] {"fuel_tank", "steam_turbine_controller", "sensor", "eu_output_lv", "turbine_tier3"}) {
            String json = Files.readString(ASSETS.resolve("models/item/" + name + ".json"));
            assertTrue(json.contains("advgenerators:block/connected/edge"), name);
        }
    }
}
